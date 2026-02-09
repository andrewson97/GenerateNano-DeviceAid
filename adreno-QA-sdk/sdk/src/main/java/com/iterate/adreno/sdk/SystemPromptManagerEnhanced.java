package com.iterate.adreno.sdk;

import android.content.Context;
import android.util.Log;

import com.iterate.adreno.sdk.rag.RAGContextManager;
import com.iterate.adreno.sdk.rag.SearchResult;

import java.util.List;

/**
 * 💾 System Prompt Manager for KV Cache Optimization
 * 
 * Manages system prompts for Q&A with RAG context.
 * Supports custom system prompts with KV cache optimization and RAG document retrieval.
 * 
 * Features:
 * - Custom system prompt support
 * - RAG context integration
 * - KV cache management
 * - ChatML token formatting
 */
public class SystemPromptManagerEnhanced {
    private static final String TAG = "SystemPromptManager";
    
    // Singleton instance
    private static SystemPromptManagerEnhanced instance;
    
    private String customSystemPrompt = null;
    private String ragContext = null;
    private boolean kvCacheValid = false;
    private boolean ragEnabled = false;
    
    // Private constructor for singleton
    private SystemPromptManagerEnhanced() {
        Log.i(TAG, "✅ SystemPromptManagerEnhanced initialized");
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized SystemPromptManagerEnhanced getInstance() {
        if (instance == null) {
            instance = new SystemPromptManagerEnhanced();
        }
        return instance;
    }
    
    /**
     * Initialize with custom system prompt
     * 
     * @param context Application context
     * @param systemPrompt Custom system prompt for the assistant
     */
    public void initialize(Context context, String systemPrompt) {
        this.customSystemPrompt = systemPrompt;
        
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            Log.w(TAG, "⚠️ No system prompt provided, using default");
            this.customSystemPrompt = getDefaultSystemPrompt();
        } else {
            Log.i(TAG, "✅ Custom system prompt initialized (" + systemPrompt.length() + " chars)");
        }
        
        // Invalidate cache to regenerate with new prompt
        invalidateKVCache();
    }
    
    /**
     * Get the system prompt (custom or default)
     * Includes RAG context if enabled
     * 
     * @return System prompt string
     */
    public String getSystemPrompt() {
        String basePrompt = customSystemPrompt != null && !customSystemPrompt.isEmpty() 
            ? customSystemPrompt 
            : getDefaultSystemPrompt();
        
        // Add RAG context if available
        if (ragEnabled && ragContext != null && !ragContext.isEmpty()) {
            return basePrompt + "\n\n" + ragContext;
        }
        
        return basePrompt;
    }
    
    /**
     * Get default system prompt if none provided
     */
    private String getDefaultSystemPrompt() {
        return "You are a helpful AI assistant. Provide accurate, informative, and friendly responses to user questions.";
    }
    
    /**
     * Mark KV cache as valid after successful initialization
     */
    public void markKVCacheAsValid() {
        this.kvCacheValid = true;
        Log.i(TAG, "✅ KV cache marked as valid");
    }
    
    /**
     * Check if KV cache is valid
     */
    public boolean isKVCacheValid() {
        return kvCacheValid;
    }
    
    /**
     * Invalidate KV cache (e.g., when system prompt changes)
     */
    public void invalidateKVCache() {
        this.kvCacheValid = false;
        Log.i(TAG, "🗑️ KV cache invalidated (will be re-initialized)");
    }
    
    /**
     * Get formatted system prompt with ChatML tokens
     */
    public String getFormattedSystemPrompt() {
        return LlamaTokens.formatSystemMessage(getSystemPrompt());
    }
    
    /**
     * Build complete prompt with system instructions and user query
     */
    public String buildFullPrompt(String userQuery) {
        StringBuilder prompt = new StringBuilder();
        
        // Add system prompt
        prompt.append(LlamaTokens.SYSTEM_TOKEN);
        prompt.append(getSystemPrompt());
        prompt.append(LlamaTokens.MESSAGE_END);
        
        // Add user query
        prompt.append(LlamaTokens.USER_START);
        prompt.append(userQuery);
        prompt.append(LlamaTokens.MESSAGE_END);
        
        // Start assistant response with thinking template
        prompt.append(LlamaTokens.ASSISTANT_TOKEN)
              .append(LlamaTokens.THINKING_TEMPLATE);
        
        return prompt.toString();
    }
    
    /**
     * Update system prompt (invalidates cache)
     * 
     * @param newPrompt New system prompt
     */
    public void updateSystemPrompt(String newPrompt) {
        if (newPrompt != null && !newPrompt.isEmpty()) {
            this.customSystemPrompt = newPrompt;
            invalidateKVCache();
            Log.i(TAG, "✅ System prompt updated (" + newPrompt.length() + " chars) and cache invalidated");
        } else {
            Log.w(TAG, "⚠️ Cannot update with empty system prompt");
        }
    }
    
    /**
     * Enable RAG context injection
     */
    public void enableRAG() {
        this.ragEnabled = true;
        Log.i(TAG, "🧠 RAG context injection enabled");
    }
    
    /**
     * Disable RAG context injection
     */
    public void disableRAG() {
        this.ragEnabled = false;
        this.ragContext = null;
        Log.i(TAG, "🧠 RAG context injection disabled");
    }
    
    /**
     * Set RAG context from search results
     * 
     * @param results Search results from RAG
     */
    public void setRAGContext(List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            this.ragContext = null;
            return;
        }
        
        this.ragContext = RAGContextManager.getInstance().getFormattedContext(results);
        Log.i(TAG, "🧠 RAG context set (" + results.size() + " documents, " + ragContext.length() + " chars)");
    }
    
    /**
     * Set RAG context directly from string
     * 
     * @param context RAG context string
     */
    public void setRAGContextDirect(String context) {
        this.ragContext = context;
        Log.i(TAG, "🧠 RAG context set directly (" + (context != null ? context.length() : 0) + " chars)");
    }
    
    /**
     * Clear RAG context
     */
    public void clearRAGContext() {
        this.ragContext = null;
        Log.i(TAG, "🧠 RAG context cleared");
    }
    
    /**
     * Check if RAG is enabled
     */
    public boolean isRAGEnabled() {
        return ragEnabled;
    }
    
    /**
     * Check if RAG context is set
     */
    public boolean hasRAGContext() {
        return ragContext != null && !ragContext.isEmpty();
    }
}