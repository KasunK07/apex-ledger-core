package com.ledger.domain.model;

import com.ledger.domain.exception.InvalidAmountException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyAmounts {

    public static final int SCALE = 4;

    private MoneyAmounts() {
    }

    public static BigDecimal zero() {
        return new BigDecimal("0.0000");
    }

    public static BigDecimal scale(BigDecimal value) {
        if (value == null) {
            throw new InvalidAmountException("Amount is required");
        }
        try {
            return value.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new InvalidAmountException("Amount supports at most 4 decimal places");
        }
    }

    public static BigDecimal requirePositive(BigDecimal value) {
        BigDecimal scaled = scale(value);
        if (scaled.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
        if (scaled.precision() - scaled.scale() > 15) {
            throw new InvalidAmountException("Amount exceeds the supported range");
        }
        return scaled;
    }

    public static BigDecimal normalizeForJson(BigDecimal value) {
        RoundingMode mode = value.scale() > SCALE ? RoundingMode.HALF_EVEN : RoundingMode.UNNECESSARY;
        return value.setScale(SCALE, mode);
    }
}
