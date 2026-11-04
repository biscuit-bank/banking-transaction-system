package com.depi.bank.domain;

import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DomainPrecisionAndLuhnTest {

    private final PiasterAttributeConverter converter = new PiasterAttributeConverter();

    @Test
    @DisplayName("TC-MON-02: Convert BigDecimal EGP (scale 2, HALF_UP) to exact BIGINT piasters and back")
    void testPiasterConversionPrecision() {
        BigDecimal egp = new BigDecimal("1000.50");
        Long piasters = converter.convertToDatabaseColumn(egp);
        assertEquals(100050L, piasters);
        assertEquals(new BigDecimal("1000.50"), converter.convertToEntityAttribute(piasters));
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    @DisplayName("TC-MON-01: Reject zero, negative, and >2 decimal place amounts with INVALID_AMOUNT")
    void testInvalidAmountsRejected() {
        BankException zeroEx = assertThrows(BankException.class,
                () -> MoneyUtils.parsePositiveEgpToPiasters(BigDecimal.ZERO));
        assertEquals(ErrorCode.INVALID_AMOUNT, zeroEx.getErrorCode());

        BankException negEx = assertThrows(BankException.class,
                () -> MoneyUtils.parsePositiveEgpToPiasters(new BigDecimal("-10.00")));
        assertEquals(ErrorCode.INVALID_AMOUNT, negEx.getErrorCode());

        BankException scaleEx = assertThrows(BankException.class,
                () -> MoneyUtils.parsePositiveEgpToPiasters(new BigDecimal("10.005")));
        assertEquals(ErrorCode.INVALID_AMOUNT, scaleEx.getErrorCode());
    }
}