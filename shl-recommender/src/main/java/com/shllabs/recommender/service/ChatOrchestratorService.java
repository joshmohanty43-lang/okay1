package com.shllabs.recommender.service;

import com.shllabs.recommender.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestratorService.class);
    private static final int MAX_TURNS = 8;

    private final PlannerService plannerService;
    private final SearchService searchService;
    private final CatalogService catalogService;
    private final LlmClient llmClient;

    public ChatOrchestratorService(PlannerService plannerService, SearchService searchService,
                                    CatalogService catalogService, LlmClient llmClient) {
        this.plannerService = plannerService;
        this.searchService = searchService;
        this.catalogService = catalogService;
        this.llmClient = llmClient;
    }

    public ChatResponse handle(ChatRequest request) {
        List<ChatMessage> messages = request.getMessages();
        int turnIndex = messages.size();

        Plan plan;
        try {
            plan = plannerService.plan(messages, turnIndex);
        } catch (Exception e) {
            log.warn("Planner threw an exception, falling back to clarify: {}", e.getMessage());
            return new ChatResponse(
                    "Could you tell me a bit more about the role and skills you'd like to assess?",
                    List.of(), false);
        }

        String action = plan.getAction() == null ? "clarify" : plan.getAction().toLowerCase(Locale.ROOT);
        boolean nearCap = turnIndex >= MAX_TURNS - 1;

        // Hard cap enforcement: never let the conversation run past MAX_TURNS without a shortlist.
        if ("clarify".equals(action) && nearCap) {
            action = "recommend";
        }

        return switch (action) {
            case "refuse" -> new ChatResponse(safeReply(plan.getReply(),
                    "I can only help with choosing SHL assessments — I'm not able to help with that."),
                    List.of(), false);
            case "compare" -> handleCompare(plan);
            case "recommend", "refine" -> handleRecommend(plan, turnIndex);
            default -> new ChatResponse(safeReply(plan.getReply(),
                    "Could you tell me more about the role — job title, seniority, and any specific "
                            + "skills or competencies you'd like to assess?"),
                    List.of(), false);
        };
    }

    private ChatResponse handleRecommend(Plan plan, int turnIndex) {
        String query = plan.getSearchQuery();
        SearchService.Filters filters = new SearchService.Filters();
        if (plan.getTestTypes() != null && !plan.getTestTypes().isEmpty()) {
            filters.testTypes = new HashSet<>(plan.getTestTypes());
        }
        filters.maxDurationMinutes = plan.getMaxDurationMinutes();
        filters.remoteOnly = plan.getRemoteOnly();
        filters.jobLevel = plan.getJobLevel();

        int topK = plan.getTopK() == null ? 5 : plan.getTopK();
        topK = Math.max(1, Math.min(10, topK));

        List<Assessment> results = searchService.search(query, filters, topK);

        List<Recommendation> recs = results.stream()
                .map(a -> new Recommendation(a.getName(), a.getUrl(),
                        a.getTestTypeCodes().isEmpty() ? null : a.getTestTypeCodes().get(0)))
                .collect(Collectors.toList());

        String reply = templateRecommendReply(recs, filters);
        boolean done = Boolean.TRUE.equals(plan.getDone()) || turnIndex >= MAX_TURNS;
        return new ChatResponse(reply, recs, done);
    }

    private String templateRecommendReply(List<Recommendation> recs, SearchService.Filters filters) {
        if (recs.isEmpty()) {
            return "I couldn't find a close match in the catalog for that combination — could you "
                    + "loosen one of the constraints (duration, test type, or seniority)?";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Based on what you've shared, here ").append(recs.size() == 1 ? "is " : "are ")
                .append(recs.size()).append(recs.size() == 1 ? " assessment" : " assessments")
                .append(" from the SHL catalog that could fit");
        if (filters != null && filters.maxDurationMinutes != null) {
            sb.append(" (within ").append(filters.maxDurationMinutes).append(" minutes)");
        }
        sb.append(". Let me know if you'd like me to narrow it down further, add a specific test "
                + "type (e.g. personality or ability), or compare any of them.");
        return sb.toString();
    }

    private ChatResponse handleCompare(Plan plan) {
        List<String> names = plan.getCompareNames() == null ? List.of() : plan.getCompareNames();
        List<Assessment> resolved = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (String n : names) {
            Optional<Assessment> found = catalogService.findByName(n);
            if (found.isPresent()) {
                resolved.add(found.get());
            } else {
                unresolved.add(n);
            }
        }
        // de-duplicate by id
        LinkedHashMap<String, Assessment> dedup = new LinkedHashMap<>();
        for (Assessment a : resolved) dedup.put(a.getId(), a);
        resolved = new ArrayList<>(dedup.values());

        if (resolved.size() < 2) {
            String missing = unresolved.isEmpty() ? "" : " I couldn't find: " + String.join(", ", unresolved) + ".";
            return new ChatResponse(
                    "Which two (or more) SHL assessments would you like me to compare? Please give me "
                            + "their exact names." + missing,
                    List.of(), false);
        }

        String groundedAnswer = synthesizeComparison(resolved);
        List<Recommendation> recs = resolved.stream()
                .map(a -> new Recommendation(a.getName(), a.getUrl(),
                        a.getTestTypeCodes().isEmpty() ? null : a.getTestTypeCodes().get(0)))
                .collect(Collectors.toList());
        return new ChatResponse(groundedAnswer, recs, false);
    }

    private String synthesizeComparison(List<Assessment> items) {
        StringBuilder catalogContext = new StringBuilder();
        for (Assessment a : items) {
            catalogContext.append("### ").append(a.getName()).append("\n")
                    .append("URL: ").append(a.getUrl()).append("\n")
                    .append("Duration: ").append(a.getDurationMinutes() == null ? "not specified" : a.getDurationMinutes() + " minutes").append("\n")
                    .append("Test type(s): ").append(String.join(",", a.getTestTypeCodes())).append("\n")
                    .append("Job levels: ").append(String.join(", ", a.getJobLevels())).append("\n")
                    .append("Description: ").append(a.getDescription()).append("\n\n");
        }
        String system = """
                Answer the user's question about the differences between the SHL assessments below,
                using ONLY the catalog data provided in this message. Do not invent facts that are not
                present in the descriptions. Keep the answer concise (3-6 sentences or a short bullet
                list) and end by stating which one might fit which kind of need, if that's inferable
                from the data. If the data does not support a claim, say the catalog doesn't specify it.

                CATALOG DATA:
                """ + catalogContext;

        if (!llmClient.isConfigured()) {
            return fallbackComparison(items);
        }
        String text = llmClient.chatText(system, List.of(new ChatMessage("user",
                "Compare: " + items.stream().map(Assessment::getName).collect(Collectors.joining(" vs ")))));
        if (text == null || text.isBlank()) {
            return fallbackComparison(items);
        }
        return text;
    }

    private String fallbackComparison(List<Assessment> items) {
        StringBuilder sb = new StringBuilder("Here's what the catalog says about each:\n");
        for (Assessment a : items) {
            sb.append("- ").append(a.getName()).append(": ").append(a.getDescription()).append("\n");
        }
        return sb.toString();
    }

    private String safeReply(String reply, String fallback) {
        return (reply == null || reply.isBlank()) ? fallback : reply;
    }
}
