package tech.voice.icety.configuration;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import tech.voice.icety.application.port.out.AppointmentRepository;
import tech.voice.icety.application.port.out.CallSessionRepository;
import tech.voice.icety.application.service.ManageAppointmentsService;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ReservationConfiguration {
    @Bean
    ManageAppointmentsService manageAppointmentsService(AppointmentRepository appointments,
            CallSessionRepository calls, Clock clock, @Value("${reservation.call-timeout}") Duration timeout,
            @Value("${reservation.heartbeat-interval}") Duration heartbeatInterval) {
        return new ManageAppointmentsService(appointments, calls, clock, timeout, heartbeatInterval);
    }
}
