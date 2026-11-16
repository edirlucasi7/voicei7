package tech.voice.icety;

import java.time.Duration;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tech.voice.icety.domain.model.Appointment;
import tech.voice.icety.domain.model.BookingException;
import tech.voice.icety.domain.model.BusinessSchedule;
import tech.voice.icety.domain.model.CallSession;

import static org.assertj.core.api.Assertions.*;

class ReservationDomainTest {
    private final Instant now = Instant.parse("2026-10-24T12:00:00Z");
    private final Duration timeout = Duration.ofMinutes(2);
    private final LocalDateTime time = LocalDateTime.parse("2026-10-26T10:00:00");
    private final BusinessSchedule schedule = new BusinessSchedule(Set.of(DayOfWeek.SUNDAY));

    @Test
    void heartbeatExtendsDeadlineWithoutChangingOriginalCall() {
        var call = CallSession.start("call-1", now, timeout);
        var renewed = call.heartbeat(now.plusSeconds(30), timeout);
        assertThat(renewed.expiresAt()).isEqualTo(now.plusSeconds(150));
        assertThat(call.expiresAt()).isEqualTo(now.plusSeconds(120));
        assertThat(call.activeAt(now.plusSeconds(120))).isFalse();
    }

    @Test
    void terminalCallsCannotBeReactivated() {
        var call = CallSession.start("call-1", now, timeout);
        assertThatThrownBy(() -> call.heartbeat(now.plusSeconds(120), timeout))
                .isInstanceOf(BookingException.class);
        assertThatThrownBy(() -> call.end().heartbeat(now.plusSeconds(30), timeout))
                .isInstanceOf(BookingException.class);
        assertThat(call.expire(now.plusSeconds(120)).status()).isEqualTo(CallSession.Status.EXPIRED);
        assertThat(call.end().end()).isEqualTo(call.end());
    }

    @Test
    void validatesSlotAndFutureTimeInFortaleza() {
        for (var invalid : new LocalDateTime[] {null, time.withMinute(15), time.withSecond(1), time.withNano(1)}) {
            assertThatThrownBy(() -> Appointment.reserve("call-1", invalid, now))
                    .isInstanceOf(BookingException.class);
        }

        assertThatThrownBy(() -> Appointment.reserve("call-1", LocalDateTime.parse("2026-10-24T09:00:00"), now))
                .isInstanceOf(BookingException.class);
        assertThat(Appointment.reserve("call-1", time.withMinute(30), now).dateTime())
                .isEqualTo(time.withMinute(30));
    }

    @Test
    void confirmationPreservesTimeAndRequiresOwnerNameAndActiveCall() {
        var call = CallSession.start("call-1", now, timeout);
        var temporary = Appointment.reserve(call.id(), time, now);
        assertThatThrownBy(() -> temporary.confirm(call, " ", now, schedule)).isInstanceOf(BookingException.class);
        assertThatThrownBy(() -> temporary.confirm(CallSession.start("other", now, timeout), "Ana", now, schedule))
                .isInstanceOf(BookingException.class);
        assertThatThrownBy(() -> temporary.confirm(call, "Ana", now.plusSeconds(120), schedule))
                .isInstanceOf(BookingException.class);
        var confirmed = temporary.confirm(call, " Ana ", now.plusSeconds(10), schedule);
        assertThat(confirmed.status()).isEqualTo(Appointment.Status.CONFIRMED);
        assertThat(confirmed.customerName()).isEqualTo("Ana");
        assertThat(confirmed.id()).isEqualTo(temporary.id());
        assertThat(confirmed.dateTime()).isEqualTo(temporary.dateTime());
        assertThat(temporary.status()).isEqualTo(Appointment.Status.TEMPORARY);
        assertThat(confirmed.confirm(call.end(), "Ana", now.plusSeconds(150), schedule)).isEqualTo(confirmed);
        assertThat(confirmed.expire(now.plusSeconds(150))).isEqualTo(confirmed);
        assertThatThrownBy(() -> confirmed.release(now)).isInstanceOf(BookingException.class);
    }

    @Test
    void releasedAndExpiredReservationsCannotBeConfirmed() {
        var call = CallSession.start("call-1", now, timeout);
        var temporary = Appointment.reserve(call.id(), time, now);
        var released = temporary.release(now.plusSeconds(1));
        assertThat(released.release(now.plusSeconds(2))).isEqualTo(released);
        assertThatThrownBy(() -> released.confirm(call, "Ana", now.plusSeconds(2), schedule))
                .isInstanceOf(BookingException.class);
        assertThatThrownBy(() -> temporary.expire(now).confirm(call, "Ana", now, schedule))
                .isInstanceOf(BookingException.class);
    }

    @Test
    void closedDayBlocksConfirmationButDoesNotUndoPreviousConfirmations() {
        var call = CallSession.start("call-1", now, timeout);
        var temporary = Appointment.reserve(call.id(), time.minusDays(1), now);
        assertThatThrownBy(() -> temporary.confirm(call, "Ana", now, schedule))
                .isInstanceOf(BookingException.class).hasMessageContaining("SUNDAY");
        assertThat(temporary.status()).isEqualTo(Appointment.Status.TEMPORARY);
        var confirmed = temporary.confirm(call, "Ana", now, new BusinessSchedule(Set.of()));
        assertThat(confirmed.confirm(call, "Ana", now, schedule)).isSameAs(confirmed);
    }
}
