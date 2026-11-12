import java.util.Random;

public class LuhnAccountNumberService {

    /**
     * Validates if a 12-digit account number string complies with Luhn mod 10.
     */
    public boolean isValid(String accountNumber) {
        if (accountNumber == null || !accountNumber.matches("\\d{12}")) {
            return false;
        }

        int sum = 0;
        boolean alternate = false;

        for (int i = accountNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(accountNumber.charAt(i));

            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }

            sum += digit;
            alternate = !alternate;
        }

        return sum % 10 == 0;
    }

    /**
     * Calculates the Luhn check digit for an 11-digit payload string.
     */
    public int calculateCheckDigit(String payload11Digits) {
        if (payload11Digits == null || !payload11Digits.matches("\\d{11}")) {
            throw new IllegalArgumentException("Payload must be exactly 11 numeric digits");
        }

        int sum = 0;
        // When check digit (index 11) is present, the 11th payload digit (index 10)
        // is at an odd distance from right, so its alternate flag is true.
        boolean alternate = true;

        for (int i = payload11Digits.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(payload11Digits.charAt(i));

            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }

            sum += digit;
            alternate = !alternate;
        }

        return (10 - (sum % 10)) % 10;
    }

    /**
     * Generates a deterministic 12-digit Luhn account number using a seeded Random.
     */
    public String generateDeterministic(long seed) {
        Random random = new Random(seed);
        StringBuilder builder = new StringBuilder();

        // First digit should non-zero for standard 12-digit number representation
        builder.append(random.nextInt(9) + 1);

        for (int i = 0; i < 10; i++) {
            builder.append(random.nextInt(10));
        }

        String payload = builder.toString();
        int checkDigit = calculateCheckDigit(payload);
        return payload + checkDigit;
    }
}
