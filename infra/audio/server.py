"""CPU-only speech service. Models are downloaded once; requests stay local."""
import audioop
import io
import json
import logging
import os
import threading
import wave
from email import policy
from email.parser import BytesParser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

MAX_WAV_BYTES = 1_440_044
SAMPLE_RATE = 24_000


class AudioEngine:
    def __init__(self):
        from faster_whisper import WhisperModel
        from huggingface_hub import snapshot_download
        from piper import PiperVoice
        from piper.download_voices import download_voice

        self.model_name = os.environ.get("WHISPER_MODEL", "small")
        if self.model_name not in {"tiny", "base", "small", "medium"}:
            raise ValueError("WHISPER_MODEL must be tiny, base, small or medium")
        self.voice_name = os.environ.get("PIPER_VOICE", "pt_BR-faber-medium")
        models = Path(os.environ.get("MODELS_DIRECTORY", "/models"))
        whisper_path = models / "whisper" / self.model_name
        if not all((whisper_path / name).is_file() for name in ("model.bin", "config.json", "tokenizer.json", "vocabulary.txt")):
            logging.info("Downloading local transcription model: %s", self.model_name)
            snapshot_download("Systran/faster-whisper-" + self.model_name, local_dir=str(whisper_path),
                              allow_patterns=["config.json", "model.bin", "tokenizer.json", "vocabulary.*", "preprocessor_config.json"])
        self.whisper = WhisperModel(str(whisper_path), device="cpu", compute_type="int8",
                                    cpu_threads=4, local_files_only=True)
        voice_path = models / "piper"
        voice_path.mkdir(parents=True, exist_ok=True)
        download_voice(self.voice_name, voice_path)
        self.piper = PiperVoice.load(str(voice_path / (self.voice_name + ".onnx")))
        self.transcription_lock = threading.Lock()
        self.synthesis_lock = threading.Lock()

    def transcribe(self, wav):
        if not self.transcription_lock.acquire(timeout=60):
            raise TimeoutError("Transcription busy")
        try:
            segments, _ = self.whisper.transcribe(io.BytesIO(wav), language="pt", beam_size=5,
                                                  vad_filter=True, condition_on_previous_text=False,
                                                  initial_prompt="Clínica, consultas, agendamentos e horários. Segunda-feira, terça-feira, quarta-feira, quinta-feira, sexta-feira, sábado e domingo. Atendimento aos domingos.")
            return " ".join(segment.text.strip() for segment in segments).strip()
        finally:
            self.transcription_lock.release()

    def synthesize(self, text):
        if not self.synthesis_lock.acquire(timeout=60):
            raise TimeoutError("Synthesis busy")
        try:
            state = None
            for chunk in self.piper.synthesize(text):
                pcm, state = audioop.ratecv(chunk.audio_int16_bytes, 2, 1, chunk.sample_rate, SAMPLE_RATE, state)
                for start in range(0, len(pcm), 4800):
                    yield pcm[start:start + 4800]
        finally:
            self.synthesis_lock.release()


class AudioHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *_):
        pass  # Do not log audio, transcripts or synthesis text.

    def do_GET(self):
        if self.path != "/health":
            return self.reply(404, {"error": "not_found"})
        self.reply(200, {"status": "ready", "transcription": self.server.engine.model_name,
                         "voice": self.server.engine.voice_name, "sampleRate": SAMPLE_RATE})

    def do_POST(self):
        self.connection.settimeout(60)
        sent_headers = False
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= MAX_WAV_BYTES + 4096:
                return self.reply(413, {"error": "invalid_size"})
            body = self.rfile.read(length)
            if len(body) != length:
                return self.reply(400, {"error": "incomplete_request"})
            if self.path == "/transcribe":
                content_type = self.headers.get("Content-Type", "")
                if not content_type.startswith("multipart/form-data;"):
                    raise ValueError("Expected multipart WAV")
                message = BytesParser(policy=policy.default).parsebytes(
                    ("Content-Type: " + content_type + "\r\n\r\n").encode() + body)
                files = [part.get_payload(decode=True) for part in message.iter_parts()
                         if part.get_param("name", header="content-disposition") == "file"]
                if len(files) != 1:
                    raise ValueError("Expected one audio file")
                wav = files[0]
                with wave.open(io.BytesIO(wav)) as recording:
                    if (recording.getframerate() != SAMPLE_RATE or recording.getnchannels() != 1
                            or recording.getsampwidth() != 2 or recording.getcomptype() != "NONE"
                            or not 1200 <= recording.getnframes() <= SAMPLE_RATE * 30
                            or len(recording.readframes(recording.getnframes())) != recording.getnframes() * 2):
                        raise ValueError("Invalid PCM WAV")
                self.reply(200, {"text": self.server.engine.transcribe(wav)})
            elif self.path == "/synthesize":
                data = json.loads(body)
                text = data.get("text") if isinstance(data, dict) else None
                if not isinstance(text, str) or not text.strip() or len(text) > 4000:
                    raise ValueError("Invalid synthesis text")
                iterator = self.server.engine.synthesize(text)
                try:
                    first = next(iterator)
                    self.send_response(200)
                    self.send_header("Content-Type", "audio/pcm;rate=24000;channels=1")
                    self.send_header("Transfer-Encoding", "chunked")
                    self.send_header("Connection", "close")
                    self.end_headers()
                    sent_headers = True
                    total = 0
                    import itertools
                    for pcm in itertools.chain([first], iterator):
                        total += len(pcm)
                        if len(pcm) % 2 or total > SAMPLE_RATE * 2 * 60:
                            raise ValueError("Invalid or excessive generated audio")
                        if pcm:
                            self.wfile.write(f"{len(pcm):X}\r\n".encode() + pcm + b"\r\n")
                            self.wfile.flush()
                    self.wfile.write(b"0\r\n\r\n")
                finally:
                    iterator.close()
                    self.close_connection = True
            else:
                self.reply(404, {"error": "not_found"})
        except (ValueError, wave.Error, EOFError, StopIteration):
            if not sent_headers:
                self.reply(400, {"error": "invalid_audio_or_text"})
            self.close_connection = True
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            self.close_connection = True
        except Exception as error:
            logging.error("Local speech processing failed (%s)", type(error).__name__)
            if not sent_headers:
                self.reply(500, {"error": "local_processing_failed"})
            self.close_connection = True

    def reply(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)
        self.close_connection = True


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    logging.getLogger("httpx").setLevel(logging.WARNING)
    logging.getLogger("httpx2").setLevel(logging.WARNING)
    engine = AudioEngine()
    server = ThreadingHTTPServer(("0.0.0.0", 8090), AudioHandler)
    server.engine = engine
    logging.info("Local speech service ready on port 8090")
    server.serve_forever()
