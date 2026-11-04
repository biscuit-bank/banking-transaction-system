package com.depi.bank.config;

import com.depi.bank.domain.Role;
import com.depi.bank.domain.User;
import com.depi.bank.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SeedDataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final Clock clock;
    private final String envAdminEmail;
    private final String envAdminPassword;

    public SeedDataLoader(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          Environment environment,
                          Clock clock,
                          @Value("${bank.admin.email:admin@biscuitbank.eg}") String envAdminEmail,
                          @Value("${bank.admin.password:}") String envAdminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.clock = clock;
        this.envAdminEmail = envAdminEmail;
        this.envAdminPassword = envAdminPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Set<String> activeProfiles = Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        boolean isDevOrTest = activeProfiles.contains("dev") || activeProfiles.contains("test");

        if (isDevOrTest) {
            seedUserIfAbsent("Sherif Admin", "admin@biscuitbank.eg", "+201000000001", "Admin123Pass", Role.ADMIN);
            seedUserIfAbsent("Tarek Mansour", "customer.a@example.com", "+201001234567", "Customer1Pass", Role.CUSTOMER);
            seedUserIfAbsent("Omar Khaled Hassan", "customer.b@example.com", "+201002345678", "Customer2Pass", Role.CUSTOMER);
            seedUserIfAbsent("Nadia El-Sayed", "customer.c@example.com", "+201003456789", "Customer3Pass", Role.CUSTOMER);
            seedUserIfAbsent("Karim Zaki", "customer.e@example.com", "+201005678901", "Customer5Pass", Role.CUSTOMER);
        } else {
            // Production / non-dev profile: provision admin strictly from environment variables (AUTH-08, SEC-08)
            if (envAdminEmail != null && !envAdminEmail.isBlank() && envAdminPassword != null && !envAdminPassword.isBlank()) {
                seedUserIfAbsent("System Administrator", envAdminEmail, null, envAdminPassword, Role.ADMIN);
            }
        }
    }

    private void seedUserIfAbsent(String fullName, String email, String phone, String rawPassword, Role role) {
        if (!userRepository.existsByEmailIgnoreCase(email)) {
            User user = new User(
                    fullName,
                    email,
                    phone,
                    passwordEncoder.encode(rawPassword),
                    role,
                    Instant.now(clock)
            );
            userRepository.save(user);
        }
    }
}