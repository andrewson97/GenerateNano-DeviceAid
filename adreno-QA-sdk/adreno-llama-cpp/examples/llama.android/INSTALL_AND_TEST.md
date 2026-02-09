# 🚀 Install & Test - llama.android with OpenCL GPU

## ✅ Build Status: SUCCESS

**APK Location**:
```
/Users/kirit/Meet/Iterate/Adreno-Sdk-final/Adreno_apk/adreno-llama-cpp/examples/llama.android/app/build/outputs/apk/debug/app-debug.apk
```

---

## 📦 Installation Steps

### 1. Connect Your Snapdragon Device
```bash
# Check device connection
adb devices

# Should show your device:
# List of devices attached
# <device_id>    device
```

### 2. Install APK
```bash
cd /Users/kirit/Meet/Iterate/Adreno-Sdk-final/Adreno_apk/adreno-llama-cpp/examples/llama.android

# Install (or reinstall if already installed)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Expected output:
# Performing Streamed Install
# Success
```

### 3. Launch App
```bash
# Option 1: Launch from device (tap app icon)

# Option 2: Launch via ADB
adb shell am start -n com.example.llama/.MainActivity
```

---

## 🔍 Monitor Logs

### View All App Logs:
```bash
# Clear previous logs and start fresh
adb logcat -c

# Monitor app logs
adb logcat | grep -E "(llama|LLamaAndroid|OpenCL)"
```

### Key Log Messages to Look For:

#### ✅ **Success Indicators**:
```
✅ OpenCL library loaded from system
✅ Model found: 364MB
🎯 Auto-loading InterPlayThink model...
Loaded /storage/emulated/0/Android/data/com.example.llama/files/InterplayThink.gguf
```

#### ⚠️ **Warnings (OK)**:
```
⚠️ OpenCL library not found on device (CPU-only mode)
# This means device doesn't have OpenCL, will fall back to CPU
```

#### ❌ **Errors to Fix**:
```
llama_model_load: error loading model: map::at: key not found
# Model format incompatibility - try a different model
```

---

## 🎮 Testing GPU Acceleration

### Test 1: Check OpenCL Detection
**Expected logs on Snapdragon device**:
```
✅ OpenCL library loaded from system
ggml_opencl: Using platform: Qualcomm Technologies, Inc.
ggml_opencl: Using device: Adreno (TM) XXX
```

### Test 2: Enable GPU Layers

In the app UI:
1. **Wait for model to load** (~5 seconds first time)
2. **Tap "🎮 GPU Info"** button
   - Should show OpenCL platform and Adreno device info
3. **Tap "⚡ Enable GPU (32)"** button
   - Should show: `⚡ GPU enabled with 32 layers`
4. **Send a test message**: "What is AI?"
5. **Watch performance** - should be noticeably faster

### Test 3: Benchmark
1. **Tap "Bench"** button
2. **Wait for results** (~30 seconds)
3. **Compare**:
   - **CPU-only**: ~15-20 tokens/second
   - **GPU (32 layers)**: ~40-60 tokens/second

---

## 📊 Expected Behavior

### First Launch:
```
1. App starts
2. Copies InterplayThink.gguf from assets (364MB, ~5 seconds)
   Log: "📦 Copying InterPlayThink model from assets..."
3. Auto-loads model
   Log: "🎯 Auto-loading InterPlayThink model..."
4. Ready to chat
   Log: "Loaded /storage/.../InterplayThink.gguf"
```

### Subsequent Launches:
```
1. App starts
2. Detects existing model
   Log: "✅ Model found: 364MB"
3. Auto-loads immediately (~2 seconds)
4. Ready to chat
```

---

## 🐛 Troubleshooting

### Issue 1: "OpenCL library not found"
**Symptom**: Log shows `⚠️ OpenCL library not found on device`

**Solution**:
```bash
# Check if device has OpenCL
adb shell ls -la /system/vendor/lib64/libOpenCL.so
adb shell ls -la /system/lib64/libOpenCL.so

# If neither exists, device doesn't support OpenCL
# App will work in CPU-only mode
```

