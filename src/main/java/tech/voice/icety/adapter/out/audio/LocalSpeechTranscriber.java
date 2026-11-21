package tech.voice.icety.adapter.out.audio;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import tech.voice.icety.application.port.out.SpeechTranscriber;
import tech.voice.icety.configuration.VoiceSettings;
import tools.jackson.databind.json.JsonMapper;

public class LocalSpeechTranscriber implements SpeechTranscriber {
    private final VoiceSettings settings;
    private final HttpClient client;
    private final JsonMapper json = JsonMapper.builder().build();

    public LocalSpeechTranscriber(VoiceSettings settings, HttpClient client) {
        this.settings = settings;
        this.client = client;
    }

    @Override
    public String transcribe(byte[] wav) {
        var boundary = "icety-" + UUID.randomUUID();
        var body = new ByteArrayOutputStream();
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(wav);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(settings.transcriptionUrl()).timeout(settings.timeout())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Speech transcription failed: HTTP " + response.statusCode());
            }

            var bytes = response.body();
            if (bytes.length > 65_536) {
                throw new IllegalStateException("Transcription response is too large");
            }

            var text = json.readTree(bytes).path("text").asText("");
            if (text.isBlank()) {
                throw new IllegalStateException("Empty transcription");
            }

            return text;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Transcription interrupted");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Transcription unavailable");
        }
    }
}
