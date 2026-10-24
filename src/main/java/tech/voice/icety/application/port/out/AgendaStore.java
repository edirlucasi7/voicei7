package tech.voice.icety.application.port.out;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.voice.icety.domain.model.Appointment;
import tech.voice.icety.domain.model.CallSession;

public interface AgendaStore {
    Optional<CallSession> call(String id);
    void save(CallSession call);
    List<CallSession> expiredCalls(Instant now);
    Optional<Appointment> appointment(UUID id);
    Optional<Appointment> temporaryForCall(String callId);
    boolean occupied(LocalDateTime time, Instant now);
    void save(Appointment appointment);
}
