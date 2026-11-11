package com.tesoro.application.port.in;

import com.tesoro.application.model.AccountMovement;
import com.tesoro.application.model.MovementFilter;
import com.tesoro.application.model.Requester;
import com.tesoro.application.model.TransactionDetails;
import com.tesoro.domain.model.Account;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AccountQueryUseCase {

    List<Account> myAccounts(UUID userId);

    Account account(Requester requester, UUID accountId);

    Page<AccountMovement> movements(Requester requester, UUID accountId, MovementFilter filter, Pageable pageable);

    TransactionDetails transaction(Requester requester, UUID transactionId);
}
