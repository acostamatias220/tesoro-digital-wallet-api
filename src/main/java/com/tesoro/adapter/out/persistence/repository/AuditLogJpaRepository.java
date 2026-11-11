package com.tesoro.adapter.out.persistence.repository;

import com.tesoro.adapter.out.persistence.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long> {

    Page<AuditLogEntity> findByAction(String action, Pageable pageable);
}
