package com.iterate.adreno.sdk;

/**
 * 💬 Chat Turn Data Class
 * 
 * Represents a single conversation turn (user message + assistant response).
 * Used for maintaining conversation history for context.
 */
public class ChatTurn {
    private final String userMessage;
    private final String assistantResponse;
    private final long timestamp;
    
    /**
     * Create a new chat turn
     * 
     * @param userMessage The user's message
     * @param assistantResponse The assistant's response
     */
    public ChatTurn(String userMessage, String assistantResponse) {
        this.userMessage = userMessage;
        this.assistantResponse = assistantResponse;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Create a new chat turn with custom timestamp
     */
    public ChatTurn(String userMessage, String assistantResponse, long timestamp) {
        this.userMessage = userMessage;
        this.assistantResponse = assistantResponse;
        this.timestamp = timestamp;
    }
    
    public String getUserMessage() {
        return userMessage;
    }
    
    public String getAssistantResponse() {
        return assistantResponse;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return "ChatTurn{" +
                "user='" + userMessage + '\'' +
                ", assistant='" + assistantResponse + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}

