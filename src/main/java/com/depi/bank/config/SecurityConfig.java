package com.depi.bank.config;

import com.depi.bank.domain.User;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.time.Clock;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final Clock clock;
    private final RateLimitingFilter rateLimitingFilter;

    public SecurityConfig(Clock clock, RateLimitingFilter rateLimitingFilter) {
        this.clock = clock;
        this.rateLimitingFilter = rateLimitingFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10); // Cost factor >= 10 (SEC-03)
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return email -> {
            User user = userRepository.findByEmailIgnoreCase(email.trim())
                    .orElseThrow(() -> new UsernameNotFoundException(ErrorCode.INVALID_CREDENTIALS.getDefaultMessage()));
            if (user.isAccountLocked(clock)) {
                throw new LockedException(ErrorCode.ACCOUNT_LOCKED.getDefaultMessage());
            }
            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getEmail())
                    .password(user.getPasswordHash())
                    .roles(user.getRole().name())
                    .build();
        };
    }

    /**
     * Stateless REST API Security Filter Chain (/api/**) - Exempt from CSRF, uses HTTP Basic (AUTH-02, SEC-05).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // Strict separation of duties: ADMIN is blocked with 403 on all customer money/account endpoints (US-25, ADM-07)
                        .requestMatchers("/api/v1/accounts/**", "/api/v1/transactions/**",
                                "/api/v1/transfers/**", "/api/v1/profile/**", "/api/v1/dashboard/**").hasRole("CUSTOMER")
                        .anyRequest().denyAll() // Deny-by-default (SEC-01)
                )
                .httpBasic(Customizer.withDefaults())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpStatus.UNAUTHORIZED.value());
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.getWriter().write("{\"status\":401,\"error\":\"INVALID_CREDENTIALS\",\"message\":\"Invalid email or password.\"}");
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(HttpStatus.FORBIDDEN.value());
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.getWriter().write("{\"status\":403,\"error\":\"ACCESS_DENIED\",\"message\":\"Access denied for your role.\"}");
                        })
                );
        applySecurityHeaders(http);
        return http.build();
    }

    /**
     * Stateful Web UI Security Filter Chain - Enforces CSRF, Session Fixation Migration & 15m Idle Timeout.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/login", "/register").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/dashboard/**", "/accounts/**", "/transactions/**",
                                "/transfers/**", "/statements/**", "/profile/**").hasRole("CUSTOMER")
                        .anyRequest().denyAll() // Deny-by-default (SEC-01)
                )
                .sessionManagement(sm -> sm
                        .sessionFixation(sf -> sf.migrateSession()) // Rotates Session ID on login (SEC-03)
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("BTS_SESSION")
                        .logoutSuccessUrl("/login?logout=true")
                );
        applySecurityHeaders(http);
        return http.build();
    }

    private void applySecurityHeaders(HttpSecurity http) throws Exception {
        http.headers(headers -> headers
                .contentTypeOptions(Customizer.withDefaults()) // X-Content-Type-Options: nosniff
                .frameOptions(frame -> frame.deny())           // X-Frame-Options: DENY
                .cacheControl(Customizer.withDefaults())       // Cache-Control: no-store
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; style-src 'self'; img-src 'self' data:; script-src 'self'; frame-ancestors 'none'; form-action 'self'"
                ))
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
        );
    }
}