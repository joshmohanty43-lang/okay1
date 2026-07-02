package com.shllabs.recommender.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class ChatRequest {

    @NotEmpty
    @Valid
    private List<ChatMessage> messages;

    public List<ChatMessage> getMessages() { return messages; }
    public void setMessages(List<ChatMessage> messages) { this.messages = messages; }
}
