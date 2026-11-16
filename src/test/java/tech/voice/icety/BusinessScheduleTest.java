package tech.voice.icety;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tech.voice.icety.domain.model.BookingException;
import tech.voice.icety.domain.model.BusinessSchedule;

import static org.assertj.core.api.Assertions.*;

class BusinessScheduleTest {
    private final LocalDateTime sunday = LocalDateTime.parse("2026-10-25T10:00:00");

    @Test
    void rejectsSundayButAllowsSaturdayAndMondayInLocalBusinessTime() {
        var schedule = new BusinessSchedule(Set.of(DayOfWeek.SUNDAY));
        assertThat(schedule.isOpen(sunday)).isFalse();
        assertThatThrownBy(() -> schedule.requireOpen(sunday)).isInstanceOf(BookingException.class)
                .hasMessageContaining("SUNDAY");
        assertThat(schedule.isOpen(sunday.minusDays(1))).isTrue();
        assertThat(schedule.isOpen(sunday.plusDays(1))).isTrue();
        assertThat(schedule.isOpen(sunday.withHour(0))).isFalse();
        assertThat(schedule.isOpen(sunday.withHour(23).withMinute(30))).isFalse();
        assertThat(schedule.isOpen(sunday.plusDays(1).withHour(0))).isTrue();
    }

    @Test
    void configurationIsImmutableAndCanCloseDifferentDays() {
        var configured = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.SATURDAY);
        var schedule = new BusinessSchedule(configured);
        configured.clear();
        assertThat(schedule.isOpen(sunday)).isTrue();
        assertThat(schedule.isOpen(sunday.minusDays(1))).isFalse();
        assertThat(schedule.isOpen(sunday.plusDays(1))).isFalse();
        assertThatThrownBy(() -> schedule.closedDays().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void keepsSlotValidationEvenWhenTheDayIsOpen() {
        var schedule = new BusinessSchedule(Set.of(DayOfWeek.SUNDAY));
        assertThatThrownBy(() -> schedule.requireOpen(sunday.plusDays(1).withMinute(15)))
                .isInstanceOf(BookingException.class);
        assertThatThrownBy(() -> schedule.isOpen(null)).isInstanceOf(BookingException.class);
    }
}
