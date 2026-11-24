
package resume_analyzer.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import resume_analyzer.service.ResumeAnalysisService;
import resume_analyzer.service.ResumeParserService;
import resume_analyzer.service.JwtService;
import resume_analyzer.repository.AnalysisHistoryRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ResumeController {

    private final ResumeParserService resumeParserService;
    private final ResumeAnalysisService resumeAnalysisService;
    private final JwtService jwtService;
    private final AnalysisHistoryRepository historyRepository;

    public ResumeController(
            ResumeParserService resumeParserService,
            ResumeAnalysisService resumeAnalysisService,
            JwtService jwtService,
            AnalysisHistoryRepository historyRepository
    ) {
        this.resumeParserService = resumeParserService;
        this.resumeAnalysisService = resumeAnalysisService;
        this.jwtService = jwtService;
        this.historyRepository = historyRepository;
    }

    @PostMapping(
            value = "/analyze",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Map<String, Object>> analyzeResume(

            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String authorization,

            @RequestParam("file") MultipartFile file,

            @RequestParam("job_description") String jobDescription
    ) {

        try {

            // 1. Verify authentication
            if (authorization == null ||
                    !authorization.startsWith("Bearer ")) {

                return ResponseEntity.status(401).body(
                        error("Authentication required. Please log in.")
                );
            }

            String token = authorization.substring(7);

            if (!jwtService.isTokenValid(token)) {
                return ResponseEntity.status(401).body(
                        error("Invalid or expired token. Please log in again.")
                );
            }

            Integer userId = jwtService.extractUserId(token);

            // 2. Validate uploaded file
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        error("Please upload a resume file.")
                );
            }

            if (jobDescription == null ||
                    jobDescription.isBlank()) {

                return ResponseEntity.badRequest().body(
                        error("Job description cannot be empty.")
                );
            }

            String filename = file.getOriginalFilename();

            if (filename == null || filename.isBlank()) {
                return ResponseEntity.badRequest().body(
                        error("Invalid filename.")
                );
            }

            String extension = filename.substring(
                    filename.lastIndexOf('.') + 1
            ).toLowerCase();

            if (!extension.equals("pdf") &&
                    !extension.equals("docx")) {

                return ResponseEntity.badRequest().body(
                        error("Only PDF and DOCX files are supported.")
                );
            }

            if (file.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(
                        error("File size must not exceed 5 MB.")
                );
            }

            // 3. Extract resume text
            String resumeText =
                    resumeParserService.extractResumeText(
                            file.getBytes(),
                            extension
                    );

            if (resumeText == null || resumeText.isBlank()) {
                return ResponseEntity.badRequest().body(
                        error("Could not extract text from the uploaded resume.")
                );
            }

            // 4. Analyze resume
            Map<String, Object> analysis =
                    resumeAnalysisService.analyzeResume(
                            resumeText,
                            jobDescription
                    );

            // 5. Extract analysis fields
            double score = 0.0;

            Object scoreValue = analysis.get("match_score");

            if (scoreValue instanceof Number) {
                score = ((Number) scoreValue).doubleValue();
            }

            List<String> matchedSkills =
                    getStringList(analysis.get("matched_skills"));

            List<String> missingSkills =
                    getStringList(analysis.get("missing_skills"));

            List<String> suggestions =
                    getStringList(analysis.get("suggestions"));

            // 6. Save analysis to SQLite
            historyRepository.saveAnalysis(
                    filename,
                    score,
                    matchedSkills,
                    missingSkills,
                    suggestions,
                    userId
            );

            // 7. Return response
            Map<String, Object> response = new LinkedHashMap<>();

            response.put("success", true);
            response.put("filename", filename);
            response.putAll(analysis);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(
                    error(e.getMessage())
            );

        } catch (Exception e) {

            return ResponseEntity.internalServerError().body(
                    error("Resume analysis failed: " + e.getMessage())
            );
        }
    }

    private List<String> getStringList(Object value) {

        List<String> result = new ArrayList<>();

        if (value instanceof List<?>) {
            for (Object item : (List<?>) value) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
        }

        return result;
    }

    private Map<String, Object> error(String message) {

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("success", false);
        response.put("detail", message);

        return response;
    }
}
