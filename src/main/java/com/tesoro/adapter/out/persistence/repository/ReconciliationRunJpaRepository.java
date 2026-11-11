package com.tesoro.adapter.out.persistence.repository;

import com.tesoro.adapter.out.persistence.entity.ReconciliationRunEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRunJpaRepository extends JpaRepository<ReconciliationRunEntity, UUID> {}
