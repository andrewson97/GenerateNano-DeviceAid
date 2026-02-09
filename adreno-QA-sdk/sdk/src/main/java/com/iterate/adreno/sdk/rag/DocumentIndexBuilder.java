package com.iterate.adreno.sdk.rag;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

/**
 * Builds vector index from Q&A JSON input (like reference app)
 */
public class DocumentIndexBuilder {
    private static final String TAG = "DocumentIndexBuilder";
    
    private final String inputFilePath;
    private final String outputFilePath;
    private final VectorEmbeddingGenerator embeddingGenerator;
    
    public DocumentIndexBuilder(String inputFilePath, String outputFilePath, String modelPath, String tokenizerPath) {
        this.inputFilePath = inputFilePath;
        this.outputFilePath = outputFilePath;
        this.embeddingGenerator = new VectorEmbeddingGenerator(modelPath, tokenizerPath);
    }
    
    /**
     * Build index from Input_QA.json
     * 
     * @return true if successful
     */
    public boolean buildIndex() {
        try {
            Log.i(TAG, "Building vector index...");
            Log.i(TAG, "Input: " + inputFilePath);
            Log.i(TAG, "Output: " + outputFilePath);
            
            // Initialize embedding generator
            if (!embeddingGenerator.initialize()) {
                Log.e(TAG, "Failed to initialize embedding generator");
                return false;
            }
            
            // Load Input_QA.json from file
            String jsonContent = loadFileAsString(inputFilePath);
            if (jsonContent == null) {
                Log.e(TAG, "Failed to load input JSON");
                return false;
            }
            
            JSONArray pagesArray = new JSONArray(jsonContent);
            JSONArray embeddings = new JSONArray();
            
            int totalPairs = 0;
            int processedPairs = 0;
            
            // Count total pairs
            for (int i = 0; i < pagesArray.length(); i++) {
                JSONObject page = pagesArray.getJSONObject(i);
                if (page.has("qa_pairs")) {
                    totalPairs += page.getJSONArray("qa_pairs").length();
                }
            }
            
            Log.i(TAG, "Found " + totalPairs + " Q&A pairs across " + pagesArray.length() + " pages");
            
            // Process each page
            for (int pageIndex = 0; pageIndex < pagesArray.length(); pageIndex++) {
                JSONObject page = pagesArray.getJSONObject(pageIndex);
                int pageNumber = page.getInt("page");
                
                if (!page.has("qa_pairs")) continue;
                
                JSONArray qaPairs = page.getJSONArray("qa_pairs");
                
                // Process each Q&A pair
                for (int pairIndex = 0; pairIndex < qaPairs.length(); pairIndex++) {
                    JSONObject qaPair = qaPairs.getJSONObject(pairIndex);
                    String question = qaPair.getString("question");
                    String answer = qaPair.getString("answer");
                    
                    // Handle image references
                    JSONArray imageRefs = new JSONArray();
                    if (qaPair.has("image_refs")) {
                        imageRefs = qaPair.getJSONArray("image_refs");
                    }
                    
                    // Generate embedding for question
                    float[] questionEmbedding = embeddingGenerator.generateEmbedding(question);
                    if (questionEmbedding != null) {
                        JSONObject entry = new JSONObject();
                        entry.put("id", "page" + pageNumber + "_q" + pairIndex);
                        entry.put("file", "page" + pageNumber);
                        entry.put("chunk", pairIndex);
                        entry.put("text", question);
                        entry.put("question", question);
                        entry.put("answer", answer);
                        entry.put("type", "question");
                        
                        if (imageRefs.length() > 0) {
                            entry.put("image_refs", imageRefs);
                        }
                        
                        // Add embedding array
                        JSONArray embeddingArray = new JSONArray();
                        for (float value : questionEmbedding) {
                            embeddingArray.put(value);
                        }
                        entry.put("embedding", embeddingArray);
                        
                        embeddings.put(entry);
                    }
                    
                    // Generate embedding for answer
                    float[] answerEmbedding = embeddingGenerator.generateEmbedding(answer);
                    if (answerEmbedding != null) {
                        JSONObject entry = new JSONObject();
                        entry.put("id", "page" + pageNumber + "_a" + pairIndex);
                        entry.put("file", "page" + pageNumber);
                        entry.put("chunk", pairIndex);
                        entry.put("text", answer);
                        entry.put("question", question);
                        entry.put("answer", answer);
                        entry.put("type", "answer");
                        
                        if (imageRefs.length() > 0) {
                            entry.put("image_refs", imageRefs);
                        }
                        
                        JSONArray embeddingArray = new JSONArray();
                        for (float value : answerEmbedding) {
                            embeddingArray.put(value);
                        }
                        entry.put("embedding", embeddingArray);
                        
                        embeddings.put(entry);
                    }
                    
                    processedPairs++;
                    if (processedPairs % 10 == 0) {
                        Log.i(TAG, "Progress: " + processedPairs + "/" + totalPairs);
                    }
                }
            }
            
            // Build output JSON
            JSONObject output = new JSONObject();
            JSONObject metadata = new JSONObject();
            metadata.put("version", 1);
            metadata.put("embeddingDim", 384);
            metadata.put("docCount", embeddings.length());
            output.put("metadata", metadata);
            output.put("embeddings", embeddings);
            
            // Write to file
            File outputFile = new File(outputFilePath);
            outputFile.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(output.toString().getBytes());
            fos.close();
            
            Log.i(TAG, "✅ Index built successfully: " + embeddings.length() + " embeddings");
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error building index: " + e.getMessage(), e);
            return false;
        } finally {
            embeddingGenerator.close();
        }
    }
    
    /**
     * Load file as string
     */
    private String loadFileAsString(String path) {
        try {
            FileInputStream fis = new FileInputStream(path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            reader.close();
            return content.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error loading file: " + path, e);
            return null;
        }
    }
}
