package tech.voice.icety.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Set;

public record BusinessSchedule(Set<DayOfWeek> closedDays) {
    public BusinessSchedule {
        closedDays = Set.copyOf(closedDays);
    }

    public boolean isOpen(LocalDateTime dateTime) {
        Appointment.validateTime(dateTime);
        return !closedDays.contains(dateTime.getDayOfWeek());
    }

    public void requireOpen(LocalDateTime dateTime) {
        if (!isOpen(dateTime)) {
            throw BookingException.conflict("Business is closed on " + dateTime.getDayOfWeek());
        }
    }
}
