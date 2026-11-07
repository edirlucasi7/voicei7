package tech.voice.icety.application.service;

import java.util.List;
import tech.voice.icety.application.port.in.ManageKnowledgeUseCase;
import tech.voice.icety.application.port.in.result.IngestionResult;
import tech.voice.icety.application.port.in.result.KnowledgePassage;
import tech.voice.icety.application.port.out.KnowledgeIndex;

public class ManageKnowledgeService implements ManageKnowledgeUseCase {
    private final KnowledgeIndex index;

    public ManageKnowledgeService(KnowledgeIndex index) {
        this.index = index;
    }

    @Override
    public IngestionResult ingest() {
        return index.ingest();
    }

    @Override
    public List<KnowledgePassage> search(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Knowledge query must not be blank");
        }

        return index.search(query);
    }
}
