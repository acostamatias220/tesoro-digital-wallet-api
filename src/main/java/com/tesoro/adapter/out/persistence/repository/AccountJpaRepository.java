package com.tesoro.adapter.out.persistence.repository;

import com.tesoro.adapter.out.persistence.entity.AccountEntity;
import com.tesoro.domain.model.AccountKind;
import com.tesoro.domain.model.CurrencyCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID> {

    List<AccountEntity> findByOwnerIdOrderByCurrency(UUID ownerId);

    Optional<AccountEntity> findByOwnerIdAndCurrencyAndKind(UUID ownerId, CurrencyCode currency, AccountKind kind);

    Optional<AccountEntity> findFirstByKindAndCurrency(AccountKind kind, CurrencyCode currency);
}
