package com.iterate.adreno.testapp;

import android.app.Application;
import android.util.Log;

import com.iterate.adreno.sdk.rag.RAGContextManager;

import java.io.File;

/**
 * Application class to initialize RAG index on app startup
 */
public class AdrenoTestApplication extends Application {
    private static final String TAG = "AdrenoTestApp";
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Application started");
        
        // Build RAG index in background if not exists
        new Thread(this::initializeRAG).start();
    }
    
    private void initializeRAG() {
        File indexFile = new File(getFilesDir(), "document_embeddings.json");
        
        if (!indexFile.exists()) {
            Log.i(TAG, "Building RAG index for the first time...");
            buildRAGIndex();
        } else {
            Log.i(TAG, "RAG index already exists");
        }
    }
    
    private void buildRAGIndex() {
        try {
            RAGContextManager ragManager = RAGContextManager.getInstance();
            
            String outputPath = new File(getFilesDir(), "document_embeddings.json").getAbsolutePath();
            
            boolean success = ragManager.buildIndex(
                getApplicationContext(),
                "DOCS/Input_QA.json",
                "models/InterplayGTE.onnx",
                "models/bert_tokenizer.json",
                outputPath
            );
            
            if (success) {
                Log.i(TAG, "✅ RAG index built successfully");
            } else {
                Log.e(TAG, "❌ Failed to build RAG index");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error building RAG index: " + e.getMessage(), e);
        }
    }
}
