package com.depi.bank.api;

import com.depi.bank.domain.User;
import com.depi.bank.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 50) String fullName,
            @NotBlank @Email @Size(max = 100) String email,
            String phone,
            @NotBlank @Size(min = 8, max = 64) String password
    ) {}

    public record LoginRequest(
            @NotBlank String email,
            @NotBlank String password
    ) {}

    public record AuthResponse(
            Long id,
            String fullName,
            String email,
            String role,
            boolean sessionIdRotated
    ) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest httpReq) {
        User created = authService.registerCustomer(
                req.fullName(),
                req.email(),
                req.phone(),
                req.password(),
                httpReq.getRemoteAddr()
        );
        return new AuthResponse(
                created.getId(),
                created.getFullName(),
                created.getEmail(),
                created.getRole().name(),
                false
        );
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest httpReq) {
        User user = authService.authenticate(req.email(), req.password(), httpReq.getRemoteAddr());
        HttpSession existing = httpReq.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        HttpSession newSession = httpReq.getSession(true);
        return new AuthResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                newSession.getId() != null
        );
    }
}