package com.university.management.repository;

import com.university.management.model.Account;
import com.university.management.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByEmailIgnoreCase(String email);
    Optional<Account> findByIdAndRole(Long id, Role role);
    List<Account> findAllByRoleOrderById(Role role);
    boolean existsByEmailIgnoreCase(String email);
}
