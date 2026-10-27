package tech.voice.icety.application.port.in;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import tech.voice.icety.application.port.in.result.Creation;
import tech.voice.icety.application.port.in.result.Reservation;
import tech.voice.icety.domain.model.CallSession;

public interface ManageAppointmentsUseCase {
    Creation<CallSession> startCall(String callId);
    CallSession heartbeat(String callId);
    CallSession endCall(String callId);
    boolean available(LocalDateTime dateTime);
    Creation<Reservation> reserve(String callId, LocalDateTime dateTime);
    Reservation confirm(UUID id, String callId, String customerName);
    Reservation release(UUID id, String callId);
    void expireCalls();
    Duration heartbeatInterval();
}
