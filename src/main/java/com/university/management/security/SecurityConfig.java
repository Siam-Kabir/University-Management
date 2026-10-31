package com.university.management.security;

import com.university.management.repository.AccountRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import java.io.IOException;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository) throws Exception {
        http
            .securityContext(context -> context.securityContextRepository(contextRepository))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/csrf", "/api/auth/login").permitAll()
                .requestMatchers("/api/auth/logout").authenticated()
                .requestMatchers("/api/admins/**", "/api/admins").hasRole("ADMIN")
                .requestMatchers("/api/teachers/**", "/api/teachers").hasAnyRole("ADMIN", "TEACHER")
                .requestMatchers("/api/students/**", "/api/students").hasAnyRole("ADMIN", "STUDENT")
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, ex) -> writeError(response, 401, "Authentication required"))
                .accessDeniedHandler((request, response, ex) -> {
                    boolean anonymousWrite = SecurityContextHolder.getContext().getAuthentication() == null
                            && !request.getRequestURI().startsWith("/api/auth/");
                    writeError(response, anonymousWrite ? 401 : 403,
                            anonymousWrite ? "Authentication required" : "Access denied");
                }))
            .logout(logout -> logout.logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        return http.build();
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(AccountRepository repository) {
        return email -> repository.findByEmailIgnoreCase(email)
                .map(account -> User.withUsername(account.getEmail())
                        .password(account.getPassword())
                        .roles(account.getRole().name()).build())
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
