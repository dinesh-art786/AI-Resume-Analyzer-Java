
package resume_analyzer.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Map<String, Object>> findByEmail(String email) {

        String sql = """
                SELECT id, name, email, password_hash, profile_picture
                FROM users
                WHERE email = ?
                """;

        return jdbcTemplate.query(
                sql,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    Map<String, Object> user = new HashMap<>();

                    user.put("id", rs.getInt("id"));
                    user.put("name", rs.getString("name"));
                    user.put("email", rs.getString("email"));
                    user.put("password_hash",
                            rs.getString("password_hash"));
                    user.put("profile_picture",
                            rs.getString("profile_picture"));

                    return Optional.of(user);
                },
                email
        );
    }

    public Optional<Map<String, Object>> findById(Integer userId) {

        String sql = """
                SELECT id, name, email, profile_picture
                FROM users
                WHERE id = ?
                """;

        return jdbcTemplate.query(
                sql,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    Map<String, Object> user = new HashMap<>();

                    user.put("id", rs.getInt("id"));
                    user.put("name", rs.getString("name"));
                    user.put("email", rs.getString("email"));
                    user.put("profile_picture",
                            rs.getString("profile_picture"));

                    return Optional.of(user);
                },
                userId
        );
    }
}
