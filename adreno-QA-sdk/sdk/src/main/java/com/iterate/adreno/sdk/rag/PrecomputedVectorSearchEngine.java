package com.iterate.adreno.sdk.rag;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Vector search engine for pre-computed vectors (vectorIndex.json format)
 * This engine loads vectors that were already computed by the dashboard,
 * eliminating the need for on-device embedding generation.
 */
public class PrecomputedVectorSearchEngine {
    private static final String TAG = "PrecomputedVectorSearch";
    private static final float MIN_SIMILARITY_THRESHOLD = 0.1f;
    
    private final Context context;
    private final String modelAssetPath;
    private final String tokenizerAssetPath;
    private VectorEmbeddingGenerator embeddingGenerator;
    private List<VectorEntry> vectors;
    private boolean isInitialized = false;
    private String indexModel;
    private String generatedAt;
    
    /**
     * Vector entry with pre-computed embedding and metadata
     */
    private static class VectorEntry {
        String id;
        float[] vector;
        String question;
        String answer;
        String category;
        
        VectorEntry(String id, float[] vector, String question, String answer, String category) {
            this.id = id;
            this.vector = vector;
            this.question = question;
            this.answer = answer;
            this.category = category;
        }
    }
    
    public PrecomputedVectorSearchEngine(Context context, String modelPath, String tokenizerPath) {
        this.context = context;
        this.modelAssetPath = modelPath;
        this.tokenizerAssetPath = tokenizerPath;
        this.vectors = new ArrayList<>();
    }
    