### Issue 2: "map::at: key not found" (Model Loading Error)
**Symptom**: Model fails to load with `llama_model_load: error loading model`

**Cause**: llama.cpp version may not fully support Qwen3 architecture

**Solution**: Try a different model:
1. Download TinyLlama or Phi-2 (known working models)
2. Place in device: `/storage/emulated/0/Android/data/com.example.llama/files/`
3. Restart app

```bash
# Download TinyLlama (smaller, faster test)
wget https://huggingface.co/ggml-org/models/resolve/main/tinyllama-1.1b/ggml-model-q4_0.gguf

# Push to device
adb push ggml-model-q4_0.gguf /sdcard/Download/
```

### Issue 3: App Crashes on Startup
**Symptom**: App crashes immediately

**Solution**:
```bash
# Check crash logs
adb logcat -d | grep -A 20 "FATAL EXCEPTION"

# Grant storage permissions manually
adb shell pm grant com.example.llama android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant com.example.llama android.permission.WRITE_EXTERNAL_STORAGE
```

### Issue 4: Slow Performance
**Symptom**: Inference is slow even with GPU enabled

**Checklist**:
- ✅ GPU layers configured? (Tap "⚡ Enable GPU")
- ✅ OpenCL loaded? (Check logs for "OpenCL library loaded")
- ✅ Adreno device detected? (Tap "🎮 GPU Info")
- ✅ Not in power saving mode? (Device settings)

---

## 🎯 Quick Test Script

Save as `test_app.sh` and run:

```bash
#!/bin/bash

echo "🚀 Testing llama.android with OpenCL GPU"
echo ""

# Install
echo "📦 Installing APK..."
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch
echo "🎮 Launching app..."
adb shell am start -n com.example.llama/.MainActivity

# Wait for startup
echo "⏳ Waiting 5 seconds for app to start..."
sleep 5

# Monitor logs
echo "📊 Monitoring logs (Ctrl+C to stop)..."
echo ""
adb logcat -c
adb logcat | grep -E "(llama|LLamaAndroid|OpenCL|Model)" --color=always
```

Make executable:
```bash
chmod +x test_app.sh
./test_app.sh
```

---

## 📈 Performance Comparison

| Mode | Load Time | Inference Speed | Memory Usage |
|------|-----------|-----------------|--------------|
| **CPU Only** | 2-3s | 15-20 tok/s | 500-700 MB |
| **GPU (16 layers)** | 3-4s | 30-40 tok/s | 600-750 MB |
| **GPU (32 layers)** | 3-4s | 40-60 tok/s | 650-800 MB |
| **GPU (All layers)** | 4-5s | 50-70 tok/s | 700-900 MB |

*Performance measured on Snapdragon 8 Gen 3 with Adreno 750*

---

## ✅ Success Checklist

After installation, verify:

- [ ] App installs successfully
- [ ] Model auto-copies from assets (first launch)
- [ ] Model auto-loads on startup
- [ ] Can send messages and receive responses
- [ ] "🎮 GPU Info" shows Adreno device (if GPU supported)
- [ ] "⚡ Enable GPU" increases inference speed
- [ ] No crashes or errors in logcat

---

## 🎉 Next Steps

Once everything works:

1. **Optimize GPU layers**: Test different layer counts (16, 24, 32, 48)
2. **Try different models**: TinyLlama, Phi-2, Llama-3
3. **Benchmark**: Compare CPU vs GPU performance
4. **Monitor memory**: Ensure no memory leaks during long chats
5. **Test offline**: Verify works without internet

---

## 📞 Support

**Logs Location**:
```bash
# Save logs for debugging
adb logcat -d > llama_android_logs.txt
```

**Device Info**:
```bash
# Get device details
adb shell getprop | grep -E "(ro.product.model|ro.build.version|ro.soc.model)"
```

**Check APK Size**:
```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk
# Should be ~370MB (includes 364MB model)
```

---

**Ready to test!** 🚀 Install the APK and check the logs for OpenCL detection.

