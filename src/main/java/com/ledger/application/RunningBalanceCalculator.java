package com.ledger.application;

import com.ledger.domain.model.EntryType;
import com.ledger.domain.model.MoneyAmounts;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class RunningBalanceCalculator {

    private RunningBalanceCalculator() {
    }

    public record Movement(EntryType entryType, BigDecimal amount) {
    }

    public static List<BigDecimal> afterEach(BigDecimal currentBalance,
                                              BigDecimal netOfNewerEntries,
                                              List<Movement> newestFirst) {
        BigDecimal cursor = MoneyAmounts.scale(currentBalance).subtract(MoneyAmounts.scale(netOfNewerEntries));
        List<BigDecimal> balances = new ArrayList<>(newestFirst.size());
        for (Movement movement : newestFirst) {
            balances.add(MoneyAmounts.scale(cursor));
            cursor = cursor.subtract(signed(movement));
        }
        return balances;
    }

    private static BigDecimal signed(Movement movement) {
        BigDecimal amount = MoneyAmounts.scale(movement.amount());
        return movement.entryType() == EntryType.CREDIT ? amount : amount.negate();
    }
}
