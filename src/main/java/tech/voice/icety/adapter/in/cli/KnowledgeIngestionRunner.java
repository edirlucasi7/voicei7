package tech.voice.icety.adapter.in.cli;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.voice.icety.application.port.in.ManageKnowledgeUseCase;

@Component
@Profile("ingest")
public class KnowledgeIngestionRunner implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeIngestionRunner.class);
    private final ManageKnowledgeUseCase knowledge;

    public KnowledgeIngestionRunner(ManageKnowledgeUseCase knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        var result = knowledge.ingest();
        LOG.info("Knowledge ingestion: updated={}, unchanged={}, deleted={}",
                result.updated(), result.unchanged(), result.deleted());
    }
}
