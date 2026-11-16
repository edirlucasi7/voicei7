package tech.voice.icety.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import tech.voice.icety.application.port.in.ManageAppointmentsUseCase;
import tech.voice.icety.application.port.in.result.Creation;
import tech.voice.icety.application.port.in.result.Reservation;
import tech.voice.icety.application.port.out.AppointmentRepository;
import tech.voice.icety.application.port.out.CallSessionRepository;
import tech.voice.icety.domain.model.Appointment;
import tech.voice.icety.domain.model.BookingException;
import tech.voice.icety.domain.model.BusinessSchedule;
import tech.voice.icety.domain.model.CallSession;

public class ManageAppointmentsService implements ManageAppointmentsUseCase {
    private final AppointmentRepository appointments;
    private final CallSessionRepository calls;
    private final Clock clock;
    private final Duration timeout;
    private final Duration heartbeatInterval;
    private final BusinessSchedule schedule;

    public ManageAppointmentsService(AppointmentRepository appointments, CallSessionRepository calls, Clock clock,
                                     Duration timeout, Duration heartbeatInterval, BusinessSchedule schedule) {
        if (heartbeatInterval.isNegative() || heartbeatInterval.isZero()
                || timeout.compareTo(heartbeatInterval) <= 0) {
            throw new IllegalArgumentException("Call timeout must exceed a positive heartbeat interval");
        }

        this.appointments = appointments;
        this.calls = calls;
        this.clock = clock;
        this.timeout = timeout;
        this.heartbeatInterval = heartbeatInterval;
        this.schedule = schedule;
    }

    @Override
    public Creation<CallSession> startCall(String callId) {
        CallSession.validateId(callId);
        var now = clock.instant();
        expireAt(now);
        var existing = calls.findById(callId);
        if (existing.isPresent()) {
            return new Creation<>(existing.get(), false);
        }

        var call = CallSession.start(callId, now, timeout);
        calls.save(call);
        return new Creation<>(call, true);
    }

    @Override
    public CallSession heartbeat(String callId) {
        CallSession.validateId(callId);
        var now = clock.instant();
        expireAt(now);
        var renewed = call(callId).heartbeat(now, timeout);
        calls.save(renewed);
        return renewed;
    }

    @Override
    public CallSession endCall(String callId) {
        CallSession.validateId(callId);
        var now = clock.instant();
        expireAt(now);
        var ended = call(callId).end();
        appointments.findTemporaryByCallId(callId).ifPresent(a -> appointments.save(a.release(now)));
        calls.save(ended);
        return ended;
    }

    @Override
    public boolean available(LocalDateTime dateTime) {
        Appointment.validateTime(dateTime);
        var now = clock.instant();
        return dateTime.isAfter(LocalDateTime.ofInstant(now, Appointment.ZONE))
                && schedule.isOpen(dateTime)
                && !appointments.isOccupied(dateTime, now);
    }

    @Override
    public Creation<Reservation> reserve(String callId, LocalDateTime dateTime) {
        CallSession.validateId(callId);
        schedule.requireOpen(dateTime);
        var now = clock.instant();
        expireAt(now);
        var call = call(callId);
        call.requireActive(now);
        var existing = appointments.findTemporaryByCallId(callId);
        if (existing.isPresent()) {
            if (!existing.get().dateTime().equals(dateTime)) {
                throw BookingException.conflict("Release the existing reservation before choosing another time");
            }

            return new Creation<>(view(existing.get(), call), false);
        }

        var appointment = Appointment.reserve(callId, dateTime, now);
        if (appointments.isOccupied(dateTime, now)) {
            throw BookingException.conflict("Appointment time is occupied");
        }

        appointments.save(appointment);
        return new Creation<>(view(appointment, call), true);
    }

    @Override
    public Reservation confirm(UUID id, String callId, String customerName) {
        CallSession.validateId(callId);
        var now = clock.instant();
        expireAt(now);
        var appointment = appointment(id);
        appointment.requireOwner(callId);
        var call = call(callId);
        var confirmed = appointment.confirm(call, customerName, now, schedule);
        appointments.save(confirmed);
        return view(confirmed, call);
    }

    @Override
    public Reservation release(UUID id, String callId) {
        CallSession.validateId(callId);
        var now = clock.instant();
        expireAt(now);
        var appointment = appointment(id);
        appointment.requireOwner(callId);
        var released = appointment.release(now);
        appointments.save(released);
        return view(released, call(callId));
    }

    @Override
    public void expireCalls() {
        expireAt(clock.instant());
    }

    @Override
    public Duration heartbeatInterval() {
        return heartbeatInterval;
    }

    private void expireAt(Instant now) {
        for (var call : calls.findExpired(now)) {
            appointments.findTemporaryByCallId(call.id()).ifPresent(a -> appointments.save(a.expire(now)));
            calls.save(call.expire(now));
        }
    }

    private CallSession call(String id) {
        return calls.findById(id).orElseThrow(() ->
                new BookingException(BookingException.Kind.NOT_FOUND, "Call not found"));
    }

    private Appointment appointment(UUID id) {
        return appointments.findById(id).orElseThrow(() ->
                new BookingException(BookingException.Kind.NOT_FOUND, "Appointment not found"));
    }

    private Reservation view(Appointment appointment, CallSession call) {
        return new Reservation(appointment,
                appointment.status() == Appointment.Status.TEMPORARY ? call.expiresAt() : null);
    }
}
