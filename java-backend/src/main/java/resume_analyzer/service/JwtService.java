
package resume_analyzer.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;

    private static final long TOKEN_EXPIRY =
            60L * 60L * 1000L;

    public JwtService() {

        // Read secret from Render environment variables
        String secret = System.getenv("SECRET_KEY");

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "SECRET_KEY environment variable is missing."
            );
        }

        byte[] secretBytes =
                secret.getBytes(StandardCharsets.UTF_8);

        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "SECRET_KEY must contain at least 32 bytes for HS256."
            );
        }

        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateAccessToken(Integer userId) {

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Invalid user ID.");
        }

        Date now = new Date();

        Date expiry = new Date(
                now.getTime() + TOKEN_EXPIRY
        );

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims extractClaims(String token) {

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("JWT token is required.");
        }

        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String subject = claims.getSubject();

        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Invalid token subject.");
        }

        try {
            int userId = Integer.parseInt(subject);

            if (userId <= 0) {
                throw new IllegalArgumentException("Invalid user ID.");
            }

        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid user ID.");
        }

        Date issuedAt = claims.getIssuedAt();

        if (issuedAt == null || issuedAt.after(new Date())) {
            throw new IllegalArgumentException("Invalid token issue time.");
        }

        Date expiration = claims.getExpiration();

        if (expiration == null || !expiration.after(new Date())) {
            throw new IllegalArgumentException("Token has expired.");
        }

        return claims;
    }

    public Integer extractUserId(String token) {

        Claims claims = extractClaims(token);

        return Integer.parseInt(claims.getSubject());
    }

    public boolean isTokenValid(String token) {

        try {
            extractClaims(token);
            return true;

        } catch (Exception exception) {
            return false;
        }
    }
}
