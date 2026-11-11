package com.tesoro.adapter.out.persistence;

import com.tesoro.adapter.out.persistence.entity.AccountEntity;
import com.tesoro.adapter.out.persistence.mapper.PersistenceMapper;
import com.tesoro.adapter.out.persistence.repository.AccountJpaRepository;
import com.tesoro.application.exception.NotFoundException;
import com.tesoro.application.port.out.AccountRepository;
import com.tesoro.domain.model.Account;
import com.tesoro.domain.model.AccountKind;
import com.tesoro.domain.model.CurrencyCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AccountPersistenceAdapter implements AccountRepository {

    private final AccountJpaRepository repository;
    private final EntityManager entityManager;
    private final PersistenceMapper mapper;

    AccountPersistenceAdapter(AccountJpaRepository repository, EntityManager entityManager, PersistenceMapper mapper) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.mapper = mapper;
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Account> findByOwner(UUID ownerId) {
        return repository.findByOwnerIdOrderByCurrency(ownerId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Account> findWallet(UUID ownerId, CurrencyCode currency) {
        return repository
                .findByOwnerIdAndCurrencyAndKind(ownerId, currency, AccountKind.USER_WALLET)
                .map(mapper::toDomain);
    }

    @Override
    public Account getSystemAccount(AccountKind kind, CurrencyCode currency) {
        return repository
                .findFirstByKindAndCurrency(kind, currency)
                .map(mapper::toDomain)
                .orElseThrow(() -> new IllegalStateException("Missing system account %s %s".formatted(kind, currency)));
    }

    @Override
    public Account lockForUpdate(UUID id) {
        AccountEntity entity = entityManager.find(AccountEntity.class, id);
        if (entity == null) {
            throw new NotFoundException("Cuenta no encontrada");
        }
        // refresh + PESSIMISTIC_WRITE issues SELECT ... FOR UPDATE and reloads the row, so we never
        // work with a stale balance read before the lock was taken.
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return mapper.toDomain(entity);
    }

    @Override
    public void insert(Account account) {
        entityManager.persist(mapper.toEntity(account));
    }

    @Override
    public void update(Account account) {
        AccountEntity entity = entityManager.find(AccountEntity.class, account.id());
        if (entity == null) {
            throw new NotFoundException("Cuenta no encontrada");
        }
        entity.setBalance(account.balance().amount());
        entity.setStatus(account.status());
    }
}
