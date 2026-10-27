package tech.voice.icety.adapter.in.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.voice.icety.application.port.in.ManageAppointmentsUseCase;

@Component
@ConditionalOnProperty(name = "reservation.cleanup-enabled", matchIfMissing = true)
public class ReservationCleanup {
    private final ManageAppointmentsUseCase appointments;

    public ReservationCleanup(ManageAppointmentsUseCase appointments) {
        this.appointments = appointments;
    }

    @Scheduled(fixedDelayString = "${reservation.cleanup-interval}",
            initialDelayString = "${reservation.cleanup-interval}")
    public void expire() {
        appointments.expireCalls();
    }
}
