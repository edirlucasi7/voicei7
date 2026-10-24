package tech.voice.icety.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import tech.voice.icety.domain.model.CallSession;

public interface CallSessionRepository {
    Optional<CallSession> findById(String id);
    List<CallSession> findExpired(Instant now);
    void save(CallSession call);
}
