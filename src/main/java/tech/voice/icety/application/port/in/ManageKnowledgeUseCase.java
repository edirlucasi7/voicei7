package tech.voice.icety.application.port.in;

import java.util.List;
import tech.voice.icety.application.port.in.result.IngestionResult;
import tech.voice.icety.application.port.in.result.KnowledgePassage;

public interface ManageKnowledgeUseCase {
    IngestionResult ingest();
    List<KnowledgePassage> search(String query);
}
