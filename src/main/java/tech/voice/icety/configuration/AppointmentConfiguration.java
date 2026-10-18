package tech.voice.icety.configuration;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.voice.icety.application.port.in.ParseAppointmentUseCase;
import tech.voice.icety.application.port.out.AppointmentParser;
import tech.voice.icety.application.service.ParseAppointmentService;

@Configuration(proxyBeanMethods = false)
public class AppointmentConfiguration {

    @Bean
    Clock appointmentClock() {
        return Clock.system(ZoneId.of("America/Fortaleza"));
    }

    @Bean
    ParseAppointmentUseCase parseAppointmentUseCase(AppointmentParser parser) {
        return new ParseAppointmentService(parser);
    }
}
