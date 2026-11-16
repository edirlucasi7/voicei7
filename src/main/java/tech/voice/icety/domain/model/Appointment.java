package tech.voice.icety.domain.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

public record Appointment(UUID id, String callId, LocalDateTime dateTime, String customerName,
                          Status status, Instant createdAt, Instant updatedAt) {
    public enum Status { TEMPORARY, CONFIRMED, RELEASED, EXPIRED }
    public static final ZoneId ZONE = ZoneId.of("America/Fortaleza");

    public static void validateTime(LocalDateTime time) {
        if (time == null || time.getSecond() != 0 || time.getNano() != 0
                || time.getMinute() % 30 != 0) {
            throw BookingException.invalid("dateTime must start at :00 or :30, without seconds");
        }
    }

    public static Appointment reserve(String callId, LocalDateTime time, Instant now) {
        CallSession.validateId(callId);
        validateTime(time);
        if (!time.isAfter(LocalDateTime.ofInstant(now, ZONE))) {
            throw BookingException.conflict("Appointment must be in the future");
        }

        return new Appointment(UUID.randomUUID(), callId, time, null, Status.TEMPORARY, now, now);
    }

    public void requireOwner(String owner) {
        if (!callId.equals(owner)) {
            throw BookingException.conflict("Appointment belongs to another call");
        }
    }

    public Appointment confirm(CallSession call, String name, Instant now, BusinessSchedule schedule) {
        requireOwner(call.id());
        if (name == null || name.isBlank()) {
            throw BookingException.invalid("customerName must not be blank");
        }

        if (status == Status.CONFIRMED) {
            return this;
        }

        call.requireActive(now);
        if (status != Status.TEMPORARY || !dateTime.isAfter(LocalDateTime.ofInstant(now, ZONE))) {
            throw BookingException.conflict("Reservation cannot be confirmed");
        }

        schedule.requireOpen(dateTime);
        return new Appointment(id, callId, dateTime, name.strip(), Status.CONFIRMED, createdAt, now);
    }

    public Appointment release(Instant now) {
        if (status == Status.CONFIRMED) {
            throw BookingException.conflict("Confirmed appointments cannot be released");
        }

        return status == Status.TEMPORARY ? transition(Status.RELEASED, now) : this;
    }

    public Appointment expire(Instant now) {
        return status == Status.TEMPORARY ? transition(Status.EXPIRED, now) : this;
    }

    private Appointment transition(Status next, Instant now) {
        return new Appointment(id, callId, dateTime, customerName, next, createdAt, now);
    }
}
