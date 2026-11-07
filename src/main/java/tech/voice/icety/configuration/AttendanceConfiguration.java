package tech.voice.icety.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.voice.icety.application.port.in.AttendCallUseCase;
import tech.voice.icety.application.port.in.ManageKnowledgeUseCase;
import tech.voice.icety.application.port.out.KnowledgeIndex;
import tech.voice.icety.application.service.ManageKnowledgeService;
import tech.voice.icety.application.port.out.ConversationAssistant;
import tech.voice.icety.application.service.AttendCallService;

@Configuration(proxyBeanMethods = false)
public class AttendanceConfiguration {
    @Bean
    ManageKnowledgeUseCase manageKnowledgeUseCase(KnowledgeIndex index) {
        return new ManageKnowledgeService(index);
    }

    @Bean
    AttendCallUseCase attendCallUseCase(ConversationAssistant assistant) {
        return new AttendCallService(assistant);
    }
}
