package tech.voice.icety.configuration;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("voice")
public record VoiceSettings(boolean enabled, URI transcriptionUrl, URI synthesisUrl, Duration timeout) {
    public VoiceSettings {
        if (enabled && (transcriptionUrl == null || synthesisUrl == null
                || !"http".equals(transcriptionUrl.getScheme()) || !"http".equals(synthesisUrl.getScheme()))) {
            throw new IllegalArgumentException("Local voice services require HTTP URLs");
        }

        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Voice timeout must be positive");
        }
    }

}
