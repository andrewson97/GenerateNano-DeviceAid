package com.iterate.adreno.sdk.rag;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/**
 * Generates vector embeddings using ONNX model (GTE-Tiny) - file path based like reference app
 */
public class VectorEmbeddingGenerator {
    private static final String TAG = "VectorEmbedding";
    private static final int MAX_SEQUENCE_LENGTH = 512;
    private static final int EMBEDDING_DIM = 384;
    
    private final String modelPath;
    private final String tokenizerPath;
    
    private OrtEnvironment ortEnvironment;
    private OrtSession ortSession;
    private Map<String, Integer> tokenToId;
    private boolean isInitialized = false;
    
    public VectorEmbeddingGenerator(String modelPath, String tokenizerPath) {
        this.modelPath = modelPath;
        this.tokenizerPath = tokenizerPath;
        this.tokenToId = new HashMap<>();
    }
    
    /**
     * Initialize the embedding generator
     */
    public boolean initialize() {
        try {
            Log.i(TAG, "Initializing VectorEmbeddingGenerator...");
            Log.i(TAG, "Model path: " + modelPath);
            Log.i(TAG, "Tokenizer path: " + tokenizerPath);
            
            // Check if files exist
            File modelFile = new File(modelPath);
            File tokenizerFile = new File(tokenizerPath);
            
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found: " + modelPath);
                return false;
            }
            
            if (!tokenizerFile.exists()) {
                Log.e(TAG, "Tokenizer file not found: " + tokenizerPath);
                return false;
            }
            
            // Load model from file
            byte[] modelBytes = loadFileAsBytes(modelPath);
            if (modelBytes == null) {
                Log.e(TAG, "Failed to load model from file: " + modelPath);
                return false;
            }
            
            // Initialize ONNX Runtime
            ortEnvironment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
            sessionOptions.setIntraOpNumThreads(2);
            sessionOptions.setMemoryPatternOptimization(true);
            
            ortSession = ortEnvironment.createSession(modelBytes, sessionOptions);
            Log.i(TAG, "ONNX model loaded successfully");
            
            // Load tokenizer
            loadTokenizer();
            
            isInitialized = true;
            Log.i(TAG, "VectorEmbeddingGenerator initialized successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing VectorEmbeddingGenerator: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Load tokenizer vocabulary from file
     */
    private void loadTokenizer() throws Exception {
        FileInputStream fis = new FileInputStream(tokenizerPath);
        BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line);
        }
        reader.close();
        
        // Parse tokenizer JSON
        JSONObject tokenizerJson = new JSONObject(content.toString());
        if (tokenizerJson.has("model") && tokenizerJson.getJSONObject("model").has("vocab")) {
            JSONObject vocab = tokenizerJson.getJSONObject("model").getJSONObject("vocab");
            Iterator<String> keys = vocab.keys();
            while (keys.hasNext()) {
                String token = keys.next();
                tokenToId.put(token, vocab.getInt(token));
            }
        }
        
        Log.i(TAG, "Loaded " + tokenToId.size() + " tokens into vocabulary");
    }
    
    /**
     * Load file as byte array
     */
    private byte[] loadFileAsBytes(String path) {
        try {
            FileInputStream fis = new FileInputStream(path);
            byte[] bytes = new byte[fis.available()];
            fis.read(bytes);
            fis.close();
            return bytes;
        } catch (Exception e) {
            Log.e(TAG, "Error loading file: " + path, e);
            return null;
        }
    }
    
    /**
     * Generate embedding for text
     */
    public float[] generateEmbedding(String text) {
        if (!isInitialized) {
            Log.e(TAG, "Generator not initialized");
            return null;
        }
        
        if (text == null || text.trim().isEmpty()) {
            Log.e(TAG, "Cannot generate embedding for empty text");
            return null;
        }
        
        try {
            // Tokenize
            Map<String, OnnxTensor> inputTensors = tokenize(text);
            
            // Run inference
            OrtSession.Result output = ortSession.run(inputTensors);
            
            // Extract embedding
            OnnxTensor embeddingTensor = (OnnxTensor) output.get(0);
            Object tensorData = embeddingTensor.getValue();
            
            float[] embedding;
            if (tensorData instanceof float[][][]) {
                float[][][] embeddingArray = (float[][][]) tensorData;
                embedding = embeddingArray[0][0]; // [CLS] token embedding
            } else if (tensorData instanceof float[][]) {
                float[][] embeddingArray = (float[][]) tensorData;
                embedding = embeddingArray[0];
            } else {
                embedding = (float[]) tensorData;
            }
            
            // Cleanup
            output.close();
            for (OnnxTensor tensor : inputTensors.values()) {
                tensor.close();
            }
            
            return embedding;
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating embedding: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Tokenize text for BERT model
     */
    private Map<String, OnnxTensor> tokenize(String text) throws OrtException {
        String[] words = text.toLowerCase().split("\\s+");
        
        List<Integer> inputIds = new ArrayList<>();
        List<Integer> attentionMask = new ArrayList<>();
        List<Integer> tokenTypeIds = new ArrayList<>();
        
        // Add [CLS] token
        inputIds.add(tokenToId.getOrDefault("[CLS]", 101));
        attentionMask.add(1);
        tokenTypeIds.add(0);
        
        // Add words
        for (String word : words) {
            if (inputIds.size() >= MAX_SEQUENCE_LENGTH - 1) break;
            
            inputIds.add(tokenToId.getOrDefault(word, tokenToId.getOrDefault("[UNK]", 100)));
            attentionMask.add(1);
            tokenTypeIds.add(0);
        }
        
        // Add [SEP] token
        inputIds.add(tokenToId.getOrDefault("[SEP]", 102));
        attentionMask.add(1);
        tokenTypeIds.add(0);
        
        // Convert to arrays
        int seqLength = inputIds.size();
        long[][] inputIdsArray = new long[1][seqLength];
        long[][] attentionMaskArray = new long[1][seqLength];
        long[][] tokenTypeIdsArray = new long[1][seqLength];
        
        for (int i = 0; i < seqLength; i++) {
            inputIdsArray[0][i] = inputIds.get(i);
            attentionMaskArray[0][i] = attentionMask.get(i);
            tokenTypeIdsArray[0][i] = tokenTypeIds.get(i);
        }
        
        // Create tensors
        Map<String, OnnxTensor> result = new HashMap<>();
        result.put("input_ids", OnnxTensor.createTensor(ortEnvironment, inputIdsArray));
        result.put("attention_mask", OnnxTensor.createTensor(ortEnvironment, attentionMaskArray));
        result.put("token_type_ids", OnnxTensor.createTensor(ortEnvironment, tokenTypeIdsArray));
        
        return result;
    }
    
    /**
     * Close and release resources
     */
    public synchronized void close() {
        try {
            if (ortSession != null) {
                ortSession.close();
                ortSession = null;
            }
            if (ortEnvironment != null) {
                ortEnvironment.close();
                ortEnvironment = null;
            }
            tokenToId = null;
            isInitialized = false;
            Log.i(TAG, "VectorEmbeddingGenerator closed");
        } catch (Exception e) {
            Log.e(TAG, "Error closing generator: " + e.getMessage(), e);
        }
    }
}
