import io
import json
import threading
import unittest
import urllib.error
import urllib.request
import wave
from http.server import ThreadingHTTPServer
from server import AudioHandler


class FakeEngine:
    model_name = "small"
    voice_name = "pt_BR-faber-medium"

    def transcribe(self, wav):
        return "Vocês atendem aos domingos?"

    def synthesize(self, text):
        yield b"\x00\x01\x02\x03"
        yield b"\x04\x05"


class ServerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), AudioHandler)
        cls.server.engine = FakeEngine()
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.url = "http://127.0.0.1:" + str(cls.server.server_port)

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def test_health(self):
        with urllib.request.urlopen(self.url + "/health") as response:
            self.assertEqual(json.load(response)["status"], "ready")

    def test_transcribe_valid_wav(self):
        data = io.BytesIO()
        with wave.open(data, "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(24000)
            wav.writeframes(bytes(4800))
        body = b'--test\r\nContent-Disposition: form-data; name="file"; filename="speech.wav"\r\n\r\n' + data.getvalue() + b'\r\n--test--\r\n'
        request = urllib.request.Request(self.url + "/transcribe", data=body,
                                         headers={"Content-Type": "multipart/form-data; boundary=test"})
        with urllib.request.urlopen(request) as response:
            self.assertIn("domingos", json.load(response)["text"])

    def test_streams_pcm_without_wav_header(self):
        request = urllib.request.Request(self.url + "/synthesize", data=b'{"text":"Teste local"}',
                                         headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(request) as response:
            self.assertIn("audio/pcm", response.headers["Content-Type"])
            self.assertEqual(response.read(), bytes(range(6)))

    def test_rejects_invalid_requests(self):
        for path, body, content_type in [("/synthesize", b'{"text":""}', "application/json"),
                                         ("/transcribe", b'not a wav', "application/octet-stream")]:
            request = urllib.request.Request(self.url + path, data=body, headers={"Content-Type": content_type})
            with self.assertRaises(urllib.error.HTTPError) as error:
                urllib.request.urlopen(request)
            self.assertEqual(error.exception.code, 400)


if __name__ == "__main__":
    unittest.main()
