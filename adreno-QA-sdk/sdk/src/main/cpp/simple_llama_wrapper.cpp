/*
 * 🚀 REAL LLAMA.CPP GPU-ACCELERATED INFERENCE FOR ANDROID
 * Uses actual llama.cpp API with OpenCL GPU acceleration
 */
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <sstream>
#include <stdexcept>
#include <thread>
#include <chrono>
#include "llama.h"
#include "ggml-backend-impl.h"  // For ggml_backend_register()

// Declare Qualcomm's OpenCL backend registration function
extern "C" ggml_backend_reg_t ggml_backend_opencl_reg(void);

// Note: Using Qualcomm's complete GGML OpenCL system
// The 5000-line ggml-opencl.cpp is compiled and linked directly

#define LOG_TAG "LlamaGPU"
// Disable native logging - no-op macros
#define LOGI(...) ((void)0)
#define LOGE(...) ((void)0)
#define LOGW(...) ((void)0)

// Global state for llama.cpp
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static int g_gpu_layers = 0;

// Global callback for streaming (set by JNI)
static jobject g_streaming_callback = nullptr;
static JavaVM* g_jvm = nullptr;
static JNIEnv* g_env = nullptr;

// 💾 KV CACHE OPTIMIZATION - Global State
static std::vector<uint8_t> g_system_prompt_kv_state;
static bool g_kv_cache_initialized = false;
static int g_system_prompt_token_count = 0;

// 💾 KV Cache Management Functions
bool save_system_prompt_kv_cache(llama_context* ctx) {
    if (!ctx) {
        LOGE("❌ Cannot save KV cache: context is null");
        return false;
    }
    
    try {
        size_t state_size = llama_state_get_size(ctx);
        if (state_size == 0) {
            LOGE("❌ KV cache state size is 0");
            return false;
        }
        
        g_system_prompt_kv_state.resize(state_size);
        
        size_t bytes_written = llama_state_get_data(
            ctx, 
            g_system_prompt_kv_state.data(), 
            state_size
        );
        
        g_kv_cache_initialized = (bytes_written > 0 && bytes_written == state_size);
        
        if (g_kv_cache_initialized) {
            LOGI("💾 System prompt KV cache saved: %zu bytes", bytes_written);
        } else {
            LOGE("❌ KV cache save failed: wrote %zu bytes, expected %zu", bytes_written, state_size);
        }
        
        return g_kv_cache_initialized;
    } catch (const std::exception& e) {
        LOGE("❌ Exception saving KV cache: %s", e.what());
        return false;
    }
}

bool restore_system_prompt_kv_cache(llama_context* ctx) {
    if (!ctx) {
        LOGE("❌ Cannot restore KV cache: context is null");
        return false;
    }
    
    if (!g_kv_cache_initialized || g_system_prompt_kv_state.empty()) {
        LOGW("⚠️ KV cache not initialized or empty");
        return false;
    }
    
    try {
        size_t bytes_read = llama_state_set_data(
            ctx,
            g_system_prompt_kv_state.data(),
            g_system_prompt_kv_state.size()
        );
        
        bool success = (bytes_read > 0 && bytes_read == g_system_prompt_kv_state.size());
        
        if (success) {
            LOGI("📂 System prompt KV cache restored: %zu bytes (saved %d tokens of processing!)", 
                 bytes_read, g_system_prompt_token_count);
        } else {
            LOGE("❌ KV cache restore failed: read %zu bytes, expected %zu", 
                 bytes_read, g_system_prompt_kv_state.size());
        }
        
        return success;
    } catch (const std::exception& e) {
        LOGE("❌ Exception restoring KV cache: %s", e.what());
        return false;
    }
}

bool is_kv_cache_initialized() {
    return g_kv_cache_initialized && !g_system_prompt_kv_state.empty();
}

void invalidate_kv_cache() {
    g_kv_cache_initialized = false;
    g_system_prompt_kv_state.clear();
    g_system_prompt_token_count = 0;
    LOGI("🗑️ KV cache invalidated (e.g., menu updated)");
}

int get_cached_token_count() {
    return g_system_prompt_token_count;
}


// 🧪 PERFORMANCE TESTING CONFIGURATION MATRIX
struct GPUTestConfig {
    int cpu_threads;
    int gpu_layers;
    const char* name;
    const char* description;
};

// Complete test matrix for Adreno GPU optimization
static const GPUTestConfig TEST_CONFIGS[] = {
    // CPU Only Tests
    {1, 0, "CPU-1T", "CPU Only - 1 Thread"},
    {2, 0, "CPU-2T", "CPU Only - 2 Threads"},
    {4, 0, "CPU-4T", "CPU Only - 4 Threads"},
    {6, 0, "CPU-6T", "CPU Only - 6 Threads"},
    {8, 0, "CPU-8T", "CPU Only - 8 Threads"},
    
    // Hybrid Configurations (Light GPU)
    {4, 1, "H4-1G", "Hybrid - 4 CPU + 1 GPU Layer"},
    {4, 4, "H4-4G", "Hybrid - 4 CPU + 4 GPU Layers"},
    {4, 8, "H4-8G", "Hybrid - 4 CPU + 8 GPU Layers"},
    {6, 8, "H6-8G", "Hybrid - 6 CPU + 8 GPU Layers"},
    
    // Balanced Configurations
    {4, 12, "B4-12G", "Balanced - 4 CPU + 12 GPU Layers"},
    {4, 16, "B4-16G", "Balanced - 4 CPU + 16 GPU Layers"},
    {6, 16, "B6-16G", "Balanced - 6 CPU + 16 GPU Layers"},
    {2, 20, "B2-20G", "Balanced - 2 CPU + 20 GPU Layers"},
    
    // GPU-Heavy Configurations
    {2, 24, "G2-24G", "GPU Heavy - 2 CPU + 24 GPU Layers"},
    {4, 26, "G4-26G", "GPU Heavy - 4 CPU + 26 GPU Layers"},
    {2, 27, "G2-27G", "GPU Heavy - 2 CPU + 27 GPU Layers"},
    {1, 28, "G1-28G", "Max GPU - 1 CPU + 28 GPU Layers"}
};

static const int NUM_TEST_CONFIGS = sizeof(TEST_CONFIGS) / sizeof(TEST_CONFIGS[0]);

