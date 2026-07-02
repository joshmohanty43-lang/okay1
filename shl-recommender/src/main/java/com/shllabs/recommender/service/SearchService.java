package com.shllabs.recommender.service;

import com.shllabs.recommender.model.Assessment;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Lightweight in-memory retrieval: TF-IDF ranking over a manually tokenized index, plus optional
 * hard filters (test type, max duration, remote, job level). No external vector store/dependency
 * needed — the catalog is small enough that a manual inverted index is fast and dependency-free.
 */
@Service
public class SearchService {

    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "and", "or", "of", "to", "in", "for", "with", "on", "is", "are",
            "this", "that", "test", "assessment", "measures", "measure", "candidate", "candidates"
    );

    private final CatalogService catalogService;

    private Map<String, Integer> documentFrequency = new HashMap<>();
    private int documentCount = 0;

    public SearchService(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostConstruct
    public void buildIndex() {
        List<Assessment> corpus = catalogService.all();
        documentCount = corpus.size();
        Map<String, Integer> df = new HashMap<>();
        for (Assessment a : corpus) {
            for (String term : new HashSet<>(tokenize(a.getSearchText()))) {
                df.merge(term, 1, Integer::sum);
            }
        }
        this.documentFrequency = df;
    }

    private List<String> tokenize(String text) {
        if (text == null) return List.of();
        List<String> tokens = new ArrayList<>();
        var m = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String t = m.group();
            if (t.length() > 1 && !STOPWORDS.contains(t)) {
                tokens.add(t);
            }
        }
        return tokens;
    }

    private double idf(String term) {
        int df = documentFrequency.getOrDefault(term, 0);
        return Math.log((documentCount + 1.0) / (df + 1.0)) + 1.0;
    }

    public static final class Filters {
        public Set<String> testTypes;      // null/empty = no filter
        public Integer maxDurationMinutes; // null = no filter
        public Boolean remoteOnly;         // null/false = no filter
        public String jobLevel;            // null = no filter
    }

    /**
     * Ranks the catalog against {@code query}, applying hard filters when present. If the hard
     * filters eliminate every candidate, falls back to the unfiltered ranking rather than
     * returning nothing — a filter mismatch shouldn't make the agent go silent.
     */
    public List<Assessment> search(String query, Filters filters, int topK) {
        List<String> queryTerms = tokenize(query == null ? "" : query);
        List<Assessment> corpus = catalogService.all();

        List<Assessment> filtered = applyFilters(corpus, filters);
        List<Assessment> pool = filtered.isEmpty() ? corpus : filtered;

        if (queryTerms.isEmpty()) {
            return pool.stream().limit(Math.max(1, topK)).toList();
        }

        record Scored(Assessment a, double score) { }
        List<Scored> scored = new ArrayList<>();
        for (Assessment a : pool) {
            List<String> docTerms = tokenize(a.getSearchText());
            Map<String, Long> tf = new HashMap<>();
            for (String t : docTerms) tf.merge(t, 1L, Long::sum);
            double score = 0.0;
            for (String qt : queryTerms) {
                Long f = tf.get(qt);
                if (f != null) {
                    score += f * idf(qt);
                }
            }
            // small boost for query terms appearing in the name itself
            String lowerName = a.getName().toLowerCase(Locale.ROOT);
            for (String qt : queryTerms) {
                if (lowerName.contains(qt)) score += 1.5;
            }
            if (score > 0) {
                scored.add(new Scored(a, score));
            }
        }
        scored.sort((x, y) -> Double.compare(y.score(), x.score()));

        List<Assessment> ranked = scored.stream().map(Scored::a).toList();
        if (ranked.isEmpty()) {
            // nothing matched the terms at all — fall back to the filtered pool as-is
            ranked = pool;
        }
        int k = Math.max(1, Math.min(10, topK));
        return ranked.stream().limit(k).toList();
    }

    private List<Assessment> applyFilters(List<Assessment> corpus, Filters filters) {
        if (filters == null) return corpus;
        List<Assessment> out = new ArrayList<>();
        for (Assessment a : corpus) {
            if (filters.testTypes != null && !filters.testTypes.isEmpty()) {
                boolean match = a.getTestTypeCodes().stream().anyMatch(filters.testTypes::contains);
                if (!match) continue;
            }
            if (filters.maxDurationMinutes != null && a.getDurationMinutes() != null
                    && a.getDurationMinutes() > filters.maxDurationMinutes) {
                continue;
            }
            if (Boolean.TRUE.equals(filters.remoteOnly) && !a.isRemote()) {
                continue;
            }
            if (filters.jobLevel != null && !filters.jobLevel.isBlank() && !a.getJobLevels().isEmpty()) {
                String lvl = filters.jobLevel.toLowerCase(Locale.ROOT);
                boolean match = a.getJobLevels().stream()
                        .anyMatch(jl -> jl.toLowerCase(Locale.ROOT).contains(lvl) || lvl.contains(jl.toLowerCase(Locale.ROOT)))
                        || a.getJobLevels().stream().anyMatch(jl -> jl.equalsIgnoreCase("General Population"));
                if (!match) continue;
            }
            out.add(a);
        }
        return out;
    }
}
