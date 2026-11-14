package com.depi.bank.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // 400 Bad Request
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "Please enter a valid positive amount with up to 2 decimal places."),
    INVALID_OPENING_AMOUNT(HttpStatus.BAD_REQUEST, "Opening deposit amount is outside the permitted range for this account type."),
    INVALID_ACCOUNT_NUMBER(HttpStatus.BAD_REQUEST, "Invalid 12-digit account number or Luhn check digit."),
    SAME_ACCOUNT_TRANSFER(HttpStatus.BAD_REQUEST, "Source and destination accounts cannot be the same."),
    INVALID_FD_TERM(HttpStatus.BAD_REQUEST, "Fixed Deposit term must be either 12 or 36 months."),
    UNSUPPORTED_CURRENCY(HttpStatus.BAD_REQUEST, "Only Egyptian Pound (EGP) transactions are supported."),
    INVALID_SEARCH_QUERY(HttpStatus.BAD_REQUEST, "Search query must contain at least 2 non-whitespace characters."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "One or more request fields failed validation."),

    // 401 Unauthorized
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password."),
    ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED, "Invalid email or password."),

    // 403 Forbidden
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission to access this resource."),
    ADMIN_TXN_FORBIDDEN(HttpStatus.FORBIDDEN, "Administrators are strictly prohibited from executing financial transactions."),
    LEDGER_IMMUTABLE_VIOLATION(HttpStatus.FORBIDDEN, "Ledger and audit records are append-only and cannot be modified or deleted."),

    // 404 Not Found (Also used for SEC-02 IDOR Protection)
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested account could not be found."),

    // 409 Conflict
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "An account with this email address is already registered."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "This Idempotency-Key has already been used with different transaction parameters."),
    INTEREST_ALREADY_POSTED(HttpStatus.CONFLICT, "Monthly savings interest has already been credited for the current calendar month."),
    FD_INTEREST_ALREADY_CREDITED(HttpStatus.CONFLICT, "Fixed Deposit maturity interest has already been credited."),

    // 422 Unprocessable Entity & 423 Locked
    DEPOSIT_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Deposit amount must be between 10.00 EGP and 100,000.00 EGP."),
    TXN_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Transaction amount exceeds the 70,000.00 EGP per-transaction limit."),
    DAILY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Transaction exceeds the 120,000.00 EGP daily outgoing limit."),
    MONTHLY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Transaction exceeds the 400,000.00 EGP monthly outgoing limit."),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient available funds to complete this transaction."),
    MIN_BALANCE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "Savings account balance cannot fall below the 1,000.00 EGP minimum."),
    MIN_OPENING_DEPOSIT_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "Fixed Deposit requires a minimum opening deposit of 5,000.00 EGP."),
    OVERDRAFT_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Checking account balance cannot fall below the -1,000.00 EGP overdraft limit."),
    FD_DEPOSIT_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "Additional deposits are not permitted on Fixed Deposit accounts."),
    FD_NOT_MATURE(HttpStatus.UNPROCESSABLE_ENTITY, "Fixed Deposit cannot be withdrawn before its maturity date."),
    FD_PARTIAL_WITHDRAWAL_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "Matured Fixed Deposit must be withdrawn in full."),
    MAX_ACCOUNTS_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "A customer cannot hold more than 5 non-closed accounts."),
    NON_ZERO_BALANCE_ON_CLOSE(HttpStatus.UNPROCESSABLE_ENTITY, "Account balance must be 0.00 EGP before closing."),
    ACCOUNT_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "Operation requires the account to be in ACTIVE status."),
    ACCOUNT_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY, "This account is permanently CLOSED and cannot accept operations."),
    ACCOUNT_FROZEN(HttpStatus.UNPROCESSABLE_ENTITY, "This account is currently FROZEN due to an administrative hold."),

    // 429 Too Many Requests & 500 Internal Error
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please wait before retrying."),
    TRANSFER_ATOMICITY_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR, "Transaction failed and was completely rolled back.");

    /**
     * Backwards-compatible alias for the historic catalogue name used in earlier
     * specifications. Both names resolve to the same {@code NON_ZERO_BALANCE_ON_CLOSE}
     * error code.
     */
    public static final ErrorCode NON_ZERO_BALANCE = NON_ZERO_BALANCE_ON_CLOSE;

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}