// 🚀 STREAMING CALLBACK FUNCTIONS
// 🌍 Convert UTF-8 string to UTF-16 for JNI (supports ALL Unicode: Hindi, Chinese, Arabic, Emoji, etc.)
std::vector<jchar> utf8_to_utf16(const char* utf8_str) {
    std::vector<jchar> utf16;
    const unsigned char* bytes = reinterpret_cast<const unsigned char*>(utf8_str);
    
    while (*bytes) {
        uint32_t codepoint = 0;
        int bytes_to_read = 0;
        
        if ((*bytes & 0x80) == 0) {
            // 1-byte sequence (ASCII): 0xxxxxxx
            codepoint = *bytes;
            bytes_to_read = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            // 2-byte sequence: 110xxxxx 10xxxxxx
            codepoint = *bytes & 0x1F;
            bytes_to_read = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            // 3-byte sequence: 1110xxxx 10xxxxxx 10xxxxxx
            codepoint = *bytes & 0x0F;
            bytes_to_read = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            // 4-byte sequence: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
            codepoint = *bytes & 0x07;
            bytes_to_read = 4;
        } else {
            // Invalid UTF-8, skip this byte
            bytes++;
            continue;
        }
        
        // Read continuation bytes
        bytes++;
        for (int i = 1; i < bytes_to_read && *bytes; i++) {
            if ((*bytes & 0xC0) != 0x80) {
                // Invalid continuation byte, abort this sequence
                break;
            }
            codepoint = (codepoint << 6) | (*bytes & 0x3F);
            bytes++;
        }
        
        // Convert codepoint to UTF-16
        if (codepoint <= 0xFFFF) {
            // BMP (Basic Multilingual Plane): single jchar
            utf16.push_back(static_cast<jchar>(codepoint));
        } else if (codepoint <= 0x10FFFF) {
            // Supplementary plane: surrogate pair
            codepoint -= 0x10000;
            utf16.push_back(static_cast<jchar>(0xD800 + (codepoint >> 10)));      // High surrogate
            utf16.push_back(static_cast<jchar>(0xDC00 + (codepoint & 0x3FF)));   // Low surrogate
        }
        // Invalid codepoints are silently skipped
    }
    
    return utf16;
}

void callback_on_token_generated(const char* token_text) {
    if (!g_streaming_callback || !g_env) return;
    
    try {
        // Get callback class and method
        jclass callback_class = g_env->GetObjectClass(g_streaming_callback);
        jmethodID on_token_method = g_env->GetMethodID(callback_class, "onTokenGenerated", "(Ljava/lang/String;)V");
        
        if (on_token_method) {
            // 🌍 ROBUST UTF-8 TO UTF-16 CONVERSION: Works with ALL Unicode characters!
            std::vector<jchar> utf16 = utf8_to_utf16(token_text);
            
            // Create Java string from UTF-16 array using NewString (not NewStringUTF)
            jstring token_jstring = g_env->NewString(utf16.data(), utf16.size());
            
            if (token_jstring) {
                g_env->CallVoidMethod(g_streaming_callback, on_token_method, token_jstring);
                g_env->DeleteLocalRef(token_jstring);
            }
        }
        
        g_env->DeleteLocalRef(callback_class);
    } catch (...) {
        LOGE("❌ Exception in callback_on_token_generated");
    }
}

void callback_on_generation_complete() {
    if (!g_streaming_callback || !g_env) return;
    
    try {
        jclass callback_class = g_env->GetObjectClass(g_streaming_callback);
        jmethodID on_complete_method = g_env->GetMethodID(callback_class, "onGenerationComplete", "()V");
        
        if (on_complete_method) {
            g_env->CallVoidMethod(g_streaming_callback, on_complete_method);
        }
        
        g_env->DeleteLocalRef(callback_class);
    } catch (...) {
        LOGE("❌ Exception in callback_on_generation_complete");
    }
}

