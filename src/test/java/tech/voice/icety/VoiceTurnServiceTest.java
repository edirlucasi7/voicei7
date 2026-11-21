package tech.voice.icety;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.voice.icety.application.port.in.AttendCallUseCase;
import tech.voice.icety.application.port.in.result.VoiceEvent;
import tech.voice.icety.application.port.out.CallSessionRepository;
import tech.voice.icety.application.port.out.SpeechSynthesizer;
import tech.voice.icety.application.port.out.SpeechTranscriber;
import tech.voice.icety.application.service.VoiceTurnService;
import tech.voice.icety.domain.model.CallSession;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VoiceTurnServiceTest {
    private final SpeechTranscriber transcriber = mock(SpeechTranscriber.class);
    private final SpeechSynthesizer synthesizer = mock(SpeechSynthesizer.class);
    private final AttendCallUseCase attendance = mock(AttendCallUseCase.class);
    private final CallSessionRepository calls = mock(CallSessionRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), java.time.ZoneOffset.UTC);
    private final VoiceTurnService service = new VoiceTurnService(transcriber, synthesizer, attendance, calls, clock);
    private final ArrayList<VoiceEvent> events = new ArrayList<>();

    @BeforeEach
    void activeCall() {
        when(calls.findById("a")).thenReturn(Optional.of(CallSession.start("a", clock.instant(), Duration.ofMinutes(2))));
        when(transcriber.transcribe(any())).thenReturn("Qual é o endereço?");
        when(attendance.reply("a", "Qual é o endereço?")).thenReturn("Rua das Flores, 100.");
    }

    @Test
    void emitsTranscriptReplyAndAudioInOrder() {
        doAnswer(invocation -> {
            Consumer<byte[]> audio = invocation.getArgument(1);
            audio.accept(new byte[] {0, 1});
            return null;
        }).when(synthesizer).speak(anyString(), any(), any());
        service.reply("a", "turn", new byte[44], events::add, () -> false);
        assertThat(events).extracting(VoiceEvent::type)
                .containsExactly("state", "transcript", "state", "reply", "state", "audio.delta", "turn.done");
        assertThat(events.get(5).audio()).isEqualTo("AAE=");
        verify(attendance).reply("a", "Qual é o endereço?");
    }

    @Test
    void synthesisFailurePreservesTextAndDoesNotRepeatAttendance() {
        doThrow(new IllegalStateException("secret provider detail")).when(synthesizer).speak(anyString(), any(), any());
        service.reply("a", "turn", new byte[44], events::add, () -> false);
        assertThat(events).anyMatch(event -> "reply".equals(event.type()) && event.text().contains("Flores"));
        assertThat(events).anyMatch(event -> "SYNTHESIS_FAILED".equals(event.code()));
        assertThat(events.toString()).doesNotContain("secret provider detail");
        verify(attendance, times(1)).reply(anyString(), anyString());
    }

    @Test
    void interruptedInferenceCanFinishButItsReplyIsNotSynthesized() {
        var obsolete = new AtomicBoolean();
        when(attendance.reply(anyString(), anyString())).thenAnswer(invocation -> {
            obsolete.set(true); // A tool may already have committed at this point.
            return "Reserva efetuada.";
        });
        service.reply("a", "old", new byte[44], events::add, obsolete::get);
        assertThat(events).anyMatch(event -> "reply".equals(event.type()) && event.text().equals("Reserva efetuada."));
        verifyNoInteractions(synthesizer);
    }

    @Test
    void failedTranscriptionNeverCallsAttendance() {
        when(transcriber.transcribe(any())).thenThrow(new IllegalStateException());
        service.reply("a", "turn", new byte[44], events::add, () -> false);
        assertThat(events).anyMatch(event -> "TRANSCRIPTION_FAILED".equals(event.code()));
        verifyNoInteractions(attendance, synthesizer);
    }

    @Test
    void attendanceFailureIsExplainedInPortugueseWithoutInternalStageNames() {
        when(attendance.reply(anyString(), anyString())).thenThrow(new IllegalStateException("Internal model detail"));
        service.reply("a", "turn", new byte[44], events::add, () -> false);
        var error = events.stream().filter(event -> "error".equals(event.type())).findFirst().orElseThrow();
        assertThat(error.code()).isEqualTo("ATTENDANCE_FAILED");
        assertThat(error.text()).contains("Não consegui concluir o atendimento")
                .doesNotContain("ATTENDANCE", "Internal model detail");
        verifyNoInteractions(synthesizer);
        verify(attendance, times(1)).reply(anyString(), anyString());
    }

    @Test
    void expiredOrAlreadyObsoleteTurnDoesNotCallProviders() {
        service.reply("a", "old", new byte[44], events::add, () -> true);
        when(calls.findById("a")).thenReturn(Optional.of(CallSession.start("a", clock.instant().minusSeconds(120), Duration.ofMinutes(2))));
        service.reply("a", "expired", new byte[44], events::add, () -> false);
        assertThat(events).anyMatch(event -> "CONFLICT".equals(event.code()));
        verifyNoInteractions(transcriber, attendance, synthesizer);
    }
}
