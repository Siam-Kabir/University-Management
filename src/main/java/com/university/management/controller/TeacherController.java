package com.university.management.controller;

import com.university.management.dto.*;
import com.university.management.model.Role;
import com.university.management.service.AccountService;
import com.university.management.security.AccountAccess;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/teachers")
public class TeacherController {
    private final AccountService service;

    public TeacherController(AccountService service) { this.service = service; }

    @GetMapping
    public List<AccountResponse> all(Authentication user) {
        return AccountAccess.visible(service.findAll(Role.TEACHER), user);
    }

    @GetMapping("/{id}")
    public AccountResponse one(@PathVariable Long id, Authentication user) {
        AccountResponse account = service.findById(id, Role.TEACHER);
        AccountAccess.requireOwner(account, user);
        return account;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse result = service.create(request, Role.TEACHER);
        return ResponseEntity.created(URI.create("/api/teachers/" + result.id())).body(result);
    }

    @PutMapping("/{id}")
    public AccountResponse update(@PathVariable Long id, @Valid @RequestBody UpdateAccountRequest request, Authentication user) {
        AccountAccess.requireOwner(service.findById(id, Role.TEACHER), user);
        return service.update(id, request, Role.TEACHER);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id, Role.TEACHER); }
}