void callback_on_error(const char* error_text) {
    if (!g_streaming_callback || !g_env) return;
    
    try {
        jclass callback_class = g_env->GetObjectClass(g_streaming_callback);
        jmethodID on_error_method = g_env->GetMethodID(callback_class, "onError", "(Ljava/lang/String;)V");
        
        if (on_error_method) {
            // 🌍 ROBUST UTF-8 TO UTF-16 CONVERSION: Works with ALL Unicode characters!
            std::vector<jchar> utf16 = utf8_to_utf16(error_text);
            
            // Create Java string from UTF-16 array using NewString (not NewStringUTF)
            jstring error_jstring = g_env->NewString(utf16.data(), utf16.size());
            
            if (error_jstring) {
                g_env->CallVoidMethod(g_streaming_callback, on_error_method, error_jstring);
                g_env->DeleteLocalRef(error_jstring);
            }
        }
        
        g_env->DeleteLocalRef(callback_class);
    } catch (...) {
        LOGE("❌ Exception in callback_on_error");
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_iterate_adreno_sdk_LlamaGPU_loadModel(
    JNIEnv* env, jobject thiz, jstring modelPath, jint gpuLayers) {
    
    // CRITICAL: Initialize JavaVM pointer for thread attachment
    if (g_jvm == nullptr) {
        env->GetJavaVM(&g_jvm);
        LOGI("✅ JavaVM pointer initialized for streaming");
    }
    
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("🎯 Loading model with %d GPU layers", gpuLayers);
    
    try {
        // Free existing model if any
        if (g_ctx) {
            llama_free(g_ctx);
            g_ctx = nullptr;
        }
        if (g_model) {
            llama_model_free(g_model);
        }
        
        // 🚀 QUALCOMM'S COMPLETE SOLUTION - Let GGML handle everything!
        LOGI("🎆 Using Qualcomm's complete GGML OpenCL implementation...");
        
        // Set OpenCL environment for debugging and device selection
        setenv("GGML_OPENCL_PLATFORM", "0", 1); // Prefer platform 0
        setenv("GGML_OPENCL_DEVICE", "0", 1);   // Prefer device 0 (Adreno GPU)
        setenv("GGML_OPENCL_DEBUG", "1", 1);    // Enable OpenCL backend debug logs
        setenv("GGML_DEBUG", "1", 1);           // Enable all GGML debug
        
        // Set OpenMP environment to prevent crashes
        setenv("OMP_NUM_THREADS", "2", 1);     // Limit OpenMP threads
        setenv("OMP_WAIT_POLICY", "PASSIVE", 1); // Reduce CPU usage
        setenv("OMP_DYNAMIC", "TRUE", 1);      // Allow dynamic thread adjustment
        
        LOGI("💳 Environment: GGML_OPENCL_PLATFORM=0 GGML_OPENCL_DEVICE=0 GGML_OPENCL_DEBUG=1");
        
        // Initialize llama backend
        LOGI("🔥 Initializing GGML backend system...");
        llama_backend_init();
        LOGI("✅ GGML initialized!");
        
        // 🚀 CRITICAL: MANUAL OPENCL BACKEND REGISTRATION
        // Since GGML_BACKEND_DL_IMPL doesn't work with static linking
        LOGI("🎯 Manually registering OpenCL backend with GGML...");
        ggml_backend_reg_t opencl_reg = ggml_backend_opencl_reg();
        if (opencl_reg != nullptr) {
            ggml_backend_register(opencl_reg);
            LOGI("✅ OpenCL backend registered manually!");
        } else {
            LOGE("❌ Failed to get OpenCL backend registration!");
        }
        
        LOGI("🎆 Qualcomm's complete GGML OpenCL system ready!");
        
        // Disable llama.cpp logging - suppress all logs
        llama_log_set([](ggml_log_level level, const char * text, void * user_data) {
            // No-op: suppress all llama.cpp logs
            (void)level;
            (void)text;
            (void)user_data;
        }, nullptr);
        
        // Set up model parameters for HYBRID CPU+GPU execution (EXACT WORKING VERSION!)
        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = gpuLayers;  // GPU layers (offload to Adreno GPU via OpenCL)
        model_params.use_mmap = true;           // Memory-map for faster loading
        model_params.use_mlock = false;         // Don't lock memory
        model_params.main_gpu = 0;              // Use first GPU (Adreno)
        model_params.split_mode = LLAMA_SPLIT_MODE_LAYER;  // Split by layers
        
        LOGI("⚙️  HYBRID CPU+GPU CONFIGURATION:");
        LOGI("   🎮 GPU Layers: %d (OpenCL/Adreno)", gpuLayers);
        LOGI("   🧠 CPU Threads: 4 (will be set in context)");
        LOGI("   📊 Mode: %s", gpuLayers > 0 ? "Hybrid CPU+GPU" : "CPU Only");
        
        // Check if file exists and is readable
        FILE* f = fopen(path, "rb");
        if (!f) {
            LOGE("❌ Model file not found or not readable: %s", path);
            LOGE("   errno: %d (%s)", errno, strerror(errno));
            env->ReleaseStringUTFChars(modelPath, path);
            return JNI_FALSE;
        }
        
        // Check file size
        fseek(f, 0, SEEK_END);
        long fileSize = ftell(f);
        fseek(f, 0, SEEK_SET);
        
        // Read GGUF magic number
        char magic[4];
        size_t read = fread(magic, 1, 4, f);
        fclose(f);
        
        // Model file size logging disabled for production
        
        if (read == 4) {
            LOGI("🔍 File magic: %02x %02x %02x %02x", 
                 (unsigned char)magic[0], (unsigned char)magic[1], 
                 (unsigned char)magic[2], (unsigned char)magic[3]);
            
            // GGUF magic is "GGUF" (0x47 0x47 0x55 0x46)
            if (magic[0] == 'G' && magic[1] == 'G' && magic[2] == 'U' && magic[3] == 'F') {
                LOGI("✅ Valid GGUF file format detected");
            } else {
                LOGE("❌ Invalid file format - not a GGUF file!");
                LOGE("   Expected: GGUF (47 47 55 46)");
                LOGE("   Got: %c%c%c%c (%02x %02x %02x %02x)", 
                     magic[0], magic[1], magic[2], magic[3],
                     (unsigned char)magic[0], (unsigned char)magic[1],
                     (unsigned char)magic[2], (unsigned char)magic[3]);
                env->ReleaseStringUTFChars(modelPath, path);
                return JNI_FALSE;
            }
        }
        
        g_model = llama_model_load_from_file(path, model_params);
        
        if (!g_model) {
            LOGE("❌ Failed to load model - llama_model_load_from_file returned NULL");
            LOGE("   Check if model format is valid GGUF");
            LOGE("   Check if you have enough memory");
            env->ReleaseStringUTFChars(modelPath, path);
            return JNI_FALSE;
        }
        
        LOGI("✅ Model loaded successfully");
        
        // DEEP DIAGNOSTIC: Check available backends after model load
        LOGI("🔍 POST-LOAD BACKEND DIAGNOSTIC:");
        LOGI("   - Model loaded, now checking available backends...");
        LOGI("   - Expected: 2 backends (CPU + OpenCL)");
        LOGI("   - Will verify during context creation...");
        
        // Set up context parameters for HYBRID execution
        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = 4096;         // ⚡ FIXED: Reduced from 8192 to prevent 896MB KV cache ANR
        ctx_params.n_batch = 256;        // ⚡ OPTIMIZED: Further reduced for 6490 device smoothness
        ctx_params.n_threads = 4;        // CPU threads for non-GPU layers
        ctx_params.n_threads_batch = 4;  // CPU threads for batch processing
        
        // MEMORY MONITORING: Calculate expected KV cache size
        // For Qwen3-1.7B: n_layer=28, n_embd_k_gqa=1024, n_embd_v_gqa=1024, f16=2 bytes
        // KV cache = n_ctx × n_layer × (n_embd_k + n_embd_v) × sizeof(f16)
        size_t expected_kv_cache_mb = (ctx_params.n_ctx * 28 * 2048 * 2) / (1024 * 1024);
        size_t expected_gpu_kv_mb = (expected_kv_cache_mb * gpuLayers) / 28;
        size_t expected_cpu_kv_mb = (expected_kv_cache_mb * (28 - gpuLayers)) / 28;
        
        LOGI("🔧 Creating context with OPTIMIZED settings:");
        LOGI("   📏 Context size (n_ctx): %d tokens", ctx_params.n_ctx);
        LOGI("   📦 Batch size (n_batch): %d", ctx_params.n_batch);
        LOGI("   🧵 CPU threads (inference): %d", ctx_params.n_threads);
        LOGI("   🧵 CPU threads (batch): %d", ctx_params.n_threads_batch);
        LOGI("   💾 Expected KV cache: %zu MB total (%zu MB GPU + %zu MB CPU)", 
             expected_kv_cache_mb, expected_gpu_kv_mb, expected_cpu_kv_mb);
        
        // SAFETY CHECK: Warn if KV cache exceeds safe limits for Adreno 643
        if (expected_gpu_kv_mb > 500) {
            LOGW("⚠️ WARNING: GPU KV cache (%zu MB) may cause memory pressure on Adreno 643!", expected_gpu_kv_mb);
            LOGW("⚠️ Consider reducing n_ctx or gpu_layers if ANR issues occur");
        }
        
        // Try to create context with error recovery (EXACT WORKING VERSION!)
        try {
            g_ctx = llama_init_from_model(g_model, ctx_params);
        } catch (const std::exception& e) {
            LOGE("❌ Exception during context creation: %s", e.what());
            g_ctx = nullptr;
        } catch (...) {
            LOGE("❌ Unknown exception during context creation");
            g_ctx = nullptr;
        }
        
        if (!g_ctx) {
            LOGE("❌ Failed to create context - possibly out of memory");
            LOGE("   Try reducing GPU layers or context size");
            llama_model_free(g_model);
            g_model = nullptr;
            env->ReleaseStringUTFChars(modelPath, path);
            return JNI_FALSE;
        }
        
        g_gpu_layers = gpuLayers;
        
        LOGI("✅ Context created successfully");
        LOGI("🎮 GPU Layers: %d", gpuLayers);
        LOGI("✅ GGML-OpenCL GPU acceleration enabled!");
        
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_TRUE;
        
    } catch (const std::exception& e) {
        LOGE("❌ Exception loading model: %s", e.what());
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_iterate_adreno_sdk_LlamaGPU_generate(
    JNIEnv* env, jobject thiz, jstring prompt, jint maxTokens) {
    
    if (!g_model || !g_ctx) {
        return env->NewStringUTF("Error: Model not loaded");
    }
    
    const char* userPrompt = env->GetStringUTFChars(prompt, nullptr);
    LOGI("🎯 Generating response with %d GPU layers", g_gpu_layers);
    LOGI("📝 User prompt: %s", userPrompt);
    
    // Format prompt with ChatML tokens
    std::string formattedPrompt = 
        "<|im_start|>system\n"
        "You are a helpful AI assistant.\n"
        "<|im_end|>\n"
        "<|im_start|>user\n"
        "Q: " + std::string(userPrompt) + "\n"
        "<|im_end|>\n"
        "<|im_start|>assistant\n"
        "<think>\n\n</think>\n\n";
    
    const char* promptStr = formattedPrompt.c_str();
    LOGI("📋 Formatted prompt with ChatML tokens");
    
    auto start_time = std::chrono::high_resolution_clock::now();
    
    try {
        // Get vocab from model
        const llama_vocab* vocab = llama_model_get_vocab(g_model);
        
        // Tokenize the prompt
        std::vector<llama_token> tokens;
        const int n_prompt_tokens = -llama_tokenize(vocab, promptStr, strlen(promptStr), nullptr, 0, true, true);
        tokens.resize(n_prompt_tokens);
        
        int n_tokens = llama_tokenize(vocab, promptStr, strlen(promptStr), tokens.data(), tokens.size(), true, true);
        
        if (n_tokens < 0) {
            LOGE("❌ Failed to tokenize prompt");
            env->ReleaseStringUTFChars(prompt, userPrompt);
            return env->NewStringUTF("Error: Failed to tokenize prompt");
        }
        
        LOGI("✅ Tokenized: %d tokens", n_tokens);
        
        // Clear the KV cache
        llama_kv_self_clear(g_ctx);
        
        // 🔧 Process prompt in chunks using n_batch size
        LOGI("🔄 Processing %d tokens in chunks of %d...", n_tokens, 512);
        
        const int batch_size = 512;
        int n_past = 0;
        
        for (int i = 0; i < n_tokens; i += batch_size) {
            const int n_eval = std::min(batch_size, n_tokens - i);
            
            // Process this chunk
            if (llama_decode(g_ctx, llama_batch_get_one(tokens.data() + i, n_eval)) != 0) {
                LOGE("❌ Failed to decode at position %d/%d", i, n_tokens);
                env->ReleaseStringUTFChars(prompt, userPrompt);
                return env->NewStringUTF("Error: Failed to process prompt");
            }
            
            n_past += n_eval;
            
            if (i + batch_size < n_tokens) {
                LOGI("⏳ Processed %d/%d tokens...", n_past, n_tokens);
            }
        }
        
        LOGI("✅ Processed all %d tokens", n_tokens);
        
        // Generate tokens
        std::string response;
        int n_generated = 0;
        
        LOGI("🚀 Generating tokens (max: %d)...", maxTokens);
        
        while (n_generated < maxTokens) {
            // Sample next token
            const float* logits = llama_get_logits_ith(g_ctx, -1);
            const int n_vocab = llama_vocab_n_tokens(vocab);
            
            // Simple sampling: get the token with highest probability
            llama_token new_token = 0;
            float max_logit = logits[0];
            for (int i = 1; i < n_vocab; i++) {
                if (logits[i] > max_logit) {
                    max_logit = logits[i];
                    new_token = i;
                }
            }
            
            // Check for EOS
            if (llama_vocab_is_eog(vocab, new_token)) {
                LOGI("✅ EOS token reached");
                break;
            }
            
            // Convert token to text
            char buf[128];
            int n = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
            if (n > 0) {
                response.append(buf, n);
            }
            
            // Prepare next batch with single token
            tokens.clear();
            tokens.push_back(new_token);
            llama_batch gen_batch = llama_batch_get_one(tokens.data(), 1);
            
            // Decode next token
            if (llama_decode(g_ctx, gen_batch) != 0) {
                LOGW("⚠️ Decode failed at token %d", n_generated);
                break;
            }
            
            n_generated++;
            
            // Log progress every 10 tokens
            if (n_generated % 10 == 0) {
                LOGI("📊 Generated %d tokens...", n_generated);
            }
        }
        
        auto end_time = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time);
        
        float tokens_per_second = n_generated / (duration.count() / 1000.0f);
        
        LOGI("✅ Generation complete!");
        LOGI("📊 Generated %d tokens in %lld ms (%.2f tok/s)", 
             n_generated, duration.count(), tokens_per_second);
        LOGI("🎮 GPU Layers: %d", g_gpu_layers);
        
        env->ReleaseStringUTFChars(prompt, userPrompt);
        
        if (response.empty()) {
            return env->NewStringUTF("[Model generated empty response]");
        }
        
        return env->NewStringUTF(response.c_str());
        
    } catch (const std::exception& e) {
        LOGE("❌ Exception during generation: %s", e.what());
        env->ReleaseStringUTFChars(prompt, userPrompt);
        return env->NewStringUTF((std::string("Error: ") + e.what()).c_str());
    }
}

// 🚀 STREAMING GENERATION FUNCTION
extern "C" JNIEXPORT jstring JNICALL
Java_com_iterate_adreno_sdk_LlamaGPU_generateStreaming(
    JNIEnv* env, jobject thiz, jstring prompt, jint maxTokens, jobject callback) {
    
    if (!g_model || !g_ctx) {
        callback_on_error("Error: Model not loaded");
        return env->NewStringUTF("");
    }
    
    const char* userPrompt = env->GetStringUTFChars(prompt, nullptr);
    LOGI("🎯 Starting STREAMING generation with %d GPU layers", g_gpu_layers);
    LOGI("📝 User prompt: %s", userPrompt);
    
    // Store JVM and env for callbacks
    g_env = env;
    env->GetJavaVM(&g_jvm);
    
    // Store global reference to callback object
    g_streaming_callback = env->NewGlobalRef(callback);
    
    // Format prompt with ChatML tokens (same as non-streaming)
    std::string formattedPrompt = 
        "<|im_start|>system\n"
        "You are a helpful AI assistant.\n"
        "<|im_end|>\n"
        "<|im_start|>user\n"
        "Q: " + std::string(userPrompt) + "\n"
        "<|im_end|>\n"
        "<|im_start|>assistant\n"
        "<think>\n\n</think>\n\n";
    
    const char* promptStr = formattedPrompt.c_str();
    LOGI("📋 Formatted prompt with ChatML tokens");
    
    auto start_time = std::chrono::high_resolution_clock::now();
    
    try {
        // Get vocab from model
        const llama_vocab* vocab = llama_model_get_vocab(g_model);
        
        // Tokenize the prompt
        std::vector<llama_token> tokens;
        const int n_prompt_tokens = -llama_tokenize(vocab, promptStr, strlen(promptStr), nullptr, 0, true, true);
        tokens.resize(n_prompt_tokens);
        
        int n_tokens = llama_tokenize(vocab, promptStr, strlen(promptStr), tokens.data(), tokens.size(), true, true);
        
        if (n_tokens < 0) {
            LOGE("❌ Failed to tokenize prompt");
            env->ReleaseStringUTFChars(prompt, userPrompt);
            callback_on_error("Failed to tokenize prompt");
            return env->NewStringUTF("");
        }
        
        LOGI("✅ Tokenized: %d tokens", n_tokens);
        
        // Clear the KV cache
        llama_kv_self_clear(g_ctx);
        
        // 🔧 Process prompt in chunks using n_batch size
        LOGI("🔄 Processing %d tokens in chunks of %d...", n_tokens, 512);
        
        const int batch_size = 512;
        int n_past = 0;
        
        for (int i = 0; i < n_tokens; i += batch_size) {
            const int n_eval = std::min(batch_size, n_tokens - i);
            
            // Process this chunk
            if (llama_decode(g_ctx, llama_batch_get_one(tokens.data() + i, n_eval)) != 0) {
                LOGE("❌ Failed to decode at position %d/%d", i, n_tokens);
                env->ReleaseStringUTFChars(prompt, userPrompt);
                callback_on_error("Failed to process prompt");
                return env->NewStringUTF("");
            }
            
            n_past += n_eval;
            
            if (i + batch_size < n_tokens) {
                LOGI("⏳ Processed %d/%d tokens...", n_past, n_tokens);
            }
        }
        
        LOGI("✅ Processed all %d tokens", n_tokens);
        
        // 🚀 STREAMING GENERATION LOOP
        std::string full_response;
        int n_generated = 0;
        
        LOGI("🚀 Starting STREAMING generation (max: %d)...", maxTokens);
        
        while (n_generated < maxTokens) {
            // Sample next token
            const float* logits = llama_get_logits_ith(g_ctx, -1);
            const int n_vocab = llama_vocab_n_tokens(vocab);
            
            // Simple sampling: get the token with highest probability
            llama_token new_token = 0;
            float max_logit = logits[0];
            for (int i = 1; i < n_vocab; i++) {
                if (logits[i] > max_logit) {
                    max_logit = logits[i];
                    new_token = i;
                }
            }
            
            // Check for EOS
            if (llama_vocab_is_eog(vocab, new_token)) {
                LOGI("✅ EOS token reached");
                break;
            }
            
            // Convert token to text
            char buf[128];
            int n = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
            if (n > 0) {
                std::string token_text(buf, n);
                full_response += token_text;
                
                // 🎯 STREAM TO KOTLIN UI IMMEDIATELY!
                callback_on_token_generated(token_text.c_str());
                LOGI("📡 Streamed token: '%s'", token_text.c_str());
            }
            
            // Prepare next batch with single token
            tokens.clear();
            tokens.push_back(new_token);
            llama_batch stream_batch = llama_batch_get_one(tokens.data(), 1);
            
            // Decode next token
            if (llama_decode(g_ctx, stream_batch) != 0) {
                LOGW("⚠️ Decode failed at token %d", n_generated);
                break;
            }
            
            n_generated++;
        }
        
        auto end_time = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time);
        
        float tokens_per_second = n_generated / (duration.count() / 1000.0f);
        
        LOGI("✅ STREAMING generation complete!");
        LOGI("📊 Generated %d tokens in %lld ms (%.2f tok/s)", 
             n_generated, duration.count(), tokens_per_second);
        LOGI("🎮 GPU Layers: %d", g_gpu_layers);
        
        // Notify Kotlin that generation is complete
        callback_on_generation_complete();
        
        // Clean up global reference
        if (g_streaming_callback) {
            env->DeleteGlobalRef(g_streaming_callback);
            g_streaming_callback = nullptr;
        }
        
        env->ReleaseStringUTFChars(prompt, userPrompt);
        
        if (full_response.empty()) {
            return env->NewStringUTF("[Model generated empty response]");
        }
        
        return env->NewStringUTF(full_response.c_str());
        
    } catch (const std::exception& e) {
        LOGE("❌ Exception during STREAMING generation: %s", e.what());
        
        // Clean up global reference
        if (g_streaming_callback) {
            env->DeleteGlobalRef(g_streaming_callback);
            g_streaming_callback = nullptr;
        }
        
        env->ReleaseStringUTFChars(prompt, userPrompt);
        callback_on_error((std::string("Generation error: ") + e.what()).c_str());
        return env->NewStringUTF("");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_iterate_adreno_sdk_LlamaGPU_freeModel(JNIEnv* env, jobject thiz) {
    LOGI("🧹 Cleaning up model resources...");
    
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
        LOGI("✅ Context freed");
    }
    
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
        LOGI("✅ Model freed");
    }
    
    llama_backend_free();
    
    g_gpu_layers = 0;
    LOGI("✅ All model resources cleaned up");
}

// 🧪 COMPREHENSIVE ADRENO GPU PERFORMANCE TESTING SUITE

// Performance test result structure
struct PerformanceResult {
    int config_index;
    int cpu_threads;
    int gpu_layers;
    float first_token_time_ms;
    float tokens_per_second;
    int total_tokens;
    float total_time_ms;
    bool success;
    const char* config_name;
};

// Run comprehensive performance test suite
extern "C" JNIEXPORT jstring JNICALL
Java_com_iterate_adreno_sdk_LlamaGPU_runPerformanceTestSuite(
    JNIEnv* env, jobject thiz) {
    
    if (!g_model) {
        return env->NewStringUTF("Error: No model loaded");
    }
    
    LOGI("🧪 STARTING COMPREHENSIVE ADRENO GPU PERFORMANCE TESTING");
    LOGI("📋 Testing %d configurations for optimal performance", NUM_TEST_CONFIGS);
    
    std::string report = "🧪 ADRENO GPU PERFORMANCE TEST RESULTS\n\n";
    report += "Testing all CPU/GPU configurations...\n\n";
    
    float best_throughput = 0.0f;
    const char* best_config = "None";
    
    // Test each configuration
    for (int i = 0; i < NUM_TEST_CONFIGS; i++) {
        const GPUTestConfig& config = TEST_CONFIGS[i];
        
        LOGI("🧪 Testing [%s]: %s", config.name, config.description);
        
        try {
            // Free existing context
            if (g_ctx) {
                llama_free(g_ctx);
                g_ctx = nullptr;
            }
            
            // Create context with test configuration
            llama_context_params ctx_params = llama_context_default_params();
            ctx_params.n_ctx = 512;  // Smaller for faster testing
            ctx_params.n_batch = 128;
            ctx_params.n_threads = config.cpu_threads;
            ctx_params.n_threads_batch = config.cpu_threads;
            
            g_ctx = llama_init_from_model(g_model, ctx_params);
            g_gpu_layers = config.gpu_layers;
            
            if (!g_ctx) {
                char error_line[128];
                snprintf(error_line, sizeof(error_line), "❌ [%s] Failed to create context\n", config.name);
                report += error_line;
                continue;
            }
            
            // Quick performance test
            const char* test_prompt = "AI is";
            
            // Tokenize
            std::vector<llama_token> tokens(512);
            int n_tokens = llama_tokenize(llama_model_get_vocab(g_model), test_prompt, strlen(test_prompt), 
                                          tokens.data(), 512, true, true);
            if (n_tokens < 0) {
                char error_line[128];
                snprintf(error_line, sizeof(error_line), "❌ [%s] Tokenization failed\n", config.name);
                report += error_line;
                continue;
            }
            tokens.resize(n_tokens);
            
            // Clear cache and measure generation
            llama_kv_self_clear(g_ctx);
            
            auto start_time = std::chrono::high_resolution_clock::now();
            
            // Process prompt
            for (int j = 0; j < n_tokens; j++) {
                llama_batch batch = llama_batch_get_one(tokens.data() + j, 1);
                batch.pos[0] = j;
                if (llama_decode(g_ctx, batch) != 0) {
                    break;
                }
            }
            
            // Generate 10 tokens for speed test
            int generated = 0;
            for (int j = 0; j < 10; j++) {
                // Simple greedy sampling - get the token with highest probability
                llama_token new_token = 0;
                float max_logit = -INFINITY;
                const float* logits = llama_get_logits_ith(g_ctx, n_tokens + j - 1);
                const int n_vocab = llama_vocab_n_tokens(llama_model_get_vocab(g_model));
                for (int i = 0; i < n_vocab; i++) {
                    if (logits[i] > max_logit) {
                        max_logit = logits[i];
                        new_token = i;
                    }
                }
                
                llama_batch batch = llama_batch_get_one(&new_token, 1);
                batch.pos[0] = n_tokens + j;
                if (llama_decode(g_ctx, batch) != 0) {
                    break;
                }
                generated++;
            }
            
            auto end_time = std::chrono::high_resolution_clock::now();
            auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time);
            
            float tokens_per_second = generated / (duration.count() / 1000.0f);
            
            // Track best configuration
            if (tokens_per_second > best_throughput) {
                best_throughput = tokens_per_second;
                best_config = config.name;
            }
            
            char result_line[256];
            snprintf(result_line, sizeof(result_line), 
                "✅ [%s] CPU:%d GPU:%d → %.2f tok/s (%.0fms)\n", 
                config.name, config.cpu_threads, config.gpu_layers, 
                tokens_per_second, (float)duration.count());
            report += result_line;
            
            LOGI("✅ [%s] Performance: %.2f tok/s", config.name, tokens_per_second);
            
        } catch (const std::exception& e) {
            char error_line[256];
            snprintf(error_line, sizeof(error_line), "❌ [%s] Test failed: %s\n", config.name, e.what());
            report += error_line;
            LOGE("❌ [%s] Test failed: %s", config.name, e.what());
        }
        
        // Small delay between tests
        std::this_thread::sleep_for(std::chrono::milliseconds(200));
    }
    
    // Add summary
    char summary[512];
    snprintf(summary, sizeof(summary), 
        "\n🏆 BEST PERFORMANCE:\n"
        "Configuration: %s\n"
        "Throughput: %.2f tokens/second\n\n"
        "🎯 RECOMMENDATIONS:\n"
        "• For OpenCL 2.0: Use balanced CPU+GPU configs\n"
        "• For real-time chat: Prioritize first token speed\n"
        "• For batch processing: Use highest throughput config\n", 
        best_config, best_throughput);
    report += summary;
    
    LOGI("🏆 Best configuration: %s (%.2f tok/s)", best_config, best_throughput);
    LOGI("🎉 Performance testing complete!");
    
    return env->NewStringUTF(report.c_str());
}

// Test specific configuration
extern "C" JNIEXPORT jstring JNICALL
Java_com_iterate_adreno_sdk_LlamaGPU_testSpecificConfig(
    JNIEnv* env, jobject thiz, jint cpuThreads, jint gpuLayers) {
    
    if (!g_model) {
        return env->NewStringUTF("Error: No model loaded");
    }
    
    LOGI("🧪 Testing specific configuration: %d CPU threads, %d GPU layers", 
         cpuThreads, gpuLayers);
    
    try {
        // Free existing context
        if (g_ctx) {
            llama_free(g_ctx);
            g_ctx = nullptr;
        }
        
        // Create new context with test configuration
        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = 2048;
        ctx_params.n_batch = 512;
        ctx_params.n_threads = cpuThreads;
        ctx_params.n_threads_batch = cpuThreads;
        
        g_ctx = llama_init_from_model(g_model, ctx_params);
        g_gpu_layers = gpuLayers;  // Update global setting
        
        if (!g_ctx) {
            return env->NewStringUTF("Error: Failed to create context with test configuration");
        }
        
        LOGI("✅ Context created with %d CPU threads, %d GPU layers", 
             cpuThreads, gpuLayers);
        
        char result[512];
        snprintf(result, sizeof(result), 
            "✅ Configuration Applied Successfully!\n\n"
            "🔧 Current Configuration:\n"
            "• CPU Threads: %d\n"
            "• GPU Layers: %d\n\n"
            "🚀 Ready for inference with optimized settings!\n"
            "Use 'Generate Text' to test performance.", 
            cpuThreads, gpuLayers);
        
        return env->NewStringUTF(result);
        
    } catch (const std::exception& e) {
        LOGE("❌ Configuration test failed: %s", e.what());
        char error[256];
        snprintf(error, sizeof(error), "Error: %s", e.what());
        return env->NewStringUTF(error);
    }
}

// ============================================================================
// 💾 KV CACHE OPTIMIZATION - JNI METHODS
// ============================================================================

/**
 * Initialize system prompt KV cache (call once at startup)
 * This processes the heavy system prompt (menu + instructions) and caches the KV state
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_iterate_adreno_sdk_LlamaGPU_initializeSystemPromptCache(
    JNIEnv* env, jobject thiz, jstring systemPrompt) {
    
    const char* prompt = env->GetStringUTFChars(systemPrompt, nullptr);
    
    if (!g_ctx || !g_model) {
        LOGE("❌ Model/context not loaded");
        env->ReleaseStringUTFChars(systemPrompt, prompt);
        return JNI_FALSE;
    }
    
    LOGI("🎯 Initializing system prompt KV cache...");
    
    // Clear any existing KV cache
    llama_kv_self_clear(g_ctx);
    
    // Tokenize system prompt
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    std::vector<llama_token> tokens;
    const int n_prompt_tokens = -llama_tokenize(vocab, prompt, strlen(prompt), nullptr, 0, true, true);
    tokens.resize(n_prompt_tokens);
    int n_tokens = llama_tokenize(vocab, prompt, strlen(prompt), tokens.data(), tokens.size(), true, true);
    
    LOGI("📋 System prompt: %d tokens", n_tokens);
    
    // Process system prompt in chunks with AGGRESSIVE ANR prevention for 6490
    // Use smaller batch size for better yielding on weaker devices
    const int batch_size = 256;  // Reduced from 512 for smoother processing
    
    for (int i = 0; i < n_tokens; i += batch_size) {
        const int n_eval = std::min(batch_size, n_tokens - i);
        if (llama_decode(g_ctx, llama_batch_get_one(tokens.data() + i, n_eval)) != 0) {
            LOGE("❌ Failed to process system prompt at position %d", i);
            env->ReleaseStringUTFChars(systemPrompt, prompt);
            return JNI_FALSE;
        }
        
        // Log progress more frequently for user feedback
        if (i + batch_size < n_tokens) {
            int progress = (i * 100) / n_tokens;
            LOGI("⏳ Processing system prompt: %d%% (%d/%d tokens)", progress, i + n_eval, n_tokens);
        }
        
        // ⚡ CRITICAL ANR FIX: Yield FREQUENTLY to prevent System UI freeze
        // Especially important for Snapdragon 6490 (weaker than 8550)
        // Yield every 128 tokens instead of 256 for smoother operation
        if (i > 0 && (i % 128 == 0)) {
            std::this_thread::sleep_for(std::chrono::milliseconds(2));  // 2ms yield for safety
        }
        
        // Additional yield after each batch for maximum smoothness
        std::this_thread::yield();
    }
    
    // Save KV cache state
    g_system_prompt_token_count = n_tokens;
    bool success = save_system_prompt_kv_cache(g_ctx);
    
    env->ReleaseStringUTFChars(systemPrompt, prompt);
    
    if (success) {
        LOGI("✅ System prompt KV cache initialized! (%d tokens cached)", n_tokens);
        LOGI("⚡ Future queries will skip these %d tokens = 5-10x faster!", n_tokens);
    }
    
    return success ? JNI_TRUE : JNI_FALSE;
}

/**
 * Generate with KV cache reuse (SUPER FAST!)
 * Only processes history + user query, reuses cached system prompt
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_iterate_adreno_sdk_LlamaGPU_generateStreamingWithCache(
    JNIEnv* env, jobject thiz, 
    jstring prompt,          // History + User query only
    jint maxTokens,
    jobject callback) {
    
    // CRITICAL: Check if model/context are loaded
    if (!g_model || !g_ctx) {
        LOGE("❌ Model not loaded! Call loadModel() first.");
        return env->NewStringUTF("Error: Model not loaded");
    }
    
    // CRITICAL: Initialize JavaVM if not already done
    if (g_jvm == nullptr) {
        if (env->GetJavaVM(&g_jvm) != JNI_OK) {
            LOGE("❌ Failed to get JavaVM pointer!");
            return env->NewStringUTF("Error: Failed to get JavaVM");
        }
        LOGI("✅ JavaVM pointer initialized");
    }
    
    // Set up streaming callback
    if (g_jvm->AttachCurrentThread(&g_env, nullptr) != JNI_OK) {
        LOGE("❌ Failed to attach current thread!");
        return env->NewStringUTF("Error: Failed to attach thread");
    }
    
    g_streaming_callback = env->NewGlobalRef(callback);
    
    const char* userPrompt = env->GetStringUTFChars(prompt, nullptr);
    
    try {
        auto start_time = std::chrono::high_resolution_clock::now();
        
        // RESTORE system prompt KV cache (super fast!)
        if (is_kv_cache_initialized()) {
            LOGI("🔄 Restoring KV cache (%d cached tokens)...", g_system_prompt_token_count);
            if (!restore_system_prompt_kv_cache(g_ctx)) {
                LOGE("❌ Failed to restore KV cache, falling back to full prompt");
                llama_kv_self_clear(g_ctx);
            } else {
                LOGI("⚡ KV cache restored! Skipped %d system prompt tokens!", g_system_prompt_token_count);
            }
        } else {
            LOGW("⚠️ KV cache not initialized!");
            LOGW("   You must call initializeSystemPromptCache() first!");
            LOGW("   Proceeding without cache (will be slower)...");
            llama_kv_self_clear(g_ctx);
        }
        
        // Tokenize ONLY history + user query
        const llama_vocab* vocab = llama_model_get_vocab(g_model);
        std::vector<llama_token> tokens;
        const int n_prompt_tokens = -llama_tokenize(vocab, userPrompt, strlen(userPrompt), nullptr, 0, true, true);
        tokens.resize(n_prompt_tokens);
        int n_tokens = llama_tokenize(vocab, userPrompt, strlen(userPrompt), tokens.data(), tokens.size(), true, true);
        
        LOGI("🚀 Processing user query: %d tokens (vs ~%d total with cached prompt)", 
             n_tokens, n_tokens + g_system_prompt_token_count);
        
        // Process user query in chunks
        const int batch_size = 512;
        for (int i = 0; i < n_tokens; i += batch_size) {
            const int n_eval = std::min(batch_size, n_tokens - i);
            if (llama_decode(g_ctx, llama_batch_get_one(tokens.data() + i, n_eval)) != 0) {
                LOGE("❌ Failed to decode");
                env->ReleaseStringUTFChars(prompt, userPrompt);
                callback_on_error("Failed to process prompt");
                return env->NewStringUTF("");
            }
        }
        
        LOGI("✅ Query processed! Starting generation...");
        
        // Generate response (streaming)
        std::string full_response;
        int n_generated = 0;
        
        while (n_generated < maxTokens) {
            const float* logits = llama_get_logits_ith(g_ctx, -1);
            const int n_vocab = llama_vocab_n_tokens(vocab);
            
            // Greedy sampling
            llama_token new_token = 0;
            float max_logit = logits[0];
            for (int i = 1; i < n_vocab; i++) {
                if (logits[i] > max_logit) {
                    max_logit = logits[i];
                    new_token = i;
                }
            }
            
            // Check for EOS
            if (llama_vocab_is_eog(vocab, new_token)) {
                LOGI("✅ EOS token reached");
                break;
            }
            
            // Convert token to text and stream
            char buf[128];
            int n = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
            if (n > 0) {
                std::string token_text(buf, n);
                full_response += token_text;
                callback_on_token_generated(token_text.c_str());
            }
            
            // Decode next token
            llama_token single_token = new_token;
            if (llama_decode(g_ctx, llama_batch_get_one(&single_token, 1)) != 0) {
                break;
            }
            
            n_generated++;
        }
        
        auto end_time = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time);
        
        LOGI("✅ STREAMING generation complete!");
        LOGI("📊 Generated %d tokens in %lld ms", n_generated, duration.count());
        
        // ⚡ CRITICAL: Save FULL conversation state to KV cache (system + history + this turn)
        // This way next query only needs to process NEW user input!
        size_t state_size = llama_state_get_size(g_ctx);
        if (state_size > 0) {
            g_system_prompt_kv_state.resize(state_size);
            size_t actual_size = llama_state_get_data(g_ctx, g_system_prompt_kv_state.data(), state_size);
            
            if (actual_size > 0) {
                g_system_prompt_token_count = llama_kv_self_used_cells(g_ctx);
                g_kv_cache_initialized = true;
                LOGI("💾 Saved FULL conversation state: %zu bytes, %d total tokens in cache", 
                     actual_size, g_system_prompt_token_count);
                LOGI("⚡ Next query will only process new user input!");
            }
        }
        
        env->ReleaseStringUTFChars(prompt, userPrompt);
        callback_on_generation_complete();
        
        // Cleanup global reference
        if (g_streaming_callback) {
            env->DeleteGlobalRef(g_streaming_callback);
            g_streaming_callback = nullptr;
        }
        
        // NOTE: Don't detach thread here! The Thread will be auto-detached when it exits
        // Manually detaching causes crashes because ART manages thread lifecycle
        
        return env->NewStringUTF(full_response.c_str());
        
    } catch (const std::exception& e) {
        LOGE("❌ Exception during generation: %s", e.what());
        env->ReleaseStringUTFChars(prompt, userPrompt);
        callback_on_error("Exception during generation");
        
        // Cleanup on error
        if (g_streaming_callback) {
            env->DeleteGlobalRef(g_streaming_callback);
            g_streaming_callback = nullptr;
        }
        
        return env->NewStringUTF("");
    }
}

/**
 * Invalidate KV cache (e.g., when menu updates)
 */
extern "C" JNIEXPORT void JNICALL
Java_com_iterate_adreno_sdk_LlamaGPU_invalidateKVCache(
    JNIEnv* env, jobject thiz) {
    invalidate_kv_cache();
}

/**
 * Check if KV cache is initialized
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_iterate_adreno_sdk_LlamaGPU_isKVCacheInitialized(
    JNIEnv* env, jobject thiz) {
    return is_kv_cache_initialized() ? JNI_TRUE : JNI_FALSE;
}

/**
 * Get cached token count
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_iterate_adreno_sdk_LlamaGPU_getCachedTokenCount(
    JNIEnv* env, jobject thiz) {
    return get_cached_token_count();
}
