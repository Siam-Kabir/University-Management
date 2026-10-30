package com.university.management.dto;

import com.university.management.model.Account;
import com.university.management.model.Role;

public record AccountResponse(Long id, String name, String email, Role role) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getName(), account.getEmail(), account.getRole());
    }
}
