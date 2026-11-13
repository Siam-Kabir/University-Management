package com.university.management.controller;

import com.university.management.dto.*;
import com.university.management.model.Role;
import com.university.management.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/admins")
public class AdminController {
    private final AccountService service;

    public AdminController(AccountService service) { this.service = service; }

    @GetMapping
    public List<AccountResponse> all() { return service.findAll(Role.ADMIN); }

    @GetMapping("/{id}")
    public AccountResponse one(@PathVariable Long id) { return service.findById(id, Role.ADMIN); }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse result = service.create(request, Role.ADMIN);
        return ResponseEntity.created(URI.create("/api/admins/" + result.id())).body(result);
    }

    @PutMapping("/{id}")
    public AccountResponse update(@PathVariable Long id, @Valid @RequestBody UpdateAccountRequest request) {
        return service.update(id, request, Role.ADMIN);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication user) {
        AccountResponse account = service.findById(id, Role.ADMIN);
        if (account.email().equalsIgnoreCase(user.getName())) {
            throw new AccessDeniedException("You cannot delete your own account");
        }
        service.delete(id, Role.ADMIN);
    }
}
