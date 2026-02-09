package com.iterate.adreno.sdk
import android.content.Context
import android.util.Log
import java.io.File

/**
 * 🚀 GPU-ACCELERATED LLAMA INFERENCE DEMONSTRATION
 * 
 * This class demonstrates GPU acceleration using:
 * - Adreno GPU SDK for real OpenCL device detection
 * - GGML-OpenCL for real GPU inference with embedded kernels
 * - Real timing measurements showing GPU vs CPU performance
 */
object LlamaGPU {
    private const val TAG = "LlamaGPU"
    
    init {
        System.loadLibrary("adreno-llama-jni")
        // Log.i(TAG, "✅ GPU LLaMA library loaded")
    }
    
    // Native methods
    private external fun loadModel(modelPath: String, gpuLayers: Int): Boolean
    private external fun generate(prompt: String, maxTokens: Int): String
    private external fun generateStreaming(prompt: String, maxTokens: Int, callback: StreamingCallback): String
    private external fun freeModel()
    
    // Callback interface for streaming
    interface StreamingCallback {
        fun onTokenGenerated(token: String)
        fun onGenerationComplete()
        fun onError(error: String)
    }
    
    /**
     * Initialize with custom model from assets
     * 
     * @param context Application context
     * @param modelPath Path to model in assets (e.g., "models/Qwen3-1.7B-Q4_0.gguf")
     * @param gpuLayers Number of GPU layers (0-33, configurable by app)
     */
    fun initialize(context: Context, modelPath: String, gpuLayers: Int): Boolean {
        val modelFileName = modelPath.substringAfterLast("/")
        val modelFile = File(context.filesDir, modelFileName)
        
        // Log.i(TAG, "🚀 Initializing model from: $modelPath")
        // Log.i(TAG, "   GPU Layers: $gpuLayers")
        
        // Copy model from assets if needed
        if (!modelFile.exists()) {
            try {
                // Log.i(TAG, "📥 Copying model from assets...")
                context.assets.open(modelPath).use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                // Log.i(TAG, "✅ Model copied to: ${modelFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to copy model: ${e.message}")
                return false
            }
        } else {
            // Log.i(TAG, "✅ Model found in cache")
        }
        
        return loadModel(modelFile.absolutePath, gpuLayers)
    }
    
    /**
     * Generate response with GPU acceleration (non-streaming)
     */
    fun generateResponse(prompt: String, maxTokens: Int = 100): String {
        // Log.i(TAG, "🎯 Generating with GPU...")
        val startTime = System.currentTimeMillis()
        
        val response = generate(prompt, maxTokens)
        
        val elapsed = System.currentTimeMillis() - startTime
        // Log.i(TAG, "✅ Generated in ${elapsed}ms")
        
        return response
    }
    
    /**
     * 🚀 Generate response with REAL-TIME STREAMING
     */
    fun generateResponseStreaming(prompt: String, maxTokens: Int = 100, callback: StreamingCallback) {
        // Log.i(TAG, "🎯 Starting streaming generation with GPU...")
        
        try {
            // Start generation in background thread
            Thread {
                try {
                    generateStreaming(prompt, maxTokens, callback)
                    // If we get here, generation completed successfully
                    callback.onGenerationComplete()
                } catch (e: Exception) {
                    callback.onError("Generation failed: ${e.message}")
                }
            }.start()
            
        } catch (e: Exception) {
            callback.onError("Failed to start generation: ${e.message}")
        }
    }
    
    // ============================================================================
    // 💾 KV CACHE OPTIMIZATION METHODS
    // ============================================================================
    
    /**
     * Initialize system prompt KV cache (call once at startup)
     */
    private external fun initializeSystemPromptCache(systemPrompt: String): Boolean
    
    /**
     * Generate with KV cache reuse (SUPER FAST! ⚡)
     */
    private external fun generateStreamingWithCache(
        prompt: String,
        maxTokens: Int,
        callback: StreamingCallback
    ): String
    
    /**
     * Invalidate KV cache (e.g., when menu updates)
     */
    private external fun invalidateKVCache()
    
    /**
     * Check if KV cache is initialized
     */
    external fun isKVCacheInitialized(): Boolean
    
    /**
     * Get cached token count
     */
    private external fun getCachedTokenCount(): Int
    
    /**
     * 💾 Initialize KV cache with system prompt
     * Call this ONCE after model loading
     */
    fun initializeSystemCache(@Suppress("UNUSED_PARAMETER") context: Context): Boolean {
        val systemPrompt = SystemPromptManagerEnhanced.getInstance().formattedSystemPrompt
        // Log.i(TAG, "🎯 Initializing KV cache with system prompt...")
        
        val success = initializeSystemPromptCache(systemPrompt)
        if (success) {
            SystemPromptManagerEnhanced.getInstance().markKVCacheAsValid()
            val cachedTokens = getCachedTokenCount()
            // Log.i(TAG, "✅ KV Cache initialized! Cached $cachedTokens tokens")
            // Log.i(TAG, "⚡ Future queries will be 5-10x faster!")
        } else {
            Log.e(TAG, "❌ Failed to initialize KV cache")
        }
        return success
    }
    
    /**
     * 🚀 Generate with history + user query (using cached system prompt)
     * This is THE FAST METHOD - uses KV cache!
     */
    fun generateResponseStreamingFast(
        userQuery: String,
        maxTokens: Int = 1024,
        callback: StreamingCallback
    ) {
        // Build prompt with history + user query (NO system prompt!)
        val promptWithHistory = ChatHistoryManager.getInstance().buildPromptWithHistory(userQuery)
        
        // Log.i(TAG, "⚡ Generating with cached system prompt")
        // Log.i(TAG, "📊 History size: ${ChatHistoryManager.getInstance().historySize} turns")
        // Log.i(TAG, "💾 Cached tokens: ${getCachedTokenCount()}")
        
        Thread {
            try {
                val startTime = System.currentTimeMillis()
                
                // Use cached generation method
                val response = generateStreamingWithCache(promptWithHistory, maxTokens, callback)
                
                // Save to history for next turn
                ChatHistoryManager.getInstance().addTurn(userQuery, response)
                
                val elapsed = System.currentTimeMillis() - startTime
                // Log.i(TAG, "✅ Generated in ${elapsed}ms with KV cache!")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Generation error: ${e.message}")
                callback.onError("Generation failed: ${e.message}")
            }
        }.start()
    }
    
    /**
     * Clear chat history (but keep KV cache!)
     */
    fun clearChatHistory() {
        ChatHistoryManager.getInstance().clear()
        // Log.i(TAG, "🗑️ Chat history cleared (KV cache retained)")
    }
    
    /**
     * Refresh system cache (e.g., when system prompt updates)
     */
    fun refreshSystemCache(context: Context): Boolean {
        // Log.i(TAG, "🔄 Refreshing system cache...")
        invalidateKVCache()
        SystemPromptManagerEnhanced.getInstance().invalidateKVCache()
        return initializeSystemCache(context)
    }
    
    /**
     * Get cache status for debugging
     */
    fun getCacheStatus(): String {
        return buildString {
            append("💾 KV Cache Status:\n")
            append("  Initialized: ${isKVCacheInitialized()}\n")
            append("  Cached Tokens: ${getCachedTokenCount()}\n")
            append("  History Size: ${ChatHistoryManager.getInstance().historySize} turns\n")
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        freeModel()
    }
}
