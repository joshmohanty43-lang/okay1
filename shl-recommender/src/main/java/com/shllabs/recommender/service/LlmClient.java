package com.shllabs.recommender.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shllabs.recommender.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Provider-agnostic client for any OpenAI-compatible /chat/completions endpoint
 * (Groq, OpenRouter, OpenAI itself, etc). Configure via LLM_API_BASE_URL / LLM_API_KEY / LLM_MODEL.
 */
@Service
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.connect-timeout-ms}")
    private long connectTimeoutMs;

    @Value("${llm.request-timeout-ms}")
    private long requestTimeoutMs;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpClient httpClient;

    private HttpClient client() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                    .build();
        }
        return httpClient;
    }

    /**
     * Calls the LLM asking for a strict JSON object response. Returns the raw content string
     * (already stripped of markdown code fences if the model added them).
     */
    public String chatJson(String systemPrompt, List<ChatMessage> conversation) {
        return call(systemPrompt, conversation, true);
    }

    /** Calls the LLM for a plain natural-language answer (no JSON mode). */
    public String chatText(String systemPrompt, List<ChatMessage> conversation) {
        return call(systemPrompt, conversation, false);
    }

    private String call(String systemPrompt, List<ChatMessage> conversation, boolean jsonMode) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", 0.2);
            ArrayNode messages = body.putArray("messages");
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            for (ChatMessage m : conversation) {
                ObjectNode node = messages.addObject();
                node.put("role", m.isUser() ? "user" : "assistant");
                node.put("content", m.getContent());
            }
            if (jsonMode) {
                ObjectNode fmt = body.putObject("response_format");
                fmt.put("type", "json_object");
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofMillis(requestTimeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("LLM call failed with status {}: {}", response.statusCode(), truncate(response.body()));
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return null;
            }
            String content = choices.get(0).path("message").path("content").asText(null);
            return stripCodeFences(content);
        } catch (Exception e) {
            log.warn("LLM call threw an exception: {}", e.getMessage());
            return null;
        }
    }

    private String stripCodeFences(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            int lastFence = trimmed.lastIndexOf("```");
            if (lastFence != -1) {
                trimmed = trimmed.substring(0, lastFence);
            }
        }
        return trimmed.trim();
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
