package tech.voice.icety.application.service;

import java.time.Clock;
import java.util.Base64;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import tech.voice.icety.application.port.in.AttendCallUseCase;
import tech.voice.icety.application.port.in.result.VoiceEvent;
import tech.voice.icety.application.port.out.CallSessionRepository;
import tech.voice.icety.application.port.out.SpeechSynthesizer;
import tech.voice.icety.application.port.out.SpeechTranscriber;
import tech.voice.icety.domain.model.BookingException;

public class VoiceTurnService {
    private final SpeechTranscriber transcriber;
    private final SpeechSynthesizer synthesizer;
    private final AttendCallUseCase attendance;
    private final CallSessionRepository calls;
    private final Clock clock;

    public VoiceTurnService(SpeechTranscriber transcriber, SpeechSynthesizer synthesizer,
            AttendCallUseCase attendance, CallSessionRepository calls, Clock clock) {
        this.transcriber = transcriber;
        this.synthesizer = synthesizer;
        this.attendance = attendance;
        this.calls = calls;
        this.clock = clock;
    }

    public void requireActive(String callId) {
        calls.findById(callId).orElseThrow(() ->
                new BookingException(BookingException.Kind.NOT_FOUND, "Call not found"))
                .requireActive(clock.instant());
    }

    public void playbackInterrupted(String callId) {
        attendance.playbackInterrupted(callId);
    }

    public void reply(String callId, String turnId, byte[] wav, Consumer<VoiceEvent> events,
            BooleanSupplier obsolete) {
        String stage = "TRANSCRIPTION";
        try {
            requireActive(callId);
            if (obsolete.getAsBoolean()) {
                return;
            }

            events.accept(VoiceEvent.text("state", turnId, "transcribing"));
            var text = transcriber.transcribe(wav);
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("Empty transcript");
            }

            events.accept(VoiceEvent.text("transcript", turnId, text));
            requireActive(callId);
            if (obsolete.getAsBoolean()) {
                return;
            }

            stage = "ATTENDANCE";
            events.accept(VoiceEvent.text("state", turnId, "processing"));
            var reply = attendance.reply(callId, text);
            if (reply == null || reply.isBlank()) {
                throw new IllegalStateException("Empty reply");
            }

            events.accept(VoiceEvent.text("reply", turnId, reply));
            requireActive(callId);
            if (obsolete.getAsBoolean()) {
                return;
            }

            stage = "SYNTHESIS";
            events.accept(VoiceEvent.text("state", turnId, "speaking"));
            synthesizer.speak(reply, bytes -> {
                if (!obsolete.getAsBoolean()) {
                    events.accept(new VoiceEvent("audio.delta", turnId, null, null,
                            Base64.getEncoder().encodeToString(bytes)));
                }
            }, obsolete);
        } catch (BookingException exception) {
            events.accept(VoiceEvent.error(turnId, exception.kind().name(),
                    "Esta ligação ou reserva não está mais disponível. Inicie uma nova ligação para continuar."));
        } catch (RuntimeException exception) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                    "Voice turn failed at {0} ({1})", stage, exception.getClass().getSimpleName());
            var message = switch (stage) {
                case "TRANSCRIPTION" -> "Não consegui entender o áudio. Grave sua fala novamente.";
                case "SYNTHESIS" -> "Não consegui reproduzir a resposta em voz. Você pode ler o texto na conversa.";
                default -> "Não consegui concluir o atendimento. Confira a situação da reserva antes de tentar novamente.";
            };
            events.accept(VoiceEvent.error(turnId, stage + "_FAILED", message));
        } finally {
            events.accept(VoiceEvent.text("turn.done", turnId, null));
        }
    }
}
