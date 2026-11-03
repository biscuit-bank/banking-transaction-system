package com.depi.bank.domain;

import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @ParameterizedTest
    @DisplayName("TC-TX-04: Calculate InstaPay transfer fee triplet (0.1% HALF_UP, min 0.50 EGP, max 20.00 EGP)")
    @CsvSource({
            "10000,   50",    // 100.00 EGP -> 0.50 EGP (50 piasters min)
            "50000,   50",    // 500.00 EGP -> 0.50 EGP
            "100000,  100",   // 1,000.00 EGP -> 1.00 EGP (100 piasters)
            "123455,  123",   // 1,234.55 EGP -> 1.23 EGP (123 piasters HALF_UP)
            "123500,  124",   // 1,235.00 EGP -> 1.24 EGP (124 piasters HALF_UP)
            "2000000, 2000",  // 20,000.00 EGP -> 20.00 EGP (2,000 piasters)
            "5000000, 2000"   // 50,000.00 EGP -> capped at 20.00 EGP (2,000 piasters max)
    })
    void testInstaPayFeeCalculation(long amountPiasters, long expectedFeePiasters) {
        assertEquals(expectedFeePiasters, MoneyUtils.calculateInstaPayFeePiasters(amountPiasters));
    }

    
}