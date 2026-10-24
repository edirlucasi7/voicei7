package tech.voice.icety;

import com.github.dockerjava.api.model.ExposedPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tech.voice.icety.adapter.out.persistence.JdbcAgendaStore;
import tech.voice.icety.adapter.out.persistence.JdbcAgendaTransaction;
import tech.voice.icety.application.service.ManageAppointmentsService;
import tech.voice.icety.domain.model.Appointment;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresPersistenceTest {
    @Test
    void confirmedAppointmentSurvivesPostgresContainerRestart() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:17")) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(),
                    postgres.getUsername(), postgres.getPassword());
            var flyway = Flyway.configure().dataSource(dataSource).load();
            flyway.migrate();
            var jdbc = new JdbcTemplate(dataSource);
            var store = new JdbcAgendaStore(jdbc);
            var transaction = new JdbcAgendaTransaction(jdbc, new DataSourceTransactionManager(dataSource));
            var service = new ManageAppointmentsService(store, transaction,
                    Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), Appointment.ZONE),
                    Duration.ofMinutes(2), Duration.ofSeconds(30));
            service.startCall("restart-test");
            var id = service.reserve("restart-test", LocalDateTime.parse("2026-10-25T10:00:00"))
                    .value().appointment().id();
            var confirmed = service.confirm(id, "restart-test", "Ana").appointment();

            DockerClientFactory.instance().client().restartContainerCmd(postgres.getContainerId()).exec();
            // Docker may assign a different published port after the restart.
            var binding = DockerClientFactory.instance().client().inspectContainerCmd(postgres.getContainerId())
                    .exec().getNetworkSettings().getPorts().getBindings().get(ExposedPort.tcp(5432))[0];
            dataSource.setUrl("jdbc:postgresql://" + postgres.getHost() + ":" + binding.getHostPortSpec()
                    + "/" + postgres.getDatabaseName());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (true) {
                try (var connection = dataSource.getConnection()) {
                    if (connection.isValid(1)) {
                        break;
                    }
                } catch (java.sql.SQLException exception) {
                    if (System.nanoTime() >= deadline) {
                        throw exception;
                    }
                }

                Thread.sleep(100);
            }

            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(store.appointment(id)).contains(confirmed);
            assertThat(service.available(confirmed.dateTime())).isFalse();
        }
    }
}
