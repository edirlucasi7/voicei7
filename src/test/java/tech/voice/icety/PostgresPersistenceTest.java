package tech.voice.icety;

import com.github.dockerjava.api.model.ExposedPort;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tech.voice.icety.application.port.in.ManageAppointmentsUseCase;
import tech.voice.icety.application.port.out.AppointmentRepository;
import tech.voice.icety.domain.model.Appointment;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresPersistenceTest {
    @Test
    void confirmedAppointmentSurvivesPostgresContainerRestart() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:17")) {
            postgres.start();
            Appointment confirmed;
            try (var context = application(postgres.getJdbcUrl(), postgres)) {
                var service = context.getBean(ManageAppointmentsUseCase.class);
                service.startCall("restart-test");
                var time = LocalDateTime.now(Appointment.ZONE).plusDays(1)
                        .withHour(10).withMinute(0).withSecond(0).withNano(0);
                var id = service.reserve("restart-test", time).value().appointment().id();
                service.confirm(id, "restart-test", "Ana");
                // Compare database snapshots at PostgreSQL timestamp precision.
                confirmed = context.getBean(AppointmentRepository.class).findById(id).orElseThrow();
            }

            DockerClientFactory.instance().client().restartContainerCmd(postgres.getContainerId()).exec();
            // Docker may assign a different published port after the restart.
            var binding = DockerClientFactory.instance().client().inspectContainerCmd(postgres.getContainerId())
                    .exec().getNetworkSettings().getPorts().getBindings().get(ExposedPort.tcp(5432))[0];
            var url = "jdbc:postgresql://" + postgres.getHost() + ":" + binding.getHostPortSpec()
                    + "/" + postgres.getDatabaseName();
            var dataSource = new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword());
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

            try (var context = application(url, postgres)) {
                assertThat(context.getBean(Flyway.class).migrate().migrationsExecuted).isZero();
                assertThat(context.getBean(AppointmentRepository.class).findById(confirmed.id())).contains(confirmed);
                assertThat(context.getBean(ManageAppointmentsUseCase.class).available(confirmed.dateTime())).isFalse();
            }
        }
    }

    private ConfigurableApplicationContext application(String url, PostgreSQLContainer postgres) {
        return new SpringApplicationBuilder(IcetyApplication.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--reservation.cleanup-enabled=false", "--logging.level.root=WARN");
    }
}
