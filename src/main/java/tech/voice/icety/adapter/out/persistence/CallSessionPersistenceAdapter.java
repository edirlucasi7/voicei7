package tech.voice.icety.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import tech.voice.icety.adapter.out.persistence.entity.CallSessionEntity;
import tech.voice.icety.adapter.out.persistence.repository.CallSessionJpaRepository;
import tech.voice.icety.application.port.out.CallSessionRepository;
import tech.voice.icety.domain.model.CallSession;

@Repository
public class CallSessionPersistenceAdapter implements CallSessionRepository {
    private final CallSessionJpaRepository repository;

    public CallSessionPersistenceAdapter(CallSessionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<CallSession> findById(String id) {
        return repository.findById(id).map(CallSessionEntity::toDomain);
    }

    @Override
    public List<CallSession> findExpired(Instant now) {
        return repository.findByStatusAndExpiresAtLessThanEqual(CallSession.Status.ACTIVE, now)
                .stream().map(CallSessionEntity::toDomain).toList();
    }

    @Override
    public void save(CallSession call) {
        repository.saveAndFlush(CallSessionEntity.from(call));
    }
}
