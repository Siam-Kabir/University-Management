package com.university.management.security;

import com.university.management.model.Account;
import com.university.management.model.Role;
import com.university.management.repository.AccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "demo.users.enabled", havingValue = "true")
public class DemoDataInitializer implements CommandLineRunner {
    private final AccountRepository repository;
    private final PasswordEncoder encoder;
    private final Environment environment;

    public DemoDataInitializer(AccountRepository repository, PasswordEncoder encoder, Environment environment) {
        this.repository = repository;
        this.encoder = encoder;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        create("Demo Admin", "admin@university.test", "demo.admin.password", Role.ADMIN);
        create("Demo Teacher", "teacher@university.test", "demo.teacher.password", Role.TEACHER);
        create("Demo Student", "student@university.test", "demo.student.password", Role.STUDENT);
    }

    private void create(String name, String email, String passwordProperty, Role role) {
        String password = environment.getRequiredProperty(passwordProperty);
        if (password.length() < 8) throw new IllegalStateException("Demo passwords must have at least 8 characters");
        if (!repository.existsByEmailIgnoreCase(email)) {
            repository.save(new Account(name, email, encoder.encode(password), role));
        }
    }
}
