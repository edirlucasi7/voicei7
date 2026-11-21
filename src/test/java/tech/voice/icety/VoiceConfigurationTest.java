package tech.voice.icety;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tech.voice.icety.configuration.VoiceConfiguration;
import tech.voice.icety.configuration.VoiceSettings;
import tech.voice.icety.application.port.out.SpeechTranscriber;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(VoiceConfiguration.class)
            .withBean(tech.voice.icety.application.port.in.AttendCallUseCase.class,
                    () -> org.mockito.Mockito.mock(tech.voice.icety.application.port.in.AttendCallUseCase.class))
            .withBean(tech.voice.icety.application.port.in.ManageAppointmentsUseCase.class,
                    () -> org.mockito.Mockito.mock(tech.voice.icety.application.port.in.ManageAppointmentsUseCase.class))
            .withBean(tech.voice.icety.application.port.out.CallSessionRepository.class,
                    () -> org.mockito.Mockito.mock(tech.voice.icety.application.port.out.CallSessionRepository.class))
            .withBean(java.time.Clock.class, java.time.Clock::systemUTC)
            .withPropertyValues("voice.timeout=60s", "voice.transcription-url=http://localhost:8090/transcribe",
                    "voice.synthesis-url=http://localhost:8090/synthesize");

    @Test
    void disabledVoiceDoesNotRequireCredentialsOrCreateProviderClients() {
        context.withPropertyValues("voice.enabled=false").run(application -> {
            assertThat(application).hasNotFailed().doesNotHaveBean(SpeechTranscriber.class)
                    .doesNotHaveBean(VoiceSettings.class);
        });
    }

    @Test
    void enabledVoiceDoesNotRequireAnyApiKey() {
        context.withPropertyValues("voice.enabled=true")
                .run(application -> {
                    assertThat(application).hasNotFailed().hasSingleBean(SpeechTranscriber.class)
                            .hasSingleBean(VoiceSettings.class);
                });
    }
}
