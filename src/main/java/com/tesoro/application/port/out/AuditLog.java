package com.tesoro.application.port.out;

import com.tesoro.application.model.AuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditLog {

    void record(AuditEntry entry);

    Page<AuditEntry> find(String action, Pageable pageable);
}
