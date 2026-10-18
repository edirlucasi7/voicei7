package tech.voice.icety.domain.model;

import java.time.LocalTime;

public enum DayPeriod {
    MORNING, AFTERNOON, EVENING, UNSPECIFIED;

    public static DayPeriod from(LocalTime time) {
        if (time == null) {
            return UNSPECIFIED;
        }

        return switch (time.getHour()) {
            case 6, 7, 8, 9, 10, 11 -> MORNING;
            case 12, 13, 14, 15, 16, 17 -> AFTERNOON;
            default -> EVENING;
        };
    }
}
