package tech.voice.icety.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tech.voice.icety.adapter.out.persistence.entity.AppointmentEntity;
import tech.voice.icety.adapter.out.persistence.repository.AppointmentJpaRepository;
import tech.voice.icety.application.port.out.AppointmentRepository;
import tech.voice.icety.domain.model.Appointment;
import tech.voice.icety.domain.model.CallSession;

@Repository
public class AppointmentPersistenceAdapter implements AppointmentRepository {
    private final AppointmentJpaRepository repository;

    public AppointmentPersistenceAdapter(AppointmentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Appointment> findById(UUID id) {
        return repository.findById(id).map(AppointmentEntity::toDomain);
    }

    @Override
    public Optional<Appointment> findTemporaryByCallId(String callId) {
        return repository.findByCallIdAndStatus(callId, Appointment.Status.TEMPORARY)
                .map(AppointmentEntity::toDomain);
    }

    @Override
    public boolean isOccupied(LocalDateTime dateTime, Instant now) {
        return repository.existsOccupied(dateTime, now, Appointment.Status.CONFIRMED,
                Appointment.Status.TEMPORARY, CallSession.Status.ACTIVE);
    }

    @Override
    public void save(Appointment appointment) {
        // Flush transitions before a later insert can reuse the occupied time.
        repository.saveAndFlush(AppointmentEntity.from(appointment));
    }
}
