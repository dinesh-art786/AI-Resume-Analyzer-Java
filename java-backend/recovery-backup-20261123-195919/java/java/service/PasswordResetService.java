
package resume_analyzer.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@Service
public class PasswordResetService {

    private final JdbcTemplate jdbcTemplate;
    private final EmailService emailService;
    private final PasswordService passwordService;

    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            JdbcTemplate jdbcTemplate,
            EmailService emailService,
            PasswordService passwordService) {

        this.jdbcTemplate = jdbcTemplate;
        this.emailService = emailService;
        this.passwordService = passwordService;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hashToken(String token) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(
                digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );
    }

    @Transactional
    public void requestPasswordReset(String email) {

        String normalizedEmail = email.trim().toLowerCase();

        Optional<Map<String, Object>> user =
                jdbcTemplate.query(
                        """
                        SELECT id, name, email
                        FROM users
                        WHERE email = ?
                        """,
                        rs -> rs.next()
                                ? Optional.of(Map.of(
                                        "id", rs.getInt("id"),
                                        "name", rs.getString("name"),
                                        "email", rs.getString("email")
                                ))
                                : Optional.empty(),
                        normalizedEmail
                );

        // Do not reveal whether an email address is registered.
        if (user.isEmpty()) {
            return;
        }

        Map<String, Object> account = user.get();

        int userId = ((Number) account.get("id")).intValue();

        String token = generateToken();

        try {
            String tokenHash = hashToken(token);

            Instant now = Instant.now();
            Instant expires = now.plus(15, ChronoUnit.MINUTES);

            jdbcTemplate.update(
                    """
                    UPDATE password_reset_tokens
                    SET used_at = ?
                    WHERE user_id = ? AND used_at IS NULL
                    """,
                    now.toString(),
                    userId
            );

            jdbcTemplate.update(
                    """
                    INSERT INTO password_reset_tokens
                    (user_id, token_hash, expires_at, used_at)
                    VALUES (?, ?, ?, NULL)
                    """,
                    userId,
                    tokenHash,
                    expires.toString()
            );

            emailService.sendPasswordResetEmail(
                    (String) account.get("email"),
                    (String) account.get("name"),
                    token
            );

        } catch (Exception exception) {
            throw new RuntimeException(
                    "Unable to process password reset request.",
                    exception
            );
        }
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Reset token is required.");
        }

        if (newPassword == null
                || newPassword.length() < 8
                || newPassword.length() > 128) {
            throw new IllegalArgumentException(
                    "Password must contain 8 to 128 characters."
            );
        }

        try {
            String tokenHash = hashToken(token);

            Map<String, Object> resetRecord =
                    jdbcTemplate.query(
                            """
                            SELECT id, user_id, expires_at
                            FROM password_reset_tokens
                            WHERE token_hash = ? AND used_at IS NULL
                            """,
                            rs -> rs.next()
                                    ? Map.of(
                                            "id", rs.getInt("id"),
                                            "user_id", rs.getInt("user_id"),
                                            "expires_at", rs.getString("expires_at")
                                    )
                                    : null,
                            tokenHash
                    );

            if (resetRecord == null) {
                throw new IllegalArgumentException(
                        "Invalid or already used reset link."
                );
            }

            Instant expiry = Instant.parse(
                    (String) resetRecord.get("expires_at")
            );

            if (!expiry.isAfter(Instant.now())) {
                throw new IllegalArgumentException(
                        "Reset link has expired. Request a new one."
                );
            }

            int userId = ((Number) resetRecord.get("user_id")).intValue();
            int tokenId = ((Number) resetRecord.get("id")).intValue();

            String newHash = passwordService.hashPassword(newPassword);

            int updated = jdbcTemplate.update(
                    "UPDATE users SET password_hash = ? WHERE id = ?",
                    newHash,
                    userId
            );

            if (updated != 1) {
                throw new IllegalArgumentException("User not found.");
            }

            jdbcTemplate.update(
                    """
                    UPDATE password_reset_tokens
                    SET used_at = ?
                    WHERE user_id = ? AND used_at IS NULL
                    """,
                    Instant.now().toString(),
                    userId
            );

        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException(
                    "Unable to reset password.",
                    exception
            );
        }
    }
}
