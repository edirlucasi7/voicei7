package tech.voice.icety.adapter.out.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "knowledge_source")
public class KnowledgeSourceEntity {
    @Id
    @Column(length = 1024)
    private String path;
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    protected KnowledgeSourceEntity() { }

    public KnowledgeSourceEntity(String path, String contentHash) {
        this.path = path;
        this.contentHash = contentHash;
    }

    public String path() {
        return path;
    }

    public String contentHash() {
        return contentHash;
    }
}
