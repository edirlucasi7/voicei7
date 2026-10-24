package tech.voice.icety.configuration;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import tech.voice.icety.adapter.out.persistence.JdbcAgendaStore;
import tech.voice.icety.adapter.out.persistence.JdbcAgendaTransaction;
import tech.voice.icety.application.port.in.ManageAppointmentsUseCase;
import tech.voice.icety.application.port.out.AgendaStore;
import tech.voice.icety.application.port.out.AgendaTransaction;
import tech.voice.icety.application.service.ManageAppointmentsService;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ReservationConfiguration {
    @Bean
    AgendaStore agendaStore(JdbcTemplate jdbc) {
        return new JdbcAgendaStore(jdbc);
    }

    @Bean
    AgendaTransaction agendaTransaction(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        return new JdbcAgendaTransaction(jdbc, manager);
    }

    @Bean
    ManageAppointmentsUseCase manageAppointmentsUseCase(AgendaStore store, AgendaTransaction transaction,
            Clock clock, @Value("${reservation.call-timeout}") Duration timeout,
            @Value("${reservation.heartbeat-interval}") Duration heartbeatInterval) {
        return new ManageAppointmentsService(store, transaction, clock, timeout, heartbeatInterval);
    }
}
