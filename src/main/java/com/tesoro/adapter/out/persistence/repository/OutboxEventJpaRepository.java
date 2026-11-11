package com.tesoro.adapter.out.persistence.repository;

import com.tesoro.adapter.out.persistence.entity.OutboxEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    long countByPublishedAtIsNull();
}
