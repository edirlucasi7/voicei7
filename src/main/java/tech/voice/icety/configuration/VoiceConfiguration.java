package tech.voice.icety.configuration;

import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.voice.icety.adapter.in.voice.VoiceHandshakeInterceptor;
import tech.voice.icety.adapter.in.voice.VoiceSocketHandler;
import tech.voice.icety.adapter.out.audio.LocalSpeechSynthesizer;
import tech.voice.icety.adapter.out.audio.LocalSpeechTranscriber;
import tech.voice.icety.application.port.in.AttendCallUseCase;
import tech.voice.icety.application.port.in.ManageAppointmentsUseCase;
import tech.voice.icety.application.port.out.CallSessionRepository;
import tech.voice.icety.application.port.out.SpeechSynthesizer;
import tech.voice.icety.application.port.out.SpeechTranscriber;
import tech.voice.icety.application.service.VoiceTurnService;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "voice.enabled", havingValue = "true")
@EnableConfigurationProperties(VoiceSettings.class)
public class VoiceConfiguration {
    @Bean
    SpeechTranscriber speechTranscriber(VoiceSettings settings) {
        return new LocalSpeechTranscriber(settings, HttpClient.newBuilder().connectTimeout(settings.timeout()).build());
    }

    @Bean
    SpeechSynthesizer speechSynthesizer(VoiceSettings settings) {
        return new LocalSpeechSynthesizer(settings, HttpClient.newBuilder().connectTimeout(settings.timeout()).build());
    }

    @Bean
    VoiceTurnService voiceTurnService(SpeechTranscriber transcriber, SpeechSynthesizer synthesizer,
            AttendCallUseCase attendance, CallSessionRepository calls, Clock clock) {
        return new VoiceTurnService(transcriber, synthesizer, attendance, calls, clock);
    }

    @Bean
    VoiceSocketHandler voiceSocketHandler(VoiceTurnService turns, ManageAppointmentsUseCase appointments) {
        return new VoiceSocketHandler(turns, appointments);
    }

    @Bean
    VoiceHandshakeInterceptor voiceHandshakeInterceptor(VoiceSocketHandler sockets, VoiceTurnService turns) {
        return new VoiceHandshakeInterceptor(sockets, turns);
    }
}
