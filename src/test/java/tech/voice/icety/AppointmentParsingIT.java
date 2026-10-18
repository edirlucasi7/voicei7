package tech.voice.icety;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tech.voice.icety.application.port.in.ParseAppointmentUseCase;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.voice.icety.domain.model.UserIntent.*;
import static tech.voice.icety.domain.model.DayPeriod.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(AppointmentTestConfiguration.class)
class AppointmentParsingIT {

    @Autowired
    private ParseAppointmentUseCase parser;

    @Test
    void interpretsNextTuesdayAndAfternoon() {
        var result = parser.parse(AppointmentExamples.CARLOS);
        assertThat(result.customerName()).isEqualTo("Carlos Alberto");
        assertThat(result.requestedDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(result.specificTime()).isNull();
        assertThat(result.period()).isEqualTo(AFTERNOON);
        assertThat(result.intent()).isEqualTo(BOOK);
    }

    @Test
    void interpretsTomorrowAndSpecificTime() {
        var result = parser.parse(AppointmentExamples.AVAILABILITY);
        assertThat(result.customerName()).isNull();
        assertThat(result.requestedDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(result.specificTime()).isEqualTo("10:00");
        assertThat(result.period()).isEqualTo(MORNING);
        assertThat(result.intent()).isEqualTo(CHECK_AVAILABILITY);
    }

    @Test
    void interpretsTomorrowWithAfternoonTime() {
        var result = parser.parse("Quero agendar amanhã às 15 horas.");
        assertThat(result.requestedDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(result.specificTime()).isEqualTo("15:00");
        assertThat(result.period()).isEqualTo(AFTERNOON);
        assertThat(result.intent()).isEqualTo(BOOK);
    }

    @Test
    void doesNotInventDateOrTimeWhenAbsent() {
        var result = parser.parse("Quero agendar, meu nome é Ana.");
        assertThat(result.customerName()).isEqualTo("Ana");
        assertThat(result.requestedDate()).isNull();
        assertThat(result.specificTime()).isNull();
        assertThat(result.period()).isEqualTo(UNSPECIFIED);
        assertThat(result.intent()).isEqualTo(BOOK);
    }

}
