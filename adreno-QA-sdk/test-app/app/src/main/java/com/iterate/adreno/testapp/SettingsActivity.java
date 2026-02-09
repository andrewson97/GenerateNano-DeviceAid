package com.iterate.adreno.testapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.io.File;

/**
 * Settings Activity - Manage app settings and utilities
 */
public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
    private static final String PREFS_NAME = "AdrenoTestAppSettings";
    private static final String KEY_TTS_ENABLED = "tts_enabled";
    private static final String KEY_GPU_LAYERS = "gpu_layers";
    private static final int DEFAULT_GPU_LAYERS = 27;
    
    private SwitchCompat ttsSwitch;
    private SeekBar gpuLayersSeekBar;
    private TextView gpuLayersValue;
    private SharedPreferences sharedPreferences;
    private LinearLayout downloadModelOption;
    private LinearLayout deleteModelOption;
    private boolean modelExists = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // Setup action bar with Device Aid logo and visible back button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_back_arrow);
            getSupportActionBar().setDisplayShowCustomEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setElevation(0);
            
            // Create container with left padding to account for back button and center properly
            android.widget.RelativeLayout customViewContainer = new android.widget.RelativeLayout(this);
            android.widget.RelativeLayout.LayoutParams containerParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
            );
            customViewContainer.setLayoutParams(containerParams);
            
            // Create custom view with logo and text (same as MainActivity)
            android.widget.LinearLayout customView = new android.widget.LinearLayout(this);
            customView.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            customView.setGravity(android.view.Gravity.CENTER);
            android.widget.RelativeLayout.LayoutParams customParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
            );
            customParams.addRule(android.widget.RelativeLayout.CENTER_IN_PARENT);
            customParams.setMargins(-24, 0, 0, 0); // Shift left to compensate for back button
            customView.setLayoutParams(customParams);
            
            // Add Device Aid logo (same size as main - 80x80)
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
            
            // Add "Device Aid" title
            android.widget.TextView titleView = new android.widget.TextView(this);
            titleView.setText("Device Aid");
            titleView.setTextSize(22);
            titleView.setTextColor(0xFF282D32);
            titleView.setTypeface(null, android.graphics.Typeface.BOLD);
            textContainer.addView(titleView);
            
            // Add "Powered by Generate Nano" subtitle
            android.widget.TextView subtitleView = new android.widget.TextView(this);
            android.text.SpannableString spannableSubtitle = new android.text.SpannableString("Powered by Generate Nano");
            android.text.style.ForegroundColorSpan colorSpan = new android.text.style.ForegroundColorSpan(0xFF4285F4);
            spannableSubtitle.setSpan(colorSpan, 11, 24, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            subtitleView.setText(spannableSubtitle);
            subtitleView.setTextSize(13);
            subtitleView.setTextColor(0xFF666666);
            textContainer.addView(subtitleView);
            
            customView.addView(textContainer);
            customViewContainer.addView(customView);
            getSupportActionBar().setCustomView(customViewContainer);
        }
        
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // Initialize views
        ttsSwitch = findViewById(R.id.ttsSwitch);
        gpuLayersSeekBar = findViewById(R.id.gpuLayersSeekBar);
        gpuLayersValue = findViewById(R.id.gpuLayersValue);
        downloadModelOption = findViewById(R.id.downloadModelOption);
        LinearLayout loadModelOption = findViewById(R.id.loadModelOption);
        deleteModelOption = findViewById(R.id.deleteModelOption);
        LinearLayout rebuildIndexOption = findViewById(R.id.rebuildIndexOption);
        
        // Check model existence and update button states
        updateModelButtonStates();
        
        // Load TTS setting
        boolean ttsEnabled = sharedPreferences.getBoolean(KEY_TTS_ENABLED, false);
        ttsSwitch.setChecked(ttsEnabled);
        
        // Load GPU layers setting
        int gpuLayers = sharedPreferences.getInt(KEY_GPU_LAYERS, DEFAULT_GPU_LAYERS);
        gpuLayersSeekBar.setProgress(gpuLayers);
        gpuLayersValue.setText(String.valueOf(gpuLayers));
        
        // TTS toggle
        ttsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(KEY_TTS_ENABLED, isChecked).apply();
            Toast.makeText(this, 
                "TTS " + (isChecked ? "enabled" : "disabled"), 
                Toast.LENGTH_SHORT).show();
            
            // Send result back to MainActivity
            Intent resultIntent = new Intent();
            resultIntent.putExtra("tts_enabled", isChecked);
            resultIntent.putExtra("action", "tts_changed");
            setResult(RESULT_OK, resultIntent);
        });
        
        // GPU layers slider
        gpuLayersSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int originalValue = sharedPreferences.getInt(KEY_GPU_LAYERS, DEFAULT_GPU_LAYERS);
            
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                gpuLayersValue.setText(String.valueOf(progress));
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                originalValue = seekBar.getProgress();
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int newLayers = seekBar.getProgress();
                
                // Only show confirmation if value actually changed
                if (newLayers != originalValue) {
                    showGpuLayersConfirmation(newLayers, originalValue);
                }
            }
        });
        
        // Download Model
        downloadModelOption.setOnClickListener(v -> {
            if (modelExists) {
                Toast.makeText(this, "Model already downloaded", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent resultIntent = new Intent();
            resultIntent.putExtra("action", "download_model");
            setResult(RESULT_OK, resultIntent);
            finish();
        });
        
        // Load Model
        loadModelOption.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("action", "load_model");
            setResult(RESULT_OK, resultIntent);
            finish();
        });
        
        // Delete Model
        deleteModelOption.setOnClickListener(v -> {
            if (!modelExists) {
                Toast.makeText(this, "Model not downloaded", Toast.LENGTH_SHORT).show();
                return;
            }
            File modelFile = new File(getFilesDir(), MainActivity.MODEL_NAME);
            if (modelFile.exists()) {
                if (modelFile.delete()) {
                    Toast.makeText(this, "Model deleted successfully", Toast.LENGTH_SHORT).show();
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("action", "model_deleted");
                    setResult(RESULT_OK, resultIntent);
                    updateModelButtonStates();
                } else {
                    Toast.makeText(this, "Failed to delete model", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Model file not found", Toast.LENGTH_SHORT).show();
            }
        });
        
        // Rebuild Index
        rebuildIndexOption.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("action", "rebuild_index");
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }
    
    private void showGpuLayersConfirmation(int newLayers, int originalLayers) {
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("⚠️ Reload Model Required")
            .setMessage("Changing GPU layers from " + originalLayers + " to " + newLayers + 
                        " requires reloading the model.\n\nThis may take a few moments. Continue?")
            .setPositiveButton("Apply & Reload", (d, which) -> {
                // Save the new value
                sharedPreferences.edit().putInt(KEY_GPU_LAYERS, newLayers).apply();
                
                Toast.makeText(this, 
                    "GPU layers set to " + newLayers + ". Model will reload...", 
                    Toast.LENGTH_SHORT).show();
                
                // Send result back to MainActivity to reload model
                Intent resultIntent = new Intent();
                resultIntent.putExtra("action", "gpu_layers_changed");
                resultIntent.putExtra("gpu_layers", newLayers);
                setResult(RESULT_OK, resultIntent);
                finish();
            })
            .setNegativeButton("Cancel", (d, which) -> {
                // Revert the slider to original value
                gpuLayersSeekBar.setProgress(originalLayers);
                gpuLayersValue.setText(String.valueOf(originalLayers));
                d.dismiss();
            })
            .setCancelable(false)
            .create();
        
        dialog.show();
        
        // Fix button colors to be visible (white theme made them invisible)
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF2196F3); // Blue
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF757575); // Gray
        }
    }
    
    private void updateModelButtonStates() {
        File modelFile = new File(getFilesDir(), MainActivity.MODEL_NAME);
        modelExists = modelFile.exists() && modelFile.length() > 100_000_000;
        
        if (modelExists) {
            // Model exists: disable download, enable delete
            downloadModelOption.setAlpha(0.4f);
            downloadModelOption.setEnabled(false);
            deleteModelOption.setAlpha(1.0f);
            deleteModelOption.setEnabled(true);
        } else {
            // Model doesn't exist: enable download, disable delete
            downloadModelOption.setAlpha(1.0f);
            downloadModelOption.setEnabled(true);
            deleteModelOption.setAlpha(0.4f);
            deleteModelOption.setEnabled(false);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Update button states when returning to settings
        updateModelButtonStates();
    }
    
    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
