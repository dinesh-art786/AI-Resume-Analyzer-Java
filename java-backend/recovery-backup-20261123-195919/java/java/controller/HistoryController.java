
package resume_analyzer.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import resume_analyzer.repository.AnalysisHistoryRepository;
import resume_analyzer.service.JwtService;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final AnalysisHistoryRepository historyRepository;
    private final JwtService jwtService;

    public HistoryController(
            AnalysisHistoryRepository historyRepository,
            JwtService jwtService
    ) {
        this.historyRepository = historyRepository;
        this.jwtService = jwtService;
    }

    private Integer authenticate(String authorization) {

        if (authorization == null ||
                !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException(
                    "Authentication required."
            );
        }

        String token = authorization.substring(7);

        if (!jwtService.isTokenValid(token)) {
            throw new IllegalArgumentException(
                    "Invalid or expired token."
            );
        }

        return jwtService.extractUserId(token);
    }

    // Get latest history with optional date filters
    @GetMapping
    public ResponseEntity<?> getHistory(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String authorization,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate start_date,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate end_date
    ) {

        try {
            Integer userId = authenticate(authorization);

            if (start_date != null && end_date != null &&
                    start_date.isAfter(end_date)) {
                return ResponseEntity.badRequest().body(
                        error("Start date cannot be after end date.")
                );
            }

            String start = start_date == null
                    ? null : start_date.toString();

            String end = end_date == null
                    ? null : end_date.toString();

            List<Map<String, Object>> history =
                    historyRepository.getHistory(
                            userId,
                            start,
                            end
                    );

            return ResponseEntity.ok(history);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(
                    error(e.getMessage())
            );
        }
    }

    // Get one analysis
    @GetMapping("/{id}")
    public ResponseEntity<?> getAnalysis(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String authorization,

            @PathVariable Integer id
    ) {

        try {
            Integer userId = authenticate(authorization);

            Map<String, Object> analysis =
                    historyRepository.getAnalysisById(
                            id,
                            userId
                    );

            if (analysis == null) {
                return ResponseEntity.status(404).body(
                        error("Analysis not found.")
                );
            }

            return ResponseEntity.ok(analysis);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(
                    error(e.getMessage())
            );
        }
    }

    // Delete one analysis
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAnalysis(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String authorization,

            @PathVariable Integer id
    ) {

        try {
            Integer userId = authenticate(authorization);

            int deleted = historyRepository.deleteAnalysis(
                    id,
                    userId
            );

            if (deleted == 0) {
                return ResponseEntity.status(404).body(
                        error("Analysis not found.")
                );
            }

            Map<String, Object> response =
                    new LinkedHashMap<>();

            response.put("success", true);
            response.put("message", "Analysis deleted successfully.");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(
                    error(e.getMessage())
            );
        }
    }

    // Delete all history belonging to the logged-in user
    @DeleteMapping
    public ResponseEntity<?> deleteAllHistory(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String authorization
    ) {

        try {
            Integer userId = authenticate(authorization);

            int deleted = historyRepository.deleteAllHistory(userId);

            Map<String, Object> response =
                    new LinkedHashMap<>();

            response.put("success", true);
            response.put("deleted_count", deleted);
            response.put(
                    "message",
                    deleted > 0
                            ? "All analysis history deleted successfully."
                            : "No analysis history found."
            );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(
                    error(e.getMessage())
            );
        }
    }

    private Map<String, Object> error(String message) {

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("success", false);
        response.put("detail", message);

        return response;
    }
}
