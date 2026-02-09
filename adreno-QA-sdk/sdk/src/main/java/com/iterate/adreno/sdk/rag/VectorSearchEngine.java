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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Vector search engine with cosine similarity and hybrid boosting
 */
public class VectorSearchEngine {
    private static final String TAG = "VectorSearchEngine";
    private static final float MIN_SIMILARITY_THRESHOLD = 0.1f;
    
    private final Context context;
    private final String modelAssetPath;
    private final String tokenizerAssetPath;
    private VectorEmbeddingGenerator embeddingGenerator;
    private List<Map<String, Object>> embeddings;
    private boolean isInitialized = false;
    
    public VectorSearchEngine(Context context, String modelPath, String tokenizerPath) {
        this.context = context;
        this.modelAssetPath = modelPath;
        this.tokenizerAssetPath = tokenizerPath;
        this.embeddings = new ArrayList<>();
    }
    
    /**
     * Initialize search engine and load index
     */
    public boolean initialize(String indexPath) {
        try {
            Log.i(TAG, "Initializing VectorSearchEngine...");
            Log.i(TAG, "Index path: " + indexPath);
            
            // Copy model and tokenizer assets to cache
            File cacheDir = context.getCacheDir();
            String modelPath = copyAssetToCache(modelAssetPath, cacheDir);
            String tokenizerPath = copyAssetToCache(tokenizerAssetPath, cacheDir);
            
            if (modelPath == null || tokenizerPath == null) {
                Log.e(TAG, "Failed to copy model/tokenizer to cache");
                return false;
            }
            
            // Create embedding generator with file paths
            embeddingGenerator = new VectorEmbeddingGenerator(modelPath, tokenizerPath);
            
            // Initialize embedding generator
            if (!embeddingGenerator.initialize()) {
                Log.e(TAG, "Failed to initialize embedding generator");
                return false;
            }
            
            // Load index
            String jsonContent = loadIndexFile(indexPath);
            if (jsonContent == null) {
                Log.e(TAG, "Failed to load index file");
                return false;
            }
            
            JSONObject root = new JSONObject(jsonContent);
            
            // Log metadata
            if (root.has("metadata")) {
                JSONObject metadata = root.getJSONObject("metadata");
                Log.i(TAG, "Index version: " + metadata.optInt("version", 0));
                Log.i(TAG, "Embedding dim: " + metadata.optInt("embeddingDim", 0));
                Log.i(TAG, "Doc count: " + metadata.optInt("docCount", 0));
            }
            
            // Load embeddings
            if (root.has("embeddings")) {
                JSONArray embeddingsArray = root.getJSONArray("embeddings");
                
                for (int i = 0; i < embeddingsArray.length(); i++) {
                    JSONObject entryJson = embeddingsArray.getJSONObject(i);
                    
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("id", entryJson.getString("id"));
                    entry.put("file", entryJson.getString("file"));
                    entry.put("chunk", entryJson.getInt("chunk"));
                    entry.put("text", entryJson.getString("text"));
                    
                    if (entryJson.has("question")) {
                        entry.put("question", entryJson.getString("question"));
                    }
                    if (entryJson.has("answer")) {
                        entry.put("answer", entryJson.getString("answer"));
                    }
                    if (entryJson.has("type")) {
                        entry.put("type", entryJson.getString("type"));
                    }
                    if (entryJson.has("image_refs")) {
                        JSONArray imageRefsArray = entryJson.getJSONArray("image_refs");
                        List<String> imageRefs = new ArrayList<>();
                        for (int j = 0; j < imageRefsArray.length(); j++) {
                            imageRefs.add(imageRefsArray.getString(j));
                        }
                        entry.put("image_refs", imageRefs);
                    }
                    
                    // Load embedding vector
                    JSONArray vectorJson = entryJson.getJSONArray("embedding");
                    float[] vector = new float[vectorJson.length()];
                    for (int j = 0; j < vectorJson.length(); j++) {
                        vector[j] = (float) vectorJson.getDouble(j);
                    }
                    entry.put("embedding", vector);
                    
                    embeddings.add(entry);
                }
            }
            
            isInitialized = true;
            Log.i(TAG, "✅ VectorSearchEngine initialized with " + embeddings.size() + " embeddings");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing search engine: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Search for similar documents
     */
    public List<SearchResult> search(String query, int topK) {
        if (!isInitialized) {
            Log.e(TAG, "Search engine not initialized");
            return Collections.emptyList();
        }
        
        try {
            Log.i(TAG, "Searching for: " + query);
            
            // Generate query embedding
            float[] queryEmbedding = embeddingGenerator.generateEmbedding(query);
            if (queryEmbedding == null) {
                Log.e(TAG, "Failed to generate query embedding");
                return Collections.emptyList();
            }
            
            // Extract keywords for boosting
            Set<String> queryKeywords = extractKeywords(query);
            
            // Priority queue for top-K results
            PriorityQueue<SearchResult> topResults = new PriorityQueue<>(
                topK + 1, Comparator.comparing(SearchResult::getScore)
            );
            
            // Compute similarities
            for (Map<String, Object> entry : embeddings) {
                String text = (String) entry.get("text");
                float[] embedding = (float[]) entry.get("embedding");
                
                // Cosine similarity
                float score = cosineSimilarity(queryEmbedding, embedding);
                
                // Early termination
                if (score < MIN_SIMILARITY_THRESHOLD) {
                    continue;
                }
                
                // Boost: Exact match
                String textLower = text.toLowerCase(Locale.ROOT).trim();
                String queryLower = query.toLowerCase(Locale.ROOT).trim();
                if (textLower.equals(queryLower)) {
                    score += 0.30f;
                }
                
                // Boost: Keyword overlap
                String questionText = entry.containsKey("question") ? 
                    (String) entry.get("question") : text;
                Set<String> textKeywords = extractKeywords(questionText);
                int overlap = 0;
                for (String kw : queryKeywords) {
                    if (textKeywords.contains(kw)) {
                        overlap++;
                    }
                }
                if (overlap > 0) {
                    score += 0.05f * overlap;
                }
                
                // Create search result
                SearchResult result = new SearchResult(
                    (String) entry.get("id"), 
                    text, 
                    score
                );
                
                if (entry.containsKey("question")) {
                    result.setQuestion((String) entry.get("question"));
                }
                if (entry.containsKey("answer")) {
                    result.setAnswer((String) entry.get("answer"));
                }
                if (entry.containsKey("file")) {
                    result.setFile((String) entry.get("file"));
                }
                if (entry.containsKey("image_refs")) {
                    @SuppressWarnings("unchecked")
                    List<String> imageRefs = (List<String>) entry.get("image_refs");
                    result.setImageRefs(imageRefs);
                }
                
                // Add to priority queue
                topResults.add(result);
                if (topResults.size() > topK) {
                    topResults.poll(); // Remove lowest
                }
            }
            
            // Convert to sorted list (descending)
            List<SearchResult> results = new ArrayList<>(topResults);
            Collections.sort(results, Comparator.comparing(SearchResult::getScore).reversed());
            
            Log.i(TAG, "✅ Found " + results.size() + " results");
            return results;
            
        } catch (Exception e) {
            Log.e(TAG, "Error during search: " + e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Compute cosine similarity
     */
    private float cosineSimilarity(float[] vec1, float[] vec2) {
        float dotProduct = 0.0f;
        float norm1 = 0.0f;
        float norm2 = 0.0f;
        
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }
        
        return dotProduct / (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * Extract keywords from text
     */
    private Set<String> extractKeywords(String text) {
        Set<String> keywords = new HashSet<>();
        if (text == null) return keywords;
        
        String[] tokens = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String token : tokens) {
            if (token.length() > 2) {
                keywords.add(token);
            }
        }
        return keywords;
    }
    
    /**
     * Copy asset to cache directory
     */
    private String copyAssetToCache(String assetPath, File cacheDir) {
        try {
            String fileName = assetPath.substring(assetPath.lastIndexOf('/') + 1);
            File outputFile = new File(cacheDir, fileName);
            
            // Copy if doesn't exist
            if (!outputFile.exists()) {
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
            }
            
            return outputFile.getAbsolutePath();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to copy asset " + assetPath + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Load index file
     */
    private String loadIndexFile(String path) {
        try {
            // Try as file path first
            File file = new File(path);
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
                reader.close();
                return content.toString();
            }
            
            // Try as asset path
            InputStream is = context.getAssets().open(path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            reader.close();
            return content.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading index file: " + path, e);
            return null;
        }
    }
    
    /**
     * Check if initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    /**
     * Close and release resources
     */
    public void close() {
        embeddingGenerator.close();
        embeddings.clear();
        isInitialized = false;
        Log.i(TAG, "VectorSearchEngine closed");
    }
}
