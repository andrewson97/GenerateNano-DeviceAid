package com.iterate.adreno.sdk;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * 📚 Chat History Manager for KV Cache Optimization
 * 
 * Manages conversation history with a sliding window approach.
 * Keeps only the last N turns to maintain context without overwhelming the model.
 * 
 * The system prompt is cached separately, so this only manages user/assistant turns.
 */
public class ChatHistoryManager {
    private static final String TAG = "ChatHistoryManager";
    private static final int MAX_HISTORY = 0; // ⚡ Chat history disabled to prevent context overflow
    
    // Singleton instance
    private static ChatHistoryManager instance;
    
    private final List<ChatTurn> history;
    
    // Private constructor for singleton
    private ChatHistoryManager() {
        this.history = new ArrayList<>();
        // Log.i(TAG, "✅ ChatHistoryManager initialized (max history: " + MAX_HISTORY + " turns)");
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized ChatHistoryManager getInstance() {
        if (instance == null) {
            instance = new ChatHistoryManager();
        }
        return instance;
    }
    
    /**
     * Add a new conversation turn (user message + assistant response)
     * 
     * Automatically removes oldest turn if history exceeds MAX_HISTORY.
     * 
     * @param userMessage The user's message
     * @param assistantResponse The assistant's response
     */
    public synchronized void addTurn(String userMessage, String assistantResponse) {
        ChatTurn turn = new ChatTurn(userMessage, assistantResponse);
        history.add(turn);
        
        // Remove oldest turn if we exceed max history
        if (history.size() > MAX_HISTORY) {
            ChatTurn removed = history.remove(0);
            // Log.d(TAG, "🗑️ Removed oldest turn from history (total: " + history.size() + ")");
        }
        
        // Log.d(TAG, "➕ Added turn to history (total: " + history.size() + " turns)");
    }
    
    /**
     * Get formatted history as ChatML string
     * 
     * This format is used when building prompts with history context.
     * Uses LlamaTokens constants for proper formatting.
     * 
     * @return Formatted history string with ChatML tokens
     */
    public synchronized String getFormattedHistory() {
        if (history.isEmpty()) {
            return "";
        }
        
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            ChatTurn turn = history.get(i);
            
            // Format complete conversation turn using LlamaTokens
            formatted.append(LlamaTokens.formatConversationTurn(
                turn.getUserMessage(),
                turn.getAssistantResponse()
            ));
            
            // Add separator between turns (except for last one)
            if (i < history.size() - 1) {
                formatted.append("\n");
            }
        }
        
        return formatted.toString();
    }
    
    /**
     * Build complete prompt with history + new user query
     * 
     * This is used with KV cache - system prompt is cached separately!
     * The prompt includes:
     * 1. Previous conversation history (if any)
     * 2. New user query
     * 3. Assistant start token (ready for generation)
     * 
     * @param userQuery New user query
     * @return Complete prompt ready for inference (history + new query)
     */
    public synchronized String buildPromptWithHistory(String userQuery) {
        StringBuilder prompt = new StringBuilder();
        
        // Add history if exists
        String historyText = getFormattedHistory();
        if (!historyText.isEmpty()) {
            prompt.append(historyText);
            prompt.append("\n");
        }
        
        // Add new user query using LlamaTokens
        prompt.append(LlamaTokens.USER_START);
        prompt.append(userQuery);
        prompt.append(LlamaTokens.MESSAGE_END);
        
        // Start assistant response with thinking template (exact pattern)
        prompt.append(LlamaTokens.ASSISTANT_TOKEN)
             .append(LlamaTokens.THINKING_TEMPLATE);  // <think>\n\n</think>\n\n
        
        return prompt.toString();
    }
    
    /**
     * Clear all conversation history
     * 
     * Note: This does NOT invalidate the KV cache for the system prompt!
     * System prompt remains cached for continued fast responses.
     */
    public synchronized void clear() {
        int prevSize = history.size();
        history.clear();
        Log.i(TAG, "🗑️ Cleared chat history (" + prevSize + " turns removed, KV cache retained)");
    }
    
    /**
     * Get current history size
     */
    public synchronized int getHistorySize() {
        return history.size();
    }
    
    /**
     * Get a copy of all history turns
     * 
     * @return List of all chat turns (defensive copy)
     */
    public synchronized List<ChatTurn> getHistory() {
        return new ArrayList<>(history);
    }
    
    /**
     * Get the last chat turn (if any)
     * 
     * @return Last chat turn or null if history is empty
     */
    public synchronized ChatTurn getLastTurn() {
        if (history.isEmpty()) {
            return null;
        }
        return history.get(history.size() - 1);
    }
    
    /**
     * Get debug information about current history state
     */
    public synchronized String getDebugInfo() {
        StringBuilder info = new StringBuilder();
        info.append("📚 Chat History Status:\n");
        info.append("  Turns: ").append(history.size()).append("/").append(MAX_HISTORY).append("\n");
        info.append("  Total Chars: ");
        
        int totalChars = 0;
        for (ChatTurn turn : history) {
            totalChars += turn.getUserMessage().length() + turn.getAssistantResponse().length();
        }
        info.append(totalChars).append("\n");
        
        return info.toString();
    }
}

