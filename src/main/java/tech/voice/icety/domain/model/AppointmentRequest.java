package tech.voice.icety.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentRequest(
        String customerName,
        LocalDate requestedDate,
        String specificTime,
        DayPeriod period,
        UserIntent intent) {

    public AppointmentRequest {
        if (specificTime != null) {
            period = DayPeriod.from(LocalTime.parse(specificTime));
        }
    }

}
