
package resume_analyzer.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class ProfilePictureService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private final JdbcTemplate jdbcTemplate;

    private final Path uploadDirectory = Paths.get(
            "../backend/uploads/profiles"
    ).toAbsolutePath().normalize();

    public ProfilePictureService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String uploadPicture(
            Integer userId,
            MultipartFile file
    ) {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please select an image."
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Image must not exceed 5 MB."
            );
        }

        String contentType = file.getContentType();

        try {
            byte[] bytes = file.getBytes();

            String extension;

            if ("image/jpeg".equalsIgnoreCase(contentType)
                    && isJpeg(bytes)) {
                extension = ".jpg";
            } else if ("image/png".equalsIgnoreCase(contentType)
                    && isPng(bytes)) {
                extension = ".png";
            } else {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Only valid JPG and PNG images are allowed."
                );
            }

            Files.createDirectories(uploadDirectory);

            String filename = "user_" + userId + "_"
                    + UUID.randomUUID().toString().replace("-", "")
                    + extension;

            Path target = uploadDirectory.resolve(filename);

            Files.copy(
                    file.getInputStream(),
                    target,
                    StandardCopyOption.REPLACE_EXISTING
            );

            String oldPicture = jdbcTemplate.query(
                    "SELECT profile_picture FROM users WHERE id = ?",
                    rs -> rs.next() ? rs.getString(1) : null,
                    userId
            );

            int updated = jdbcTemplate.update(
                    "UPDATE users SET profile_picture = ? WHERE id = ?",
                    "/uploads/profiles/" + filename,
                    userId
            );

            if (updated == 0) {
                Files.deleteIfExists(target);

                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found."
                );
            }

            deleteOldPicture(oldPicture, filename);

            return "/uploads/profiles/" + filename;

        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not save profile picture."
            );
        }
    }

    public void deletePicture(Integer userId) {

        String oldPicture = jdbcTemplate.query(
                "SELECT profile_picture FROM users WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                userId
        );

        if (oldPicture == null || oldPicture.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No profile picture found."
            );
        }

        int updated = jdbcTemplate.update(
                "UPDATE users SET profile_picture = NULL WHERE id = ?",
                userId
        );

        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User not found."
            );
        }

        deleteOldPicture(oldPicture, null);
    }

    private void deleteOldPicture(
            String oldPicture,
            String newFilename
    ) {

        if (oldPicture == null || oldPicture.isBlank()) {
            return;
        }

        String filename = Paths.get(oldPicture).getFileName().toString();

        if (newFilename != null && filename.equals(newFilename)) {
            return;
        }

        if (!filename.matches("user_\\d+_[a-fA-F0-9]+\\.(jpg|png)")) {
            return;
        }

        try {
            Files.deleteIfExists(uploadDirectory.resolve(filename));
        } catch (IOException ignored) {
            // Keep the database update even if old-file cleanup fails.
        }
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A;
    }
}
