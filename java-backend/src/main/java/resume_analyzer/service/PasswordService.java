
package resume_analyzer.service;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    private final Argon2PasswordEncoder passwordEncoder;

    public PasswordService() {
        this.passwordEncoder = new Argon2PasswordEncoder(
                16,
                32,
                4,
                65536,
                3
        );
    }

    public boolean verifyPassword(
            String rawPassword,
            String storedHash
    ) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }

        try {
            return passwordEncoder.matches(rawPassword, storedHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException(
                    "Password cannot be empty."
            );
        }

        return passwordEncoder.encode(rawPassword);
    }
}
