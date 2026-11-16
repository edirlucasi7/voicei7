package tech.voice.icety.adapter.in.cli;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.voice.icety.application.port.in.ManageKnowledgeUseCase;

@Component
public class KnowledgeIngestionRunner implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeIngestionRunner.class);
    private final ManageKnowledgeUseCase knowledge;
    private final Environment environment;
    private final boolean ingestOnStartup;

    public KnowledgeIngestionRunner(ManageKnowledgeUseCase knowledge, Environment environment,
            @Value("${knowledge.ingest-on-startup:true}") boolean ingestOnStartup) {
        this.knowledge = knowledge;
        this.environment = environment;
        this.ingestOnStartup = ingestOnStartup;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!ingestOnStartup && !environment.matchesProfiles("ingest")) {
            return;
        }

        var result = knowledge.ingest();
        LOG.info("Knowledge ingestion: updated={}, unchanged={}, deleted={}",
                result.updated(), result.unchanged(), result.deleted());
    }
}
