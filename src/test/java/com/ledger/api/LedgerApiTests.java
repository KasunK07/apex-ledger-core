package com.ledger.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.api.dto.response.AccountResponse;
import com.ledger.api.dto.response.AccountStatementResponse;
import com.ledger.api.dto.response.CashMovementResponse;
import com.ledger.api.dto.response.CustomerResponse;
import com.ledger.api.dto.response.TransferResponse;
import com.ledger.domain.model.AccountStatus;
import com.ledger.domain.model.EntryType;
import com.ledger.domain.model.SystemParties;
import com.ledger.infrastructure.persistence.AccountRepository;
import com.ledger.infrastructure.persistence.TransactionEntryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIf("dockerAvailable")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/db/treasury-test-seed.sql")
class LedgerApiTests {

    static PostgreSQLContainer<?> postgres;

    @org.springframework.test.context.DynamicPropertySource
    static void datasource(org.springframework.test.context.DynamicPropertyRegistry registry) {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransactionEntryRepository entryRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @PersistenceContext
    EntityManager entityManager;

    private String token;

    @Test
    void rejectsMissingCredentials() {
        ResponseEntity<String> response = rest.exchange("/api/v1/customers", HttpMethod.POST,
                new HttpEntity<>("{\"email\":\"a@b.co\",\"fullName\":\"A\"}", jsonHeaders(null)), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void depositTransferAndStatementConserveMoney() throws Exception {
        BigDecimal before = usdSupply();
        UUID customerA = register("Ada");
        UUID customerB = register("Grace");
        UUID source = open(customerA, "USD");
        UUID destination = open(customerB, "USD");

        CashMovementResponse deposit = deposit(source, "100.0000", "deposit-" + UUID.randomUUID());
        assertThat(deposit.balance()).isEqualByComparingTo("100.0000");
        assertJournal(deposit.referenceId(), EntryType.CREDIT, source);

        TransferResponse transfer = transfer(source, destination, "40.0000", "transfer-" + UUID.randomUUID());
        assertThat(transfer.sourceBalance()).isEqualByComparingTo("60.0000");
        assertThat(transfer.destinationBalance()).isEqualByComparingTo("40.0000");
        assertJournal(transfer.referenceId(), EntryType.DEBIT, source);

        AccountStatementResponse statement = statement(source);
        assertThat(statement.balance()).isEqualByComparingTo("60.0000");
        assertThat(statement.entries()).hasSize(2);
        assertThat(statement.entries().get(0).entryType()).isEqualTo(EntryType.DEBIT);
        assertThat(statement.entries().get(0).runningBalance()).isEqualByComparingTo("60.0000");
        assertThat(statement.entries().get(1).entryType()).isEqualTo(EntryType.CREDIT);
        assertThat(statement.entries().get(1).runningBalance()).isEqualByComparingTo("100.0000");
        assertThat(usdSupply()).isEqualByComparingTo(before);

        CashMovementResponse withdrawal = withdraw(source, "10.0000", "withdraw-" + UUID.randomUUID());
        assertThat(withdrawal.balance()).isEqualByComparingTo("50.0000");
        assertThat(usdSupply()).isEqualByComparingTo(before);
    }

    @Test
    void replaysTheOriginalTransferAndRejectsADifferentBody() throws Exception {
        UUID source = open(register("Replay"), "USD");
        UUID destination = open(register("Replay Dest"), "USD");
        deposit(source, "30.0000", "seed-" + UUID.randomUUID());
        String key = "same-key-" + UUID.randomUUID();

        ResponseEntity<TransferResponse> first = postTransfer(source, destination, "10.0000", key);
        ResponseEntity<TransferResponse> second = postTransfer(source, destination, "10.0000", key);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getHeaders().getFirst("Idempotency-Replayed")).isEqualTo("true");
        assertThat(second.getBody().referenceId()).isEqualTo(first.getBody().referenceId());
        assertThat(statement(source).balance()).isEqualByComparingTo("20.0000");

        ResponseEntity<String> conflict = postTransferRaw(source, destination, "11.0000", key);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(objectMapper.readTree(conflict.getBody()).get("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(statement(source).balance()).isEqualByComparingTo("20.0000");
    }

    @Test
    void concurrentSameKeyDepositPostsOnce() throws Exception {
        UUID accountId = open(register("Once"), "USD");
        String key = "concurrent-key-" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<ResponseEntity<String>>> futures = List.of(
                    pool.submit(() -> postDepositRaw(accountId, "25.0000", key)),
                    pool.submit(() -> postDepositRaw(accountId, "25.0000", key)));
            List<Integer> statuses = new ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                ResponseEntity<String> response = future.get();
                statuses.add(response.getStatusCode().value());
                assertThat(response.getStatusCode().is2xxSuccessful())
                        .withFailMessage(response.getBody())
                        .isTrue();
            }
            assertThat(statuses).contains(201);
            assertThat(statement(accountId).balance()).isEqualByComparingTo("25.0000");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentTransfersDoNotOverdraw() throws Exception {
        BigDecimal before = usdSupply();
        UUID source = open(register("Hot"), "USD");
        UUID destination = open(register("Cold"), "USD");
        deposit(source, "50.0000", "fund-" + UUID.randomUUID());
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String key = "parallel-" + UUID.randomUUID();
                futures.add(pool.submit(() -> postTransferRaw(source, destination, "10.0000", key)));
            }
            for (Future<ResponseEntity<String>> future : futures) {
                ResponseEntity<String> response = future.get();
                assertThat(response.getStatusCode().is2xxSuccessful())
                        .withFailMessage(response.getBody())
                        .isTrue();
            }
        } finally {
            pool.shutdownNow();
        }
        AccountStatementResponse sourceStatement = statement(source);
        AccountStatementResponse destinationStatement = statement(destination);
        assertThat(sourceStatement.balance()).isEqualByComparingTo("0.0000");
        assertThat(destinationStatement.balance()).isEqualByComparingTo("50.0000");
        assertThat(usdSupply()).isEqualByComparingTo(before);
    }

    @Test
    void businessRulesRejectInvalidMovements() throws Exception {
        UUID active = open(register("Rules"), "USD");
        UUID other = open(register("Other"), "EUR");
        deposit(active, "5.0000", "rules-fund-" + UUID.randomUUID());

        ResponseEntity<String> same = postTransferRaw(active, active, "1.0000", "same-" + UUID.randomUUID());
        assertThat(code(same)).isEqualTo("SAME_ACCOUNT_TRANSFER");

        UUID usdPeer = open(register("Peer"), "USD");
        ResponseEntity<String> mismatch = postTransferRaw(active, other, "1.0000", "fx-" + UUID.randomUUID());
        assertThat(code(mismatch)).isEqualTo("CURRENCY_MISMATCH");

        ResponseEntity<String> poor = postTransferRaw(active, usdPeer, "6.0000", "poor-" + UUID.randomUUID());
        assertThat(poor.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(code(poor)).isEqualTo("INSUFFICIENT_FUNDS");

        var frozenAccount = accountRepository.findById(active).orElseThrow();
        frozenAccount.setStatus(AccountStatus.FROZEN);
        accountRepository.saveAndFlush(frozenAccount);
        ResponseEntity<String> frozen = postTransferRaw(active, usdPeer, "1.0000", "frozen-" + UUID.randomUUID());
        assertThat(code(frozen)).isEqualTo("ACCOUNT_FROZEN");

        ResponseEntity<String> gbp = post("/api/v1/accounts",
                "{\"customerId\":\"" + register("NoGbp") + "\",\"currency\":\"GBP\"}", null);
        assertThat(code(gbp)).isEqualTo("UNSUPPORTED_CURRENCY");

        ResponseEntity<String> missingKey = postTransferRaw(usdPeer, active, "1.0000", null);
        assertThat(missingKey.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void suspendedAndKycCustomersCannotMoveMoney() throws Exception {
        CustomerResponse pending = registerRaw("Pending", "KYC_PENDING");
        UUID pendingAccount = open(pending.id(), "LKR");
        ResponseEntity<String> blocked = postDepositRaw(pendingAccount, "1.0000", "kyc-" + UUID.randomUUID());
        assertThat(code(blocked)).isEqualTo("CUSTOMER_KYC_PENDING");

        CustomerResponse suspended = registerRaw("Stopped", "SUSPENDED");
        ResponseEntity<String> openAttempt = post("/api/v1/accounts",
                "{\"customerId\":\"" + suspended.id() + "\",\"currency\":\"LKR\"}", null);
        assertThat(code(openAttempt)).isEqualTo("CUSTOMER_SUSPENDED");
    }

    @Test
    void databaseRejectsAnUnbalancedJournal() {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            entityManager.joinTransaction();
            Object id = entityManager.createNativeQuery("""
                            insert into transaction_journals (reference_id, type, status, created_at)
                            values (:reference, 'TRANSFER', 'COMPLETED', now())
                            returning id
                            """)
                    .setParameter("reference", UUID.randomUUID())
                    .getSingleResult();
            entityManager.createNativeQuery("""
                            insert into transaction_entries (journal_id, account_id, amount, entry_type, currency)
                            values (:journalId, :accountId, 1.0000, 'DEBIT', 'USD')
                            """)
                    .setParameter("journalId", ((Number) id).longValue())
                    .setParameter("accountId", SystemParties.TREASURY_USD_ACCOUNT_ID)
                    .executeUpdate();
            entityManager.flush();
        })).satisfies(ex -> assertThat(chain(ex)).contains("exactly two entries"));
    }

    private static String chain(Throwable ex) {
        StringBuilder builder = new StringBuilder();
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor.getMessage() != null) {
                builder.append(cursor.getMessage()).append('\n');
            }
            cursor = cursor.getCause();
        }
        return builder.toString();
    }

