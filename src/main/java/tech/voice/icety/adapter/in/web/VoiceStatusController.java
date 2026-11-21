package tech.voice.icety.adapter.in.web;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VoiceStatusController {
    private final boolean enabled;

    public VoiceStatusController(@Value("${voice.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @GetMapping("/api/voice/status")
    public Map<String, Object> status() {
        return Map.of("enabled", enabled, "sampleRate", 24000, "maxRecordingSeconds", 30);
    }
}
