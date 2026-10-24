package tech.voice.icety;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tech.voice.icety.adapter.in.web.BookingExceptionHandler;
import tech.voice.icety.adapter.in.web.CallController;
import tech.voice.icety.adapter.in.web.ReservationController;
import tech.voice.icety.application.port.in.ManageAppointmentsUseCase;
import tech.voice.icety.application.port.out.AgendaStore;
import tech.voice.icety.application.port.out.AgendaTransaction;
import tech.voice.icety.application.service.ManageAppointmentsService;
import tech.voice.icety.domain.model.Appointment;
import tech.voice.icety.domain.model.BookingException;
import tech.voice.icety.domain.model.CallSession;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Import(ReservationDatabaseTest.TimeConfiguration.class)
class ReservationDatabaseTest extends PostgresTestSupport {
    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");
    private static final LocalDateTime SLOT = LocalDateTime.parse("2026-10-25T10:00:00");
    @Autowired ManageAppointmentsUseCase service;
    @Autowired AgendaStore store;
    @Autowired AgendaTransaction transaction;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired MutableClock clock;
    @Autowired CallController calls;
    @Autowired ReservationController reservations;
    private MockMvc mvc;

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private volatile Instant now = NOW;

        void advance(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return Appointment.ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE appointments, call_sessions");
        clock.now = NOW;
        mvc = MockMvcBuilders.standaloneSetup(calls, reservations)
                .setControllerAdvice(new BookingExceptionHandler()).build();
    }

    @Test
    void migrationsAreRepeatableAndDataSurvivesNewConnections() {
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        service.startCall("a");
        var reservation = service.reserve("a", SLOT).value();
        try (var connection = jdbc.getDataSource().getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM appointments")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        } catch (java.sql.SQLException e) {
            throw new AssertionError(e);
        }

        assertThat(store.appointment(reservation.appointment().id())).contains(reservation.appointment());
    }

    @Test
    void heartbeatRenewsAndEndReleasesWithoutReopeningCall() {
        service.startCall("a");
        var reservation = service.reserve("a", SLOT).value();
        assertThat(service.available(SLOT)).isFalse();
        clock.advance(90);
        assertThat(service.heartbeat("a").expiresAt()).isEqualTo(NOW.plusSeconds(210));
        clock.advance(40);
        service.expireCalls();
        assertThat(service.available(SLOT)).isFalse();
        assertThat(service.endCall("a").status()).isEqualTo(CallSession.Status.ENDED);
        assertThat(service.available(SLOT)).isTrue();
        assertThat(store.appointment(reservation.appointment().id()).orElseThrow().status())
                .isEqualTo(Appointment.Status.RELEASED);
        assertThat(service.endCall("a").status()).isEqualTo(CallSession.Status.ENDED);
        assertThat(service.startCall("a").created()).isFalse();
        assertThatThrownBy(() -> service.heartbeat("a")).isInstanceOf(BookingException.class);
    }

    @Test
    void expiredReservationIsAvailableBeforeCleanupAndCanBeTakenImmediately() {
        service.startCall("a");
        var old = service.reserve("a", SLOT).value().appointment();
        clock.advance(120);
        assertThat(service.available(SLOT)).isTrue();
        assertThatThrownBy(() -> service.heartbeat("a")).isInstanceOf(BookingException.class);
        assertThatThrownBy(() -> service.confirm(old.id(), "a", "Ana"))
                .isInstanceOf(BookingException.class);
        service.startCall("b");
        var replacement = service.reserve("b", SLOT).value().appointment();
        assertThat(store.appointment(old.id()).orElseThrow().status()).isEqualTo(Appointment.Status.EXPIRED);
        assertThat(replacement.status()).isEqualTo(Appointment.Status.TEMPORARY);
        assertThat(service.available(SLOT)).isFalse();
    }

    @Test
    void cleanupExpiresTemporaryButPreservesConfirmedAppointments() {
        service.startCall("a");
        var confirmed = service.reserve("a", SLOT).value().appointment();
        service.confirm(confirmed.id(), "a", "Ana");
        service.startCall("b");
        var temporary = service.reserve("b", SLOT.plusMinutes(30)).value().appointment();
        clock.advance(120);
        service.expireCalls();
        assertThat(store.call("a").orElseThrow().status()).isEqualTo(CallSession.Status.EXPIRED);
        assertThat(store.appointment(confirmed.id()).orElseThrow().status()).isEqualTo(Appointment.Status.CONFIRMED);
        assertThat(store.appointment(temporary.id()).orElseThrow().status()).isEqualTo(Appointment.Status.EXPIRED);
        assertThat(service.available(SLOT)).isFalse();
        assertThat(service.available(SLOT.plusMinutes(30))).isTrue();
        assertThat(service.confirm(confirmed.id(), "a", "Ana").appointment().status())
                .isEqualTo(Appointment.Status.CONFIRMED);
        service.expireCalls();
    }

