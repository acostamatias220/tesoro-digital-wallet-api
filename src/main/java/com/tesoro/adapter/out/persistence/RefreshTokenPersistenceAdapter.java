package com.tesoro.adapter.out.persistence;

import com.tesoro.adapter.out.persistence.mapper.PersistenceMapper;
import com.tesoro.adapter.out.persistence.repository.RefreshTokenJpaRepository;
import com.tesoro.application.model.RefreshToken;
import com.tesoro.application.port.out.RefreshTokenRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class RefreshTokenPersistenceAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository repository;
    private final PersistenceMapper mapper;
    private final EntityManager entityManager;

    RefreshTokenPersistenceAdapter(
            RefreshTokenJpaRepository repository, PersistenceMapper mapper, EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public void insert(RefreshToken token) {
        entityManager.persist(mapper.toEntity(token));
    }

    @Override
    public Optional<RefreshToken> findByHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    @Override
    public boolean revoke(UUID tokenId, Instant now) {
        return repository.revokeIfActive(tokenId, now) == 1;
    }

    @Override
    public int revokeFamily(UUID familyId, Instant now) {
        return repository.revokeFamily(familyId, now);
    }
}
