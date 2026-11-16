package tech.voice.icety;

import java.time.Clock;
import java.time.DayOfWeek;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.convert.ApplicationConversionService;
import tech.voice.icety.application.port.out.AppointmentRepository;
import tech.voice.icety.application.port.out.CallSessionRepository;
import tech.voice.icety.configuration.ReservationConfiguration;
import tech.voice.icety.domain.model.BusinessSchedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BusinessScheduleConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(application -> application.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(ReservationConfiguration.class)
            .withBean(AppointmentRepository.class, () -> mock(AppointmentRepository.class))
            .withBean(CallSessionRepository.class, () -> mock(CallSessionRepository.class))
            .withBean(Clock.class, Clock::systemUTC)
            .withPropertyValues("reservation.call-timeout=2m", "reservation.heartbeat-interval=30s");

    @Test
    void bindsMultipleClosedDays() {
        context.withPropertyValues("schedule.closed-days=SUNDAY,SATURDAY").run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(application.getBean(BusinessSchedule.class).closedDays())
                    .containsExactlyInAnyOrder(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        });
    }

    @Test
    void invalidDayPreventsStartup() {
        context.withPropertyValues("schedule.closed-days=NOT_A_DAY").run(application -> {
            assertThat(application).hasFailed();
            assertThat(application.getStartupFailure()).hasStackTraceContaining("NOT_A_DAY");
        });
    }
}
