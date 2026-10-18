package tech.voice.icety;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import tech.voice.icety.domain.model.DayPeriod;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.voice.icety.domain.model.DayPeriod.*;

class DayPeriodTest {
    @Test
    void classifiesTimesAtEachBoundaryIncludingMidnight() {
        for (String time : new String[] { "00:00", "05:59", "18:00", "23:59" }) {
            assertThat(DayPeriod.from(LocalTime.parse(time))).isEqualTo(EVENING);
        }
        for (String time : new String[] { "06:00", "10:00", "11:59" }) {
            assertThat(DayPeriod.from(LocalTime.parse(time))).isEqualTo(MORNING);
        }
        for (String time : new String[] { "12:00", "15:00", "17:59" }) {
            assertThat(DayPeriod.from(LocalTime.parse(time))).isEqualTo(AFTERNOON);
        }
        assertThat(DayPeriod.from(null)).isEqualTo(UNSPECIFIED);
    }
}
