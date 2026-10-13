package com.ledger.application;

import com.ledger.infrastructure.idempotency.IdempotencyRequestAttributes;
import com.ledger.api.dto.request.TransferRequest;
import com.ledger.api.dto.response.TransferResponse;
import org.springframework.stereotype.Service;

@Service
public class TransferService {

    private final LedgerEngine ledgerEngine;
    private final PostingRetryExecutor postingRetryExecutor;

    public TransferService(LedgerEngine ledgerEngine, PostingRetryExecutor postingRetryExecutor) {
        this.ledgerEngine = ledgerEngine;
        this.postingRetryExecutor = postingRetryExecutor;
    }

    public TransferResponse transfer(String idempotencyKey, TransferRequest request) {
        IdempotencyRequestAttributes.requireKey(idempotencyKey);
        return postingRetryExecutor.execute(() -> ledgerEngine.transfer(request));
    }
}
