package com.ledger.application;

import com.ledger.domain.model.EntryType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunningBalanceCalculatorTests {

    @Test
    void walksNewestEntriesBackToTheBalanceAfterEachPosting() {
        List<BigDecimal> balances = RunningBalanceCalculator.afterEach(
                new BigDecimal("60.0000"),
                new BigDecimal("0.0000"),
                List.of(
                        new RunningBalanceCalculator.Movement(EntryType.DEBIT, new BigDecimal("40.0000")),
                        new RunningBalanceCalculator.Movement(EntryType.CREDIT, new BigDecimal("100.0000"))
                ));

        assertThat(balances).hasSize(2);
        assertThat(balances.get(0)).isEqualByComparingTo("60.0000");
        assertThat(balances.get(1)).isEqualByComparingTo("100.0000");
    }

    @Test
    void subtractsMovementsThatAreNewerThanThePage() {
        List<BigDecimal> balances = RunningBalanceCalculator.afterEach(
                new BigDecimal("60.0000"),
                new BigDecimal("-40.0000"),
                List.of(new RunningBalanceCalculator.Movement(EntryType.CREDIT, new BigDecimal("100.0000"))));

        assertThat(balances.get(0)).isEqualByComparingTo("100.0000");
    }
}
