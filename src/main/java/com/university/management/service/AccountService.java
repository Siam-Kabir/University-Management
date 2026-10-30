package com.university.management.service;

import com.university.management.dto.*;
import com.university.management.exception.DuplicateEmailException;
import com.university.management.exception.ResourceNotFoundException;
import com.university.management.model.Account;
import com.university.management.model.Role;
import com.university.management.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;

@Service
public class AccountService {
    private final AccountRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<AccountResponse> findAll(Role role) {
        return repository.findAllByRoleOrderById(role).stream().map(AccountResponse::from).toList();
    }

    public AccountResponse findById(Long id, Role role) {
        return AccountResponse.from(findAccount(id, role));
    }

    public AccountResponse create(CreateAccountRequest request, Role role) {
        String email = normalizeEmail(request.email());
        if (repository.existsByEmailIgnoreCase(email)) throw new DuplicateEmailException();
        Account account = new Account(request.name().trim(), email,
                passwordEncoder.encode(request.password()), role);
        return AccountResponse.from(repository.save(account));
    }

    @Transactional
    public AccountResponse update(Long id, UpdateAccountRequest request, Role role) {
        Account account = findAccount(id, role);
        String email = normalizeEmail(request.email());
        if (!account.getEmail().equals(email) && repository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException();
        }
        account.setName(request.name().trim());
        account.setEmail(email);
        if (request.password() != null && !request.password().isBlank()) {
            // Store only the BCrypt hash, never the submitted password.
            account.setPassword(passwordEncoder.encode(request.password()));
        }
        return AccountResponse.from(account);
    }

    public void delete(Long id, Role role) {
        repository.delete(findAccount(id, role));
    }

    private Account findAccount(Long id, Role role) {
        return repository.findByIdAndRole(id, role).orElseThrow(ResourceNotFoundException::new);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
