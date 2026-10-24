package tech.voice.icety.adapter.out.persistence.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import tech.voice.icety.adapter.out.persistence.entity.ScheduleEntity;

public interface ScheduleJpaRepository extends JpaRepository<ScheduleEntity, Short> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select s from ScheduleEntity s where s.id = :id")
    Optional<ScheduleEntity> findAndLockById(@Param("id") Short id);
}
