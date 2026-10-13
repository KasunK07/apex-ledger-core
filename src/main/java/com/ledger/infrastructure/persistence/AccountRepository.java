package com.ledger.infrastructure.persistence;

import com.ledger.domain.model.Account;
import com.ledger.domain.model.AccountRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

    @Query("select a.currency from Account a where a.id = :id")
    Optional<String> findCurrencyById(@Param("id") UUID id);

    @Query("select a.id from Account a where a.accountRole = :role and a.currency = :currency")
    Optional<UUID> findIdByRoleAndCurrency(@Param("role") AccountRole role, @Param("currency") String currency);

    @Query("""
            select new com.ledger.infrastructure.persistence.AccountLockRef(a.id, c.id, a.accountRole)
            from Account a join a.customer c
            where a.id in :ids
            """)
    List<AccountLockRef> findLockRefs(@Param("ids") Collection<UUID> ids);

    @Query("select coalesce(sum(a.balance), 0) from Account a where a.currency = :currency")
    BigDecimal sumBalanceByCurrency(@Param("currency") String currency);
}
