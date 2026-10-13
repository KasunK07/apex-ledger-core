package com.ledger.application;

import com.ledger.domain.exception.LedgerException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Slf4j
@Component
public class PostingRetryExecutor {

    static final int MAX_ATTEMPTS = 5;

    public <T> T execute(Supplier<T> work) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return work.get();
            } catch (RuntimeException ex) {
                if (!isRetryable(ex) || attempt == MAX_ATTEMPTS) {
                    throw ex;
                }
                last = ex;
                log.warn("Retrying ledger posting after a concurrency conflict (attempt {}/{})", attempt, MAX_ATTEMPTS);
                sleep(25L * attempt);
            }
        }
        throw last;
    }

    private boolean isRetryable(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof LedgerException) {
                return false;
            }
            if (cursor instanceof ConcurrencyFailureException) {
                return true;
            }
            String message = cursor.getMessage();
            if (message != null && message.contains("uq_idempotency_key")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ledger retry interrupted", ex);
        }
    }
}
