package com.shllabs.recommender.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shllabs.recommender.model.Assessment;
import com.shllabs.recommender.model.RawAssessment;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads the scraped SHL Individual Test Solutions catalog from classpath JSON and normalizes
 * each entry into an {@link Assessment}. This is the single source of truth the rest of the
 * app is grounded against — no assessment name or URL ever reaches the API response unless
 * it originates here.
 */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private static final Map<String, String> CATEGORY_TO_CODE = Map.ofEntries(
            Map.entry("Ability & Aptitude", "A"),
            Map.entry("Biodata & Situational Judgment", "B"),
            Map.entry("Competencies", "C"),
            Map.entry("Development & 360", "D"),
            Map.entry("Assessment Exercises", "E"),
            Map.entry("Knowledge & Skills", "K"),
            Map.entry("Personality & Behavior", "P"),
            Map.entry("Simulations", "S")
    );

    private static final Pattern DURATION_NUMBER = Pattern.compile("(\\d+)");

    @Value("${catalog.resource-path}")
    private String catalogResourcePath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private List<Assessment> assessments = List.of();

    @PostConstruct
    public void load() {
        try (InputStream in = new ClassPathResource(catalogResourcePath).getInputStream()) {
            List<RawAssessment> raw = objectMapper.readValue(in, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, RawAssessment.class));
            List<Assessment> normalized = new ArrayList<>(raw.size());
            for (RawAssessment r : raw) {
                Assessment a = normalize(r);
                if (a != null) {
                    normalized.add(a);
                }
            }
            this.assessments = Collections.unmodifiableList(normalized);
            log.info("Loaded {} assessments from catalog '{}'", assessments.size(), catalogResourcePath);
        } catch (IOException e) {
            log.error("Failed to load catalog from '{}': {}", catalogResourcePath, e.getMessage(), e);
            this.assessments = List.of();
        }
    }

    private Assessment normalize(RawAssessment r) {
        if (r.getName() == null || r.getLink() == null) {
            return null;
        }
        List<String> testTypeCodes = new ArrayList<>();
        if (r.getKeys() != null) {
            for (String k : r.getKeys()) {
                String code = CATEGORY_TO_CODE.get(k);
                if (code != null && !testTypeCodes.contains(code)) {
                    testTypeCodes.add(code);
                }
            }
        }
        Integer durationMinutes = parseDurationMinutes(r.getDurationRaw() != null ? r.getDurationRaw() : r.getDuration());
        boolean remote = r.getRemote() == null || "yes".equalsIgnoreCase(r.getRemote().trim());
        boolean adaptive = r.getAdaptive() != null && "yes".equalsIgnoreCase(r.getAdaptive().trim());
        List<String> jobLevels = r.getJobLevels() == null ? List.of() : r.getJobLevels();
        List<String> languages = r.getLanguages() == null ? List.of() : r.getLanguages();
        String description = r.getDescription() == null ? "" : r.getDescription();

        StringBuilder searchText = new StringBuilder();
        searchText.append(r.getName()).append(' ').append(description).append(' ');
        if (r.getKeys() != null) {
            searchText.append(String.join(" ", r.getKeys())).append(' ');
        }
        searchText.append(String.join(" ", jobLevels));

        return new Assessment(
                r.getEntityId(),
                r.getName().trim(),
                r.getLink().trim(),
                jobLevels,
                languages,
                durationMinutes,
                remote,
                adaptive,
                description,
                testTypeCodes,
                searchText.toString().toLowerCase(Locale.ROOT)
        );
    }

    private Integer parseDurationMinutes(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("variable") || lower.contains("untimed") || lower.contains("tbc") || lower.equals("-")) {
            return null;
        }
        Matcher m = DURATION_NUMBER.matcher(raw);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public List<Assessment> all() {
        return assessments;
    }

    public int size() {
        return assessments.size();
    }

    /** Fuzzy, case-insensitive lookup by (partial) name — used for the "compare" flow. */
    public Optional<Assessment> findByName(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String q = query.toLowerCase(Locale.ROOT).trim();
        // exact match first
        for (Assessment a : assessments) {
            if (a.getName().toLowerCase(Locale.ROOT).equals(q)) {
                return Optional.of(a);
            }
        }
        // then contains-match, preferring the shortest name (most specific)
        return assessments.stream()
                .filter(a -> a.getName().toLowerCase(Locale.ROOT).contains(q) || q.contains(a.getName().toLowerCase(Locale.ROOT)))
                .min(Comparator.comparingInt(a -> a.getName().length()));
    }
}
