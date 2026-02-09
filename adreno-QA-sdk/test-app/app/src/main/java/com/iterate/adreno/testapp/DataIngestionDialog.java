package com.iterate.adreno.testapp;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Dialog for entering data ingestion endpoints and showing progress
 */
public class DataIngestionDialog extends Dialog {
    
    private EditText qaEndpointInput;
    private EditText imagesEndpointInput;
    private Button startButton;
    private Button cancelButton;
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView statusText;
    
    private DataIngestionListener listener;
    private boolean isInProgress = false;
    
    public interface DataIngestionListener {
        void onStartIngestion(String qaEndpoint, String imagesEndpoint);
        void onCancelIngestion();
    }
    
    public DataIngestionDialog(Context context, DataIngestionListener listener) {
        super(context);
        this.listener = listener;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_data_ingestion);
        
        initializeViews();
        setupListeners();
    }
    
    private void initializeViews() {
        qaEndpointInput = findViewById(R.id.qaEndpointInput);
        imagesEndpointInput = findViewById(R.id.imagesEndpointInput);
        startButton = findViewById(R.id.startButton);
        cancelButton = findViewById(R.id.cancelButton);
        progressBar = findViewById(R.id.progressBar);
        progressText = findViewById(R.id.progressText);
        statusText = findViewById(R.id.statusText);
        
        // Set placeholder text
        qaEndpointInput.setHint("https://your-server.com/api/Input_QA.json");
        imagesEndpointInput.setHint("https://your-server.com/api/images/");
        
        // Initially hide progress views
        progressBar.setVisibility(View.GONE);
        progressText.setVisibility(View.GONE);
        statusText.setVisibility(View.GONE);
    }
    
    private void setupListeners() {
        startButton.setOnClickListener(v -> {
            String qaEndpoint = qaEndpointInput.getText().toString().trim();
            String imagesEndpoint = imagesEndpointInput.getText().toString().trim();
            
            if (TextUtils.isEmpty(qaEndpoint)) {
                qaEndpointInput.setError("QA endpoint is required");
                return;
            }
            
            if (TextUtils.isEmpty(imagesEndpoint)) {
                imagesEndpointInput.setError("Images endpoint is required");
                return;
            }
            
            if (!qaEndpoint.startsWith("http://") && !qaEndpoint.startsWith("https://")) {
                qaEndpointInput.setError("Must start with http:// or https://");
                return;
            }
            
            if (!imagesEndpoint.startsWith("http://") && !imagesEndpoint.startsWith("https://")) {
                imagesEndpointInput.setError("Must start with http:// or https://");
                return;
            }
            
            startIngestion(qaEndpoint, imagesEndpoint);
        });
        
        cancelButton.setOnClickListener(v -> {
            if (isInProgress) {
                if (listener != null) {
                    listener.onCancelIngestion();
                }
            }
            dismiss();
        });
    }
    
    private void startIngestion(String qaEndpoint, String imagesEndpoint) {
        isInProgress = true;
        
        // Disable inputs
        qaEndpointInput.setEnabled(false);
        imagesEndpointInput.setEnabled(false);
        startButton.setEnabled(false);
        
        // Show progress views
        progressBar.setVisibility(View.VISIBLE);
        progressText.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.VISIBLE);
        
        // Change cancel button text
        cancelButton.setText("Cancel");
        
        // Notify listener
        if (listener != null) {
            listener.onStartIngestion(qaEndpoint, imagesEndpoint);
        }
    }
    
    public void updateProgress(String message, int progress) {
        if (progressBar != null) {
            progressBar.setProgress(progress);
            progressText.setText(progress + "%");
            statusText.setText(message);
        }
    }
    
    public void showSuccess(String message) {
        isInProgress = false;
        
        if (statusText != null) {
            statusText.setText("✓ " + message);
            statusText.setTextColor(0xFF4CAF50); // Green
        }
        
        if (cancelButton != null) {
            cancelButton.setText("Close");
        }
    }
    
    public void showError(String message) {
        isInProgress = false;
        
        if (statusText != null) {
            statusText.setText("✗ " + message);
            statusText.setTextColor(0xFFF44336); // Red
        }
        
        if (cancelButton != null) {
            cancelButton.setText("Close");
        }
        
        // Re-enable inputs for retry
        if (qaEndpointInput != null) qaEndpointInput.setEnabled(true);
        if (imagesEndpointInput != null) imagesEndpointInput.setEnabled(true);
        if (startButton != null) startButton.setEnabled(true);
    }
}
