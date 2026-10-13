package com.ledger.infrastructure.persistence;

import com.ledger.domain.model.TransactionEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface TransactionEntryRepository extends JpaRepository<TransactionEntry, Long> {

    Page<TransactionEntry> findByAccount_Id(UUID accountId, Pageable pageable);

    @Query("""
            select e from TransactionEntry e
            join fetch e.journal j
            where j.referenceId = :referenceId
            """)
    List<TransactionEntry> findByJournalReferenceId(@Param("referenceId") UUID referenceId);

    @Query(value = """
            select coalesce(sum(case when entry_type = 'CREDIT' then amount else -amount end), 0)
            from transaction_entries
            where account_id = cast(:accountId as uuid) and id > :afterId
            """, nativeQuery = true)
    BigDecimal netOfEntriesNewerThan(@Param("accountId") UUID accountId, @Param("afterId") long afterId);
}
