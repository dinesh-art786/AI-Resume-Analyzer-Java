
package resume_analyzer.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import resume_analyzer.repository.AnalysisHistoryRepository;
import resume_analyzer.service.JwtService;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ExportController {

    private final AnalysisHistoryRepository historyRepository;
    private final JwtService jwtService;

    public ExportController(
            AnalysisHistoryRepository historyRepository,
            JwtService jwtService
    ) {
        this.historyRepository = historyRepository;
        this.jwtService = jwtService;
    }

    @GetMapping("/export")
    public List<Map<String, Object>> exportAnalyses(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String authorization,
            @RequestParam(required = false) String start_date,
            @RequestParam(required = false) String end_date
    ) {

        // Authenticate user
        if (authorization == null ||
                !authorization.startsWith("Bearer ")) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required."
            );
        }

        String token = authorization.substring(7);

        if (!jwtService.isTokenValid(token)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid or expired token."
            );
        }

        Integer userId = jwtService.extractUserId(token);

        // Validate date filters
        validateDate(start_date, "start date");
        validateDate(end_date, "end date");

        if (start_date != null && end_date != null &&
                start_date.compareTo(end_date) > 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start date cannot be after end date."
            );
        }

        // Retrieve all matching records
        return historyRepository.getAllHistoryForExport(
                userId,
                start_date,
                end_date
        );
    }

    private void validateDate(String date, String fieldName) {

        if (date == null || date.isBlank()) {
            return;
        }

        try {
            LocalDate parsed = LocalDate.parse(date);

            if (!parsed.toString().equals(date)) {
                throw new DateTimeParseException(
                        "Invalid date",
                        date,
                        0
                );
            }

        } catch (DateTimeParseException exception) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid " + fieldName + ". Use YYYY-MM-DD."
            );
        }
    }
}
