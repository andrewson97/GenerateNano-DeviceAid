package com.iterate.adreno.sdk.rag;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.List;

/**
 * Main RAG Context Manager - integrates document retrieval with SDK
 */
public class RAGContextManager {
    private static final String TAG = "RAGContextManager";
    private static RAGContextManager instance;
    
    private VectorSearchEngine searchEngine;
    private PrecomputedVectorSearchEngine precomputedSearchEngine;
    private Context context;
    private boolean isInitialized = false;
    private boolean usingPrecomputedVectors = false;
    
    private RAGContextManager() {}
    
    public static synchronized RAGContextManager getInstance() {
        if (instance == null) {
            instance = new RAGContextManager();
        }
        return instance;
    }
    
    /**
     * Initialize RAG system
     * 
     * @param context Application context
     * @param modelPath Path to ONNX model (in assets)
     * @param tokenizerPath Path to tokenizer JSON (in assets)
     * @param indexPath Path to document_embeddings.json
     * @return true if successful
     */
    public boolean initialize(Context context, String modelPath, String tokenizerPath, String indexPath) {
        this.context = context.getApplicationContext();
        
        try {
            Log.i(TAG, "🧠 Initializing RAG Context Manager...");
            
            // Check if index exists
            if (!indexExists(indexPath)) {
                Log.w(TAG, "⚠️ Index not found at: " + indexPath);
                Log.w(TAG, "⚠️ You need to build the index first using buildIndex()");
                return false;
            }
            
            // Auto-detect index format
            String indexFormat = detectIndexFormat(indexPath);
            Log.i(TAG, "📊 Detected index format: " + indexFormat);
            
            if ("vectorIndex".equals(indexFormat)) {
                // Use precomputed vector search engine
                Log.i(TAG, "🚀 Using PrecomputedVectorSearchEngine (fast loading)");
                precomputedSearchEngine = new PrecomputedVectorSearchEngine(context, modelPath, tokenizerPath);
                if (!precomputedSearchEngine.initialize(indexPath)) {
                    Log.e(TAG, "❌ Failed to initialize precomputed search engine");
                    return false;
                }
                usingPrecomputedVectors = true;
                Log.i(TAG, "✅ Loaded " + precomputedSearchEngine.getVectorCount() + " precomputed vectors");
            } else {
                // Use traditional vector search engine
                Log.i(TAG, "🔧 Using VectorSearchEngine (traditional)");
                searchEngine = new VectorSearchEngine(context, modelPath, tokenizerPath);
                if (!searchEngine.initialize(indexPath)) {
                    Log.e(TAG, "❌ Failed to initialize search engine");
                    return false;
                }
                usingPrecomputedVectors = false;
            }
            
            isInitialized = true;
            Log.i(TAG, "✅ RAG Context Manager initialized successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error initializing RAG: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Detect index format by checking JSON structure
     */
    private String detectIndexFormat(String indexPath) {
        try {
            File file = new File(indexPath);
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(fis));
            
            // Read first few lines to detect format
            StringBuilder sample = new StringBuilder();
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < 20) {
                sample.append(line);
                lineCount++;
            }
            reader.close();
            
            String content = sample.toString();
            
            // Check for vectorIndex.json format (has "vectors" array and "metadata" with question/answer)
            if (content.contains("\"vectors\"") && content.contains("\"metadata\"") && 
                content.contains("\"question\"") && content.contains("\"answer\"")) {
                return "vectorIndex";
            }
            
            // Check for document_embeddings.json format (has "embeddings" array)
            if (content.contains("\"embeddings\"") && content.contains("\"embedding\"")) {
                return "documentEmbeddings";
            }
            
            Log.w(TAG, "⚠️ Unknown index format, defaulting to documentEmbeddings");
            return "documentEmbeddings";
            
        } catch (Exception e) {
            Log.e(TAG, "Error detecting index format: " + e.getMessage());
            return "documentEmbeddings";
        }
    }
    
    /**
     * Build vector index from Input_QA.json
     * 
     * @param context Application context (to copy assets to cache)
     * @param inputJsonAsset Path to Input_QA.json in assets (e.g., "DOCS/Input_QA.json")
     * @param modelAsset Path to ONNX model in assets (e.g., "models/InterplayGTE.onnx") 
     * @param tokenizerAsset Path to tokenizer in assets (e.g., "models/bert_tokenizer.json")
     * @param outputPath Output path for index file
     * @return true if successful
     */
    public boolean buildIndex(Context context, String inputJsonAsset, String modelAsset, String tokenizerAsset, String outputPath) {
        try {
            Log.i(TAG, "🔨 Building RAG index...");
            Log.i(TAG, "   Input asset: " + inputJsonAsset);
            Log.i(TAG, "   Output: " + outputPath);
            
            // Copy assets to cache directory so they can be accessed as file paths
            File cacheDir = context.getCacheDir();
            String inputPath = copyAssetToCache(context, inputJsonAsset, cacheDir);
            String modelPath = copyAssetToCache(context, modelAsset, cacheDir);
            String tokenizerPath = copyAssetToCache(context, tokenizerAsset, cacheDir);
            
            if (inputPath == null || modelPath == null || tokenizerPath == null) {
                Log.e(TAG, "❌ Failed to copy assets to cache");
                return false;
            }
            
            Log.i(TAG, "   Input file: " + inputPath);
            Log.i(TAG, "   Model file: " + modelPath);
            Log.i(TAG, "   Tokenizer file: " + tokenizerPath);
            
            // Build index using file paths (like reference app)
            DocumentIndexBuilder builder = new DocumentIndexBuilder(inputPath, outputPath, modelPath, tokenizerPath);
            boolean success = builder.buildIndex();
            
            if (success) {
                Log.i(TAG, "✅ Index built successfully at: " + outputPath);
            } else {
                Log.e(TAG, "❌ Failed to build index");
            }
            
            return success;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error building index: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Copy asset to cache directory (like reference app does)
     */
    private String copyAssetToCache(Context context, String assetPath, File cacheDir) {
        try {
            String fileName = assetPath.substring(assetPath.lastIndexOf('/') + 1);
            File outputFile = new File(cacheDir, fileName);
            
            // Copy if doesn't exist or is outdated
            java.io.InputStream is = context.getAssets().open(assetPath);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.close();
            is.close();
            
            Log.i(TAG, "✅ Copied asset to: " + outputFile.getAbsolutePath());
            return outputFile.getAbsolutePath();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to copy asset " + assetPath + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Search for relevant documents
     * 
     * @param query User query
     * @param topK Number of results to return
     * @return List of search results
     */
    public List<SearchResult> search(String query, int topK) {
        if (!isInitialized) {
            Log.e(TAG, "❌ RAG not initialized. Call initialize() first.");
            return null;
        }
        
        if (usingPrecomputedVectors) {
            return precomputedSearchEngine.search(query, topK);
        } else {
            return searchEngine.search(query, topK);
        }
    }
    
    /**
     * Get formatted context string from search results
     * 
     * @param results Search results
     * @return Formatted context string for system prompt
     */
    public String getFormattedContext(List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("RELEVANT CONTEXT:\n\n");
        
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            context.append(String.format("[Document %d] (Score: %.3f)\n", i + 1, result.getScore()));
            context.append("Q: ").append(result.getQuestion()).append("\n");
            if (result.getAnswer() != null) {
                context.append("A: ").append(result.getAnswer()).append("\n");
            }
            
            // Add image references if available
            if (result.getImageRefs() != null && !result.getImageRefs().isEmpty()) {
                context.append("Images: ");
                for (int j = 0; j < result.getImageRefs().size(); j++) {
                    if (j > 0) context.append(", ");
                    context.append(result.getImageRefs().get(j));
                }
                context.append("\n");
            }
            
            context.append("\n");
        }
        
        context.append("---\nAnswer the user's question based on the above context.\n");
        
        return context.toString();
    }
    
    /**
     * Get formatted context with custom template
     */
    public String getFormattedContext(List<SearchResult> results, String template) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        
        StringBuilder docs = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            docs.append(String.format("\nDocument %d:\n", i + 1));
            docs.append("Question: ").append(result.getQuestion()).append("\n");
            if (result.getAnswer() != null) {
                docs.append("Answer: ").append(result.getAnswer()).append("\n");
            }
        }
        
        return template.replace("{DOCUMENTS}", docs.toString());
    }
    
    /**
     * Search and get formatted context in one call
     */
    public String searchAndGetContext(String query, int topK) {
        List<SearchResult> results = search(query, topK);
        return getFormattedContext(results);
    }
    
    /**
     * Check if RAG is initialized and ready
     */
    public boolean isReady() {
        return isInitialized && searchEngine != null && searchEngine.isInitialized();
    }
    
    /**
     * Check if index file exists
     */
    private boolean indexExists(String path) {
        try {
            File file = new File(path);
            if (file.exists()) {
                return true;
            }
            
            // Try as asset
            context.getAssets().open(path).close();
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get index status information
     */
    public String getStatus() {
        if (!isInitialized) {
            return "RAG not initialized";
        }
        if (searchEngine == null || !searchEngine.isInitialized()) {
            return "Search engine not ready";
        }
        return "RAG ready";
    }
    
    /**
     * Close and release resources
     */
    public void close() {
        if (searchEngine != null) {
            searchEngine.close();
            searchEngine = null;
        }
        if (precomputedSearchEngine != null) {
            precomputedSearchEngine.close();
            precomputedSearchEngine = null;
        }
        isInitialized = false;
        usingPrecomputedVectors = false;
        Log.i(TAG, "RAG Context Manager closed");
    }
}
