package com.depi.bank.domain;

import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtils {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MoneyUtils() {
    }

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
}