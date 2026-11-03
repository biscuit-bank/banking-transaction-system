package com.depi.bank.domain;

import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtils {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MoneyUtils() {
    }

    /**
     * Validates that an input EGP amount is strictly positive and has at most 2 decimal places (BR-01).
     */
    public static long parsePositiveEgpToPiasters(BigDecimal amountEgp) {
        if (amountEgp == null || amountEgp.scale() > 2 || amountEgp.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException(ErrorCode.INVALID_AMOUNT);
        }
        return toPiasters(amountEgp);
    }

    public static long toPiasters(BigDecimal amountEgp) {
        if (amountEgp == null) {
            return 0L;
        }
        return amountEgp.setScale(2, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .longValueExact();
    }

    public static BigDecimal toEgp(long piasters) {
        return BigDecimal.valueOf(piasters, 2);
    }

    /**
     * Calculates the InstaPay inter-customer transfer fee:
     * 0.1% rounded HALF_UP, minimum 0.50 EGP (50 piasters), maximum 20.00 EGP (2,000 piasters) (BR-26).
     */
    public static long calculateInstaPayFeePiasters(long transferAmountPiasters) {
        BigDecimal rawFeePiasters = BigDecimal.valueOf(transferAmountPiasters)
                .multiply(new BigDecimal("0.001"))
                .setScale(0, RoundingMode.HALF_UP);
        long fee = rawFeePiasters.longValueExact();
        if (fee < 50L) {
            return 50L;
        }
        return Math.min(fee, 2000L);
    }
}