    /**
     * Initialize search engine and load pre-computed vectors
     */
    public boolean initialize(String indexPath) {
        try {
            Log.i(TAG, "Initializing PrecomputedVectorSearchEngine...");
            Log.i(TAG, "Index path: " + indexPath);
            
            // Copy model and tokenizer assets to cache (needed for query embedding)
            File cacheDir = context.getCacheDir();
            String modelPath = copyAssetToCache(modelAssetPath, cacheDir);
            String tokenizerPath = copyAssetToCache(tokenizerAssetPath, cacheDir);
            
            if (modelPath == null || tokenizerPath == null) {
                Log.e(TAG, "Failed to copy model/tokenizer to cache");
                return false;
            }
            
            // Create embedding generator for query embedding
            embeddingGenerator = new VectorEmbeddingGenerator(modelPath, tokenizerPath);
            
            // Initialize embedding generator
            if (!embeddingGenerator.initialize()) {
                Log.e(TAG, "Failed to initialize embedding generator");
                return false;
            }
            
            // Load vectorIndex.json
            String jsonContent = loadIndexFile(indexPath);
            if (jsonContent == null) {
                Log.e(TAG, "Failed to load index file");
                return false;
            }
            
            JSONObject root = new JSONObject(jsonContent);
            
            // Parse metadata
            if (root.has("version")) {
                Log.i(TAG, "Vector index version: " + root.getString("version"));
            }
            if (root.has("model")) {
                indexModel = root.getString("model");
                Log.i(TAG, "Index model: " + indexModel);
            }
            if (root.has("generatedAt")) {
                generatedAt = root.getString("generatedAt");
                Log.i(TAG, "Generated at: " + generatedAt);
            }
            
            // Load vectors
            if (!root.has("vectors")) {
                Log.e(TAG, "No 'vectors' array found in index");
                return false;
            }
            
            JSONArray vectorsArray = root.getJSONArray("vectors");
            Log.i(TAG, "Loading " + vectorsArray.length() + " pre-computed vectors...");
            
            for (int i = 0; i < vectorsArray.length(); i++) {
                JSONObject vectorObj = vectorsArray.getJSONObject(i);
                
                // Parse vector entry
                String id = vectorObj.getString("id");
                JSONArray vectorArray = vectorObj.getJSONArray("vector");
                
                // Convert JSONArray to float[]
                float[] vector = new float[vectorArray.length()];
                for (int j = 0; j < vectorArray.length(); j++) {
                    vector[j] = (float) vectorArray.getDouble(j);
                }
                
                // Parse metadata
                JSONObject metadata = vectorObj.getJSONObject("metadata");
                String question = metadata.getString("question");
                String answer = metadata.getString("answer");
                String category = metadata.optString("category", "general");
                
                // Create and store vector entry
                VectorEntry entry = new VectorEntry(id, vector, question, answer, category);
                vectors.add(entry);
                
                if ((i + 1) % 50 == 0) {
                    Log.d(TAG, "Loaded " + (i + 1) + "/" + vectorsArray.length() + " vectors");
                }
            }
            
            isInitialized = true;
            Log.i(TAG, "✅ PrecomputedVectorSearchEngine initialized with " + vectors.size() + " vectors");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing PrecomputedVectorSearchEngine: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Search for most relevant documents using cosine similarity
     */
    public List<SearchResult> search(String query, int topK) {
        if (!isInitialized) {
            Log.e(TAG, "Search engine not initialized");
            return new ArrayList<>();
        }
        
        try {
            Log.d(TAG, "Searching for: '" + query + "' (top " + topK + ")");
            
            // Generate embedding for query
            float[] queryEmbedding = embeddingGenerator.generateEmbedding(query);
            if (queryEmbedding == null) {
                Log.e(TAG, "Failed to generate query embedding");
                return new ArrayList<>();
            }
            
            // Calculate cosine similarity with all vectors
            PriorityQueue<SearchResult> topResults = new PriorityQueue<>(
                topK, 
                Comparator.comparingDouble(SearchResult::getScore)
            );
            
            for (VectorEntry entry : vectors) {
                float similarity = cosineSimilarity(queryEmbedding, entry.vector);
                
                if (similarity >= MIN_SIMILARITY_THRESHOLD) {
                    SearchResult result = new SearchResult(
                        entry.id,
                        entry.question,
                        entry.answer,
                        similarity,
                        entry.category
                    );
                    
                    if (topResults.size() < topK) {
                        topResults.offer(result);
                    } else if (similarity > topResults.peek().getScore()) {
                        topResults.poll();
                        topResults.offer(result);
                    }
                }
            }
            
            // Convert to list and sort by score (descending)
            List<SearchResult> results = new ArrayList<>(topResults);
            Collections.sort(results, (a, b) -> Float.compare(b.getScore(), a.getScore()));
            
            Log.i(TAG, "Found " + results.size() + " results above threshold");
            if (!results.isEmpty()) {
                Log.d(TAG, "Top result: " + results.get(0).getQuestion() + " (score: " + 
                      String.format("%.3f", results.get(0).getScore()) + ")");
            }
            
            return results;
            
        } catch (Exception e) {
            Log.e(TAG, "Error during search: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Calculate cosine similarity between two vectors
     */
    private float cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            Log.e(TAG, "Vector dimension mismatch: " + vec1.length + " vs " + vec2.length);
            return 0.0f;
        }
        
        float dotProduct = 0.0f;
        float norm1 = 0.0f;
        float norm2 = 0.0f;
        
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }
        
        if (norm1 == 0.0f || norm2 == 0.0f) {
            return 0.0f;
        }
        
        return dotProduct / (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * Load index file from path
     */
    private String loadIndexFile(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) {
                Log.e(TAG, "Index file not found: " + path);
                return null;
            }
            
            FileInputStream fis = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            StringBuilder content = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            
            reader.close();
            return content.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading index file: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Copy asset to cache directory
     */
    private String copyAssetToCache(String assetPath, File cacheDir) {
        try {
            String fileName = assetPath.substring(assetPath.lastIndexOf('/') + 1);
            File outputFile = new File(cacheDir, fileName);
            
            // Skip if already exists
            if (outputFile.exists()) {
                return outputFile.getAbsolutePath();
            }
            
            InputStream is = context.getAssets().open(assetPath);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            
            is.close();
            fos.close();
            
            return outputFile.getAbsolutePath();
            
        } catch (Exception e) {
            Log.e(TAG, "Error copying asset: " + assetPath, e);
            return null;
        }
    }
    
    /**
     * Check if engine is initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    /**
     * Get number of loaded vectors
     */
    public int getVectorCount() {
        return vectors.size();
    }
    
    /**
     * Get index model name
     */
    public String getIndexModel() {
        return indexModel;
    }
    
    /**
     * Close and cleanup resources
     */
    public void close() {
        if (embeddingGenerator != null) {
            embeddingGenerator.close();
        }
        vectors.clear();
        isInitialized = false;
        Log.i(TAG, "PrecomputedVectorSearchEngine closed");
    }
}
