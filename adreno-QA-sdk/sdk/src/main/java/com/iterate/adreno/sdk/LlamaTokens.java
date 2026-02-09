package com.iterate.adreno.sdk;

/**
 * 🎯 Llama Chat Template Tokens
 * 
 * ChatML format tokens for Qwen/InterplayThink model.
 * These tokens structure the conversation between system, user, and assistant.
 * 
 * Format:
 * <|im_start|>system
 * {system instructions}
 * <|im_end|>
 * <|im_start|>user
 * {user message}
 * <|im_end|>
 * <|im_start|>assistant
 * {assistant response}
 * <|im_end|>
 */
public final class LlamaTokens {
    
    // ============================================================================
    // ChatML Tokens (Qwen/InterplayThink format)
    // ============================================================================
    
    /**
     * System message start token
     * Use this to begin system instructions/prompt
     */
    public static final String SYSTEM_TOKEN = "<|im_start|>system\n";
    
    /**
     * User message start token
     * Use this to begin user input
     */
    public static final String USER_TOKEN = "<|im_end|>\n<|im_start|>user\n";
    
    /**
     * Assistant message start token
     * Use this to begin assistant response
     */
    public static final String ASSISTANT_TOKEN = "<|im_end|>\n<|im_start|>assistant\n";
    
    /**
     * End of turn token
     * Marks the end of a message
     */
    public static final String EOT_TOKEN = "<|im_end|>";
    
    /**
     * Legacy end of text token (for older models)
     */
    public static final String LEGACY_EOT_TOKEN = "<|end_of_text|>";
    
    /**
     * Alternative format: Start of turn without closing previous
     * Use for first message in conversation
     */
    public static final String SYSTEM_START = "<|im_start|>system\n";
    public static final String USER_START = "<|im_start|>user\n";
    public static final String ASSISTANT_START = "<|im_start|>assistant\n";
    
    /**
     * Message separator
     */
    public static final String MESSAGE_END = "<|im_end|>\n";
    
    /**
     * Stop tokens for generation
     * Model should stop generating when these are encountered
     */
    public static final String[] STOP_TOKENS = { 
        EOT_TOKEN, 
        LEGACY_EOT_TOKEN 
    };
    
    // ============================================================================
    // Special Tokens
    // ============================================================================
    
    /**
     * Thinking template for chain-of-thought reasoning
     * Model uses this empty template to show its reasoning process
     * Format: <think>\n\n</think>\n\n
     * 
     * This primes the model to think before responding, improving answer quality
     */
    public static final String THINKING_TEMPLATE = "<think>\n\n</think>\n\n";
    
    // ============================================================================
    // Helper Methods
    // ============================================================================
    
    /**
     * Build a complete system message
     * 
     * @param systemPrompt The system instructions
     * @return Formatted system message with tokens
     */
    public static String formatSystemMessage(String systemPrompt) {
        return SYSTEM_START + systemPrompt + MESSAGE_END;
    }
    
    /**
     * Build a complete user message
     * 
     * @param userMessage The user's message
     * @return Formatted user message with tokens
     */
    public static String formatUserMessage(String userMessage) {
        return USER_START + userMessage + MESSAGE_END;
    }
    
    /**
     * Build a complete assistant message
     * 
     * @param assistantMessage The assistant's response
     * @return Formatted assistant message with tokens
     */
    public static String formatAssistantMessage(String assistantMessage) {
        return ASSISTANT_START + assistantMessage + MESSAGE_END;
    }
    
    /**
     * Start an assistant response with thinking template
     * Combines ASSISTANT_TOKEN with thinking template for better reasoning
     * 
     * Pattern:
     * <|im_end|>
     * <|im_start|>assistant
     * <think>
     * 
     * </think>
     * 
     * @return Assistant token with thinking template ready for generation
     */
    public static String startAssistantWithThinking() {
        return ASSISTANT_TOKEN + THINKING_TEMPLATE;
    }
    
    /**
     * Build a conversation turn (user + assistant)
     * 
     * @param userMessage User's message
     * @param assistantResponse Assistant's response
     * @return Complete formatted turn
     */
    public static String formatConversationTurn(String userMessage, String assistantResponse) {
        StringBuilder turn = new StringBuilder();
        turn.append(formatUserMessage(userMessage));
        turn.append(formatAssistantMessage(assistantResponse));
        return turn.toString();
    }
    
    // Private constructor to prevent instantiation
    private LlamaTokens() {
        throw new AssertionError("Utility class - cannot be instantiated.");
    }
}

