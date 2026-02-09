package com.iterate.adreno.testapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.iterate.adreno.sdk.rag.RAGContextManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Service for downloading and ingesting QA data and images from remote endpoints
 */
public class DataIngestionService {
    private static final String TAG = "DataIngestionService";
    
    private final Context context;
    private final Handler mainHandler;
    private IngestionCallback callback;
    private volatile boolean isCancelled = false;
    
    // Configuration
    private String qaJsonEndpoint;
    private String imagesBaseUrl;
    private int downloadTimeoutSeconds;
    private long maxDownloadSizeMB;
    private boolean validateJsonFormat;
    private boolean validateImages;
    private int keepBackupVersions;
    
    public interface IngestionCallback {
        void onProgress(String message, int progress);
        void onSuccess(String message, int qaCount);
        void onError(String errorType, String errorMessage);
    }
    
    public DataIngestionService(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        loadConfiguration();
    }
    
    public void setCallback(IngestionCallback callback) {
        this.callback = callback;
    }
    
    public void cancel() {
        isCancelled = true;
        Log.d(TAG, "Data ingestion cancelled by user");
    }
    
    /**
     * Load configuration from data_config.json
     */
    private void loadConfiguration() {
        try {
            InputStream is = context.getAssets().open("data_config.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder json = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            reader.close();
            
            JSONObject config = new JSONObject(json.toString());
            qaJsonEndpoint = config.optString("QA_JSON_ENDPOINT", "");
            imagesBaseUrl = config.optString("IMAGES_BASE_URL", "");
            downloadTimeoutSeconds = config.optInt("DOWNLOAD_TIMEOUT_SECONDS", 300);
            maxDownloadSizeMB = config.optLong("MAX_DOWNLOAD_SIZE_MB", 100);
            validateJsonFormat = config.optBoolean("VALIDATE_JSON_FORMAT", true);
            validateImages = config.optBoolean("VALIDATE_IMAGES", true);
            keepBackupVersions = config.optInt("KEEP_BACKUP_VERSIONS", 3);
            
            Log.i(TAG, "Configuration loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error loading configuration, using defaults: " + e.getMessage());
            // Set defaults
            qaJsonEndpoint = "";
            imagesBaseUrl = "";
            downloadTimeoutSeconds = 300;
            maxDownloadSizeMB = 100;
            validateJsonFormat = true;
            validateImages = true;
            keepBackupVersions = 3;
        }
    }
    
    /**
     * Start data ingestion process with custom endpoints
     */
    public void startIngestion(String customQaEndpoint, String customImagesBaseUrl) {
        // Use custom endpoints if provided, otherwise use config
        final String qaUrl = (customQaEndpoint != null && !customQaEndpoint.isEmpty()) 
            ? customQaEndpoint : qaJsonEndpoint;
        final String imgUrl = (customImagesBaseUrl != null && !customImagesBaseUrl.isEmpty()) 
            ? customImagesBaseUrl : imagesBaseUrl;
        
        isCancelled = false;
        
        // Run in background thread
        new Thread(() -> {
            try {
                performIngestion(qaUrl, imgUrl);
            } catch (Exception e) {
                Log.e(TAG, "Fatal error during ingestion: " + e.getMessage(), e);
                notifyError("FATAL_ERROR", "Unexpected error: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Main ingestion workflow
     */
    private void performIngestion(String qaUrl, String imgUrl) {
        try {
            // Step 1: Backup current data
            notifyProgress("Backing up current data...", 5);
            if (!backupCurrentData()) {
                notifyError("BACKUP_FAILED", "Failed to backup current data. Aborting to prevent data loss.");
                return;
            }
            
            if (isCancelled) {
                notifyError("CANCELLED", "Operation cancelled by user");
                return;
            }
            
            // Step 2: Download QA JSON
            notifyProgress("Downloading QA data from endpoint...", 15);
            File tempQaFile = downloadQaJson(qaUrl);
            if (tempQaFile == null) {
                rollbackData();
                return;
            }
            
            if (isCancelled) {
                cleanupTempFiles(tempQaFile);
                notifyError("CANCELLED", "Operation cancelled by user");
                return;
            }
            
            // Step 3: Validate JSON format
            if (validateJsonFormat) {
                notifyProgress("Validating JSON format...", 30);
                ValidationResult validation = validateQaJson(tempQaFile);
                if (!validation.isValid) {
                    cleanupTempFiles(tempQaFile);
                    rollbackData();
                    notifyError("INVALID_JSON", validation.errorMessage);
                    return;
                }
            }
            
            if (isCancelled) {
                cleanupTempFiles(tempQaFile);
                notifyError("CANCELLED", "Operation cancelled by user");
                return;
            }
            
            // Step 4: Download images
            notifyProgress("Downloading referenced images...", 45);
            List<String> imageRefs = extractImageReferences(tempQaFile);
            if (!downloadImages(imageRefs, imgUrl)) {
                cleanupTempFiles(tempQaFile);
                rollbackData();
                return;
            }
            
            if (isCancelled) {
                cleanupTempFiles(tempQaFile);
                notifyError("CANCELLED", "Operation cancelled by user");
                return;
            }
            
            // Step 5: Replace old data with new
            notifyProgress("Installing new data...", 75);
            if (!installNewData(tempQaFile)) {
                rollbackData();
                return;
            }
            
            // Step 6: Rebuild RAG index
            notifyProgress("Rebuilding search index...", 85);
            if (!rebuildIndex()) {
                rollbackData();
                notifyError("INDEX_BUILD_FAILED", "Failed to rebuild search index. Rolled back to previous version.");
                return;
            }
            
            // Step 7: Cleanup old backups
            notifyProgress("Cleaning up...", 95);
            cleanupOldBackups();
            cleanupTempFiles(tempQaFile);
            
            // Success!
            int qaCount = countQaPairs(getDocsDir() + "/Input_QA.json");
            notifySuccess("Data updated successfully!", qaCount);
            
        } catch (Exception e) {
            Log.e(TAG, "Error during ingestion: " + e.getMessage(), e);
            rollbackData();
            notifyError("UNEXPECTED_ERROR", "An unexpected error occurred: " + e.getMessage());
        }
    }
    
    /**
     * Backup current data
     */
    private boolean backupCurrentData() {
        try {
            File docsDir = new File(getDocsDir());
            if (!docsDir.exists()) {
                Log.w(TAG, "No existing data to backup");
                return true;
            }
            
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File backupDir = new File(context.getFilesDir(), "DOCS_backup_" + timestamp);
            
            if (!copyDirectory(docsDir, backupDir)) {
                Log.e(TAG, "Failed to create backup");
                return false;
            }
            
            Log.i(TAG, "Backup created: " + backupDir.getAbsolutePath());
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating backup: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Download QA JSON from endpoint
     */
    private File downloadQaJson(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(downloadTimeoutSeconds * 1000);
            connection.setReadTimeout(downloadTimeoutSeconds * 1000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                notifyError("DOWNLOAD_FAILED", "Server returned error code: " + responseCode);
                return null;
            }
            
            long contentLength = connection.getContentLengthLong();
            if (contentLength > maxDownloadSizeMB * 1024 * 1024) {
                notifyError("FILE_TOO_LARGE", "QA JSON file exceeds maximum size of " + maxDownloadSizeMB + "MB");
                return null;
            }
            
            File tempFile = new File(context.getCacheDir(), "temp_qa.json");
            FileOutputStream fos = new FileOutputStream(tempFile);
            BufferedInputStream bis = new BufferedInputStream(connection.getInputStream());
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;
            
            while ((bytesRead = bis.read(buffer)) != -1 && !isCancelled) {
                fos.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                
                if (contentLength > 0) {
                    int progress = (int) (15 + (totalRead * 15 / contentLength));
                    notifyProgress("Downloading QA data... " + (totalRead / 1024) + "KB", progress);
                }
            }
            
            bis.close();
            fos.close();
            
            if (isCancelled) {
                tempFile.delete();
                return null;
            }
            
            Log.i(TAG, "QA JSON downloaded: " + tempFile.length() + " bytes");
            return tempFile;
            
        } catch (Exception e) {
            Log.e(TAG, "Error downloading QA JSON: " + e.getMessage(), e);
            notifyError("DOWNLOAD_FAILED", "Failed to download QA data: " + e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Validate QA JSON format (supports both Input_QA.json and vectorIndex.json)
     */
    private ValidationResult validateQaJson(File jsonFile) {
        try {
            FileInputStream fis = new FileInputStream(jsonFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            StringBuilder content = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            reader.close();
            
            String jsonContent = content.toString();
            
            // Detect format: vectorIndex.json or Input_QA.json
            if (jsonContent.contains("\"vectors\"") && jsonContent.contains("\"metadata\"")) {
                // vectorIndex.json format
                return validateVectorIndexJson(jsonContent);
            } else {
                // Input_QA.json format (array of pages)
                return validateInputQaJson(jsonContent);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "JSON validation error: " + e.getMessage(), e);
            return new ValidationResult(false, "Invalid JSON format: " + e.getMessage());
        }
    }
    
    /**
     * Validate vectorIndex.json format
     */
    private ValidationResult validateVectorIndexJson(String jsonContent) {
        try {
            JSONObject root = new JSONObject(jsonContent);
            
            if (!root.has("vectors")) {
                return new ValidationResult(false, "vectorIndex.json missing 'vectors' array");
            }
            
            JSONArray vectors = root.getJSONArray("vectors");
            
            if (vectors.length() == 0) {
                return new ValidationResult(false, "vectorIndex.json contains no vectors");
            }
            
            // Validate first few vectors
            int samplesToCheck = Math.min(5, vectors.length());
            for (int i = 0; i < samplesToCheck; i++) {
                JSONObject vectorEntry = vectors.getJSONObject(i);
                
                if (!vectorEntry.has("id") || !vectorEntry.has("vector") || !vectorEntry.has("metadata")) {
                    return new ValidationResult(false, "Vector " + i + " missing required fields");
                }
                
                JSONObject metadata = vectorEntry.getJSONObject("metadata");
                if (!metadata.has("question") || !metadata.has("answer")) {
                    return new ValidationResult(false, "Vector " + i + " metadata missing question or answer");
                }
            }
            
            Log.i(TAG, "vectorIndex.json validation passed: " + vectors.length() + " vectors found");
            return new ValidationResult(true, "Valid vectorIndex.json with " + vectors.length() + " vectors");
            
        } catch (Exception e) {
            return new ValidationResult(false, "Invalid vectorIndex.json: " + e.getMessage());
        }
    }
    
    /**
     * Validate Input_QA.json format
     */
    private ValidationResult validateInputQaJson(String jsonContent) {
        try {
            JSONArray pagesArray = new JSONArray(jsonContent);
            
            if (pagesArray.length() == 0) {
                return new ValidationResult(false, "JSON file contains no pages");
            }
            
            int totalQaPairs = 0;
            for (int i = 0; i < pagesArray.length(); i++) {
                JSONObject page = pagesArray.getJSONObject(i);
                
                if (!page.has("page")) {
                    return new ValidationResult(false, "Page " + i + " missing 'page' field");
                }
                
                if (!page.has("qa_pairs")) {
                    continue; // Empty page is OK
                }
                
                JSONArray qaPairs = page.getJSONArray("qa_pairs");
                for (int j = 0; j < qaPairs.length(); j++) {
                    JSONObject qaPair = qaPairs.getJSONObject(j);
                    
                    if (!qaPair.has("question") || !qaPair.has("answer")) {
                        return new ValidationResult(false, 
                            "Page " + i + ", QA pair " + j + " missing question or answer");
                    }
                    
                    totalQaPairs++;
                }
            }
            
            if (totalQaPairs == 0) {
                return new ValidationResult(false, "JSON file contains no QA pairs");
            }
            
            Log.i(TAG, "Input_QA.json validation passed: " + totalQaPairs + " QA pairs found");
            return new ValidationResult(true, "Valid Input_QA.json with " + totalQaPairs + " QA pairs");
            
        } catch (Exception e) {
            return new ValidationResult(false, "Invalid Input_QA.json: " + e.getMessage());
        }
    }
    
    /**
     * Extract image references from QA JSON
     */
    private List<String> extractImageReferences(File jsonFile) {
        Set<String> imageRefs = new HashSet<>();
        try {
            FileInputStream fis = new FileInputStream(jsonFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            StringBuilder content = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            reader.close();
            
            JSONArray pagesArray = new JSONArray(content.toString());
            
            for (int i = 0; i < pagesArray.length(); i++) {
                JSONObject page = pagesArray.getJSONObject(i);
                if (!page.has("qa_pairs")) continue;
                
                JSONArray qaPairs = page.getJSONArray("qa_pairs");
                for (int j = 0; j < qaPairs.length(); j++) {
                    JSONObject qaPair = qaPairs.getJSONObject(j);
                    
                    if (qaPair.has("image_refs")) {
                        JSONArray refs = qaPair.getJSONArray("image_refs");
                        for (int k = 0; k < refs.length(); k++) {
                            imageRefs.add(refs.getString(k));
                        }
                    }
                }
            }
            
            Log.i(TAG, "Found " + imageRefs.size() + " unique image references");
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting image references: " + e.getMessage(), e);
        }
        
        return new ArrayList<>(imageRefs);
    }
    
    /**
     * Download images from endpoint
     */
    private boolean downloadImages(List<String> imageRefs, String baseUrl) {
        if (imageRefs.isEmpty()) {
            Log.i(TAG, "No images to download");
            return true;
        }
        
        File imagesDir = new File(context.getCacheDir(), "temp_images");
        imagesDir.mkdirs();
        
        int successCount = 0;
        int totalImages = imageRefs.size();
        
        for (int i = 0; i < totalImages && !isCancelled; i++) {
            String imageName = imageRefs.get(i);
            String imageUrl = baseUrl + imageName;
            
            int progress = 45 + (i * 30 / totalImages);
            notifyProgress("Downloading images... (" + (i + 1) + "/" + totalImages + ")", progress);
            
            if (downloadImage(imageUrl, new File(imagesDir, imageName))) {
                successCount++;
            } else if (validateImages) {
                notifyError("IMAGE_DOWNLOAD_FAILED", 
                    "Failed to download required image: " + imageName + "\nRolling back to previous version.");
                return false;
            }
        }
        
        if (isCancelled) {
            return false;
        }
        
        Log.i(TAG, "Downloaded " + successCount + "/" + totalImages + " images");
        return true;
    }
    
    /**
     * Download single image
     */
    private boolean downloadImage(String urlString, File outputFile) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                Log.w(TAG, "Failed to download image: " + urlString + " (code: " + responseCode + ")");
                return false;
            }
            
            FileOutputStream fos = new FileOutputStream(outputFile);
            BufferedInputStream bis = new BufferedInputStream(connection.getInputStream());
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = bis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            
            bis.close();
            fos.close();
            
            return true;
            
        } catch (Exception e) {
            Log.w(TAG, "Error downloading image " + urlString + ": " + e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Install new data (replace old with new)
     */
    private boolean installNewData(File newQaFile) {
        try {
            File docsDir = new File(getDocsDir());
            docsDir.mkdirs();
            
            // Copy new QA JSON
            File targetQaFile = new File(docsDir, "Input_QA.json");
            if (!copyFile(newQaFile, targetQaFile)) {
                Log.e(TAG, "Failed to copy new QA JSON");
                return false;
            }
            
            // Copy new images
            File tempImagesDir = new File(context.getCacheDir(), "temp_images");
            if (tempImagesDir.exists()) {
                File targetImagesDir = new File(docsDir, "images");
                targetImagesDir.mkdirs();
                
                if (!copyDirectory(tempImagesDir, targetImagesDir)) {
                    Log.e(TAG, "Failed to copy new images");
                    return false;
                }
            }
            
            Log.i(TAG, "New data installed successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error installing new data: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Rebuild RAG index
     */
    private boolean rebuildIndex() {
        try {
            RAGContextManager ragManager = RAGContextManager.getInstance();
            
            String inputJsonPath = getDocsDir() + "/Input_QA.json";
            String outputIndexPath = getDocsDir() + "/document_embeddings.json";
            
            boolean success = ragManager.buildIndex(
                context,
                "DOCS/Input_QA.json",
                "models/InterplayGTE.onnx",
                "models/bert_tokenizer.json",
                outputIndexPath
            );
            
            if (success) {
                Log.i(TAG, "RAG index rebuilt successfully");
            } else {
                Log.e(TAG, "Failed to rebuild RAG index");
            }
            
            return success;
            
        } catch (Exception e) {
            Log.e(TAG, "Error rebuilding index: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Rollback to previous version
     */
    private void rollbackData() {
        try {
            notifyProgress("Rolling back to previous version...", 90);
            
            // Find most recent backup
            File filesDir = context.getFilesDir();
            File[] backups = filesDir.listFiles((dir, name) -> name.startsWith("DOCS_backup_"));
            
            if (backups == null || backups.length == 0) {
                Log.w(TAG, "No backup found for rollback");
                return;
            }
            
            // Sort by name (timestamp) descending
            java.util.Arrays.sort(backups, (a, b) -> b.getName().compareTo(a.getName()));
            File latestBackup = backups[0];
            
            // Restore from backup
            File docsDir = new File(getDocsDir());
            deleteDirectory(docsDir);
            
            if (copyDirectory(latestBackup, docsDir)) {
                Log.i(TAG, "Rolled back to backup: " + latestBackup.getName());
            } else {
                Log.e(TAG, "Failed to rollback data");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error during rollback: " + e.getMessage(), e);
        }
    }
    
    /**
     * Cleanup old backups (keep only N most recent)
     */
    private void cleanupOldBackups() {
        try {
            File filesDir = context.getFilesDir();
            File[] backups = filesDir.listFiles((dir, name) -> name.startsWith("DOCS_backup_"));
            
            if (backups == null || backups.length <= keepBackupVersions) {
                return;
            }
            
            // Sort by name (timestamp) descending
            java.util.Arrays.sort(backups, (a, b) -> b.getName().compareTo(a.getName()));
            
            // Delete old backups
            for (int i = keepBackupVersions; i < backups.length; i++) {
                deleteDirectory(backups[i]);
                Log.i(TAG, "Deleted old backup: " + backups[i].getName());
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up backups: " + e.getMessage(), e);
        }
    }
    
    /**
     * Cleanup temporary files
     */
    private void cleanupTempFiles(File... files) {
        for (File file : files) {
            if (file != null && file.exists()) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        
        // Also cleanup temp images directory
        File tempImagesDir = new File(context.getCacheDir(), "temp_images");
        if (tempImagesDir.exists()) {
            deleteDirectory(tempImagesDir);
        }
    }
    
    /**
     * Count QA pairs in JSON file
     */
    private int countQaPairs(String jsonPath) {
        try {
            FileInputStream fis = new FileInputStream(jsonPath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            StringBuilder content = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            reader.close();
            
            JSONArray pagesArray = new JSONArray(content.toString());
            int count = 0;
            
            for (int i = 0; i < pagesArray.length(); i++) {
                JSONObject page = pagesArray.getJSONObject(i);
                if (page.has("qa_pairs")) {
                    count += page.getJSONArray("qa_pairs").length();
                }
            }
            
            return count;
            
        } catch (Exception e) {
            Log.e(TAG, "Error counting QA pairs: " + e.getMessage());
            return 0;
        }
    }
    
    // Utility methods
    
    private String getDocsDir() {
        return context.getFilesDir().getAbsolutePath() + "/DOCS";
    }
    
    private boolean copyFile(File source, File dest) {
        try {
            FileInputStream fis = new FileInputStream(source);
            FileOutputStream fos = new FileOutputStream(dest);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            
            fis.close();
            fos.close();
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error copying file: " + e.getMessage());
            return false;
        }
    }
    
    private boolean copyDirectory(File source, File dest) {
        try {
            if (!dest.exists()) {
                dest.mkdirs();
            }
            
            File[] files = source.listFiles();
            if (files == null) return true;
            
            for (File file : files) {
                File destFile = new File(dest, file.getName());
                
                if (file.isDirectory()) {
                    if (!copyDirectory(file, destFile)) {
                        return false;
                    }
                } else {
                    if (!copyFile(file, destFile)) {
                        return false;
                    }
                }
            }
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error copying directory: " + e.getMessage());
            return false;
        }
    }
    
    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }
    
    private void notifyProgress(final String message, final int progress) {
        Log.d(TAG, message + " (" + progress + "%)");
        if (callback != null) {
            mainHandler.post(() -> callback.onProgress(message, progress));
        }
    }
    
    private void notifySuccess(final String message, final int qaCount) {
        Log.i(TAG, message);
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(message, qaCount));
        }
    }
    
    private void notifyError(final String errorType, final String errorMessage) {
        Log.e(TAG, errorType + ": " + errorMessage);
        if (callback != null) {
            mainHandler.post(() -> callback.onError(errorType, errorMessage));
        }
    }
    
    // Helper classes
    
    private static class ValidationResult {
        final boolean isValid;
        final String errorMessage;
        
        ValidationResult(boolean isValid, String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }
    }
}
