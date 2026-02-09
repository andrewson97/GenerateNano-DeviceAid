#include <jni.h>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <string>
#include <vector>

#define LOG_TAG "LlamaGPU"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Real llama.cpp headers - include the actual API
#include "llama.h"
#include "common.h"

// Global state for model and context
struct llama_model* g_model = nullptr;
struct llama_context* g_ctx = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_hello_1world_1cl_LlamaGPU_loadModel(
    JNIEnv* env, jobject thiz, jstring modelPath, jint gpuLayers) {
    
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("🎯 Loading model with %d GPU layers: %s", gpuLayers, path);
    
    try {
        // Initialize llama backend
        llama_backend_init();
        
        // Set up model parameters
        llama_model_params model_params = llama_model_default_params();
        
        // Load model
        g_model = llama_load_model_from_file(path, model_params);
        
        if (!g_model) {
            LOGE("❌ Failed to load model from: %s", path);
            env->ReleaseStringUTFChars(modelPath, path);
            return JNI_FALSE;
        }
        
        // Set up context parameters with GPU layers
        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = 2048;
        ctx_params.n_gpu_layers = gpuLayers;  // 🚀 REAL GPU ACCELERATION!
        
        // Create context
        g_ctx = llama_new_context_with_model(g_model, ctx_params);
        
        if (!g_ctx) {
            LOGE("❌ Failed to create context");
            llama_free_model(g_model);
            g_model = nullptr;
            env->ReleaseStringUTFChars(modelPath, path);
            return JNI_FALSE;
        }
        
        env->ReleaseStringUTFChars(modelPath, path);
        LOGI("✅ Model loaded with %d GPU layers!", gpuLayers);
        return JNI_TRUE;
        
    } catch (...) {
        LOGE("❌ Exception loading model");
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_hello_1world_1cl_LlamaGPU_generate(
    JNIEnv* env, jobject thiz, jstring prompt, jint maxTokens) {
    
    if (!g_model || !g_ctx) {
        return env->NewStringUTF("Error: Model not loaded");
    }
    
    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    LOGI("🎯 Generating with real GPU acceleration...");
    
    try {
        // Tokenize the prompt using model
        std::vector<llama_token> tokens_list = llama_tokenize(g_model, promptStr, true, true);
        
        if (tokens_list.empty()) {
            env->ReleaseStringUTFChars(prompt, promptStr);
            return env->NewStringUTF("Error: Failed to tokenize prompt");
        }
        
        // Clear the context using newer API
        llama_kv_cache_clear(g_ctx);
        
        // Process the prompt using correct batch API
        llama_batch batch = llama_batch_get_one(tokens_list.data(), tokens_list.size());
        
        if (llama_decode(g_ctx, batch) != 0) {
            env->ReleaseStringUTFChars(prompt, promptStr);
            return env->NewStringUTF("Error: Failed to decode prompt");
        }
        
        // Generate response
        std::string response;
        int n_gen = 0;
        
        while (n_gen < maxTokens) {
            // Get logits and sample next token (simplified approach)
            float* logits = llama_get_logits(g_ctx);
            llama_token new_token_id = 0;
            
            // Simple greedy sampling - find max logit
            int vocab_size = llama_n_vocab(g_model);
            for (int i = 1; i < vocab_size; i++) {
                if (logits[i] > logits[new_token_id]) {
                    new_token_id = i;
                }
            }
            
            // Check for end of sequence using model
            if (new_token_id == llama_token_eos(g_model)) {
                break;
            }
            
            // Convert token to text using buffer
            char token_buf[256];
            int len = llama_token_to_piece(g_model, new_token_id, token_buf, sizeof(token_buf), 0, true);
            if (len > 0) {
                std::string token_str(token_buf, len);
                response += token_str;
            }
            
            // Process the new token
            llama_batch new_batch = llama_batch_get_one(&new_token_id, 1);
            
            if (llama_decode(g_ctx, new_batch) != 0) {
                LOGE("❌ Failed to decode token %d", n_gen);
                break;
            }
            
            n_gen++;
        }
        
        env->ReleaseStringUTFChars(prompt, promptStr);
        LOGI("✅ Generated %d tokens with GPU acceleration", n_gen);
        
        return env->NewStringUTF(response.c_str());
        
    } catch (...) {
        env->ReleaseStringUTFChars(prompt, promptStr);
        return env->NewStringUTF("Error: Exception during generation");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_hello_1world_1cl_LlamaGPU_freeModel(JNIEnv* env, jobject thiz) {
    LOGI("🧹 Cleaning up LLaMA GPU resources...");
    
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    
    llama_backend_free();
    
    LOGI("✅ LLaMA GPU resources cleaned up");
}
