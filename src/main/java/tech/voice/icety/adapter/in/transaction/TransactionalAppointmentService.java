package tech.voice.icety.adapter.in.transaction;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tech.voice.icety.adapter.out.persistence.repository.ScheduleJpaRepository;
import tech.voice.icety.application.port.in.ManageAppointmentsUseCase;
import tech.voice.icety.application.port.in.result.Creation;
import tech.voice.icety.application.port.in.result.Reservation;
import tech.voice.icety.application.service.ManageAppointmentsService;
import tech.voice.icety.domain.model.CallSession;

@Service
@Primary
@Transactional(isolation = Isolation.READ_COMMITTED)
public class TransactionalAppointmentService implements ManageAppointmentsUseCase {
    private final ManageAppointmentsService delegate;
    private final ScheduleJpaRepository schedules;

    public TransactionalAppointmentService(ManageAppointmentsService delegate, ScheduleJpaRepository schedules) {
        this.delegate = delegate;
        this.schedules = schedules;
    }

    @Override
    public Creation<CallSession> startCall(String callId) {
        lockSchedule();
        return delegate.startCall(callId);
    }

    @Override
    public CallSession heartbeat(String callId) {
        lockSchedule();
        return delegate.heartbeat(callId);
    }

    @Override
    public CallSession endCall(String callId) {
        lockSchedule();
        return delegate.endCall(callId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean available(LocalDateTime dateTime) {
        return delegate.available(dateTime);
    }

    @Override
    public Creation<Reservation> reserve(String callId, LocalDateTime dateTime) {
        lockSchedule();
        return delegate.reserve(callId, dateTime);
    }

    @Override
    public Reservation confirm(UUID id, String callId, String customerName) {
        lockSchedule();
        return delegate.confirm(id, callId, customerName);
    }

    @Override
    public Reservation release(UUID id, String callId) {
        lockSchedule();
        return delegate.release(id, callId);
    }

    @Override
    public void expireCalls() {
        lockSchedule();
        delegate.expireCalls();
    }

    @Override
    @Transactional(readOnly = true)
    public Duration heartbeatInterval() {
        return delegate.heartbeatInterval();
    }

    private void lockSchedule() {
        schedules.findAndLockById((short) 1)
                .orElseThrow(() -> new IllegalStateException("Schedule not configured"));
    }
}
