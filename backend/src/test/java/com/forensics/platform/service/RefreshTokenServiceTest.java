package com.forensics.platform.service;

import com.forensics.platform.entity.RefreshToken;
import com.forensics.platform.exception.RefreshTokenException;
import com.forensics.platform.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {
    @Test
    void validateAndRevokeRejectsRevokedRefreshToken() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        RefreshTokenService service = new RefreshTokenService(repository, 604800000);
        RefreshToken token = new RefreshToken();
        token.setRevokedAt(Instant.now());
        token.setExpiresAt(Instant.now().plusSeconds(60));
        when(repository.findByTokenHash(service.hash("raw"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.validateAndRevoke("raw")).isInstanceOf(RefreshTokenException.class);
    }

    @Test
    void validateAndRevokeMarksValidTokenRevoked() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        RefreshTokenService service = new RefreshTokenService(repository, 604800000);
        RefreshToken token = new RefreshToken();
        token.setExpiresAt(Instant.now().plusSeconds(60));
        when(repository.findByTokenHash(service.hash("raw"))).thenReturn(Optional.of(token));

        RefreshToken result = service.validateAndRevoke("raw");

        assertThat(result.getRevokedAt()).isNotNull();
    }
}
