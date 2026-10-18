package tech.voice.icety;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tech.voice.icety.application.service.ParseAppointmentService;
import tech.voice.icety.domain.model.AppointmentRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.voice.icety.domain.model.DayPeriod.*;
import static tech.voice.icety.domain.model.UserIntent.*;

class ParseAppointmentServiceTest {

    private final ParseAppointmentService service = new ParseAppointmentService(text ->
            new AppointmentRequest(null, LocalDate.of(2026, 10, 20), null, UNSPECIFIED, CHECK_AVAILABILITY));

    @Test
    void recoversExplicitTimeWhenModelOmitsIt() {
        for (String text : new String[] { "Tem vaga para amanhã às 10 horas?", "Tem vaga às 10h?",
                "Tem vaga às 10:00?", "Tem vaga AS 10 HORAS?" }) {
            var result = service.parse(text);
            assertThat(result.specificTime()).isEqualTo("10:00");
            assertThat(result.period()).isEqualTo(MORNING);
            assertThat(result.requestedDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        }
    }

    @Test
    void respectsMinutesAndDayQualifiers() {
        assertThat(service.parse("Tem vaga às 10h30?").specificTime()).isEqualTo("10:30");
        assertThat(service.parse("Tem vaga às 10:45?").specificTime()).isEqualTo("10:45");
        var evening = service.parse("Tem vaga às 10 horas da noite?");
        assertThat(evening.specificTime()).isEqualTo("22:00");
        assertThat(evening.period()).isEqualTo(EVENING);
        assertThat(service.parse("Tem vaga às 3 horas da tarde?").specificTime()).isEqualTo("15:00");
    }

    @Test
    void leavesAmbiguousMissingAndInvalidTimesToModel() {
        for (String text : new String[] { "Quero o primeiro horário da tarde", "Tem vaga amanhã?",
                "Quero agendar, mas não às 10 horas", "Às 10 horas ou às 14 horas?",
                "Entre às 10 horas e às 11 horas", "Às 10 horas, às 14 horas",
                "Tem vaga em 10 horas?", "Tem vaga às 25 horas?", "Tem vaga às 10:99?" }) {
            assertThat(service.parse(text).specificTime()).as(text).isNull();
        }
    }

    @Test
    void preservesModelTimeAndDoesNotSupplementOtherIntents() {
        var existing = new AppointmentRequest(null, null, "14:00", AFTERNOON, BOOK);
        assertThat(new ParseAppointmentService(text -> existing).parse("Tem vaga às 10 horas?"))
                .isSameAs(existing);
        var cancellation = new AppointmentRequest(null, null, null, UNSPECIFIED, CANCEL);
        assertThat(new ParseAppointmentService(text -> cancellation).parse("Cancele a consulta às 10 horas"))
                .isSameAs(cancellation);
    }
}