    @Test
    void reservationAndConfirmationAreIdempotentAndOwnerIsChecked() {
        service.startCall("a");
        service.startCall("b");
        var first = service.reserve("a", SLOT);
        var repeated = service.reserve("a", SLOT);
        assertThat(first.created()).isTrue();
        assertThat(repeated.created()).isFalse();
        assertThat(repeated.value()).isEqualTo(first.value());
        assertThatThrownBy(() -> service.reserve("a", SLOT.plusMinutes(30))).isInstanceOf(BookingException.class);
        var id = first.value().appointment().id();
        assertThatThrownBy(() -> service.confirm(id, "b", "Ana")).isInstanceOf(BookingException.class);
        assertThatThrownBy(() -> service.release(id, "b")).isInstanceOf(BookingException.class);
        var confirmed = service.confirm(id, "a", "Ana");
        service.endCall("a");
        assertThat(service.confirm(id, "a", "Ana")).isEqualTo(confirmed);
        assertThat(service.available(SLOT)).isFalse();
    }

    @Test
    void releasingAllowsTheCallToChooseAnotherTime() {
        service.startCall("a");
        var id = service.reserve("a", SLOT).value().appointment().id();
        var released = service.release(id, "a");
        assertThat(service.release(id, "a")).isEqualTo(released);
        assertThat(service.available(SLOT)).isTrue();
        assertThat(service.reserve("a", SLOT.plusMinutes(30)).created()).isTrue();
    }

    @Test
    void concurrentReservationsWaitThenOnlyOneSucceeds() throws Exception {
        service.startCall("a");
        service.startCall("b");
        var outcome = ordered(s -> s.reserve("a", SLOT), () -> service.reserve("b", SLOT), false);
        assertThat(outcome).isInstanceOf(BookingException.class);
        assertThat(((BookingException) outcome).kind()).isEqualTo(BookingException.Kind.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointments", Integer.class)).isEqualTo(1);
    }

    @Test
    void rollbackLetsWaitingReservationSucceed() throws Exception {
        service.startCall("a");
        service.startCall("b");
        var outcome = ordered(s -> s.reserve("a", SLOT), () -> service.reserve("b", SLOT), true);
        assertThat(outcome).isInstanceOf(ManageAppointmentsUseCase.Creation.class);
        assertThat(store.temporaryForCall("a")).isEmpty();
        assertThat(store.temporaryForCall("b")).isPresent();
    }

    @Test
    void differentTimesCanBothBeReserved() throws Exception {
        service.startCall("a");
        service.startCall("b");
        assertThat(ordered(s -> s.reserve("a", SLOT), () -> service.reserve("b", SLOT.plusMinutes(30)), false))
                .isInstanceOf(ManageAppointmentsUseCase.Creation.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointments", Integer.class)).isEqualTo(2);
    }

    @Test
    void confirmationBeforeEndRemainsConfirmed() throws Exception {
        service.startCall("a");
        var id = service.reserve("a", SLOT).value().appointment().id();
        assertThat(ordered(s -> s.confirm(id, "a", "Ana"), () -> service.endCall("a"), false))
                .isInstanceOf(CallSession.class);
        assertThat(store.appointment(id).orElseThrow().status()).isEqualTo(Appointment.Status.CONFIRMED);
    }

    @Test
    void endBeforeConfirmationPreventsConfirmation() throws Exception {
        service.startCall("a");
        var id = service.reserve("a", SLOT).value().appointment().id();
        assertThat(ordered(s -> s.endCall("a"), () -> service.confirm(id, "a", "Ana"), false))
                .isInstanceOf(BookingException.class);
        assertThat(store.appointment(id).orElseThrow().status()).isEqualTo(Appointment.Status.RELEASED);
    }

    @Test
    void heartbeatBeforeCleanupPreservesReservation() throws Exception {
        service.startCall("a");
        service.reserve("a", SLOT);
        clock.advance(110);
        assertThat(ordered(s -> s.heartbeat("a"), () -> {
            clock.advance(20);
            service.expireCalls();
            return "cleaned";
        }, false)).isEqualTo("cleaned");
        assertThat(service.available(SLOT)).isFalse();
    }

    @Test
    void cleanupBeforeLateHeartbeatPreventsResurrection() throws Exception {
        service.startCall("a");
        service.reserve("a", SLOT);
        clock.advance(120);
        assertThat(ordered(s -> { s.expireCalls(); return "cleaned"; }, () -> service.heartbeat("a"), false))
                .isInstanceOf(BookingException.class);
        assertThat(service.available(SLOT)).isTrue();
    }

    @Test
    void timeIsCapturedAfterLockAcquisition() throws Exception {
        service.startCall("a");
        service.reserve("a", SLOT);
        clock.advance(110);
        assertThat(ordered(s -> { s.expireCalls(); return "locked"; }, () -> {
            clock.advance(10);
            return service.heartbeat("a");
        }, false)).isInstanceOf(BookingException.class);
        assertThat(service.available(SLOT)).isTrue();
    }

    @Test
    void lockTimeoutReturnsBusyAndLeavesNoPartialData() throws Exception {
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var blocker = executor.submit(() -> transaction.locked(() -> {
                acquired.countDown();
                await(release);
                return null;
            }));
            try {
                assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
                mvc.perform(post("/api/calls").contentType("application/json").content("{\"callId\":\"a\"}"))
                        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("BUSY"));
                assertThat(store.call("a")).isEmpty();
            } finally {
                release.countDown();
                blocker.get(10, TimeUnit.SECONDS);
            }
        }

        assertThat(service.startCall("a").created()).isTrue();
    }

