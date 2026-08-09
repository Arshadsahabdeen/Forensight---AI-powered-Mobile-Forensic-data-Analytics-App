package com.forensics.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final long accessExpirationMs;

    public JwtService(@Value("${app.security.jwt-secret}") String secret, @Value("${app.security.jwt-access-expiration-ms}") long accessExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
    }

    public String generateToken(UserDetails userDetails) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(getAccessExpiration(now)))
                .signWith(key)
                .compact();
    }

    public Instant getAccessExpiration(Instant now) {
        return now.plusMillis(accessExpirationMs);
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationMs / 1000;
    }

    public String extractUsername(String token) {
        return claims(token).getSubject();
    }

    public boolean isValid(String token, UserDetails userDetails) {
        Claims claims = claims(token);
        return userDetails.getUsername().equals(claims.getSubject())
                && claims.getExpiration().after(new Date())
                && "access".equals(claims.get("typ", String.class));
    }

    public boolean isAccessToken(String token) {
        try {
            return "access".equals(claims(token).get("typ", String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
