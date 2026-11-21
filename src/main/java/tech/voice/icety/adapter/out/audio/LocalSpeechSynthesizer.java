package tech.voice.icety.adapter.out.audio;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import tech.voice.icety.application.port.out.SpeechSynthesizer;
import tech.voice.icety.configuration.VoiceSettings;
import tools.jackson.databind.json.JsonMapper;

public class LocalSpeechSynthesizer implements SpeechSynthesizer {
    private final VoiceSettings settings;
    private final HttpClient client;
    private final JsonMapper json = JsonMapper.builder().build();

    public LocalSpeechSynthesizer(VoiceSettings settings, HttpClient client) {
        this.settings = settings;
        this.client = client;
    }

    @Override
    public void speak(String text, Consumer<byte[]> audio, BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) {
            return;
        }

        var subscriber = new PcmAudioSubscriber(audio, cancelled);
        var request = HttpRequest.newBuilder(settings.synthesisUrl()).timeout(settings.timeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("text", text)))).build();
        var response = client.sendAsync(request, info -> {
            if (info.statusCode() != 200 || !info.headers().firstValue("Content-Type").orElse("").startsWith("audio/pcm")) {
                subscriber.fail(new IllegalStateException("Local synthesis rejected: HTTP " + info.statusCode()));
            }

            return subscriber;
        });
        long deadline = System.nanoTime() + settings.timeout().toNanos();
        try {
            while (!cancelled.getAsBoolean()) {
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException("Local synthesis timed out");
                }

                try {
                    response.get(100, TimeUnit.MILLISECONDS);
                    return;
                } catch (TimeoutException exception) {
                    // Check interruption and total deadline while audio arrives.
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Local synthesis interrupted");
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("Local synthesis unavailable", exception.getCause());
        } finally {
            subscriber.cancel();
            response.cancel(true);
        }
    }
}
