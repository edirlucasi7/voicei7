package tech.voice.icety.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import tech.voice.icety.domain.model.Appointment;

@Entity
@Table(name = "appointments")
public class AppointmentEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 128)
    private String callId;

    @Column(nullable = false)
    private LocalDateTime dateTime;

    @Column(columnDefinition = "text")
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Appointment.Status status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected AppointmentEntity() { }

    public static AppointmentEntity from(Appointment appointment) {
        var entity = new AppointmentEntity();
        entity.id = appointment.id();
        entity.callId = appointment.callId();
        entity.dateTime = appointment.dateTime();
        entity.customerName = appointment.customerName();
        entity.status = appointment.status();
        entity.createdAt = appointment.createdAt();
        entity.updatedAt = appointment.updatedAt();
        return entity;
    }

    public Appointment toDomain() {
        return new Appointment(id, callId, dateTime, customerName, status, createdAt, updatedAt);
    }
}
