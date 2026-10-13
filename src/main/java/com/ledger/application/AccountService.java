package com.ledger.application;

import com.ledger.infrastructure.idempotency.IdempotencyRequestAttributes;
import com.ledger.api.dto.response.AccountResponse;
import com.ledger.api.dto.response.AccountStatementResponse;
import com.ledger.api.dto.request.CashMovementRequest;
import com.ledger.api.dto.response.CashMovementResponse;
import com.ledger.api.dto.request.OpenAccountRequest;
import com.ledger.api.dto.response.StatementLineResponse;
import com.ledger.domain.exception.AccountNotFoundException;
import com.ledger.domain.exception.CustomerNotFoundException;
import com.ledger.domain.exception.CustomerSuspendedException;
import com.ledger.domain.exception.InvalidRequestException;
import com.ledger.domain.exception.TreasuryProtectedException;
import com.ledger.domain.exception.UnsupportedCurrencyException;
import com.ledger.domain.model.Account;
import com.ledger.domain.model.AccountRole;
import com.ledger.domain.model.AccountStatus;
import com.ledger.domain.model.Customer;
import com.ledger.domain.model.CustomerStatus;
import com.ledger.domain.model.MoneyAmounts;
import com.ledger.domain.model.SystemParties;
import com.ledger.domain.model.TransactionEntry;
import com.ledger.infrastructure.persistence.AccountRepository;
import com.ledger.infrastructure.persistence.CustomerRepository;
import com.ledger.infrastructure.persistence.TransactionEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final TransactionEntryRepository entryRepository;
    private final LedgerEngine ledgerEngine;
    private final PostingRetryExecutor postingRetryExecutor;

    public AccountService(CustomerRepository customerRepository,
                          AccountRepository accountRepository,
                          TransactionEntryRepository entryRepository,
                          LedgerEngine ledgerEngine,
                          PostingRetryExecutor postingRetryExecutor) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
        this.ledgerEngine = ledgerEngine;
        this.postingRetryExecutor = postingRetryExecutor;
    }

    @Transactional
    public AccountResponse open(OpenAccountRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new CustomerNotFoundException(request.customerId()));
        if (SystemParties.TREASURY_CUSTOMER_ID.equals(customer.getId())) {
            throw new TreasuryProtectedException("Cannot open customer accounts for the treasury principal");
        }
        if (customer.getStatus() == CustomerStatus.SUSPENDED) {
            throw new CustomerSuspendedException(customer.getId());
        }
        if (accountRepository.findIdByRoleAndCurrency(AccountRole.TREASURY, request.currency()).isEmpty()) {
            throw new UnsupportedCurrencyException(request.currency());
        }
        Account account = new Account();
        account.setCustomer(customer);
        account.setCurrency(request.currency());
        account.setBalance(MoneyAmounts.zero());
        account.setStatus(AccountStatus.ACTIVE);
        account.setAccountRole(AccountRole.CUSTOMER);
        return AccountResponse.from(accountRepository.save(account));
    }

    public CashMovementResponse deposit(String idempotencyKey, UUID accountId, CashMovementRequest request) {
        IdempotencyRequestAttributes.requireKey(idempotencyKey);
        return postingRetryExecutor.execute(() -> ledgerEngine.deposit(accountId, request));
    }

    public CashMovementResponse withdraw(String idempotencyKey, UUID accountId, CashMovementRequest request) {
        IdempotencyRequestAttributes.requireKey(idempotencyKey);
        return postingRetryExecutor.execute(() -> ledgerEngine.withdraw(accountId, request));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AccountStatementResponse statement(UUID accountId, int page, int size) {
        if (page < 0 || size < 1) {
            throw new InvalidRequestException("page must be >= 0 and size must be >= 1");
        }
        int pageSize = Math.min(size, 200);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        if (account.getAccountRole() != AccountRole.CUSTOMER) {
            throw new TreasuryProtectedException("Treasury statements are not exposed on this API");
        }
        Page<TransactionEntry> result = entryRepository.findByAccount_Id(
                accountId, PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "id")));
        List<TransactionEntry> newestFirst = result.getContent().stream()
                .sorted(Comparator.comparing(TransactionEntry::getId).reversed())
                .toList();
        BigDecimal netNewer = BigDecimal.ZERO;
        if (!newestFirst.isEmpty()) {
            long newestId = newestFirst.get(0).getId();
            BigDecimal net = entryRepository.netOfEntriesNewerThan(accountId, newestId);
            netNewer = net == null ? BigDecimal.ZERO : net;
        }
        List<RunningBalanceCalculator.Movement> movements = newestFirst.stream()
                .map(entry -> new RunningBalanceCalculator.Movement(entry.getEntryType(), entry.getAmount()))
                .toList();
        List<BigDecimal> running = RunningBalanceCalculator.afterEach(account.getBalance(), netNewer, movements);
        List<StatementLineResponse> lines = new ArrayList<>(newestFirst.size());
        for (int i = 0; i < newestFirst.size(); i++) {
            TransactionEntry entry = newestFirst.get(i);
            lines.add(new StatementLineResponse(
                    entry.getId(),
                    entry.getJournal().getReferenceId(),
                    entry.getJournal().getType(),
                    entry.getEntryType(),
                    MoneyAmounts.scale(entry.getAmount()),
                    entry.getCurrency(),
                    running.get(i),
                    entry.getJournal().getMemo(),
                    entry.getJournal().getCreatedAt()));
        }
        return new AccountStatementResponse(
                account.getId(),
                account.getCustomer().getId(),
                account.getCurrency(),
                MoneyAmounts.scale(account.getBalance()),
                account.getStatus(),
                page,
                pageSize,
                result.getTotalElements(),
                result.getTotalPages(),
                lines);
    }
}
