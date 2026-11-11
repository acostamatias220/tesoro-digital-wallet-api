package com.tesoro.application.port.in;

import com.tesoro.application.model.ReconciliationReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReconciliationUseCase {

    ReconciliationReport run(String trigger);

    Page<ReconciliationReport> history(Pageable pageable);
}
