package tech.voice.icety.adapter.out.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.voice.icety.adapter.out.persistence.entity.KnowledgeSourceEntity;

public interface KnowledgeSourceJpaRepository extends JpaRepository<KnowledgeSourceEntity, String> { }
