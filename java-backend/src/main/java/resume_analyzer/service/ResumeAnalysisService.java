
package resume_analyzer.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ResumeAnalysisService {

    private static final Map<String, List<String>> SKILL_ALIASES =
            new LinkedHashMap<>();

    private static final List<String> RESUME_SECTIONS = List.of(
            "education", "experience", "work experience",
            "skills", "technical skills", "projects",
            "certifications", "achievements", "summary", "objective"
    );

    private static final List<String> STRONG_ACTION_VERBS = List.of(
            "developed", "created", "designed", "implemented",
            "managed", "built", "optimized", "improved",
            "analyzed", "led", "delivered", "engineered",
            "automated", "configured", "maintained", "deployed",
            "tested", "coordinated", "achieved", "increased",
            "reduced", "streamlined", "integrated", "launched",
            "executed"
    );

    private static final Set<String> KEYWORD_STOPWORDS = Set.of(
            "the", "and", "for", "with", "you", "your", "our",
            "are", "is", "was", "were", "will", "can", "must",
            "should", "have", "has", "had", "this", "that",
            "from", "into", "about", "who", "what", "when",
            "where", "need", "needed", "looking", "seeking",
            "candidate", "ability", "work", "working", "good",
            "strong", "knowledge", "skills"
    );

    // Common English stopwords used when calculating text similarity.
    private static final Set<String> TFIDF_STOPWORDS = Set.of(
            "a", "about", "above", "after", "again", "against",
            "all", "am", "an", "and", "any", "are", "as", "at",
            "be", "because", "been", "before", "being", "below",
            "between", "both", "but", "by", "could", "did",
            "do", "does", "doing", "down", "during", "each",
            "few", "for", "from", "further", "had", "has",
            "have", "having", "he", "her", "here", "hers",
            "herself", "him", "himself", "his", "how", "i",
            "if", "in", "into", "is", "it", "its", "itself",
            "just", "me", "more", "most", "my", "myself",
            "no", "nor", "not", "now", "of", "off", "on",
            "once", "only", "or", "other", "our", "ours",
            "ourselves", "out", "over", "own", "same", "she",
            "should", "so", "some", "such", "than", "that",
            "the", "their", "theirs", "them", "themselves",
            "then", "there", "these", "they", "this", "those",
            "through", "to", "too", "under", "until", "up",
            "very", "was", "we", "were", "what", "when",
            "where", "which", "while", "who", "whom", "why",
            "will", "with", "would", "you", "your", "yours"
    );

    static {
        addSkill("python", "python", "py");
        addSkill("java", "java");
        addSkill("javascript", "javascript", "js");
        addSkill("typescript", "typescript", "ts");
        addSkill("c++", "c++", "cpp");
        addSkill("c#", "c#", "c sharp");
        addSkill("sql", "sql");
        addSkill("html", "html");
        addSkill("css", "css");
        addSkill("react", "react", "reactjs", "react.js");
        addSkill("angular", "angular");
        addSkill("vue", "vue", "vuejs", "vue.js");
        addSkill("node.js", "node.js", "nodejs", "node js");
        addSkill("fastapi", "fastapi");
        addSkill("django", "django");
        addSkill("flask", "flask");
        addSkill("machine learning", "machine learning", "ml");
        addSkill("deep learning", "deep learning");
        addSkill("artificial intelligence", "artificial intelligence", "ai");
        addSkill("data analysis", "data analysis");
        addSkill("data science", "data science");
        addSkill("pandas", "pandas");
        addSkill("numpy", "numpy");
        addSkill("scikit-learn", "scikit-learn", "sklearn");
        addSkill("tensorflow", "tensorflow");
        addSkill("pytorch", "pytorch");
        addSkill("nlp", "nlp", "natural language processing");
        addSkill("power bi", "power bi", "powerbi");
        addSkill("tableau", "tableau");
        addSkill("excel", "excel", "microsoft excel");
        addSkill("statistics", "statistics", "statistical analysis");
        addSkill("aws", "aws", "amazon web services");
        addSkill("azure", "azure", "microsoft azure");
        addSkill("docker", "docker");
        addSkill("kubernetes", "kubernetes", "k8s");
        addSkill("git", "git");
        addSkill("linux", "linux");
        addSkill("rest api", "rest api", "restful api", "rest apis");
        addSkill("mongodb", "mongodb", "mongo db");
        addSkill("postgresql", "postgresql", "postgres");
        addSkill("mysql", "mysql");
        addSkill("communication", "communication", "communication skills");
        addSkill("leadership", "leadership");
        addSkill("problem solving", "problem solving", "problem-solving");
        addSkill("project management", "project management");
        addSkill("agile", "agile");
        addSkill("testing", "testing", "software testing");
        addSkill("cybersecurity", "cybersecurity", "cyber security");
    }

    private static void addSkill(String skill, String... aliases) {
        SKILL_ALIASES.put(skill, Arrays.asList(aliases));
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }

        return text.toLowerCase(Locale.ROOT)
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean containsTerm(String text, String term) {
        if (text == null || term == null || term.isBlank()) {
            return false;
        }

        String regex = "(?<![a-z0-9])" +
                Pattern.quote(term.toLowerCase(Locale.ROOT)) +
                "(?![a-z0-9])";

        return Pattern.compile(regex).matcher(text).find();
    }

    private List<String> findSkills(String text) {
        String cleaned = cleanText(text);
        List<String> found = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry :
                SKILL_ALIASES.entrySet()) {

            for (String alias : entry.getValue()) {
                if (containsTerm(cleaned, alias)) {
                    found.add(entry.getKey());
                    break;
                }
            }
        }

        Collections.sort(found);
        return found;
    }

    private List<String> findSections(String text) {
        String cleaned = cleanText(text);
        List<String> found = new ArrayList<>();

        for (String section : RESUME_SECTIONS) {
            if (containsTerm(cleaned, section)) {
                found.add(section);
            }
        }

        return found;
    }

    private List<String> findMissingSections(List<String> sections) {
        List<String> required = List.of(
                "education", "experience", "skills", "projects"
        );

        List<String> missing = new ArrayList<>();

        for (String section : required) {
            boolean found = sections.contains(section);

            if (section.equals("experience")
                    && sections.contains("work experience")) {
                found = true;
            }

            if (section.equals("skills")
                    && sections.contains("technical skills")) {
                found = true;
            }

            if (!found) {
                missing.add(section);
            }
        }

        return missing;
    }

    private List<String> findActionVerbs(String text) {
        String cleaned = cleanText(text);
        List<String> found = new ArrayList<>();

        for (String verb : STRONG_ACTION_VERBS) {
            if (containsTerm(cleaned, verb)) {
                found.add(verb);
            }
        }

        return found;
    }

    private boolean checkAchievements(String text) {
        boolean hasNumber = Pattern.compile(
                "\\b\\d+(\\.\\d+)?%?\\b"
        ).matcher(text).find();

        List<String> achievementWords = List.of(
                "increased", "decreased", "reduced", "improved",
                "saved", "generated", "achieved", "growth",
                "performance", "efficiency"
        );

        boolean hasAchievementWord = achievementWords.stream()
                .anyMatch(word -> containsTerm(text, word));

        return hasNumber && hasAchievementWord;
    }

    private boolean hasEmail(String text) {
        return Pattern.compile(
                "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
        ).matcher(text).find();
    }

    private boolean hasPhone(String text) {
        return Pattern.compile(
                "\\+?\\d[\\d\\s().-]{7,}\\d"
        ).matcher(text).find();
    }

    private Map<String, Object> analyzeResumeQuality(
            String text,
            List<String> sections,
            List<String> actionVerbs,
            boolean achievements,
            boolean email,
            boolean phone
    ) {
        List<String> missingSections = findMissingSections(sections);

        double qualityScore = 0;

        qualityScore += Math.min(sections.size() * 10, 40);
        qualityScore += Math.min(actionVerbs.size() * 5, 25);

        if (achievements) {
            qualityScore += 20;
        }

        if (email) {
            qualityScore += 7;
        }

        if (phone) {
            qualityScore += 8;
        }

        Map<String, Object> quality = new LinkedHashMap<>();

        quality.put("quality_score", qualityScore);
        quality.put("sections", sections);
        quality.put("missing_sections", missingSections);
        quality.put("action_verbs", actionVerbs);
        quality.put("has_measurable_achievements", achievements);
        quality.put("has_email", email);
        quality.put("has_phone", phone);

        return quality;
    }

    // Creates unigram and bigram terms for TF-IDF.
    private List<String> tokenizeForSimilarity(String text) {
        List<String> words = new ArrayList<>();

        Matcher matcher = Pattern.compile(
                "[a-zA-Z0-9_]{2,}"
        ).matcher(cleanText(text));

        while (matcher.find()) {
            String word = matcher.group();

            if (!TFIDF_STOPWORDS.contains(word)) {
                words.add(word);
            }
        }

        List<String> tokens = new ArrayList<>(words);

        for (int i = 0; i < words.size() - 1; i++) {
            tokens.add(words.get(i) + " " + words.get(i + 1));
        }

        return tokens;
    }

    private double calculateSimilarity(
            String resumeText,
            String jobDescription
    ) {
        List<String> documents = List.of(
                resumeText, jobDescription
        );

        List<List<String>> tokenized = new ArrayList<>();
        Map<String, Integer> totalFrequency = new HashMap<>();
        Map<String, Integer> documentFrequency = new HashMap<>();

        for (String document : documents) {
            List<String> tokens = tokenizeForSimilarity(document);
            tokenized.add(tokens);

            Set<String> unique = new HashSet<>(tokens);

            for (String token : tokens) {
                totalFrequency.merge(token, 1, Integer::sum);
            }

            for (String token : unique) {
                documentFrequency.merge(token, 1, Integer::sum);
            }
        }

        List<String> vocabulary = new ArrayList<>(
                totalFrequency.keySet()
        );

        vocabulary.sort(
                Comparator.<String>comparingInt(
                        token -> totalFrequency.get(token)
                ).reversed().thenComparing(token -> token)
        );

        if (vocabulary.size() > 5000) {
            vocabulary = new ArrayList<>(
                    vocabulary.subList(0, 5000)
            );
        }

        Set<String> allowedTerms = new HashSet<>(vocabulary);

        List<Map<String, Double>> vectors = new ArrayList<>();

        for (List<String> tokens : tokenized) {
            Map<String, Integer> termCounts = new HashMap<>();

            for (String token : tokens) {
                if (allowedTerms.contains(token)) {
                    termCounts.merge(token, 1, Integer::sum);
                }
            }

            Map<String, Double> vector = new HashMap<>();
            double normSquared = 0;

            for (Map.Entry<String, Integer> entry :
                    termCounts.entrySet()) {

                String term = entry.getKey();
                double tf = entry.getValue();

                double idf = Math.log(
                        3.0 / (1.0 + documentFrequency.get(term))
                ) + 1.0;

                double weight = tf * idf;

                vector.put(term, weight);
                normSquared += weight * weight;
            }

            double norm = Math.sqrt(normSquared);

            if (norm > 0) {
                for (String term : vector.keySet()) {
                    vector.put(term, vector.get(term) / norm);
                }
            }

            vectors.add(vector);
        }

        Map<String, Double> first = vectors.get(0);
        Map<String, Double> second = vectors.get(1);

        double similarity = 0;

        for (String term : first.keySet()) {
            similarity += first.get(term) *
                    second.getOrDefault(term, 0.0);
        }

        return Math.round(similarity * 1000.0) / 10.0;
    }

    private Map<String, Object> calculateKeywordMatch(
            String resumeText,
            String jobDescription
    ) {
        Set<String> keywords = new TreeSet<>();

        Matcher matcher = Pattern.compile(
                "\\b[a-zA-Z][a-zA-Z0-9+#.]*\\b"
        ).matcher(cleanText(jobDescription));

        while (matcher.find()) {
            String word = matcher.group().toLowerCase(Locale.ROOT);

            if (word.length() > 2 &&
                    !KEYWORD_STOPWORDS.contains(word)) {
                keywords.add(word);
            }
        }

        // Include recognized multi-word skills from the job description.
        for (String skill : SKILL_ALIASES.keySet()) {
            if (containsTerm(cleanText(jobDescription), skill)) {
                keywords.add(skill);
            }
        }

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String keyword : keywords) {
            if (containsTerm(cleanText(resumeText), keyword)) {
                matched.add(keyword);
            } else {
                missing.add(keyword);
            }
        }

        double score = keywords.isEmpty()
                ? 0
                : (double) matched.size() / keywords.size() * 100;

        score = Math.round(score * 10.0) / 10.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("keyword_match", score);
        result.put("matched_keywords", matched);
        result.put("missing_keywords", missing);

        return result;
    }

    private String getJobReadiness(double score) {
        if (score >= 85) {
            return "Excellent";
        } else if (score >= 70) {
            return "Good";
        } else if (score >= 50) {
            return "Average";
        }

        return "Needs Improvement";
    }

    private Map<String, Object> analyzeStrengthsWeaknesses(
            List<String> matchedSkills,
            List<String> missingSkills,
            List<String> sections,
            List<String> actionVerbs,
            boolean achievements,
            boolean email,
            boolean phone
    ) {
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        List<Map<String, String>> improvements = new ArrayList<>();

        if (!matchedSkills.isEmpty()) {
            strengths.add(
                    "Your resume matches " + matchedSkills.size()
                            + " job-related skills."
            );
        }

        if (!sections.isEmpty()) {
            strengths.add(
                    "Your resume contains recognizable sections."
            );
        }

        if (!actionVerbs.isEmpty()) {
            strengths.add(
                    "Your resume uses " + actionVerbs.size()
                            + " recognized action verbs."
            );
        }

        if (achievements) {
            strengths.add(
                    "Your resume contains measurable achievement evidence."
            );
        }

        if (email && phone) {
            strengths.add(
                    "Your resume contains email and phone contact information."
            );
        }

        if (!missingSkills.isEmpty()) {
            weaknesses.add(
                    "Your resume is missing some skills required by the job description."
            );
        }

        if (!sections.contains("skills")
                && !sections.contains("technical skills")) {
            weaknesses.add(
                    "Your resume does not clearly include a Skills section."
            );
        }

        if (!achievements) {
            weaknesses.add(
                    "Measurable achievements were not clearly detected."
            );
        }

        if (!email || !phone) {
            weaknesses.add(
                    "Your resume may be missing contact information."
            );
        }

        if (actionVerbs.isEmpty()) {
            weaknesses.add(
                    "Your resume could use stronger action verbs."
            );
        }

        if (!missingSkills.isEmpty()) {
            addImprovement(
                    improvements,
                    "High",
                    "Missing job-related skills",
                    "Add relevant missing skills only if you genuinely possess them."
            );
        }

        if (!sections.contains("skills")
                && !sections.contains("technical skills")) {
            addImprovement(
                    improvements,
                    "High",
                    "Missing Skills section",
                    "Add a clearly labelled Skills section where relevant to your background."
            );
        }

        if (!achievements) {
            addImprovement(
                    improvements,
                    "Medium",
                    "Missing measurable achievements",
                    "Include genuine results using numbers, percentages, or measurable outcomes."
            );
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("strengths", strengths);
        result.put("weaknesses", weaknesses);
        result.put("priority_improvements", improvements);

        return result;
    }

    private void addImprovement(
            List<Map<String, String>> improvements,
            String priority,
            String title,
            String description
    ) {
        Map<String, String> item = new LinkedHashMap<>();

        item.put("priority", priority);
        item.put("title", title);
        item.put("description", description);

        improvements.add(item);
    }

    public Map<String, Object> analyzeResume(
            String resumeText,
            String jobDescription
    ) {
        if (resumeText == null || resumeText.isBlank()) {
            throw new IllegalArgumentException(
                    "Resume text cannot be empty."
            );
        }

        if (jobDescription == null || jobDescription.isBlank()) {
            throw new IllegalArgumentException(
                    "Job description cannot be empty."
            );
        }

        String resume = cleanText(resumeText);
        String job = cleanText(jobDescription);

        List<String> resumeSkills = findSkills(resume);
        List<String> jobSkills = findSkills(job);

        List<String> matchedSkills = new ArrayList<>();
        List<String> missingSkills = new ArrayList<>();

        for (String skill : jobSkills) {
            if (resumeSkills.contains(skill)) {
                matchedSkills.add(skill);
            } else {
                missingSkills.add(skill);
            }
        }

        double skillCoverage = jobSkills.isEmpty()
                ? 0
                : (double) matchedSkills.size() /
                        jobSkills.size() * 100;

        double textSimilarity = calculateSimilarity(resume, job);

        Map<String, Object> keywordResult =
                calculateKeywordMatch(resume, job);

        double keywordMatch =
                (double) keywordResult.get("keyword_match");

        @SuppressWarnings("unchecked")
        List<String> matchedKeywords =
                (List<String>) keywordResult.get("matched_keywords");

        @SuppressWarnings("unchecked")
        List<String> missingKeywords =
                (List<String>) keywordResult.get("missing_keywords");

        List<String> sections = findSections(resume);
        List<String> actionVerbs = findActionVerbs(resume);

        boolean achievements = checkAchievements(resume);
        boolean email = hasEmail(resume);
        boolean phone = hasPhone(resume);

        double experienceEvidence = 0;

        if (sections.contains("experience")
                || sections.contains("work experience")
                || sections.contains("projects")) {
            experienceEvidence += 50;
        }

        if (!actionVerbs.isEmpty()) {
            experienceEvidence += 25;
        }

        if (achievements) {
            experienceEvidence += 25;
        }

        double resumeStructure = Math.min(
                (double) sections.size() / 4 * 100,
                100
        );

        double matchScore;

        if (!jobSkills.isEmpty()) {
            matchScore =
                    skillCoverage * 0.40
                    + textSimilarity * 0.25
                    + keywordMatch * 0.20
                    + experienceEvidence * 0.10
                    + resumeStructure * 0.05;
        } else {
            matchScore =
                    textSimilarity * 0.60
                    + keywordMatch * 0.30
                    + resumeStructure * 0.10;
        }

        matchScore = Math.max(
                0,
                Math.min(100, Math.round(matchScore * 10.0) / 10.0)
        );

        Map<String, Object> quality = analyzeResumeQuality(
                resume,
                sections,
                actionVerbs,
                achievements,
                email,
                phone
        );

        Map<String, Object> strengthsWeaknesses =
                analyzeStrengthsWeaknesses(
                        matchedSkills,
                        missingSkills,
                        sections,
                        actionVerbs,
                        achievements,
                        email,
                        phone
                );

        List<String> suggestions = new ArrayList<>();

        if (!missingSkills.isEmpty()) {
            suggestions.add(
                    "Consider adding relevant missing skills if you have those skills."
            );
        }

        if (!achievements) {
            suggestions.add(
                    "Add measurable achievements to demonstrate your impact."
            );
        }

        if (!sections.contains("skills")
                && !sections.contains("technical skills")) {
            suggestions.add(
                    "Add a clearly labelled Skills section."
            );
        }

        if (actionVerbs.isEmpty()) {
            suggestions.add(
                    "Use strong action verbs to describe your work and projects."
            );
        }

        if (!email || !phone) {
            suggestions.add(
                    "Make sure your contact information is clearly visible."
            );
        }

        if (suggestions.isEmpty()) {
            suggestions.add(
                    "Continue tailoring your resume to the specific job description."
            );
        }

        Map<String, Object> scoreBreakdown = new LinkedHashMap<>();
        scoreBreakdown.put("skill_match", Math.round(skillCoverage * 10.0) / 10.0);
        scoreBreakdown.put("text_similarity", textSimilarity);
        scoreBreakdown.put("keyword_match", keywordMatch);
        scoreBreakdown.put("experience_evidence", experienceEvidence);
        scoreBreakdown.put("resume_structure", resumeStructure);

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("match_score", matchScore);
        result.put("text_similarity", textSimilarity);
        result.put("skill_coverage", Math.round(skillCoverage * 10.0) / 10.0);
        result.put("resume_skills", resumeSkills);
        result.put("job_skills", jobSkills);
        result.put("matched_skills", matchedSkills);
        result.put("missing_skills", missingSkills);
        result.put("suggestions", suggestions);
        result.put("job_readiness", getJobReadiness(matchScore));
        result.put("resume_sections", sections);
        result.put("action_verbs", actionVerbs);
        result.put("has_measurable_achievements", achievements);
        result.put("resume_quality", quality);
        result.put("strengths_weaknesses", strengthsWeaknesses);
        result.put("keyword_match", keywordMatch);
        result.put("matched_keywords", matchedKeywords);
        result.put("missing_keywords", missingKeywords);
        result.put("score_breakdown", scoreBreakdown);

        return result;
    }
}
