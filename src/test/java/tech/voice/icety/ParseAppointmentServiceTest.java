package tech.voice.icety;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tech.voice.icety.application.service.ParseAppointmentService;
import tech.voice.icety.domain.model.AppointmentRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.voice.icety.domain.model.DayPeriod.*;
import static tech.voice.icety.domain.model.UserIntent.*;

class ParseAppointmentServiceTest {

    @Test
    void forwardsOriginalTextAndPreservesParserResult() {
        String userText = "  Tem vaga amanhã às 10 horas?  ";
        var date = LocalDate.of(2026, 10, 20);

        for (var result : new AppointmentRequest[] {
                new AppointmentRequest("Ana", date, "14:00", AFTERNOON, BOOK),
                new AppointmentRequest(null, date, null, UNSPECIFIED, CHECK_AVAILABILITY) }) {
            var service = new ParseAppointmentService(text -> {
                assertThat(text).isEqualTo(userText);
                return result;
            });

            assertThat(service.parse(userText)).isSameAs(result);
        }
    }

    @Test
    void rejectsBlankTextWithoutCallingParser() {
        var service = new ParseAppointmentService(text -> {
            throw new AssertionError("Parser must not be called for blank text");
        });

        for (String text : new String[] { null, "", "   ", "\t\n" }) {
            assertThatThrownBy(() -> service.parse(text))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }
    }
}
