package com.iterate.adreno.testapp;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.iterate.whispersdk.WhisperSDK;
import com.iterate.whispersdk.WhisperSDKCallback;

import androidx.appcompat.app.AppCompatActivity;

import com.iterate.adreno.sdk.AdrenoMenuSDK;
import com.iterate.adreno.sdk.LlamaGPU;
import com.iterate.adreno.sdk.SystemPromptManagerEnhanced;
import com.iterate.adreno.sdk.rag.RAGContextManager;
import com.iterate.adreno.sdk.rag.SearchResult;

import java.io.File;
import java.util.List;

/**
 * Main activity demonstrating RAG + LLM Q&A
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    
    public static final String MODEL_NAME = "InterplayThink.gguf";  // Public for SettingsActivity
    private static final String MODEL_URL = "https://qualcommapk.interplay.iterate.ai/InterplayThink.gguf";
    private static final String PREFS_NAME = "AdrenoTestAppSettings";
    private static final String KEY_TTS_ENABLED = "tts_enabled";
    
    private EditText inputField;
    private android.widget.ImageButton sendButton;
    private android.widget.ImageButton micButton;
    private android.widget.ImageButton refreshButton;
    private ScrollView chatScrollView;
    private LinearLayout chatContainer;
    
    // WhisperSDK for voice input
    private WhisperSDK whisperSDK;
    private boolean isWhisperActive = false;
    private boolean isProgrammaticTextChange = false;
    private boolean isTTSEnabled = false;
    private String lastTranscription = "";  // Track last transcription for when SDK doesn't call final callback
    private SharedPreferences sharedPreferences;
    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 1001;
    private static final int REQUEST_CODE_SETTINGS = 1002;
    
    private AdrenoMenuSDK sdk;
    private RAGContextManager ragManager;
    private Handler mainHandler;
    
    private boolean isInitialized = false;
    private boolean isGenerating = false;
    private boolean isDownloading = false;
    private boolean isLoadingModel = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isTTSEnabled = sharedPreferences.getBoolean(KEY_TTS_ENABLED, false);
        
        // Set action bar with Device Aid logo and text
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowCustomEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setElevation(0); // Remove shadow
            
            // Create custom view with logo and text
            android.widget.LinearLayout customView = new android.widget.LinearLayout(this);
            customView.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            customView.setGravity(android.view.Gravity.CENTER);
            android.widget.LinearLayout.LayoutParams customParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT
            );
            customView.setLayoutParams(customParams);
            
            // Add Device Aid logo (bigger)
            android.widget.ImageView logo = new android.widget.ImageView(this);
            logo.setImageResource(R.drawable.device_aid_logo);
            android.widget.LinearLayout.LayoutParams logoParams = new android.widget.LinearLayout.LayoutParams(80, 80);
            logoParams.setMargins(0, 0, 16, 0);
            logo.setLayoutParams(logoParams);
            customView.addView(logo);
            
            // Add text container
            android.widget.LinearLayout textContainer = new android.widget.LinearLayout(this);
            textContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
            textContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
            
            // Add "Device Aid" title (bigger)
            android.widget.TextView titleView = new android.widget.TextView(this);
            titleView.setText("Device Aid");
            titleView.setTextSize(22);
            titleView.setTextColor(0xFF282D32);
            titleView.setTypeface(null, android.graphics.Typeface.BOLD);
            textContainer.addView(titleView);
            
            // Add "Powered by Generate Nano" subtitle with colored "Generate Nano"
            android.widget.TextView subtitleView = new android.widget.TextView(this);
            android.text.SpannableString spannableSubtitle = new android.text.SpannableString("Powered by Generate Nano");
            android.text.style.ForegroundColorSpan colorSpan = new android.text.style.ForegroundColorSpan(0xFF4285F4);
            spannableSubtitle.setSpan(colorSpan, 11, 24, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            subtitleView.setText(spannableSubtitle);
            subtitleView.setTextSize(13);
            subtitleView.setTextColor(0xFF666666);
            textContainer.addView(subtitleView);
            
            customView.addView(textContainer);
            getSupportActionBar().setCustomView(customView);
        }
        
        // Initialize UI
        inputField = findViewById(R.id.inputField);
        sendButton = findViewById(R.id.sendButton);
        micButton = findViewById(R.id.micButton);
        refreshButton = findViewById(R.id.refreshButton);
        chatScrollView = findViewById(R.id.chatScrollView);
        chatContainer = findViewById(R.id.chatContainer);
        
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Setup send button
        sendButton.setOnClickListener(v -> sendQuery());
        
        // Setup refresh button to clear chat
        refreshButton.setOnClickListener(v -> clearChat());
        
        // Monitor text changes to stop Whisper if user types
        inputField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Stop Whisper if user starts typing manually
                if (!isProgrammaticTextChange && whisperSDK != null && whisperSDK.isRecording()) {
                    whisperSDK.forceStop();
                }
            }
            
            @Override
            public void afterTextChanged(Editable s) {
                isProgrammaticTextChange = false;
            }
        });
        
        // Setup buttons
        micButton.setOnClickListener(v -> toggleVoiceInput());
        
        // Show welcome message
        addChatMessage("Welcome! I'm your Device Aid AI Assistant.\n\nHow can I help you today?", false);
        
        // Initialize RAG only (not LLM) in background
        new Thread(this::initializeRAG).start();
        
        // Initialize Whisper SDK for voice input
        initializeWhisper();
        
        // Request microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.RECORD_AUDIO}, 
                PERMISSION_REQUEST_RECORD_AUDIO);
        }
    }
    
    private void initializeRAG() {
        updateStatus("Initializing RAG...");
        
        try {
            // Wait for index to be built
            File indexFile = new File(getFilesDir(), "document_embeddings.json");
            int waitCount = 0;
            while ((!indexFile.exists() || indexFile.length() == 0) && waitCount < 120) {
                if (waitCount % 5 == 0) {
                    Log.i(TAG, "Waiting for index to be built... (" + waitCount + "s)");
                    updateStatus("Building index... " + waitCount + "s");
                }
                Thread.sleep(1000);
                waitCount++;
            }
            
            if (!indexFile.exists() || indexFile.length() == 0) {
                showError("Failed to build RAG index. Please restart the app.");
                return;
            }
            
            Log.i(TAG, "Index file ready: " + indexFile.length() + " bytes");
            
            // Initialize RAG
            // Initializing RAG
            ragManager = RAGContextManager.getInstance();
            
            String indexPath = indexFile.getAbsolutePath();
            boolean ragSuccess = ragManager.initialize(
                getApplicationContext(),
                "models/InterplayGTE.onnx",
                "models/bert_tokenizer.json",
                indexPath
            );
            
            if (!ragSuccess) {
                showError("Failed to initialize RAG");
                return;
            }
            
            // RAG is ready
            updateStatus("Ready - RAG Active");
            enableInput();
            updateLoadModelButtonState();
            
            addChatMessage("✅ RAG initialized!\n\nClick 'Load Model' button to enable LLM responses.", false);
            
        } catch (Exception e) {
            Log.e(TAG, "RAG initialization error: " + e.getMessage(), e);
            showError("Initialization failed: " + e.getMessage());
        }
    }
    
    private void loadModel() {
        if (isLoadingModel) {
            Toast.makeText(this, "Model loading already in progress", Toast.LENGTH_SHORT).show();
            return;
        }
        
        File modelFile = new File(getFilesDir(), MODEL_NAME);
        if (!modelFile.exists() || modelFile.length() < 100_000_000) {
            Toast.makeText(this, "Model not found. Please download it first.", Toast.LENGTH_LONG).show();
            return;
        }
        
        new Thread(() -> {
            try {
                isLoadingModel = true;
                addChatMessage("🚀 Loading LLM model...", false);
                updateStatus("Loading LLM model...");
                
                // Initialize SDK
                sdk = AdrenoMenuSDK.initialize(
                    getApplicationContext(),
                    MODEL_NAME,  // File in internal storage
                    "You are a helpful assistant. Answer questions based on the provided context accurately and concisely.",
                    27
                );
                
                // Enable RAG
                SystemPromptManagerEnhanced.getInstance().enableRAG();
                
                isInitialized = true;
                updateStatus("Ready - Full AI Active");
                
                addChatMessage("✅ LLM model loaded successfully!\n\nYou can now get AI-powered responses.", false);
                
            } catch (Exception e) {
                Log.e(TAG, "Model loading error: " + e.getMessage(), e);
                addChatMessage("❌ Failed to load model: " + e.getMessage(), false);
            } finally {
                isLoadingModel = false;
            }
        }).start();
    }
    
    private void updateLoadModelButtonState() {
        // Button state management removed - now handled in settings
    }
    
    private void askQuestion(String query) {
        if (isGenerating) return;
        
        isGenerating = true;
        updateStatus("Searching...");
        
        addChatMessage("You: " + query, true);
        
        new Thread(() -> {
            try {
                // Search for relevant documents
                List<SearchResult> results = ragManager.search(query, 3);
                
                if (results != null && !results.isEmpty()) {
                    // Log search results (not shown in chat)
                    Log.i(TAG, "📚 Found " + results.size() + " relevant documents");
                    for (int i = 0; i < results.size(); i++) {
                        SearchResult result = results.get(i);
                        Log.i(TAG, String.format("  %d. %s (score: %.3f)", 
                            i + 1, result.getQuestion(), result.getScore()));
                        
                        // Log image references if present
                        if (result.getImageRefs() != null && !result.getImageRefs().isEmpty()) {
                            Log.i(TAG, "     📷 Images: " + result.getImageRefs());
                        }
                    }
                    
                    // Get top result for images
                    SearchResult topResult = results.get(0);
                    
                    // If SDK is initialized, generate LLM response with context
                    if (isInitialized && sdk != null) {
                        // Inject RAG context and log the final context
                        SystemPromptManagerEnhanced promptManager = SystemPromptManagerEnhanced.getInstance();
                        
                        // Get formatted context
                        String finalContext = ragManager.getFormattedContext(results);
                        
                        // Log the final RAG context that will be sent to LLM
                        Log.i(TAG, "╔════════════════════════════════════════════════════════════╗");
                        Log.i(TAG, "║ 📝 FINAL RAG CONTEXT INJECTED INTO LLM:");
                        Log.i(TAG, "╠════════════════════════════════════════════════════════════╣");
                        Log.i(TAG, finalContext);
                        Log.i(TAG, "╚════════════════════════════════════════════════════════════╝");
                        
                        // Set the context
                        promptManager.setRAGContext(results);
                        sdk.refreshCache();
                        
                        // Create a new TextView for streaming response
                        TextView[] responseViews = createResponseTextView();
                        TextView responseView = responseViews[0];
                        TextView timeView = responseViews[1];
                        responseView.setText("Assistant: ");
                        
                        // Track start time for response generation
                        final long startTime = System.currentTimeMillis();
                        final boolean[] firstTokenReceived = {false};
                        
                        // Generate response
                        mainHandler.post(() -> updateStatus("Generating..."));
                        
                        sdk.generateResponse(query, 512, new LlamaGPU.StreamingCallback() {
                            @Override
                            public void onTokenGenerated(String token) {
                                mainHandler.post(() -> {
                                    // Calculate time to first token
                                    if (!firstTokenReceived[0]) {
                                        long firstTokenTime = System.currentTimeMillis();
                                        float elapsedSeconds = (firstTokenTime - startTime) / 1000.0f;
                                        timeView.setText(String.format("%.1fs", elapsedSeconds));
                                        firstTokenReceived[0] = true;
                                    }
                                    
                                    responseView.append(token);
                                    scrollToBottom();
                                });
                            }
                            
                            @Override
                            public void onGenerationComplete() {
                                mainHandler.post(() -> {
                                    // Get the generated response text
                                    String responseText = responseView.getText().toString();
                                    // Remove "Assistant: " prefix for TTS
                                    if (responseText.startsWith("Assistant: ")) {
                                        responseText = responseText.substring("Assistant: ".length());
                                    }
                                    
                                    // Speak the response if TTS is enabled
                                    if (isTTSEnabled && whisperSDK != null && !responseText.trim().isEmpty()) {
                                        whisperSDK.speak(responseText.trim());
                                        Log.d(TAG, "🔊 Speaking response with TTS");
                                    }
                                    
                                    // Images will be displayed after response completes
                                    if (topResult.getImageRefs() != null && !topResult.getImageRefs().isEmpty()) {
                                        displayImages(topResult.getImageRefs());
                                    }
                                });
                            }
                            
                            @Override
                            public void onError(String error) {
                                mainHandler.post(() -> responseView.append("\n\nError: " + error));
                            }
                        });
                    } else {
                        // LLM not loaded, show direct RAG answer
                        SearchResult bestResult = results.get(0);
                        String answer = bestResult.getAnswer();
                        addChatMessage("Assistant: " + answer, false);
                        Log.i(TAG, "💡 Showing direct RAG answer (LLM not loaded)");
                        
                        // Speak the answer if TTS is enabled
                        if (isTTSEnabled && whisperSDK != null && !answer.trim().isEmpty()) {
                            whisperSDK.speak(answer.trim());
                            Log.d(TAG, "🔊 Speaking direct RAG answer with TTS");
                        }
                        
                        // Also display images for direct RAG answer
                        if (bestResult.getImageRefs() != null && !bestResult.getImageRefs().isEmpty()) {
                            displayImages(bestResult.getImageRefs());
                        }
                    }
                    
                } else {
                    Log.w(TAG, "⚠️ No RAG results found for query: " + query);
                    String noResultsMessage = "I couldn't find relevant information in the knowledge base.";
                    addChatMessage("Assistant: " + noResultsMessage, false);
                    
                    // Speak the error message if TTS is enabled
                    if (isTTSEnabled && whisperSDK != null) {
                        whisperSDK.speak(noResultsMessage);
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error handling question: " + e.getMessage(), e);
                addChatMessage("Error: " + e.getMessage(), false);
            } finally {
                isGenerating = false;
                mainHandler.post(() -> updateStatus("Ready"));
            }
        }).start();
    }
    
    /**
     * Add a chat message as a chat bubble with avatar
     */
    private void addChatMessage(String message, boolean isUser) {
        mainHandler.post(() -> {
            // Create container for avatar + message
            LinearLayout messageContainer = new LinearLayout(this);
            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            containerParams.setMargins(0, 8, 0, 8);
            messageContainer.setLayoutParams(containerParams);
            messageContainer.setOrientation(LinearLayout.HORIZONTAL);
            
            if (isUser) {
                messageContainer.setGravity(android.view.Gravity.END);
            } else {
                messageContainer.setGravity(android.view.Gravity.START);
            }
            
            // Create avatar
            android.widget.ImageView avatar = new android.widget.ImageView(this);
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(32, 32);
            avatarParams.setMargins(8, 0, 8, 0);
            avatar.setLayoutParams(avatarParams);
            avatar.setImageResource(isUser ? R.drawable.ic_person : R.drawable.ic_robot);
            avatar.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            
            // Create message bubble
            TextView messageView = new TextView(this);
            LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            messageParams.setMargins(0, 0, 0, 0);
            messageParams.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.7f);
            messageView.setLayoutParams(messageParams);
            messageView.setText(message);
            messageView.setTextColor(isUser ? 0xFFFFFFFF : 0xFF1A1A1A);
            messageView.setTextSize(15);
            messageView.setPadding(24, 18, 24, 18);
            messageView.setLineSpacing(6, 1.0f);
            
            // Use modern drawable backgrounds
            if (isUser) {
                messageView.setBackgroundResource(R.drawable.user_message_bubble);
                messageView.setElevation(4);
            } else {
                messageView.setBackgroundResource(R.drawable.assistant_message_bubble);
                messageView.setElevation(2);
            }
            
            // Add views in correct order
            if (isUser) {
                messageContainer.addView(messageView);
                messageContainer.addView(avatar);
            } else {
                messageContainer.addView(avatar);
                
                // Create a vertical container for message + copy button
                LinearLayout messageWithCopyContainer = new LinearLayout(this);
                messageWithCopyContainer.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                messageWithCopyContainer.setLayoutParams(wrapperParams);
                
                // Add message to container
                messageWithCopyContainer.addView(messageView);
                
                // Add copy button for assistant messages
                android.widget.ImageButton copyButton = new android.widget.ImageButton(this);
                LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(28, 28);
                copyParams.setMargins(24, 4, 0, 0);
                copyButton.setLayoutParams(copyParams);
                copyButton.setImageResource(R.drawable.ic_copy);
                copyButton.setBackgroundColor(0x00000000); // Transparent
                copyButton.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                copyButton.setPadding(4, 4, 4, 4);
                copyButton.setOnClickListener(v -> {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("Assistant Response", message);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                });
                
                messageWithCopyContainer.addView(copyButton);
                messageContainer.addView(messageWithCopyContainer);
            }
            
            chatContainer.addView(messageContainer);
            scrollToBottom();
        });
    }
    
    /**
     * Create a TextView for streaming responses with chat bubble styling
     * Returns array: [0] = responseView, [1] = timeView
     */
    private TextView[] createResponseTextView() {
        // Create container for avatar + message
        LinearLayout messageContainer = new LinearLayout(this);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        containerParams.setMargins(0, 8, 0, 8);
        messageContainer.setLayoutParams(containerParams);
        messageContainer.setOrientation(LinearLayout.HORIZONTAL);
        messageContainer.setGravity(android.view.Gravity.START);
        
        // Create avatar
        android.widget.ImageView avatar = new android.widget.ImageView(this);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(32, 32);
        avatarParams.setMargins(8, 0, 8, 0);
        avatar.setLayoutParams(avatarParams);
        avatar.setImageResource(R.drawable.ic_robot);
        avatar.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        
        // Create a vertical container for message + copy button
        LinearLayout messageWithCopyContainer = new LinearLayout(this);
        messageWithCopyContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        messageWithCopyContainer.setLayoutParams(wrapperParams);
        
        // Create a FrameLayout to hold message bubble + time overlay
        android.widget.FrameLayout bubbleContainer = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams bubbleContainerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bubbleContainerParams.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.7f);
        bubbleContainer.setLayoutParams(bubbleContainerParams);
        
        // Create message bubble
        TextView responseView = new TextView(this);
        android.widget.FrameLayout.LayoutParams messageParams = new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        );
        responseView.setLayoutParams(messageParams);
        responseView.setTextColor(0xFF1A1A1A);
        responseView.setTextSize(15);
        responseView.setPadding(24, 18, 24, 36);
        responseView.setLineSpacing(6, 1.0f);
        
        // Use modern assistant bubble background
        responseView.setBackgroundResource(R.drawable.assistant_message_bubble);
        responseView.setElevation(2);
        
        // Create time display TextView (bottom right corner)
        TextView timeView = new TextView(this);
        android.widget.FrameLayout.LayoutParams timeParams = new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        );
        timeParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
        timeParams.setMargins(0, 0, 16, 10);
        timeView.setLayoutParams(timeParams);
        timeView.setTextColor(0xFF999999);
        timeView.setTextSize(11);
        timeView.setText("");
        timeView.setElevation(4);
        
        // Add views to bubble container
        bubbleContainer.addView(responseView);
        bubbleContainer.addView(timeView);
        
        // Add bubble container to message container
        messageWithCopyContainer.addView(bubbleContainer);
        
        // Add copy button for streaming response
        android.widget.ImageButton copyButton = new android.widget.ImageButton(this);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(28, 28);
        copyParams.setMargins(24, 4, 0, 0);
        copyButton.setLayoutParams(copyParams);
        copyButton.setImageResource(R.drawable.ic_copy);
        copyButton.setBackgroundColor(0x00000000); // Transparent
        copyButton.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        copyButton.setPadding(4, 4, 4, 4);
        copyButton.setOnClickListener(v -> {
            String text = responseView.getText().toString();
            if (!text.trim().isEmpty()) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Assistant Response", text);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });
        
        messageWithCopyContainer.addView(copyButton);
        
        messageContainer.addView(avatar);
        messageContainer.addView(messageWithCopyContainer);
        
        mainHandler.post(() -> {
            chatContainer.addView(messageContainer);
            scrollToBottom();
        });
        
        return new TextView[]{responseView, timeView};
    }
    
    private void showError(String error) {
        mainHandler.post(() -> {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        });
    }
    
    private void updateStatus(String status) {
        // Status updates now shown in chat or can be added to a status bar if needed
        // Custom action bar view is static with logo and "Powered by Generate Nano"
    }
    
    
    private void enableInput() {
        mainHandler.post(() -> sendButton.setEnabled(true));
    }
    
    private void scrollToBottom() {
        chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }
    
    private void updateModelButtonState() {
        // Button state management removed - now handled in settings
    }
    
    private void downloadModel() {
        if (isDownloading) {
            Toast.makeText(this, "Download already in progress", Toast.LENGTH_SHORT).show();
            return;
        }
        
        File modelFile = new File(getFilesDir(), MODEL_NAME);
        boolean hasModel = modelFile.exists() && modelFile.length() > 100_000_000;
        
        // If model exists, don't download again
        if (hasModel) {
            Toast.makeText(this, "Model already downloaded", Toast.LENGTH_SHORT).show();
            addChatMessage("ℹ️ Model already exists. Use Delete Model to remove it.", false);
            return;
        }
        
        // Download the model
        new Thread(() -> {
            try {
                isDownloading = true;
                File destFile = new File(getFilesDir(), MODEL_NAME);
                
                addChatMessage("📥 Downloading model...", false);
                updateStatus("Downloading model...");
                
                java.net.URL url = new java.net.URL(MODEL_URL);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(30000);
                connection.connect();
                
                int responseCode = connection.getResponseCode();
                if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
                    throw new Exception("Server returned HTTP " + responseCode + " " + connection.getResponseMessage());
                }
                
                long fileLength = connection.getContentLength();
                boolean hasContentLength = fileLength > 0;
                
                java.io.InputStream input = connection.getInputStream();
                java.io.FileOutputStream output = new java.io.FileOutputStream(destFile);
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytesRead = 0;
                int lastProgress = -1;
                
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    
                    // Update button with progress
                    if (hasContentLength) {
                        int progress = (int) ((totalBytesRead * 100) / fileLength);
                        if (progress != lastProgress) {
                            lastProgress = progress;
                        }
                    }
                }
                
                output.close();
                input.close();
                
                addChatMessage("✅ Model downloaded successfully!", false);
                updateStatus("Ready (RAG Only)");
                updateModelButtonState();
                updateLoadModelButtonState();
                
                mainHandler.post(() -> 
                    Toast.makeText(MainActivity.this, "Model downloaded! Click 'Load Model' to use it.", Toast.LENGTH_LONG).show()
                );
                
            } catch (Exception e) {
                Log.e(TAG, "Download error: " + e.getMessage(), e);
                addChatMessage("❌ Download failed: " + e.getMessage(), false);
            } finally {
                isDownloading = false;
            }
        }).start();
    }
    
    private void rebuildIndex() {
        new Thread(() -> {
            try {
                addChatMessage("🔨 Rebuilding RAG index...", false);
                // Building index
                
                // Delete old index
                File indexFile = new File(getFilesDir(), "document_embeddings.json");
                if (indexFile.exists()) {
                    indexFile.delete();
                }
                
                // Rebuild
                RAGContextManager ragMgr = RAGContextManager.getInstance();
                String outputPath = indexFile.getAbsolutePath();
                
                boolean success = ragMgr.buildIndex(
                    getApplicationContext(),
                    "DOCS/Input_QA.json",
                    "models/InterplayGTE.onnx",
                    "models/bert_tokenizer.json",
                    outputPath
                );
                
                if (success) {
                    addChatMessage("✅ Index rebuilt successfully!", false);
                    // Index ready
                    mainHandler.post(() -> 
                        Toast.makeText(MainActivity.this, "Index rebuilt! Restart app to use it.", Toast.LENGTH_LONG).show()
                    );
                } else {
                    addChatMessage("❌ Failed to rebuild index", false);
                    // Index error
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Index rebuild error: " + e.getMessage(), e);
                addChatMessage("❌ Error: " + e.getMessage(), false);
            }
        }).start();
    }
    
    private void clearChat() {
        mainHandler.post(() -> {
            // Remove all views from chat container
            chatContainer.removeAllViews();
            addChatMessage("Chat cleared.", false);
            Toast.makeText(this, "Chat cleared", Toast.LENGTH_SHORT).show();
        });
    }
    
    private void handleGpuLayersChange(int newGpuLayers) {
        if (!isInitialized) {
            addChatMessage("⚠️ GPU layers updated to " + newGpuLayers + ". Load model to apply changes.", false);
            return;
        }
        
        // Model is loaded, need to reload with new GPU layers
        addChatMessage("🔄 Reloading model with " + newGpuLayers + " GPU layers...", false);
        
        new Thread(() -> {
            try {
                isLoadingModel = true;
                isInitialized = false;
                updateStatus("Reloading model...");
                
                // Reinitialize SDK with new GPU layers
                // The SDK will automatically handle cleanup and reinit if GPU layers changed
                sdk = AdrenoMenuSDK.initialize(
                    getApplicationContext(),
                    MODEL_NAME,
                    "You are a helpful assistant. Answer questions based on the provided context accurately and concisely.",
                    newGpuLayers
                );
                
                SystemPromptManagerEnhanced.getInstance().enableRAG();
                
                isInitialized = true;
                updateStatus("Ready - Full AI Active");
                
                addChatMessage("✅ Model reloaded with " + newGpuLayers + " GPU layers!", false);
                
            } catch (Exception e) {
                Log.e(TAG, "Model reload error: " + e.getMessage(), e);
                addChatMessage("❌ Failed to reload model: " + e.getMessage(), false);
            } finally {
                isLoadingModel = false;
            }
        }).start();
    }
    
    /**
     * Display images from asset paths
     */
    private void displayImages(List<String> imageRefs) {
        if (imageRefs == null || imageRefs.isEmpty()) {
            return;
        }
        
        mainHandler.post(() -> {
            for (String imageRef : imageRefs) {
                try {
                    // Load image from assets
                    String imagePath = "DOCS/images/" + imageRef;
                    java.io.InputStream is = getAssets().open(imagePath);
                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    is.close();
                    
                    if (bitmap != null) {
                        // Create ImageView
                        ImageView imageView = new ImageView(this);
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        );
                        params.setMargins(0, 16, 0, 16);
                        imageView.setLayoutParams(params);
                        imageView.setAdjustViewBounds(true);
                        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        imageView.setImageBitmap(bitmap);
                        
                        // Add to chat container
                        chatContainer.addView(imageView);
                        
                        Log.i(TAG, "✅ Displayed image: " + imageRef);
                    } else {
                        Log.w(TAG, "⚠️ Failed to decode image: " + imageRef);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error loading image " + imageRef + ": " + e.getMessage());
                }
            }
            scrollToBottom();
        });
    }
    
    
    /**
     * Send query to RAG system
     */
    private void sendQuery() {
        String query = inputField.getText().toString().trim();
        if (!query.isEmpty()) {
            // Stop Whisper if recording
            if (!isProgrammaticTextChange && whisperSDK != null && whisperSDK.isRecording()) {
                whisperSDK.forceStop();
            }
            
            setInputTextProgrammatically("");
            askQuestion(query);
        }
    }
    
    /**
     * Set input text programmatically (prevents TextWatcher from triggering)
     */
    private void setInputTextProgrammatically(String text) {
        isProgrammaticTextChange = true;
        inputField.setText(text);
        inputField.setSelection(text.length());
    }
    
    /**
     * Initialize Whisper SDK for voice input
     * NOTE: TTS is always enabled in SDK - we control it via isTTSEnabled flag
     */
    private void initializeWhisper() {
        try {
            Log.d(TAG, "🎤 Initializing WhisperSDK...");
            
            // Always enable TTS in SDK - we control usage with isTTSEnabled flag
            whisperSDK = WhisperSDK.builder(this)
                .enableTTS(true)  // Always true - controlled by flag
                .setLanguage("en")
                .setTimeout(30000)
                .enableContinuous(false)
                .setDebugMode(true)
                .build();
            
            whisperSDK.setCallback(new WhisperSDKCallback() {
                @Override
                public void onTranscriptionReceived(String transcription, boolean isFinal) {
                    runOnUiThread(() -> {
                        Log.d(TAG, "📝 Transcription: isFinal=" + isFinal + ", text='" + transcription + "'");
                        
                        // Track the transcription
                        if (!transcription.trim().isEmpty()) {
                            lastTranscription = transcription.trim();
                        }
                        
                        if (!isFinal) {
                            // Show partial transcription
                            if (!transcription.trim().isEmpty()) {
                                setInputTextProgrammatically(transcription);
                            }
                        } else if (!transcription.trim().isEmpty()) {
                            // Final transcription - set and send
                            setInputTextProgrammatically(transcription);
                            sendQuery();
                            lastTranscription = "";  // Clear after sending
                        } else {
                            // Empty final - stop recording
                            if (whisperSDK != null && whisperSDK.isRecording()) {
                                whisperSDK.stopRecording();
                            }
                        }
                    });
                }
                
                @Override
                public void onRecordingStarted() {
                    runOnUiThread(() -> {
                        Log.d(TAG, "🎤 Recording started");
                        isWhisperActive = true;
                        lastTranscription = "";  // Clear previous transcription
                        setInputTextProgrammatically("");
                        inputField.setHint("Listening...");
                        micButton.setImageResource(R.drawable.ic_stop);
                        micButton.setBackgroundResource(R.drawable.mic_recording_background);
                    });
                }
                
                @Override
                public void onRecordingStopped() {
                    runOnUiThread(() -> {
                        Log.d(TAG, "🎤 Recording stopped");
                        isWhisperActive = false;
                        micButton.setImageResource(R.drawable.ic_microphone);
                        micButton.setBackgroundResource(R.drawable.modern_icon_button);
                        
                        // If we have accumulated transcription but SDK didn't call final callback, use it
                        if (!lastTranscription.isEmpty() && inputField.getText().toString().trim().isEmpty()) {
                            Log.d(TAG, "📝 Using accumulated transcription: '" + lastTranscription + "'");
                            setInputTextProgrammatically(lastTranscription);
                            // Auto-send the query
                            sendQuery();
                            lastTranscription = "";  // Clear after using
                        } else if (inputField.getText().toString().trim().isEmpty()) {
                            inputField.setHint("Ask me anything...");
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Log.e(TAG, "❌ Whisper error: " + error);
                        isWhisperActive = false;
                        lastTranscription = "";  // Clear on error
                        micButton.setImageResource(R.drawable.ic_microphone);
                        micButton.setBackgroundResource(R.drawable.modern_icon_button);
                        if (!error.toLowerCase().contains("timeout")) {
                            Toast.makeText(MainActivity.this, "Voice error: " + error, Toast.LENGTH_SHORT).show();
                        }
                        inputField.setHint("Ask me anything...");
                    });
                }
            });
            
            Log.i(TAG, "✅ Whisper SDK initialized");
        } catch (Exception e) {
            Log.e(TAG, "❌ Whisper SDK initialization failed: " + e.getMessage());
        }
    }
    
    /**
     * Toggle voice input
     */
    private void toggleVoiceInput() {
        if (whisperSDK == null) {
            Toast.makeText(this, "Voice input not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        boolean currentlyRecording = isWhisperActive && whisperSDK.isRecording();
        
        if (currentlyRecording) {
            // Stop recording
            whisperSDK.stopRecording();
        } else {
            // Start recording
            whisperSDK.startRecording();
        }
    }
    
    /**
     * Open settings activity
     */
    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivityForResult(intent, REQUEST_CODE_SETTINGS);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_SETTINGS && resultCode == RESULT_OK && data != null) {
            String action = data.getStringExtra("action");
            
            if (action != null) {
                switch (action) {
                    case "download_model":
                        downloadModel();
                        break;
                    case "load_model":
                        loadModel();
                        break;
                    case "rebuild_index":
                        rebuildIndex();
                        break;
                    case "clear_chat":
                        clearChat();
                        break;
                    case "model_deleted":
                        updateStatus("Model deleted");
                        break;
                    case "gpu_layers_changed":
                        int gpuLayers = data.getIntExtra("gpu_layers", 27);
                        handleGpuLayersChange(gpuLayers);
                        break;
                }
            }
            
            // Check if TTS setting changed
            if (data.hasExtra("tts_enabled")) {
                boolean newTTSEnabled = data.getBooleanExtra("tts_enabled", false);
                if (newTTSEnabled != isTTSEnabled) {
                    isTTSEnabled = newTTSEnabled;
                    
                    // Stop any ongoing speech if disabling TTS
                    if (!isTTSEnabled && whisperSDK != null && whisperSDK.isSpeaking()) {
                        whisperSDK.stopSpeaking();
                    }
                    
                    String message = isTTSEnabled ? 
                        "TTS enabled" : 
                        "TTS disabled";
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    
                    Log.d(TAG, "TTS setting changed to: " + isTTSEnabled);
                }
            }
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            openSettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Microphone permission required for voice input", Toast.LENGTH_LONG).show();
                micButton.setVisibility(android.widget.ImageButton.GONE);
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (ragManager != null) {
            ragManager.close();
        }
        if (whisperSDK != null) {
            whisperSDK.destroy();
        }
    }
}
