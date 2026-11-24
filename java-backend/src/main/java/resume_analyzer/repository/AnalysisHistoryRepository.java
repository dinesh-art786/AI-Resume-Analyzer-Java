
package resume_analyzer.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AnalysisHistoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalysisHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Save a new resume analysis
    public void saveAnalysis(
            String filename,
            double score,
            List<String> matchedSkills,
            List<String> missingSkills,
            List<String> suggestions,
            Integer userId
    ) throws Exception {

        String sql = """
                INSERT INTO analyses
                (filename, score, matched_skills, missing_skills,
                 suggestions, user_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                filename,
                score,
                objectMapper.writeValueAsString(matchedSkills),
                objectMapper.writeValueAsString(missingSkills),
                objectMapper.writeValueAsString(suggestions),
                userId
        );
    }

    // Get latest history with maximum 20 records
    public List<Map<String, Object>> getHistory(
            Integer userId,
            String startDate,
            String endDate
    ) {
        return fetchHistory(userId, startDate, endDate, true);
    }

    // Get all matching records for export
    public List<Map<String, Object>> getAllHistoryForExport(
            Integer userId,
            String startDate,
            String endDate
    ) {
        return fetchHistory(userId, startDate, endDate, false);
    }

    // Shared database retrieval method
    private List<Map<String, Object>> fetchHistory(
            Integer userId,
            String startDate,
            String endDate,
            boolean limitResults
    ) {

        StringBuilder sql = new StringBuilder("""
                SELECT id, filename, score, matched_skills,
                       missing_skills, suggestions, created_at
                FROM analyses
                WHERE user_id = ?
                """);

        List<Object> parameters = new ArrayList<>();
        parameters.add(userId);

        if (startDate != null && !startDate.isBlank()) {
            sql.append(" AND date(created_at) >= date(?)");
            parameters.add(startDate);
        }

        if (endDate != null && !endDate.isBlank()) {
            sql.append(" AND date(created_at) <= date(?)");
            parameters.add(endDate);
        }

        sql.append(" ORDER BY id DESC");

        if (limitResults) {
            sql.append(" LIMIT 20");
        }

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> {

                    Map<String, Object> item = new HashMap<>();

                    item.put("id", rs.getInt("id"));
                    item.put("filename", rs.getString("filename"));
                    item.put("match_score", rs.getDouble("score"));

                    item.put(
                            "matched_skills",
                            parseJsonList(rs.getString("matched_skills"))
                    );

                    item.put(
                            "missing_skills",
                            parseJsonList(rs.getString("missing_skills"))
                    );

                    item.put(
                            "suggestions",
                            parseJsonList(rs.getString("suggestions"))
                    );

                    item.put("created_at", rs.getString("created_at"));

                    return item;
                },
                parameters.toArray()
        );
    }

    // Get one analysis by ID for the logged-in user
    public Map<String, Object> getAnalysisById(
            Integer analysisId,
            Integer userId
    ) {

        List<Map<String, Object>> results = jdbcTemplate.query(
                """
                SELECT id, filename, score, matched_skills,
                       missing_skills, suggestions, created_at
                FROM analyses
                WHERE id = ? AND user_id = ?
                """,
                (rs, rowNum) -> {

                    Map<String, Object> item = new HashMap<>();

                    item.put("id", rs.getInt("id"));
                    item.put("filename", rs.getString("filename"));
                    item.put("match_score", rs.getDouble("score"));

                    item.put(
                            "matched_skills",
                            parseJsonList(rs.getString("matched_skills"))
                    );

                    item.put(
                            "missing_skills",
                            parseJsonList(rs.getString("missing_skills"))
                    );

                    item.put(
                            "suggestions",
                            parseJsonList(rs.getString("suggestions"))
                    );

                    item.put("created_at", rs.getString("created_at"));

                    return item;
                },
                analysisId,
                userId
        );

        return results.isEmpty() ? null : results.get(0);
    }

    // Delete one analysis belonging to the logged-in user
    public int deleteAnalysis(
            Integer analysisId,
            Integer userId
    ) {

        return jdbcTemplate.update(
                "DELETE FROM analyses WHERE id = ? AND user_id = ?",
                analysisId,
                userId
        );
    }

    // Delete ALL analysis records belonging to one user
    public int deleteAllHistory(Integer userId) {

        return jdbcTemplate.update(
                "DELETE FROM analyses WHERE user_id = ?",
                userId
        );
    }

    // Convert JSON text into a Java list
    private List<String> parseJsonList(String json) {

        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<List<String>>() {}
            );
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
