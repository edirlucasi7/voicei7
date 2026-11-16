package tech.voice.icety.configuration;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import tech.voice.icety.application.port.out.AppointmentRepository;
import tech.voice.icety.application.port.out.CallSessionRepository;
import tech.voice.icety.application.service.ManageAppointmentsService;
import tech.voice.icety.domain.model.BusinessSchedule;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ReservationConfiguration {
    @Bean
    BusinessSchedule businessSchedule(@Value("${schedule.closed-days}") Set<DayOfWeek> closedDays) {
        return new BusinessSchedule(closedDays);
    }

    @Bean
    ManageAppointmentsService manageAppointmentsService(AppointmentRepository appointments,
            CallSessionRepository calls, Clock clock, @Value("${reservation.call-timeout}") Duration timeout,
            @Value("${reservation.heartbeat-interval}") Duration heartbeatInterval, BusinessSchedule schedule) {
        return new ManageAppointmentsService(appointments, calls, clock, timeout, heartbeatInterval, schedule);
    }
}
