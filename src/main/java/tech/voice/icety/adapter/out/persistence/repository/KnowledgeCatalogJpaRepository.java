package tech.voice.icety.adapter.out.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import tech.voice.icety.adapter.out.persistence.entity.KnowledgeCatalogEntity;

public interface KnowledgeCatalogJpaRepository extends JpaRepository<KnowledgeCatalogEntity, Short> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select catalog from KnowledgeCatalogEntity catalog where catalog.id = 1")
    KnowledgeCatalogEntity lockCatalog();
}
