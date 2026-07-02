package com.shllabs.recommender.model;

import java.util.Collections;
import java.util.List;

public class ChatResponse {

    private String reply;
    private List<Recommendation> recommendations;
    private boolean end_of_conversation;

    public ChatResponse() { }

    public ChatResponse(String reply, List<Recommendation> recommendations, boolean endOfConversation) {
        this.reply = reply;
        this.recommendations = recommendations == null ? Collections.emptyList() : recommendations;
        this.end_of_conversation = endOfConversation;
    }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public List<Recommendation> getRecommendations() { return recommendations; }
    public void setRecommendations(List<Recommendation> recommendations) { this.recommendations = recommendations; }

    public boolean isEnd_of_conversation() { return end_of_conversation; }
    public void setEnd_of_conversation(boolean endOfConversation) { this.end_of_conversation = endOfConversation; }
}
