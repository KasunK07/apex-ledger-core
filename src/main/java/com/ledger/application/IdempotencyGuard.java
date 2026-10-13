package com.ledger.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.infrastructure.idempotency.IdempotencyRequestAttributes;
import com.ledger.domain.exception.IdempotencyConflictException;
import com.ledger.domain.model.IdempotencyKey;
import com.ledger.infrastructure.persistence.IdempotencyKeyRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Function;

@Component
public class IdempotencyGuard {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public IdempotencyGuard(IdempotencyKeyRepository idempotencyKeyRepository,
                             EntityManager entityManager,
                             ObjectMapper objectMapper) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    public <T> T run(Class<T> responseType, Function<IdempotencyKey, T> action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Idempotent posting must run inside a transaction");
        }
        entityManager.createNativeQuery("select set_config('lock_timeout', '10s', true)").getSingleResult();
        String key = IdempotencyRequestAttributes.key();
        String hash = IdempotencyRequestAttributes.hash();
        // void-returning lock; getResultList avoids a null single-result failure.
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(cast(?1 as text), 0))")
                .setParameter(1, key)
                .getResultList();

        return idempotencyKeyRepository.findByKeyValue(key)
                .map(existing -> replay(existing, hash, responseType))
                .orElseGet(() -> execute(key, hash, responseType, action));
    }

    private <T> T replay(IdempotencyKey existing, String hash, Class<T> responseType) {
        if (!existing.getRequestHash().equals(hash)) {
            throw new IdempotencyConflictException();
        }
        if (existing.getResponsePayload() == null) {
            throw new IdempotencyConflictException();
        }
        IdempotencyRequestAttributes.markReplayed();
        return read(existing.getResponsePayload(), responseType);
    }

    private <T> T execute(String key, String hash, Class<T> responseType, Function<IdempotencyKey, T> action) {
        IdempotencyKey created = new IdempotencyKey();
        created.setKeyValue(key);
        created.setRequestHash(hash);
        idempotencyKeyRepository.saveAndFlush(created);
        T response = action.apply(created);
        created.setResponsePayload(write(response));
        return response;
    }

    private String write(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not store the idempotent response", ex);
        }
    }

    private <T> T read(String payload, Class<T> responseType) {
        try {
            return objectMapper.readValue(payload, responseType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored idempotent response is unreadable", ex);
        }
    }
}
