package tech.voice.icety.adapter.out.persistence.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tech.voice.icety.adapter.out.persistence.entity.AppointmentEntity;
import tech.voice.icety.domain.model.Appointment;
import tech.voice.icety.domain.model.CallSession;

public interface AppointmentJpaRepository extends JpaRepository<AppointmentEntity, UUID> {
    Optional<AppointmentEntity> findByCallIdAndStatus(String callId, Appointment.Status status);

    @Query("""
            select count(a) > 0 from AppointmentEntity a
            where a.dateTime = :dateTime and (a.status = :confirmed
                or (a.status = :temporary and exists (
                    select c.id from CallSessionEntity c where c.id = a.callId
                        and c.status = :active and c.expiresAt > :now)))
            """)
    boolean existsOccupied(@Param("dateTime") LocalDateTime dateTime, @Param("now") Instant now,
            @Param("confirmed") Appointment.Status confirmed, @Param("temporary") Appointment.Status temporary,
            @Param("active") CallSession.Status active);
}
