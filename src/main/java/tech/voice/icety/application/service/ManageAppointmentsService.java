package tech.voice.icety.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import tech.voice.icety.application.port.in.ManageAppointmentsUseCase;
import tech.voice.icety.application.port.out.AgendaStore;
import tech.voice.icety.application.port.out.AgendaTransaction;
import tech.voice.icety.domain.model.Appointment;
import tech.voice.icety.domain.model.BookingException;
import tech.voice.icety.domain.model.CallSession;

public class ManageAppointmentsService implements ManageAppointmentsUseCase {
    private final AgendaStore store;
    private final AgendaTransaction transaction;
    private final Clock clock;
    private final Duration timeout;
    private final Duration heartbeatInterval;

    public ManageAppointmentsService(AgendaStore store, AgendaTransaction transaction, Clock clock,
                                     Duration timeout, Duration heartbeatInterval) {
        if (heartbeatInterval.isNegative() || heartbeatInterval.isZero()
                || timeout.compareTo(heartbeatInterval) <= 0) {
            throw new IllegalArgumentException("Call timeout must exceed a positive heartbeat interval");
        }

        this.store = store;
        this.transaction = transaction;
        this.clock = clock;
        this.timeout = timeout;
        this.heartbeatInterval = heartbeatInterval;
    }

    @Override
    public Creation<CallSession> startCall(String callId) {
        CallSession.validateId(callId);
        return transaction.locked(() -> {
            var now = clock.instant();
            expireAt(now);
            var existing = store.call(callId);
            if (existing.isPresent()) {
                return new Creation<>(existing.get(), false);
            }

            var call = CallSession.start(callId, now, timeout);
            store.save(call);
            return new Creation<>(call, true);
        });
    }

    @Override
    public CallSession heartbeat(String callId) {
        CallSession.validateId(callId);
        return transaction.locked(() -> {
            var now = clock.instant();
            expireAt(now);
            var renewed = call(callId).heartbeat(now, timeout);
            store.save(renewed);
            return renewed;
        });
    }

    @Override
    public CallSession endCall(String callId) {
        CallSession.validateId(callId);
        return transaction.locked(() -> {
            var now = clock.instant();
            expireAt(now);
            var ended = call(callId).end();
            store.temporaryForCall(callId).ifPresent(a -> store.save(a.release(now)));
            store.save(ended);
            return ended;
        });
    }

    @Override
    public boolean available(LocalDateTime dateTime) {
        Appointment.validateTime(dateTime);
        var now = clock.instant();
        return dateTime.isAfter(LocalDateTime.ofInstant(now, Appointment.ZONE))
                && !store.occupied(dateTime, now);
    }

    @Override
    public Creation<Reservation> reserve(String callId, LocalDateTime dateTime) {
        CallSession.validateId(callId);
        Appointment.validateTime(dateTime);
        return transaction.locked(() -> {
            var now = clock.instant();
            expireAt(now);
            var call = call(callId);
            call.requireActive(now);
            var existing = store.temporaryForCall(callId);
            if (existing.isPresent()) {
                if (!existing.get().dateTime().equals(dateTime)) {
                    throw BookingException.conflict("Release the existing reservation before choosing another time");
                }

                return new Creation<>(view(existing.get(), call), false);
            }

            var appointment = Appointment.reserve(callId, dateTime, now);
            if (store.occupied(dateTime, now)) {
                throw BookingException.conflict("Appointment time is occupied");
            }

            store.save(appointment);
            return new Creation<>(view(appointment, call), true);
        });
    }

    @Override
    public Reservation confirm(UUID id, String callId, String customerName) {
        CallSession.validateId(callId);
        return transaction.locked(() -> {
            var now = clock.instant();
            expireAt(now);
            var appointment = appointment(id);
            appointment.requireOwner(callId);
            var call = call(callId);
            var confirmed = appointment.confirm(call, customerName, now);
            store.save(confirmed);
            return view(confirmed, call);
        });
    }

    @Override
    public Reservation release(UUID id, String callId) {
        CallSession.validateId(callId);
        return transaction.locked(() -> {
            var now = clock.instant();
            expireAt(now);
            var appointment = appointment(id);
            appointment.requireOwner(callId);
            var released = appointment.release(now);
            store.save(released);
            return view(released, call(callId));
        });
    }

    @Override
    public void expireCalls() {
        transaction.locked(() -> {
            expireAt(clock.instant());
            return null;
        });
    }

    @Override
    public Duration heartbeatInterval() {
        return heartbeatInterval;
    }

    private void expireAt(Instant now) {
        for (var call : store.expiredCalls(now)) {
            store.temporaryForCall(call.id()).ifPresent(a -> store.save(a.expire(now)));
            store.save(call.expire(now));
        }
    }

    private CallSession call(String id) {
        return store.call(id).orElseThrow(() ->
                new BookingException(BookingException.Kind.NOT_FOUND, "Call not found"));
    }

    private Appointment appointment(UUID id) {
        return store.appointment(id).orElseThrow(() ->
                new BookingException(BookingException.Kind.NOT_FOUND, "Appointment not found"));
    }

    private Reservation view(Appointment appointment, CallSession call) {
        return new Reservation(appointment,
                appointment.status() == Appointment.Status.TEMPORARY ? call.expiresAt() : null);
    }
}
