package tech.voice.icety;

import org.junit.jupiter.api.Test;
import tech.voice.icety.domain.model.AppointmentRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.voice.icety.domain.model.DayPeriod.*;
import static tech.voice.icety.domain.model.UserIntent.OTHER;

class AppointmentRequestTest {
    @Test
    void derivesPeriodFromSpecificTime() {
        assertThat(new AppointmentRequest(null, null, "10:00", AFTERNOON, OTHER).period())
                .isEqualTo(MORNING);
    }

    @Test
    void preservesExtractedPeriodWhenTimeIsAbsent() {
        assertThat(new AppointmentRequest(null, null, null, AFTERNOON, OTHER).period()).isEqualTo(AFTERNOON);
        assertThat(new AppointmentRequest(null, null, null, UNSPECIFIED, OTHER).period()).isEqualTo(UNSPECIFIED);
    }

}
