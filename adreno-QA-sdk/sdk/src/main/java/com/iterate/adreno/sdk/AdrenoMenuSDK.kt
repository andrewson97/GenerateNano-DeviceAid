package com.iterate.adreno.sdk

import android.content.Context
import android.util.Log

/**
 * 🚀 Adreno Q&A SDK
 * 
 * High-performance Q&A SDK with RAG context:
 * - GPU-accelerated LLaMA inference
 * - KV cache optimization (5-10x faster responses)
 * - Conversation history management
 * - Streaming responses
 * - Custom system prompts
 * 
 * Usage:
 * ```kotlin
 * val sdk = AdrenoMenuSDK.initialize(
 *     context = applicationContext,
 *     modelPath = "models/Qwen3-1.7B-Q4_0.gguf",
 *     systemPrompt = "You are a helpful assistant.",
 *     gpuLayers = 27
 * )
 * 
 * sdk.generateResponse("What is machine learning?") { token ->
 *     // Handle streaming token
 * }
 * ```
 */
class AdrenoMenuSDK private constructor(
    private val context: Context,
    private val gpuLayers: Int
) {
    companion object {
        private const val TAG = "AdrenoMenuSDK"
        private var instance: AdrenoMenuSDK? = null
        private var currentGpuLayers: Int = -1
        
        /**
         * Initialize the SDK with model and system prompt
         * 
         * @param context Application context
         * @param modelPath Path to model file in assets (e.g., "models/Qwen3-1.7B-Q4_0.gguf")
         * @param systemPrompt System prompt for the AI assistant
         * @param gpuLayers Number of GPU layers (0-33, default 27)
         * @return Initialized SDK instance
         */
        @JvmStatic
        @JvmOverloads
        fun initialize(
            context: Context,
            modelPath: String,
            systemPrompt: String,
            gpuLayers: Int = 27
        ): AdrenoMenuSDK {
            // 🔥 FIX: Reinitialize if GPU layers changed
            if (instance != null && currentGpuLayers != gpuLayers) {
                Log.w(TAG, "⚠️ GPU layers changed from $currentGpuLayers to $gpuLayers - reinitializing...")
                instance!!.cleanup()
                instance = null
            }
            
            if (instance == null) {
                // Log.i(TAG, "🚀 Initializing SDK with $gpuLayers GPU layers")
                
                // ⚠️ Warn if GPU layers is 0 (CPU only mode)
                if (gpuLayers == 0) {
                    Log.w(TAG, "╔══════════════════════════════════════════════════════╗")
                    Log.w(TAG, "║ ⚠️  WARNING: GPU LAYERS SET TO 0 (CPU ONLY MODE)    ║")
                    Log.w(TAG, "╠══════════════════════════════════════════════════════╣")
                    Log.w(TAG, "║ This will NOT use GPU acceleration!                  ║")
                    Log.w(TAG, "║ Performance will be significantly slower.            ║")
                    Log.w(TAG, "║                                                      ║")
                    Log.w(TAG, "║ To enable GPU acceleration:                          ║")
                    Log.w(TAG, "║  • Go to: Menu → Edit Menu & Performance             ║")
                    Log.w(TAG, "║  • Move Performance slider to 27 or higher           ║")
                    Log.w(TAG, "║  • Tap Save to apply changes                         ║")
                    Log.w(TAG, "╚══════════════════════════════════════════════════════╝")
                }
                
                instance = AdrenoMenuSDK(context.applicationContext, gpuLayers)
                instance!!.initializeInternal(modelPath, systemPrompt)
                currentGpuLayers = gpuLayers
            }
            return instance!!
        }
        
        /**
         * Get SDK instance (must call initialize first)
         */
        @JvmStatic
        fun getInstance(): AdrenoMenuSDK {
            return instance ?: throw IllegalStateException("SDK not initialized. Call initialize() first.")
        }
        
        /**
         * Check if KV cache is ready (for loading screen)
         */
        @JvmStatic
        fun isKVCacheReady(): Boolean {
            return try {
                instance?.isInitialized == true && LlamaGPU.isKVCacheInitialized()
            } catch (e: Exception) {
                false
            }
        }
    }
    
    private var isInitialized = false
    
    /**
     * Internal initialization
     */
    private fun initializeInternal(modelPath: String, systemPrompt: String) {
        // Log.i(TAG, "🚀 Initializing Adreno Q&A SDK...")
        // Log.i(TAG, "   Model: $modelPath")
        // Log.i(TAG, "   System Prompt: ${systemPrompt.take(100)}...")
        // Log.i(TAG, "   GPU Layers: $gpuLayers")
        
        try {
            // Initialize AppContextHolder
            AppContextHolder.setContext(context)
            
            // Initialize SystemPromptManager with custom prompt
            SystemPromptManagerEnhanced.getInstance().initialize(context, systemPrompt)
            
            // Initialize LlamaGPU with custom model path
            val success = LlamaGPU.initialize(context, modelPath, gpuLayers)
            
            if (success) {
                // Log.i(TAG, "✅ Model loaded successfully!")
                // Log.i(TAG, "⚡ KV cache will be initialized on first query")
                isInitialized = true
                
                // Initialize KV cache in background thread (non-blocking)
                Thread {
                    try {
                        // Log.i(TAG, "🔄 Initializing KV cache in background...")
                        LlamaGPU.initializeSystemCache(context)
                        // Log.i(TAG, "✅ KV cache ready! Future responses will be 5-10x faster")
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Background KV cache init failed: ${e.message}")
                    }
                }.start()
            } else {
                throw RuntimeException("Failed to initialize model")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ SDK initialization failed: ${e.message}")
            throw RuntimeException("SDK initialization failed", e)
        }
    }
    
    /**
     * Check if SDK is initialized
     */
    fun isInitialized(): Boolean = isInitialized
    
    /**
     * Cleanup SDK resources (internal use)
     */
    private fun cleanup() {
        // Log.i(TAG, "🧹 Cleaning up SDK resources...")
        try {
            LlamaGPU.cleanup()
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Cleanup error: ${e.message}")
        }
    }
    
    /**
     * Generate response with streaming
     * 
     * @param query User query (e.g., "Add 2 burgers")
     * @param maxTokens Maximum tokens to generate
     * @param callback Streaming callback for tokens
     */
    @JvmOverloads
    fun generateResponse(
        query: String,
        maxTokens: Int = 1024,
        callback: LlamaGPU.StreamingCallback
    ) {
        if (!isInitialized) {
            callback.onError("SDK not initialized")
            return
        }
        
        LlamaGPU.generateResponseStreamingFast(query, maxTokens, callback)
    }
    
    /**
     * Get chat history manager
     */
    fun getHistoryManager(): ChatHistoryManager {
        return ChatHistoryManager.getInstance()
    }
    
    /**
     * Clear chat history
     */
    fun clearHistory() {
        ChatHistoryManager.getInstance().clear()
    }
    
    /**
     * Get cache status
     */
    fun getCacheStatus(): String {
        return LlamaGPU.getCacheStatus()
    }
    
    /**
     * Refresh KV cache (e.g., when system prompt changes)
     */
    fun refreshCache() {
        LlamaGPU.refreshSystemCache(context)
    }
    
    /**
     * Get current GPU layers
     */
    fun getGpuLayers(): Int = gpuLayers
    
    /**
     * Get SDK version
     */
    fun getVersion(): String = "1.0.0"
    
    /**
     * Get SDK info
     */
    fun getInfo(): String {
        return """
            Adreno Q&A SDK v${getVersion()}
            GPU Layers: $gpuLayers
            KV Cache: ${if (LlamaGPU.isKVCacheInitialized()) "Enabled" else "Disabled"}
            History Turns: ${ChatHistoryManager.getInstance().historySize}
        """.trimIndent()
    }
}

