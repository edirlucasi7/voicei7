package tech.voice.icety.application.port.out;

import java.util.List;
import tech.voice.icety.application.port.in.result.KnowledgePassage;
import tech.voice.icety.application.port.in.result.IngestionResult;

public interface KnowledgeIndex {
    IngestionResult ingest();
    List<KnowledgePassage> search(String query);
}
