package tech.voice.icety.domain.model;

import java.time.Duration;
import java.time.Instant;

public record CallSession(String id, Status status, Instant lastActivityAt, Instant expiresAt) {
    public enum Status { ACTIVE, ENDED, EXPIRED }

    public static void validateId(String id) {
        if (id == null || id.isBlank() || id.length() > 128) {
            throw BookingException.invalid("callId must contain 1 to 128 characters");
        }
    }

    public static CallSession start(String id, Instant now, Duration timeout) {
        validateId(id);
        return new CallSession(id, Status.ACTIVE, now, now.plus(timeout));
    }

    public boolean activeAt(Instant now) {
        return status == Status.ACTIVE && expiresAt.isAfter(now);
    }

    public void requireActive(Instant now) {
        if (!activeAt(now)) {
            throw BookingException.conflict("Call is ended or expired");
        }
    }

    public CallSession heartbeat(Instant now, Duration timeout) {
        requireActive(now);
        return new CallSession(id, status, now, now.plus(timeout));
    }

    public CallSession end() {
        return status == Status.ACTIVE
                ? new CallSession(id, Status.ENDED, lastActivityAt, expiresAt) : this;
    }

    public CallSession expire(Instant now) {
        return status == Status.ACTIVE && !expiresAt.isAfter(now)
                ? new CallSession(id, Status.EXPIRED, lastActivityAt, expiresAt) : this;
    }
}
