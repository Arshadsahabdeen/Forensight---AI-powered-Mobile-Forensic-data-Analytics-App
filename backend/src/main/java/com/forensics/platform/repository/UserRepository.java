package com.forensics.platform.repository;

import com.forensics.platform.entity.User;
import com.forensics.platform.entity.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsernameOrEmail(String username, String email);
    long countByEnabledTrue();
    long countByRoles_Name(RoleName roleName);
}