    @Test
    void databaseConstraintsRejectDuplicateOccupancyInvalidGridAndMissingName() {
        service.startCall("a");
        service.startCall("b");
        service.reserve("a", SLOT);
        assertThatThrownBy(() -> store.save(Appointment.reserve("b", SLOT, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> store.save(Appointment.reserve("a", SLOT.plusMinutes(30), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> store.save(new Appointment(UUID.randomUUID(), "b", SLOT.plusMinutes(15),
                null, Appointment.Status.TEMPORARY, NOW, NOW))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> store.save(new Appointment(UUID.randomUUID(), "b", SLOT.plusMinutes(30),
                null, Appointment.Status.CONFIRMED, NOW, NOW))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void httpFlowReturnsCreationIdempotencyAndConfirmedState() throws Exception {
        mvc.perform(post("/api/calls").contentType("application/json").content("{\"callId\":\"a\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.heartbeatIntervalSeconds").value(30));
        mvc.perform(post("/api/calls").contentType("application/json").content("{\"callId\":\"a\"}"))
                .andExpect(status().isOk());
        var body = "{\"callId\":\"a\",\"dateTime\":\"2026-10-25T10:00:00\"}";
        mvc.perform(post("/api/appointments/reservations").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("TEMPORARY"))
                .andExpect(jsonPath("$.expiresAt").exists());
        mvc.perform(post("/api/appointments/reservations").contentType("application/json").content(body))
                .andExpect(status().isOk());
        var id = store.temporaryForCall("a").orElseThrow().id();
        mvc.perform(get("/api/appointments/availability").param("dateTime", SLOT.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(post("/api/calls/a/heartbeat")).andExpect(status().isOk());
        mvc.perform(post("/api/appointments/" + id + "/confirm").contentType("application/json")
                        .content("{\"callId\":\"a\",\"customerName\":\"Ana\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(post("/api/calls/a/end")).andExpect(status().isOk());
        assertThat(service.available(SLOT)).isFalse();
    }

    @Test
    void httpReturnsProblemDetailsForInvalidMissingAndConflict() throws Exception {
        for (var body : new String[] {"{}", "null", "{", "{\"callId\":\" \"}"}) {
            mvc.perform(post("/api/calls").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(content().contentType("application/problem+json"));
        }

        mvc.perform(get("/api/appointments/availability")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/appointments/availability").param("dateTime", "bad"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/appointments/bad/confirm").contentType("application/json")
                        .content("{\"callId\":\"a\",\"customerName\":\"Ana\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/calls/missing/heartbeat")).andExpect(status().isNotFound());
        service.startCall("a");
        service.endCall("a");
        mvc.perform(post("/api/calls/a/heartbeat")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(post("/api/appointments/" + UUID.randomUUID() + "/release")
                        .contentType("application/json").content("{\"callId\":\"a\"}"))
                .andExpect(status().isNotFound());
    }

    // Execute a complete use case, pause before its commit, and prove the other
    // connection is waiting in PostgreSQL before letting the first finish.
    private Object ordered(Function<ManageAppointmentsUseCase, ?> first, Supplier<?> second, boolean rollback)
            throws Exception {
        var changed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        AgendaTransaction gated = new AgendaTransaction() {
            @Override
            public <T> T locked(Supplier<T> action) {
                return transaction.locked(() -> {
                    T value = action.get();
                    changed.countDown();
                    await(release);
                    if (rollback) {
                        throw new IllegalStateException("intentional rollback");
                    }

                    return value;
                });
            }
        };
        var firstService = new ManageAppointmentsService(store, gated, clock,
                Duration.ofMinutes(2), Duration.ofSeconds(30));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(() -> attempt(() -> first.apply(firstService)));
            try {
                assertThat(changed.await(5, TimeUnit.SECONDS)).isTrue();
                var secondResult = executor.submit(() -> attempt(second));
                waitForDatabaseLock();
                release.countDown();
                var firstOutcome = firstResult.get(10, TimeUnit.SECONDS);
                if (rollback) {
                    assertThat(firstOutcome).isInstanceOf(IllegalStateException.class);
                } else {
                    assertThat(firstOutcome).isNotInstanceOf(Throwable.class);
                }

                return secondResult.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
    }

    private void waitForDatabaseLock() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            var waiting = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname = current_database() AND wait_event_type = 'Lock'
                        AND query LIKE 'SELECT id FROM agenda%'
                    """, Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }

            Thread.sleep(10);
        }

        throw new AssertionError("Second connection did not wait for the agenda lock");
    }

    private static Object attempt(Supplier<?> action) {
        try {
            return action.get();
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for test transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
