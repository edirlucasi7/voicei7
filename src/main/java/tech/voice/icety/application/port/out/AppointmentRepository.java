package tech.voice.icety.application.port.out;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import tech.voice.icety.domain.model.Appointment;

public interface AppointmentRepository {
    Optional<Appointment> findById(UUID id);
    Optional<Appointment> findTemporaryByCallId(String callId);
    boolean isOccupied(LocalDateTime dateTime, Instant now);
    void save(Appointment appointment);
}
