package tech.voice.icety.adapter.out.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tech.voice.icety.adapter.out.persistence.entity.KnowledgeSourceEntity;
import tech.voice.icety.adapter.out.persistence.repository.KnowledgeSourceJpaRepository;
import tech.voice.icety.adapter.out.persistence.repository.KnowledgeCatalogJpaRepository;
import tech.voice.icety.application.port.out.KnowledgeIndex;
import tech.voice.icety.application.port.in.result.KnowledgePassage;
import tech.voice.icety.application.port.in.result.IngestionResult;

@Component
public class PgVectorKnowledgeIndex implements KnowledgeIndex {
    private final VectorStore vectors;
    private final KnowledgeSourceJpaRepository sources;
    private final TransactionTemplate transaction;
    private final KnowledgeCatalogJpaRepository catalog;
    private final Path directory;
    private final boolean reindex;
    private final int topK;
    private final double threshold;
    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(500).withMinChunkSizeChars(100).withMinChunkLengthToEmbed(1).build();

    public PgVectorKnowledgeIndex(VectorStore vectors, KnowledgeSourceJpaRepository sources,
            KnowledgeCatalogJpaRepository catalog, TransactionTemplate transaction, @Value("${knowledge.directory:knowledge}") String directory,
            @Value("${knowledge.search.top-k:4}") int topK,
            @Value("${knowledge.reindex:false}") boolean reindex,
            @Value("${knowledge.search.threshold:0.5}") double threshold) {
        this.vectors = vectors;
        this.sources = sources;
        this.transaction = transaction;
        this.catalog = catalog;
        this.directory = Path.of(directory);
        this.reindex = reindex;
        this.topK = topK;
        this.threshold = threshold;
    }

    @Override
    public IngestionResult ingest() {
        // Complete the scan before touching the index: an unreadable/missing folder is not an empty corpus.
        var documents = readDirectory();
        return transaction.execute(status -> {
            catalog.lockCatalog();
            var old = new HashMap<String, String>();
            sources.findAll().forEach(source -> old.put(source.path(), source.contentHash()));
            int updated = 0;
            int unchanged = 0;
            for (var document : documents) {
                var source = (String) document.getMetadata().get("source");
                var hash = hash(document.getText());
                if (hash.equals(old.remove(source)) && !reindex) {
                    unchanged++;
                    continue;
                }

                var chunks = splitter.apply(List.of(document));
                for (int i = 0; i < chunks.size(); i++) {
                    chunks.get(i).getMetadata().put("chunk", i);
                }

                // PgVectorStore and JPA share this transaction. Embedding errors roll back the replacement.
                deleteSource(source);
                if (!chunks.isEmpty()) {
                    vectors.add(chunks);
                }

                sources.save(new KnowledgeSourceEntity(source, hash));
                updated++;
            }

            old.keySet().forEach(source -> {
                deleteSource(source);
                sources.deleteById(source);
            });
            return new IngestionResult(updated, unchanged, old.size());
        });
    }

    @Override
    public List<KnowledgePassage> search(String query) {
        return vectors.similaritySearch(SearchRequest.builder().query(query)
                        .topK(topK).similarityThreshold(threshold).build()).stream()
                .map(document -> new KnowledgePassage((String) document.getMetadata().get("source"),
                        document.getText())).toList();
    }

    private List<Document> readDirectory() {
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("Knowledge directory does not exist: " + directory);
        }

        var documents = new ArrayList<Document>();
        try (var paths = Files.walk(directory)) {
            for (var path : paths.filter(Files::isRegularFile).sorted().toList()) {
                if (!path.toString().endsWith(".txt") && !path.toString().endsWith(".md")) {
                    continue;
                }

                var metadata = new HashMap<String, Object>();
                metadata.put("source", directory.relativize(path).toString().replace('\\', '/'));
                documents.add(new Document(Files.readString(path), metadata));
            }

            return documents;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read knowledge directory", exception);
        }
    }

    private void deleteSource(String source) {
        vectors.delete(new FilterExpressionBuilder().eq("source", source).build());
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
