package com.forensics.platform.repository;

import com.forensics.platform.entity.RefreshToken;
import com.forensics.platform.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void deleteByUser(User user);
    List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);
}
