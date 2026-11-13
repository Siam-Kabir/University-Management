package com.university.management.security;

import com.university.management.dto.AccountResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import java.util.List;

public final class AccountAccess {
    private AccountAccess() { }

    public static List<AccountResponse> visible(List<AccountResponse> accounts, Authentication user) {
        if (isAdmin(user)) return accounts;
        return accounts.stream().filter(account -> account.email().equalsIgnoreCase(user.getName())).toList();
    }

    public static void requireOwner(AccountResponse account, Authentication user) {
        if (!isAdmin(user) && !account.email().equalsIgnoreCase(user.getName())) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private static boolean isAdmin(Authentication user) {
        return user.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
