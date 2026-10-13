package com.ledger.domain.model;

import com.ledger.domain.exception.InvalidAmountException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyAmountsTests {

    @Test
    void scalesValuesThatFitTheLedgerScale() {
        assertThat(MoneyAmounts.requirePositive(new BigDecimal("10.5")))
                .isEqualByComparingTo("10.5000");
        assertThat(MoneyAmounts.scale(new BigDecimal("0"))).isEqualByComparingTo("0.0000");
    }

    @Test
    void rejectsNonPositiveAndOverPreciseAmounts() {
        assertThatThrownBy(() -> MoneyAmounts.requirePositive(new BigDecimal("0.0000")))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> MoneyAmounts.requirePositive(new BigDecimal("-1.0000")))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> MoneyAmounts.scale(new BigDecimal("1.00001")))
                .isInstanceOf(InvalidAmountException.class);
    }
}
