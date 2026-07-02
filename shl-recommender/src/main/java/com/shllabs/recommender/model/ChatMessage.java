package com.shllabs.recommender.model;

import jakarta.validation.constraints.NotBlank;

public class ChatMessage {

    @NotBlank
    private String role; // "user" | "assistant"

    @NotBlank
    private String content;

    public ChatMessage() { }

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public boolean isUser() { return "user".equalsIgnoreCase(role); }
}
