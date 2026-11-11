package com.tesoro.adapter.out.persistence;

import com.tesoro.adapter.out.persistence.entity.AuditLogEntity;
import com.tesoro.adapter.out.persistence.mapper.PersistenceMapper;
import com.tesoro.adapter.out.persistence.repository.AuditLogJpaRepository;
import com.tesoro.application.model.AuditEntry;
import com.tesoro.application.port.out.AuditLog;
import com.tesoro.application.port.out.CallerContext;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
class AuditLogPersistenceAdapter implements AuditLog {

    private final AuditLogJpaRepository repository;
    private final PersistenceMapper mapper;
    private final CallerContext callerContext;

    AuditLogPersistenceAdapter(
            AuditLogJpaRepository repository, PersistenceMapper mapper, CallerContext callerContext) {
        this.repository = repository;
        this.mapper = mapper;
        this.callerContext = callerContext;
    }

    @Override
    public void record(AuditEntry entry) {
        AuditLogEntity entity = mapper.toEntity(entry);
        entity.setId(null);
        if (entity.getIpAddress() == null) {
            entity.setIpAddress(callerContext.ipAddress().orElse(null));
        }
        if (entity.getDetails() == null) {
            entity.setDetails(Map.of());
        }
        repository.save(entity);
    }

    @Override
    public Page<AuditEntry> find(String action, Pageable pageable) {
        Page<AuditLogEntity> page = action == null || action.isBlank()
                ? repository.findAll(pageable)
                : repository.findByAction(action, pageable);
        return page.map(mapper::toDomain);
    }
}
