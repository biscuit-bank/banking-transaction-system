package com.depi.bank.web;

import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.Instant;

@ControllerAdvice(basePackages = "com.depi.bank.web")
public class WebExceptionHandler {

    private final Clock clock;

    public WebExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public String handleNotFound(HttpServletRequest req, HttpServletResponse res, Model model) {
        res.setStatus(HttpStatus.NOT_FOUND.value());
        populateErrorModel(model, HttpStatus.NOT_FOUND.value(), ErrorCode.ACCOUNT_NOT_FOUND.name(),
                "The requested banking resource is unavailable.", req.getRequestURI());
        return "error/404";
    }

    @ExceptionHandler(BankException.class)
    public String handleBankException(BankException ex, HttpServletRequest req, HttpServletResponse res, Model model) {
        ErrorCode code = ex.getErrorCode();
        res.setStatus(code.getHttpStatus().value());
        if (code == ErrorCode.ACCOUNT_NOT_FOUND) {
            populateErrorModel(model, 404, code.name(),
                    "The requested banking resource is unavailable.", req.getRequestURI());
            return "error/404";
        }
        populateErrorModel(model, code.getHttpStatus().value(), code.name(), ex.getMessage(), req.getRequestURI());
        return "error/general";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(HttpServletRequest req, HttpServletResponse res, Model model) {
        res.setStatus(HttpStatus.FORBIDDEN.value());
        populateErrorModel(model, 403, ErrorCode.ACCESS_DENIED.name(),
                ErrorCode.ACCESS_DENIED.getDefaultMessage(), req.getRequestURI());
        return "error/403";
    }

    @ExceptionHandler(Exception.class)
    public String handleUnexpected(HttpServletRequest req, HttpServletResponse res, Model model) {
        res.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        populateErrorModel(model, 500, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Uncommitted changes were safely rolled back.", req.getRequestURI());
        return "error/general";
    }

    private void populateErrorModel(Model model, int status, String errorCode, String message, String path) {
        model.addAttribute("timestamp", Instant.now(clock));
        model.addAttribute("status", status);
        model.addAttribute("error", errorCode);
        model.addAttribute("message", message);
        model.addAttribute("path", path);
    }
}