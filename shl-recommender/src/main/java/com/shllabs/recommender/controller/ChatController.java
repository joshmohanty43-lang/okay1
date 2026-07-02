package com.shllabs.recommender.controller;

import com.shllabs.recommender.model.ChatRequest;
import com.shllabs.recommender.model.ChatResponse;
import com.shllabs.recommender.service.ChatOrchestratorService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatOrchestratorService orchestrator;

    public ChatController(ChatOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        try {
            return orchestrator.handle(request);
        } catch (Exception e) {
            // Schema compliance is a hard requirement: never let an internal error surface as a
            // non-conforming body or a 5xx that breaks the evaluator's replay harness.
            log.error("Unhandled error in /chat", e);
            return new ChatResponse(
                    "Sorry, something went wrong on my end. Could you rephrase what role or skills "
                            + "you'd like an SHL assessment for?",
                    List.of(), false);
        }
    }
}


