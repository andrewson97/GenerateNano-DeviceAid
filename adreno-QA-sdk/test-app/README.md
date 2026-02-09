# Device Aid Test App - SDK Integration Guide

Professional Android test application demonstrating the Device Aid SDK capabilities, including RAG (Retrieval-Augmented Generation), voice input, and LLM integration.

---

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Test App Setup](#test-app-setup)
4. [Using the Test App](#using-the-test-app)
5. [SDK Integration Guide](#sdk-integration-guide)
6. [API Reference](#api-reference)
7. [Troubleshooting](#troubleshooting)

---

## Overview

This test application showcases the Device Aid SDK's core features:

- **RAG Document Search**: Semantic search using vector embeddings
- **Voice Input**: Speech-to-text with real-time transcription
- **LLM Integration**: On-device language model responses with context injection
- **Text-to-Speech**: Optional audio output for responses
- **Model Management**: Download, load, and delete LLM models
- **Settings**: Configurable GPU layers and TTS options

### What's Included

- **Adreno_QA_SDK_V1.0.aar**: Core SDK with RAG capabilities and embedding models
- **GWK6490_V1.0.aar**: Whisper SDK for voice input functionality
- **Sample Documents**: 560 KB of Q&A pairs from PONSSE Scorpion Manual
- **Reference Images**: 414 images linked to answers

---

## Prerequisites

### Development Environment

- **Android Studio**: Arctic Fox (2020.3.1) or newer
- **JDK**: Version 17
- **Android SDK**: API Level 26 (minimum) to API Level 34 (target)
- **Gradle**: 8.0 or newer

### Device Requirements

- **Architecture**: arm64-v8a
- **Android Version**: 8.0 (Oreo) or higher
- **Storage**: Minimum 2 GB free space for models
- **RAM**: 4 GB+ recommended for LLM operations

---

## Test App Setup

### 1. Open Project in Android Studio

```bash
cd test-app
```

Open the `test-app` folder in Android Studio.

### 2. Sync Gradle

Android Studio will automatically detect dependencies and sync Gradle. The SDKs are located in:

```
app/libs/
├── Adreno_QA_SDK_V1.0.aar
└── GWK6490_V1.0.aar
```

### 3. Build and Run

**Option A**: Android Studio
- Click **Run** (green play button)
- Select your connected device or emulator

**Option B**: Command Line
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. First Launch Behavior

On first launch, the app will:
1. Build the vector index from documents (2-3 minutes)
2. Generate embeddings for all Q&A pairs
3. Save the index to device storage for future use

Subsequent launches will load the existing index instantly.

---

## Using the Test App

### Main Interface

#### Chat Window
- Displays conversation history
- Shows user queries and assistant responses
- Auto-scrolls to latest messages
- Supports formatted text and inline images

#### Input Controls (Bottom Bar)

1. **Refresh Button** (far left)
   - Clears all chat history
   - Resets conversation context
   - Does not affect stored models or indexes

2. **Text Input Field** (center)
   - Type your questions here
   - Supports multi-line input
   - Auto-expands up to 3 lines

3. **Microphone Button** (inside text field, right)
   - Tap to start/stop voice recording
   - Real-time transcription appears in text field
   - Automatically sends when speech ends

4. **Send Button** (far right)
   - Submits your query
   - Enabled only when text is entered
   - Disabled during response generation

#### Menu Button (Top Right)
Opens settings page with additional options.

### Settings Page

#### Model Management

**Download Model**
- Downloads the LLM model from cloud storage
- Shows download progress
- Disabled (grayed out) when model already exists
- Clicking when disabled shows: "Model already downloaded"

**Load Model**
- Initializes the LLM for on-device inference
- Required before generating responses
- Loads model into memory

**Delete Model**
- Removes the downloaded model file
- Frees up storage space
- Disabled (grayed out) when no model exists
- Clicking when disabled shows: "Model not downloaded"

**Rebuild Index**
- Regenerates the vector index from documents
- Use if index becomes corrupted
- Takes 2-3 minutes to complete

#### Configuration Options

**Text-to-Speech (TTS)**
- Toggle audio output for responses
- When enabled, responses are read aloud
- Controlled via runtime flag (no reinitialization needed)
- Default: OFF

**GPU Layers**
- Adjustable slider (0-27 layers)
- Controls how many model layers run on GPU
- Higher values = faster inference (more GPU memory)
- Lower values = more CPU usage (less GPU memory)
- Recommended: 20-27 for optimal performance
- Requires app reload to apply changes

---

## SDK Integration Guide

This section explains how to integrate the Device Aid SDK into your own Android application.

### Step 1: Add Dependencies

In your app's `build.gradle`:

```gradle
dependencies {
    // Device Aid SDK
    implementation files('libs/Adreno_QA_SDK_V1.0.aar')
    
    // Whisper SDK (optional, for voice input)
    implementation files('libs/GWK6490_V1.0.aar')
    
    // Required dependencies
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.9.0'
}
```

### Step 2: Application Initialization

Create an Application class to initialize the RAG index:

```java
import android.app.Application;
import com.iterate.adreno.sdk.rag.RAGContextManager;
import java.io.File;

public class YourApplication extends Application {
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize RAG index in background
        new Thread(this::initializeRAG).start();
    }
    
    private void initializeRAG() {
        File indexFile = new File(getFilesDir(), "document_embeddings.json");
        
        if (!indexFile.exists()) {
            buildRAGIndex();
        }
    }
    
    private void buildRAGIndex() {
        RAGContextManager ragManager = RAGContextManager.getInstance();
        
        String outputPath = new File(getFilesDir(), "document_embeddings.json")
            .getAbsolutePath();
        
        boolean success = ragManager.buildIndex(
            getApplicationContext(),
            "DOCS/Input_QA.json",          // Your Q&A document
            "models/InterplayGTE.onnx",     // Embedding model
            "models/bert_tokenizer.json",   // Tokenizer
            outputPath
        );
    }
}
```

Register in `AndroidManifest.xml`:

```xml
<application
    android:name=".YourApplication"
    ... >
</application>
```

### Step 3: Initialize RAG in Activity

```java
import com.iterate.adreno.sdk.rag.RAGContextManager;
import com.iterate.adreno.sdk.rag.SearchResult;
import java.io.File;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    
    private RAGContextManager ragManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Get RAG manager instance
        ragManager = RAGContextManager.getInstance();
        
        // Initialize with existing index
        File indexFile = new File(getFilesDir(), "document_embeddings.json");
        String indexPath = indexFile.getAbsolutePath();
        
        boolean success = ragManager.initialize(
            getApplicationContext(),
            "models/InterplayGTE.onnx",
            "models/bert_tokenizer.json",
            indexPath
        );
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ragManager != null) {
            ragManager.close();
        }
    }
}
```

### Step 4: Perform RAG Search

```java
private void searchDocuments(String query) {
    new Thread(() -> {
        // Search for top 5 relevant documents
        List<SearchResult> results = ragManager.search(query, 5);
        
        if (results != null && !results.isEmpty()) {
            SearchResult bestMatch = results.get(0);
            
            // Access result data
            String question = bestMatch.getQuestion();
            String answer = bestMatch.getAnswer();
            float score = bestMatch.getScore();
            List<String> imageRefs = bestMatch.getImageRefs();
            
            // Display answer on UI thread
            runOnUiThread(() -> displayAnswer(answer, imageRefs));
        }
    }).start();
}
```

### Step 5: LLM Integration (Optional)

```java
import com.iterate.adreno.sdk.AdrenoMenuSDK;
import com.iterate.adreno.sdk.LlamaGPU;
import com.iterate.adreno.sdk.SystemPromptManagerEnhanced;

private AdrenoMenuSDK sdk;
private boolean isInitialized = false;

// Initialize LLM
private void initializeLLM() {
    new Thread(() -> {
        try {
            sdk = AdrenoMenuSDK.initialize(
                getApplicationContext(),
                "your_model.gguf",      // Model file name in internal storage
                "You are a helpful assistant.",  // System prompt
                27                       // GPU layers (0-27)
            );
            
            // Enable RAG context injection
            SystemPromptManagerEnhanced.getInstance().enableRAG();
            
            isInitialized = (sdk != null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize LLM: " + e.getMessage());
        }
    }).start();
}

// Generate response with RAG context
private void generateResponse(String query, List<SearchResult> ragResults) {
    if (!isInitialized || sdk == null) {
        // Fallback to direct RAG answer
        displayAnswer(ragResults.get(0).getAnswer());
        return;
    }
    
    new Thread(() -> {
        // Inject RAG context into prompt
        SystemPromptManagerEnhanced promptManager = 
            SystemPromptManagerEnhanced.getInstance();
        
        promptManager.setRAGContext(ragResults);
        sdk.refreshCache();
        
        // Generate streaming response
        sdk.generateResponse(query, 512, new LlamaGPU.StreamingCallback() {
            @Override
            public void onTokenGenerated(String token) {
                runOnUiThread(() -> appendToken(token));
            }
            
            @Override
            public void onGenerationComplete() {
                runOnUiThread(() -> onResponseComplete());
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> showError(error));
            }
        });
    }).start();
}
```

### Step 6: Voice Input Integration (Optional)

```java
import com.iterate.whispersdk.WhisperSDK;
import com.iterate.whispersdk.WhisperSDKCallback;

private WhisperSDK whisperSDK;
private boolean isTTSEnabled = false;  // Control TTS at runtime

private void initializeWhisper() {
    // Build Whisper SDK - always enable TTS capability
    whisperSDK = WhisperSDK.builder(this)
        .enableTTS(true)                       // Always true - control via flag
        .setLanguage("en")                     // Language code
        .setTimeout(30000)                      // Timeout in ms
        .enableContinuous(false)               // Continuous mode
        .setDebugMode(true)
        .build();
    
    // Set callback for transcriptions
    whisperSDK.setCallback(new WhisperSDKCallback() {
        @Override
        public void onTranscriptionReceived(String transcription, boolean isFinal) {
            runOnUiThread(() -> {
                if (isFinal && !transcription.isEmpty()) {
                    // Process final transcription
                    handleQuery(transcription);
                } else if (!isFinal && !transcription.isEmpty()) {
                    // Show partial transcription
                    updateInputField(transcription);
                }
            });
        }
        
        @Override
        public void onRecordingStarted() {
            runOnUiThread(() -> {
                // Update UI to show recording state
                updateMicButtonState(true);
            });
        }
        
        @Override
        public void onRecordingStopped() {
            runOnUiThread(() -> {
                // Update UI to show stopped state
                updateMicButtonState(false);
            });
        }
        
        @Override
        public void onError(String error) {
            runOnUiThread(() -> showError(error));
        }
    });
}

// Start/stop recording
private void toggleRecording() {
    if (whisperSDK.isRecording()) {
        whisperSDK.stopRecording();
    } else {
        whisperSDK.startRecording();
    }
}

// Control TTS at runtime (no reinitialization needed)
private void handleResponse(String response) {
    // Display response
    displayMessage(response);
    
    // Speak response if TTS is enabled
    if (isTTSEnabled && whisperSDK != null && !response.isEmpty()) {
        whisperSDK.speak(response);
    }
}

// Toggle TTS setting
private void setTTSEnabled(boolean enabled) {
    isTTSEnabled = enabled;
    // Stop any ongoing speech if disabling
    if (!enabled && whisperSDK != null && whisperSDK.isSpeaking()) {
        whisperSDK.stopSpeaking();
    }
}

// Cleanup
@Override
protected void onDestroy() {
    super.onDestroy();
    if (whisperSDK != null) {
        whisperSDK.destroy();
    }
}
```

---

## API Reference

### RAGContextManager

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `getInstance()` | - | `RAGContextManager` | Get singleton instance |
| `buildIndex()` | `Context`, `String docsPath`, `String modelPath`, `String tokenizerPath`, `String outputPath` | `boolean` | Build vector index from documents |
| `initialize()` | `Context`, `String modelPath`, `String tokenizerPath`, `String indexPath` | `boolean` | Load existing vector index |
| `search()` | `String query`, `int topK` | `List<SearchResult>` | Search for relevant documents |
| `getFormattedContext()` | `List<SearchResult>` | `String` | Format results for LLM context |
| `close()` | - | `void` | Release resources |

### SearchResult

| Method | Returns | Description |
|--------|---------|-------------|
| `getQuestion()` | `String` | Question from knowledge base |
| `getAnswer()` | `String` | Answer from knowledge base |
| `getScore()` | `float` | Relevance score (0-1) |
| `getImageRefs()` | `List<String>` | List of related image paths |

### AdrenoMenuSDK (LLM)

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `initialize()` (static) | `Context`, `String modelFileName`, `String systemPrompt`, `int gpuLayers` | `AdrenoMenuSDK` | Initialize LLM model (returns SDK instance or null) |
| `generateResponse()` | `String prompt`, `int maxTokens`, `StreamingCallback` | `void` | Generate streaming response |
| `refreshCache()` | - | `void` | Clear KV cache |

### SystemPromptManagerEnhanced

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `getInstance()` | - | `SystemPromptManagerEnhanced` | Get singleton instance |
| `setRAGContext()` | `List<SearchResult>` | `void` | Inject RAG context into prompt |
| `enableRAG()` | - | `void` | Enable RAG mode |

### WhisperSDK

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `builder()` (static) | `Context` | `Builder` | Create SDK builder |
| `enableTTS()` | `boolean enabled` | `Builder` | Enable/disable TTS |
| `setLanguage()` | `String language` | `Builder` | Set language (e.g., "en") |
| `setTimeout()` | `int milliseconds` | `Builder` | Set recording timeout |
| `enableContinuous()` | `boolean enabled` | `Builder` | Enable continuous mode |
| `setDebugMode()` | `boolean enabled` | `Builder` | Enable debug logging |
| `build()` | - | `WhisperSDK` | Build SDK instance |
| `setCallback()` | `WhisperSDKCallback` | `void` | Set transcription callback |
| `startRecording()` | - | `void` | Start voice recording |
| `stopRecording()` | - | `void` | Stop voice recording |
| `isRecording()` | - | `boolean` | Check recording status |
| `destroy()` | - | `void` | Release resources |

---

## Troubleshooting

### RAG Index Build Fails

**Symptoms**: App shows "Failed to build RAG index"

**Solutions**:
- Verify `DOCS/Input_QA.json` exists in `app/src/main/assets/`
- Check device has sufficient storage (need ~100 MB)
- Review logcat for detailed error messages
- Clear app data and rebuild: Settings → Apps → Device Aid → Clear Data

### Model Download Issues

**Symptoms**: Download fails or gets stuck

**Solutions**:
- Check internet connection
- Verify sufficient storage (models are 1-2 GB)
- Try using WiFi instead of mobile data
- Clear app cache and retry
- Check download URL is accessible

### LLM Initialization Fails

**Symptoms**: "Failed to initialize model" error

**Solutions**:
- Ensure model file is completely downloaded
- Verify file is valid GGUF format
- Check device has 4+ GB RAM
- Try lowering GPU layers (15-20 instead of 27)
- Ensure device is arm64-v8a architecture

### Voice Input Not Working

**Symptoms**: Microphone button unresponsive or no transcription

**Solutions**:
- Grant microphone permission: Settings → Apps → Device Aid → Permissions
- Check microphone works in other apps
- Verify Whisper model exists in assets
- Restart the app
- Clear app data and reinitialize

### GPU Layer Errors

**Symptoms**: FastRPC errors or DSP crashes

**Solutions**:
- Lower GPU layers to 15-20
- Try GPU layers = 0 (CPU only mode)
- Ensure device supports Qualcomm Adreno GPU
- Close other GPU-intensive apps
- Restart device

### Out of Memory

**Symptoms**: App crashes during model load or inference

**Solutions**:
- Lower GPU layers (try 15-20 or even 0 for CPU-only mode)
- Close background apps
- Restart device to free up memory
- Increase heap size in `AndroidManifest.xml`:
  ```xml
  <application android:largeHeap="true">
  ```

---

## Support

For technical support and questions:

**iterate.ai**  
Website: iterate.ai  
Documentation: Included in SDK package

---

*Device Aid SDK - Powered by iterate.ai*
