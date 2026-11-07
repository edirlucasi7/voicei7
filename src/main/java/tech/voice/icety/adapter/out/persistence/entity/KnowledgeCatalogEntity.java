package tech.voice.icety.adapter.out.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "knowledge_catalog")
public class KnowledgeCatalogEntity {
    @Id
    private Short id;

    protected KnowledgeCatalogEntity() { }
}