    private void assertJournal(UUID referenceId, EntryType customerSide, UUID customerAccountId) {
        var entries = entryRepository.findByJournalReferenceId(referenceId);
        assertThat(entries).hasSize(2);
        BigDecimal debits = entries.stream()
                .filter(entry -> entry.getEntryType() == EntryType.DEBIT)
                .map(entry -> entry.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = entries.stream()
                .filter(entry -> entry.getEntryType() == EntryType.CREDIT)
                .map(entry -> entry.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(debits).isEqualByComparingTo(credits);
        assertThat(entries).anyMatch(entry -> entry.getAccount().getId().equals(customerAccountId)
                && entry.getEntryType() == customerSide);
    }

    private BigDecimal usdSupply() {
        return accountRepository.sumBalanceByCurrency("USD");
    }

    private UUID register(String name) throws Exception {
        return registerRaw(name, null).id();
    }

    private CustomerResponse registerRaw(String name, String status) throws Exception {
        String email = name.toLowerCase().replace(" ", "-") + "-" + UUID.randomUUID() + "@example.com";
        String statusJson = status == null ? "" : ",\"status\":\"" + status + "\"";
        ResponseEntity<String> response = post("/api/v1/customers",
                "{\"email\":\"" + email + "\",\"fullName\":\"" + name + "\"" + statusJson + "}", null);
        assertThat(response.getStatusCode()).withFailMessage(response.getBody()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readValue(response.getBody(), CustomerResponse.class);
    }

    private UUID open(UUID customerId, String currency) throws Exception {
        ResponseEntity<String> response = post("/api/v1/accounts",
                "{\"customerId\":\"" + customerId + "\",\"currency\":\"" + currency + "\"}", null);
        assertThat(response.getStatusCode()).withFailMessage(response.getBody()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readValue(response.getBody(), AccountResponse.class).id();
    }

    private CashMovementResponse deposit(UUID accountId, String amount, String key) throws Exception {
        ResponseEntity<String> response = postDepositRaw(accountId, amount, key);
        assertThat(response.getStatusCode()).withFailMessage(response.getBody()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readValue(response.getBody(), CashMovementResponse.class);
    }

    private CashMovementResponse withdraw(UUID accountId, String amount, String key) throws Exception {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/accounts/" + accountId + "/withdrawals",
                "{\"amount\":\"" + amount + "\"}", key);
        assertThat(response.getStatusCode()).withFailMessage(response.getBody()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readValue(response.getBody(), CashMovementResponse.class);
    }

    private TransferResponse transfer(UUID source, UUID destination, String amount, String key) throws Exception {
        ResponseEntity<TransferResponse> response = postTransfer(source, destination, amount, key);
        assertThat(response.getStatusCode()).withFailMessage(String.valueOf(response.getBody())).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ResponseEntity<TransferResponse> postTransfer(UUID source, UUID destination, String amount, String key) throws Exception {
        ResponseEntity<String> raw = postTransferRaw(source, destination, amount, key);
        if (!raw.getStatusCode().is2xxSuccessful()) {
            throw new AssertionError(raw.getStatusCode() + " " + raw.getBody());
        }
        return new ResponseEntity<>(objectMapper.readValue(raw.getBody(), TransferResponse.class), raw.getHeaders(), raw.getStatusCode());
    }

    private ResponseEntity<String> postTransferRaw(UUID source, UUID destination, String amount, String key) throws Exception {
        return post("/api/v1/transfers",
                "{\"sourceAccountId\":\"" + source + "\",\"destinationAccountId\":\"" + destination + "\",\"amount\":\"" + amount + "\"}",
                key);
    }

    private ResponseEntity<String> postDepositRaw(UUID accountId, String amount, String key) throws Exception {
        return post("/api/v1/accounts/" + accountId + "/deposits", "{\"amount\":\"" + amount + "\"}", key);
    }

    private AccountStatementResponse statement(UUID accountId) throws Exception {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/accounts/" + accountId + "/statement", null, null);
        assertThat(response.getStatusCode()).withFailMessage(response.getBody()).isEqualTo(HttpStatus.OK);
        return objectMapper.readValue(response.getBody(), AccountStatementResponse.class);
    }

    private ResponseEntity<String> post(String path, String body, String idempotencyKey) throws Exception {
        return exchange(HttpMethod.POST, path, body, idempotencyKey);
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, String body, String idempotencyKey) throws Exception {
        HttpHeaders headers = jsonHeaders(bearer());
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private String code(ResponseEntity<String> response) throws Exception {
        JsonNode tree = objectMapper.readTree(response.getBody());
        return tree.get("code").asText();
    }

    private synchronized String bearer() throws Exception {
        if (token != null) {
            return token;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange("/api/v1/auth/token", HttpMethod.POST,
                new HttpEntity<>("{\"clientId\":\"ledger-api\",\"clientSecret\":\"ledger-secret\"}", headers), String.class);
        assertThat(response.getStatusCode()).withFailMessage(response.getBody()).isEqualTo(HttpStatus.OK);
        token = objectMapper.readTree(response.getBody()).get("accessToken").asText();
        return token;
    }

    private HttpHeaders jsonHeaders(String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return headers;
    }
}
