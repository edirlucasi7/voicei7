package tech.voice.icety.application.port.in;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import tech.voice.icety.domain.model.Appointment;
import tech.voice.icety.domain.model.CallSession;

public interface ManageAppointmentsUseCase {
    record Creation<T>(T value, boolean created) { }
    record Reservation(Appointment appointment, Instant expiresAt) { }

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
