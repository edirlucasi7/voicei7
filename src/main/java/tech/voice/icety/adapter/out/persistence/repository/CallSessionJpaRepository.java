package tech.voice.icety.adapter.out.persistence.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.voice.icety.adapter.out.persistence.entity.CallSessionEntity;
import tech.voice.icety.domain.model.CallSession;

public interface CallSessionJpaRepository extends JpaRepository<CallSessionEntity, String> {
    List<CallSessionEntity> findByStatusAndExpiresAtLessThanEqual(CallSession.Status status, Instant now);
}
