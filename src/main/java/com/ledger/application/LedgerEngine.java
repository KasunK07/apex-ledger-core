package com.ledger.application;

import com.ledger.api.dto.request.CashMovementRequest;
import com.ledger.api.dto.response.CashMovementResponse;
import com.ledger.api.dto.request.TransferRequest;
import com.ledger.api.dto.response.TransferResponse;
import com.ledger.domain.exception.AccountClosedException;
import com.ledger.domain.exception.AccountFrozenException;
import com.ledger.domain.exception.AccountNotFoundException;
import com.ledger.domain.exception.CurrencyMismatchException;
import com.ledger.domain.exception.CustomerKycPendingException;
import com.ledger.domain.exception.CustomerNotFoundException;
import com.ledger.domain.exception.CustomerSuspendedException;
import com.ledger.domain.exception.InsufficientFundsException;
import com.ledger.domain.exception.InvalidRequestException;
import com.ledger.domain.exception.SameAccountTransferException;
import com.ledger.domain.exception.TreasuryProtectedException;
import com.ledger.domain.exception.UnsupportedCurrencyException;
import com.ledger.domain.model.Account;
import com.ledger.domain.model.AccountRole;
import com.ledger.domain.model.AccountStatus;
import com.ledger.domain.model.Customer;
import com.ledger.domain.model.CustomerStatus;
import com.ledger.domain.model.EntryType;
import com.ledger.domain.model.IdempotencyKey;
import com.ledger.domain.model.JournalStatus;
import com.ledger.domain.model.JournalType;
import com.ledger.domain.model.MoneyAmounts;
import com.ledger.domain.model.TransactionEntry;
import com.ledger.domain.model.TransactionJournal;
import com.ledger.infrastructure.persistence.AccountLockRef;
import com.ledger.infrastructure.persistence.AccountRepository;
import com.ledger.infrastructure.persistence.CustomerRepository;
import com.ledger.infrastructure.persistence.TransactionEntryRepository;
import com.ledger.infrastructure.persistence.TransactionJournalRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class LedgerEngine {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final TransactionJournalRepository journalRepository;
    private final TransactionEntryRepository entryRepository;
    private final IdempotencyGuard idempotencyGuard;

    public LedgerEngine(AccountRepository accountRepository,
                        CustomerRepository customerRepository,
                        TransactionJournalRepository journalRepository,
                        TransactionEntryRepository entryRepository,
                        IdempotencyGuard idempotencyGuard) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.journalRepository = journalRepository;
        this.entryRepository = entryRepository;
        this.idempotencyGuard = idempotencyGuard;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public TransferResponse transfer(TransferRequest request) {
        return idempotencyGuard.run(TransferResponse.class, key -> postTransfer(request, key));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public CashMovementResponse deposit(UUID accountId, CashMovementRequest request) {
        return idempotencyGuard.run(CashMovementResponse.class, key -> postCash(accountId, request, key, JournalType.DEPOSIT));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public CashMovementResponse withdraw(UUID accountId, CashMovementRequest request) {
        return idempotencyGuard.run(CashMovementResponse.class, key -> postCash(accountId, request, key, JournalType.WITHDRAWAL));
    }

    private TransferResponse postTransfer(TransferRequest request, IdempotencyKey key) {
        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw new SameAccountTransferException();
        }
        BigDecimal amount = MoneyAmounts.requirePositive(request.amount());
        Map<UUID, Account> locked = lockForPosting(Set.of(request.sourceAccountId(), request.destinationAccountId()));
        Account source = locked.get(request.sourceAccountId());
        Account destination = locked.get(request.destinationAccountId());
        ensureCustomerAccount(source);
        ensureCustomerAccount(destination);
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new CurrencyMismatchException(source.getCurrency(), destination.getCurrency());
        }
        String memo = memo(request.memo());
        TransactionJournal journal = post(JournalType.TRANSFER, key, memo, List.of(
                new PostingLeg(source.getId(), EntryType.DEBIT, amount, source.getCurrency()),
                new PostingLeg(destination.getId(), EntryType.CREDIT, amount, destination.getCurrency())
        ), locked);
        return new TransferResponse(
                journal.getReferenceId(),
                journal.getType(),
                journal.getStatus(),
                source.getId(),
                destination.getId(),
                amount,
                source.getCurrency(),
                MoneyAmounts.scale(source.getBalance()),
                MoneyAmounts.scale(destination.getBalance()),
                journal.getMemo(),
                journal.getCreatedAt());
    }

    private CashMovementResponse postCash(UUID accountId, CashMovementRequest request, IdempotencyKey key, JournalType type) {
        BigDecimal amount = MoneyAmounts.requirePositive(request.amount());
        String currency = accountRepository.findCurrencyById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        UUID treasuryId = accountRepository.findIdByRoleAndCurrency(AccountRole.TREASURY, currency)
                .orElseThrow(() -> new UnsupportedCurrencyException(currency));
        Map<UUID, Account> locked = lockForPosting(Set.of(accountId, treasuryId));
        Account customerAccount = locked.get(accountId);
        Account treasury = locked.get(treasuryId);
        ensureCustomerAccount(customerAccount);
        ensureTreasury(treasury);
        List<PostingLeg> legs = type == JournalType.DEPOSIT
                ? List.of(
                new PostingLeg(treasury.getId(), EntryType.DEBIT, amount, currency),
                new PostingLeg(customerAccount.getId(), EntryType.CREDIT, amount, currency))
                : List.of(
                new PostingLeg(customerAccount.getId(), EntryType.DEBIT, amount, currency),
                new PostingLeg(treasury.getId(), EntryType.CREDIT, amount, currency));
        TransactionJournal journal = post(type, key, memo(request.memo()), legs, locked);
        return new CashMovementResponse(
                journal.getReferenceId(),
                journal.getType(),
                journal.getStatus(),
                customerAccount.getId(),
                amount,
                currency,
                MoneyAmounts.scale(customerAccount.getBalance()),
                journal.getMemo(),
                journal.getCreatedAt());
    }

    private TransactionJournal post(JournalType type,
                                    IdempotencyKey key,
                                    String memo,
                                    List<PostingLeg> legs,
                                    Map<UUID, Account> locked) {
        if (legs.size() != 2) {
            throw new IllegalStateException("A journal must contain exactly two entries");
        }
        BigDecimal debits = MoneyAmounts.zero();
        BigDecimal credits = MoneyAmounts.zero();
        for (PostingLeg leg : legs) {
            Account account = required(locked, leg.accountId());
            if (!account.getCurrency().equals(leg.currency())) {
                throw new CurrencyMismatchException(account.getCurrency(), leg.currency());
            }
            BigDecimal amount = MoneyAmounts.requirePositive(leg.amount());
            if (leg.entryType() == EntryType.DEBIT) {
                debits = debits.add(amount);
            } else {
                credits = credits.add(amount);
            }
        }
        if (debits.compareTo(credits) != 0) {
            throw new IllegalStateException("Unbalanced journal");
        }
        for (PostingLeg leg : legs) {
            if (leg.entryType() != EntryType.DEBIT) {
                continue;
            }
            Account account = required(locked, leg.accountId());
            if (account.getBalance().compareTo(leg.amount()) < 0) {
                throw new InsufficientFundsException(account.getId(), account.getCurrency());
            }
        }
        for (PostingLeg leg : legs) {
            Account account = required(locked, leg.accountId());
            BigDecimal amount = MoneyAmounts.requirePositive(leg.amount());
            if (leg.entryType() == EntryType.DEBIT) {
                account.setBalance(account.getBalance().subtract(amount));
            } else {
                account.setBalance(account.getBalance().add(amount));
            }
        }

        TransactionJournal journal = new TransactionJournal();
        journal.setReferenceId(UUID.randomUUID());
        journal.setType(type);
        journal.setStatus(JournalStatus.COMPLETED);
        journal.setMemo(memo);
        journal.setIdempotencyKey(key);
        journalRepository.save(journal);
        for (PostingLeg leg : legs) {
            TransactionEntry entry = new TransactionEntry();
            entry.setJournal(journal);
            entry.setAccount(required(locked, leg.accountId()));
            entry.setAmount(MoneyAmounts.requirePositive(leg.amount()));
            entry.setEntryType(leg.entryType());
            entry.setCurrency(leg.currency());
            entryRepository.save(entry);
        }
        log.info("Posted {} journal {}", type, journal.getReferenceId());
        return journal;
    }

    /**
     * Retail customer rows are locked before account rows, each set in UUID order.
     * Treasury customer rows are not locked: every currency shares that one party,
     * and locking it would serialize deposits across currencies. The treasury
     * account row is still locked with the customer account.
     */
    private Map<UUID, Account> lockForPosting(Collection<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            throw new IllegalStateException("No accounts to lock");
        }
        List<AccountLockRef> refs = accountRepository.findLockRefs(accountIds);
        for (UUID accountId : accountIds) {
            boolean present = refs.stream().anyMatch(ref -> ref.id().equals(accountId));
            if (!present) {
                throw new AccountNotFoundException(accountId);
            }
        }
        List<UUID> customerIds = refs.stream()
                .filter(ref -> ref.accountRole() == AccountRole.CUSTOMER)
                .map(AccountLockRef::customerId)
                .distinct()
                .sorted()
                .toList();
        for (UUID customerId : customerIds) {
            customerRepository.findByIdForUpdate(customerId)
                    .orElseThrow(() -> new CustomerNotFoundException(customerId));
        }
        Map<UUID, Account> locked = new LinkedHashMap<>();
        accountIds.stream().sorted(Comparator.naturalOrder()).forEach(accountId -> {
            Account account = accountRepository.findByIdForUpdate(accountId)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));
            locked.put(accountId, account);
        });
        return locked;
    }

    private void ensureCustomerAccount(Account account) {
        if (account.getAccountRole() == AccountRole.TREASURY) {
            throw new TreasuryProtectedException("Treasury accounts cannot be used as customer endpoints");
        }
        ensureOperable(account);
        ensureCustomerEligible(account.getCustomer());
    }

    private void ensureTreasury(Account account) {
        if (account.getAccountRole() != AccountRole.TREASURY) {
            throw new IllegalStateException("Expected a treasury account");
        }
        ensureOperable(account);
    }

    private void ensureOperable(Account account) {
        if (account.getStatus() == AccountStatus.FROZEN) {
            throw new AccountFrozenException(account.getId());
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AccountClosedException(account.getId());
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidRequestException("Account " + account.getId() + " is not active");
        }
    }

    private void ensureCustomerEligible(Customer customer) {
        if (customer.getStatus() == CustomerStatus.SUSPENDED) {
            throw new CustomerSuspendedException(customer.getId());
        }
        if (customer.getStatus() == CustomerStatus.KYC_PENDING) {
            throw new CustomerKycPendingException(customer.getId());
        }
        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new InvalidRequestException("Customer " + customer.getId() + " cannot transact");
        }
    }

    private Account required(Map<UUID, Account> locked, UUID accountId) {
        Account account = locked.get(accountId);
        if (account == null) {
            throw new IllegalStateException("Leg account was not locked: " + accountId);
        }
        return account;
    }

    private String memo(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }
        return memo.trim();
    }
}
