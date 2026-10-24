package tech.voice.icety.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import tech.voice.icety.domain.model.CallSession;

@Entity
@Table(name = "call_sessions")
public class CallSessionEntity {
    @Id
    @Column(length = 128)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CallSession.Status status;

    @Column(nullable = false)
    private Instant lastActivityAt;

    @Column(nullable = false)
    private Instant expiresAt;

    protected CallSessionEntity() { }

    public static CallSessionEntity from(CallSession call) {
        var entity = new CallSessionEntity();
        entity.id = call.id();
        entity.status = call.status();
        entity.lastActivityAt = call.lastActivityAt();
        entity.expiresAt = call.expiresAt();
        return entity;
    }

    public CallSession toDomain() {
        return new CallSession(id, status, lastActivityAt, expiresAt);
    }
}
