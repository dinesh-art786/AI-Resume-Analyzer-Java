
package java.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import resume_analyzer.service.JwtService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class AnalyticsController {

    private final JdbcTemplate jdbcTemplate;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalyticsController(
            JdbcTemplate jdbcTemplate,
            JwtService jwtService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtService = jwtService;
    }

    @GetMapping("/analytics")
    public ResponseEntity<?> analytics(
            @RequestHeader(value = "Authorization", required = false)
            String authorization,

            @RequestParam(name = "start_date", required = false)
            String startDate,

            @RequestParam(name = "end_date", required = false)
            String endDate
    ) {

        // Authentication
        if (authorization == null ||
                !authorization.startsWith("Bearer ")) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("detail", "Not authenticated"));
        }

        String token = authorization.substring(7);

        if (!jwtService.isTokenValid(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("detail", "Invalid or expired token"));
        }

        Integer userId = jwtService.extractUserId(token);

        // Date validation
        LocalDate start = null;
        LocalDate end = null;

        try {
            if (startDate != null && !startDate.isBlank()) {
                start = LocalDate.parse(startDate);
            }

            if (endDate != null && !endDate.isBlank()) {
                end = LocalDate.parse(endDate);
            }

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "detail",
                            "Invalid date. Use YYYY-MM-DD."
                    ));
        }

        if (start != null && end != null && start.isAfter(end)) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "detail",
                            "Start date cannot be after end date."
                    ));
        }

        try {

            // Build SQL query
            StringBuilder sql = new StringBuilder("""
                    SELECT score, matched_skills,
                           missing_skills, created_at
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

            sql.append(" ORDER BY id ASC");

            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(
                            sql.toString(),
                            parameters.toArray()
                    );

            int total = rows.size();

            // Empty analytics response
            if (total == 0) {

                Map<String, Object> emptyResult = new LinkedHashMap<>();

                emptyResult.put("total_analyses", 0);
                emptyResult.put("average_score", 0);
                emptyResult.put("score_distribution", new ArrayList<>());

                emptyResult.put("skill_summary", Map.of(
                        "matched", 0,
                        "missing", 0
                ));

                emptyResult.put("history_trend", new ArrayList<>());
                emptyResult.put("top_missing_skills", new ArrayList<>());

                return ResponseEntity.ok(emptyResult);
            }

            // Calculate average score
            double totalScore = 0;

            Map<String, Integer> ranges = new LinkedHashMap<>();

            ranges.put("0-20", 0);
            ranges.put("21-40", 0);
            ranges.put("41-60", 0);
            ranges.put("61-80", 0);
            ranges.put("81-100", 0);

            int matchedCount = 0;
            int missingCount = 0;

            Map<String, Integer> missingFrequency =
                    new LinkedHashMap<>();

            List<Map<String, Object>> historyTrend =
                    new ArrayList<>();

            for (Map<String, Object> row : rows) {

                // Score
                double score = ((Number) row.get("score")).doubleValue();

                totalScore += score;

                if (score <= 20) {
                    ranges.put("0-20", ranges.get("0-20") + 1);
                } else if (score <= 40) {
                    ranges.put("21-40", ranges.get("21-40") + 1);
                } else if (score <= 60) {
                    ranges.put("41-60", ranges.get("41-60") + 1);
                } else if (score <= 80) {
                    ranges.put("61-80", ranges.get("61-80") + 1);
                } else {
                    ranges.put("81-100", ranges.get("81-100") + 1);
                }

                // Matched and missing skills
                List<String> matched =
                        parseJsonList(row.get("matched_skills"));

                List<String> missing =
                        parseJsonList(row.get("missing_skills"));

                matchedCount += matched.size();
                missingCount += missing.size();

                // Count missing skill frequency
                for (String skill : missing) {

                    if (skill == null || skill.isBlank()) {
                        continue;
                    }

                    String normalized = skill.trim().toLowerCase();

                    missingFrequency.put(
                            normalized,
                            missingFrequency.getOrDefault(normalized, 0) + 1
                    );
                }

                // History trend
                Map<String, Object> trendItem =
                        new LinkedHashMap<>();

                trendItem.put("date", row.get("created_at"));
                trendItem.put("score", Math.round(score * 10.0) / 10.0);

                historyTrend.add(trendItem);
            }

            // Score distribution
            List<Map<String, Object>> scoreDistribution =
                    new ArrayList<>();

            for (Map.Entry<String, Integer> entry : ranges.entrySet()) {

                Map<String, Object> item = new LinkedHashMap<>();

                item.put("range", entry.getKey());
                item.put("count", entry.getValue());

                scoreDistribution.add(item);
            }

            // Top 10 missing skills
            List<Map<String, Object>> topMissingSkills =
                    new ArrayList<>();

            missingFrequency.entrySet()
                    .stream()
                    .sorted(
                            Map.Entry.<String, Integer>comparingByValue()
                                    .reversed()
                    )
                    .limit(10)
                    .forEach(entry -> {

                        Map<String, Object> item =
                                new LinkedHashMap<>();

                        item.put("skill", entry.getKey());
                        item.put("count", entry.getValue());

                        topMissingSkills.add(item);
                    });

            // Final response
            Map<String, Object> result = new LinkedHashMap<>();

            result.put("total_analyses", total);

            result.put(
                    "average_score",
                    Math.round((totalScore / total) * 10.0) / 10.0
            );

            result.put("score_distribution", scoreDistribution);

            result.put("skill_summary", Map.of(
                    "matched", matchedCount,
                    "missing", missingCount
            ));

            result.put("history_trend", historyTrend);
            result.put("top_missing_skills", topMissingSkills);

            return ResponseEntity.ok(result);

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "detail",
                            "Could not retrieve analytics."
                    ));
        }
    }

    private List<String> parseJsonList(Object value) {

        if (value == null || value.toString().isBlank()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(
                    value.toString(),
                    new TypeReference<List<String>>() {}
            );

        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
