package com.university.management.service;

import com.university.management.dto.CreateAccountRequest;
import com.university.management.dto.UpdateAccountRequest;
import com.university.management.exception.DuplicateEmailException;
import com.university.management.exception.ResourceNotFoundException;
import com.university.management.model.Account;
import com.university.management.model.Role;
import com.university.management.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountServiceTests {
    private AccountRepository repository;
    private PasswordEncoder encoder;
    private AccountService service;

    @BeforeEach
    void setUp() {
        repository = mock(AccountRepository.class);
        encoder = mock(PasswordEncoder.class);
        service = new AccountService(repository, encoder);
    }

    @Test
    void createNormalizesEmailAndHashesPassword() {
        when(encoder.encode("Secret123!")).thenReturn("hashed");
        when(repository.save(any(Account.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.create(new CreateAccountRequest("  Alice  ", "  ALICE@Example.COM  ", "Secret123!"), Role.STUDENT);

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(repository).save(saved.capture());
        verify(repository).existsByEmailIgnoreCase("alice@example.com");
        assertEquals("Alice", saved.getValue().getName());
        assertEquals("alice@example.com", response.email());
        assertEquals("hashed", saved.getValue().getPassword());
        assertEquals(Role.STUDENT, response.role());
    }

    @Test
    void createRejectsDuplicateEmailBeforeEncoding() {
        when(repository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> service.create(
                new CreateAccountRequest("Alice", "ALICE@example.com", "Secret123!"), Role.TEACHER));

        verifyNoInteractions(encoder);
        verify(repository, never()).save(any());
    }

    @Test
    void updateWithBlankPasswordKeepsExistingHash() {
        Account account = new Account("Old", "old@example.com", "old-hash", Role.TEACHER);
        when(repository.findByIdAndRole(7L, Role.TEACHER)).thenReturn(Optional.of(account));

        var response = service.update(7L,
                new UpdateAccountRequest("  New  ", "  NEW@Example.COM  ", "  "), Role.TEACHER);

        assertEquals("New", response.name());
        assertEquals("new@example.com", response.email());
        assertEquals("old-hash", account.getPassword());
        verifyNoInteractions(encoder);
    }

    @Test
    void updateWithPasswordStoresNewHash() {
        Account account = new Account("Alice", "alice@example.com", "old-hash", Role.STUDENT);
        when(repository.findByIdAndRole(8L, Role.STUDENT)).thenReturn(Optional.of(account));
        when(encoder.encode("NewSecret123!")).thenReturn("new-hash");

        service.update(8L, new UpdateAccountRequest("Alice", "alice@example.com", "NewSecret123!"), Role.STUDENT);

        assertEquals("new-hash", account.getPassword());
        verify(encoder).encode("NewSecret123!");
    }

    @Test
    void updateRejectsAnotherAccountsEmailWithoutMutation() {
        Account account = new Account("Alice", "alice@example.com", "old-hash", Role.STUDENT);
        when(repository.findByIdAndRole(9L, Role.STUDENT)).thenReturn(Optional.of(account));
        when(repository.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> service.update(9L,
                new UpdateAccountRequest("Changed", "TAKEN@example.com", null), Role.STUDENT));

        assertEquals("Alice", account.getName());
        assertEquals("alice@example.com", account.getEmail());
        verifyNoInteractions(encoder);
    }

    @Test
    void findByIdRequiresTheRequestedRole() {
        when(repository.findByIdAndRole(10L, Role.TEACHER)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findById(10L, Role.TEACHER));

        verify(repository).findByIdAndRole(10L, Role.TEACHER);
    }
}
