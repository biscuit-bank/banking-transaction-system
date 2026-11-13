package com.depi.bank.domain;

import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * Generates and validates 12-digit Luhn account numbers (DOM-01, ACC-02, BR-23).
 */
@Service
public class LuhnAccountNumberService {

    /**
     * Validates a full 12-digit account number.
     *
     * <p>Standard Luhn mod-10 is used for all newly generated numbers. For backward
     * compatibility, numbers produced by the earlier check-digit convention are also accepted.</p>
     */
    public boolean isValid(String accountNumber) {
        if (accountNumber == null || !accountNumber.matches("\\d{12}")) {
            return false;
        }
        return passesStandardLuhn(accountNumber) || passesLegacyCheckDigit(accountNumber);
    }

    /**
     * Calculates the standard Luhn check digit for an 11-digit payload.
     */
    public int calculateCheckDigit(String payload11Digits) {
        if (payload11Digits == null || !payload11Digits.matches("\\d{11}")) {
            throw new IllegalArgumentException("Payload must be exactly 11 numeric digits.");
        }
        return (10 - (sumForCheckDigit(payload11Digits) % 10)) % 10;
    }

    /**
     * Generates a deterministic 12-digit Luhn-valid account number using a seeded {@link Random}.
     */
    public String generateDeterministic(long seed) {
        Random random = new Random(seed);
        StringBuilder builder = new StringBuilder(11);

        // Keep the leading digit non-zero so the value is always a full 12-digit number.
        builder.append(random.nextInt(9) + 1);
        for (int i = 0; i < 10; i++) {
            builder.append(random.nextInt(10));
        }

        String payload = builder.toString();
        return payload + calculateCheckDigit(payload);
    }

    private boolean passesStandardLuhn(String accountNumber) {
        int sum = 0;
        boolean doubleDigit = false;

        for (int i = accountNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(accountNumber.charAt(i));
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }

        return sum % 10 == 0;
    }

    private int sumForCheckDigit(String payload11Digits) {
        int sum = 0;
        // When the check digit is appended, the rightmost payload digit is doubled.
        boolean doubleDigit = true;

        for (int i = payload11Digits.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(payload11Digits.charAt(i));
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }

        return sum;
    }

    private boolean passesLegacyCheckDigit(String accountNumber) {
        String payload = accountNumber.substring(0, 11);
        int expectedCheckDigit = calculateLegacyCheckDigit(payload);
        int actualCheckDigit = Character.getNumericValue(accountNumber.charAt(11));
        return expectedCheckDigit == actualCheckDigit;
    }

    private int calculateLegacyCheckDigit(String payload11Digits) {
        int sum = 0;
        for (int i = 0; i < payload11Digits.length(); i++) {
            int digit = Character.getNumericValue(payload11Digits.charAt(i));
            if (i % 2 == 1) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return (10 - (sum % 10)) % 10;
    }
}
