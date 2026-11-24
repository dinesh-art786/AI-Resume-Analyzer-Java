
package resume_analyzer.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import resume_analyzer.repository.UserRepository;
import resume_analyzer.service.JwtService;
import resume_analyzer.service.PasswordService;
import resume_analyzer.service.ProfilePictureService;
import resume_analyzer.service.PasswordResetService;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;
    private final ProfilePictureService profilePictureService;
    private final PasswordResetService passwordResetService;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public AuthController(
            UserRepository userRepository,
            PasswordService passwordService,
            JwtService jwtService,
            JdbcTemplate jdbcTemplate,
            ProfilePictureService profilePictureService,
            PasswordResetService passwordResetService) {

        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
        this.profilePictureService = profilePictureService;
        this.passwordResetService = passwordResetService;
    }

    // LOGIN

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {

        if (request == null || request.email() == null
                || request.password() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email and password are required."
            );
        }

        String email = request.email().trim().toLowerCase();

        Optional<Map<String, Object>> optionalUser =
                userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password."
            );
        }

        Map<String, Object> user = optionalUser.get();

        boolean valid = passwordService.verifyPassword(
                request.password(),
                (String) user.get("password_hash")
        );

        if (!valid) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password."
            );
        }

        Integer userId = ((Number) user.get("id")).intValue();

        String token = jwtService.generateAccessToken(userId);

        Map<String, Object> safeUser = new HashMap<>();
        safeUser.put("id", userId);
        safeUser.put("name", user.get("name"));
        safeUser.put("email", user.get("email"));
        safeUser.put("profile_picture", user.get("profile_picture"));

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Login successful!");
        response.put("access_token", token);
        response.put("token_type", "bearer");
        response.put("user", safeUser);

        return response;
    }

    // REGISTER

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterRequest request) {

        if (request == null || request.name() == null
                || request.email() == null
                || request.password() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "All fields are required."
            );
        }

        String name = request.name().trim();
        String email = request.email().trim().toLowerCase();

        if (name.length() < 2 || name.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please enter a valid name."
            );
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid email address."
            );
        }

        if (request.password().length() < 8
                || request.password().length() > 128) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must contain 8 to 128 characters."
            );
        }

        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already registered."
            );
        }

        String hashedPassword =
                passwordService.hashPassword(request.password());

        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO users (name, email, password_hash)
                    VALUES (?, ?, ?)
                    """,
                    name,
                    email,
                    hashedPassword
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Registration failed. Email may already be registered."
            );
        }

        Map<String, Object> createdUser =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.INTERNAL_SERVER_ERROR,
                                        "Could not retrieve registered user."
                                ));

        Integer userId =
                ((Number) createdUser.get("id")).intValue();

        String token = jwtService.generateAccessToken(userId);

        Map<String, Object> safeUser = new HashMap<>();
        safeUser.put("id", userId);
        safeUser.put("name", name);
        safeUser.put("email", email);
        safeUser.put("profile_picture", null);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Registration successful!");
        response.put("access_token", token);
        response.put("token_type", "bearer");
        response.put("user", safeUser);

        return response;
    }

    // GET CURRENT USER

    @GetMapping("/me")
    public Map<String, Object> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false)
            String authorization) {

        Integer userId = authenticate(authorization);

        Map<String, Object> user =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED,
                                        "User not found."
                                ));

        Map<String, Object> response = new HashMap<>();
        response.put("user", user);

        return response;
    }

    // UPDATE PROFILE

    @PutMapping("/profile")
    public Map<String, Object> updateProfile(
            @RequestHeader(value = "Authorization", required = false)
            String authorization,
            @RequestBody UpdateProfileRequest request) {

        Integer userId = authenticate(authorization);

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Profile details are required."
            );
        }

        String name = request.name() == null
                ? "" : request.name().trim();

        String email = request.email() == null
                ? "" : request.email().trim().toLowerCase();

        if (name.length() < 2 || name.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please enter a valid name."
            );
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid email address."
            );
        }

        Integer existingCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM users
                WHERE email = ? AND id != ?
                """,
                Integer.class,
                email,
                userId
        );

        if (existingCount != null && existingCount > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This email address is already registered."
            );
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE users SET name = ?, email = ?
                WHERE id = ?
                """,
                name,
                email,
                userId
        );

        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User not found."
            );
        }

        Map<String, Object> updatedUser =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "User not found."
                                ));

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Profile updated successfully.");
        response.put("user", updatedUser);

        return response;
    }

    // UPLOAD PROFILE PICTURE

    @PostMapping(
            value = "/profile-picture",
            consumes = "multipart/form-data"
    )
    public Map<String, Object> uploadProfilePicture(
            @RequestHeader(value = "Authorization", required = false)
            String authorization,
            @RequestParam("file") MultipartFile file) {

        Integer userId = authenticate(authorization);

        String picturePath =
                profilePictureService.uploadPicture(userId, file);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Profile picture uploaded successfully.");
        response.put("profile_picture", picturePath);

        return response;
    }

    // DELETE PROFILE PICTURE

    @DeleteMapping("/profile-picture")
    public Map<String, Object> deleteProfilePicture(
            @RequestHeader(value = "Authorization", required = false)
            String authorization) {

        Integer userId = authenticate(authorization);

        profilePictureService.deletePicture(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Profile picture deleted successfully.");
        response.put("profile_picture", null);

        return response;
    }

    // CHANGE PASSWORD

    @PostMapping("/change-password")
    @Transactional
    public Map<String, Object> changePassword(
            @RequestHeader(value = "Authorization", required = false)
            String authorization,
            @RequestBody ChangePasswordRequest request) {

        Integer userId = authenticate(authorization);

        if (request == null
                || request.current_password() == null
                || request.new_password() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Both passwords are required."
            );
        }

        String currentPassword = request.current_password();
        String newPassword = request.new_password();

        if (newPassword.length() < 8 || newPassword.length() > 128) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "New password must contain 8 to 128 characters."
            );
        }

        if (currentPassword.equals(newPassword)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "New password must be different from current password."
            );
        }

        String storedHash = jdbcTemplate.query(
                "SELECT password_hash FROM users WHERE id = ?",
                rs -> rs.next() ? rs.getString("password_hash") : null,
                userId
        );

        if (storedHash == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "User not found."
            );
        }

        boolean valid = passwordService.verifyPassword(
                currentPassword,
                storedHash
        );

        if (!valid) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Current password is incorrect."
            );
        }

        String newHash = passwordService.hashPassword(newPassword);

        jdbcTemplate.update(
                "UPDATE users SET password_hash = ? WHERE id = ?",
                newHash,
                userId
        );

        jdbcTemplate.update(
                """
                UPDATE password_reset_tokens
                SET used_at = ?
                WHERE user_id = ? AND used_at IS NULL
                """,
                Instant.now().toString(),
                userId
        );

        Map<String, Object> response = new HashMap<>();
        response.put(
                "message",
                "Password changed successfully. Please log in again."
        );

        return response;
    }

    // FORGOT PASSWORD

    @PostMapping("/forgot-password")
    public Map<String, Object> forgotPassword(
            @RequestBody ForgotPasswordRequest request) {

        if (request == null || request.email() == null
                || !EMAIL_PATTERN.matcher(
                        request.email().trim()).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please enter a valid email address."
            );
        }

        try {
            passwordResetService.requestPasswordReset(
                    request.email()
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to process your request. Please try again later."
            );
        }

        Map<String, Object> response = new HashMap<>();
        response.put(
                "message",
                "If an account exists for this email, a password reset link will be sent."
        );

        return response;
    }

    // RESET PASSWORD

    @PostMapping("/reset-password")
    public Map<String, Object> resetPassword(
            @RequestBody ResetPasswordRequest request) {

        if (request == null || request.token() == null
                || request.new_password() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reset token and new password are required."
            );
        }

        try {
            passwordResetService.resetPassword(
                    request.token(),
                    request.new_password()
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage()
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to reset password. Please try again."
            );
        }

        Map<String, Object> response = new HashMap<>();
        response.put(
                "message",
                "Password reset successfully. Please log in again."
        );

        return response;
    }

    // AUTHENTICATION HELPER

    private Integer authenticate(String authorization) {

        if (authorization == null
                || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required."
            );
        }

        try {
            return jwtService.extractUserId(
                    authorization.substring(7)
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid or expired token."
            );
        }
    }

    // REQUEST RECORDS

    public record LoginRequest(String email, String password) {}

    public record RegisterRequest(
            String name,
            String email,
            String password
    ) {}

    public record UpdateProfileRequest(String name, String email) {}

    public record ChangePasswordRequest(
            String current_password,
            String new_password
    ) {}

    public record ForgotPasswordRequest(String email) {}

    public record ResetPasswordRequest(
            String token,
            String new_password
    ) {}
}
