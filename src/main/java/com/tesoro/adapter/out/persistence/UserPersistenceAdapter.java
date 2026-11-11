package com.tesoro.adapter.out.persistence;

import com.tesoro.adapter.out.persistence.mapper.PersistenceMapper;
import com.tesoro.adapter.out.persistence.repository.UserJpaRepository;
import com.tesoro.application.port.out.UserRepository;
import com.tesoro.domain.model.User;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository repository;
    private final PersistenceMapper mapper;
    private final EntityManager entityManager;

    UserPersistenceAdapter(UserJpaRepository repository, PersistenceMapper mapper, EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    public void insert(User user) {
        entityManager.persist(mapper.toEntity(user));
        entityManager.flush();
    }

    @Override
    public Page<User> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toDomain);
    }
}
