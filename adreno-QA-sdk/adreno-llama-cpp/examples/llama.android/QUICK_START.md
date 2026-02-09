# 🚀 Quick Start Guide - llama.android with GPU

## 5-Minute Setup

### 1. Open in Android Studio
```bash
File → Open → /Users/kirit/Meet/Iterate/Adreno-Sdk-final/Adreno_apk/adreno-llama-cpp/examples/llama.android
```

### 2. Build Project
```bash
Build → Make Project
```
Or:
```bash
./gradlew clean build
```

### 3. Run on Device
- Connect Snapdragon device (8 Gen 3 or Elite)
- Click ▶️ Run button
- Wait for app to launch

### 4. Test GPU
1. Tap **"🎮 GPU Info"**
2. Should show: `✅ OpenCL Backend: ENABLED`

### 5. Enable GPU
1. Tap **"⚡ Enable GPU (32)"**
2. See: `⚡ GPU enabled with 32 layers`

### 6. Download Model
1. Tap **"🎯 InterPlayThink-3B (Q4_0, GPU Optimized)"**
2. Wait for download (~2GB)

### 7. Test Inference
1. Type: "What is AI?"
2. Tap **"Send"**
3. Watch fast streaming! 🚀

---

## 🎯 Key Changes Made

| Component | What Changed |
|-----------|-------------|
| **OpenCL** | ✅ Enabled with Adreno optimizations |
| **CMake** | ✅ Added `-DGGML_OPENCL=ON` |
| **Libraries** | ✅ Linked OpenCL + headers |
| **JNI** | ✅ Added GPU info & config methods |
| **UI** | ✅ Added GPU controls & InterPlayThink model |
| **Native** | ✅ GPU layer configuration in context |

---

## 🔍 Verify It Works

**Check 1: GPU Info**
```
✅ OpenCL Backend: ENABLED
🎯 Adreno Optimizations: ACTIVE
GPU Device: Adreno (TM) 750
Compute Units: 8
```

**Check 2: GPU Enabled**
```
GPU Layers: 32
Status: ⚡ GPU Acceleration ACTIVE
```

**Check 3: Fast Streaming**
- CPU only: ~5-10 tokens/sec
- GPU (32 layers): ~20-40 tokens/sec

---

## 🆘 Quick Fixes

### Build Fails
```bash
./gradlew clean
rm -rf .gradle build
./gradlew build
```

### GPU Not Found
- Use Snapdragon 8 Gen 3 or Elite device
- Enable developer options
- Check USB debugging enabled

### Slow Performance
1. Enable GPU BEFORE loading model
2. Use Q4_0 quantized models
3. Close other apps

---

## 📁 Project Structure

```
llama.android/
├── app/                    # Android app
│   └── src/main/
│       └── java/
│           └── MainActivity.kt      # ✨ Enhanced with GPU controls
│           └── MainViewModel.kt     # ✨ Added GPU methods
│
├── llama/                  # Native library
│   ├── build.gradle.kts   # ✨ OpenCL enabled
│   ├── src/main/
│   │   ├── cpp/
│   │   │   ├── CMakeLists.txt      # ✨ OpenCL linked
│   │   │   └── llama-android.cpp   # ✨ GPU JNI methods
│   │   ├── java/
│   │   │   └── LLamaAndroid.kt     # ✨ GPU API
│   │   └── jniLibs/
│   │       └── arm64-v8a/
│   │           └── libOpenCL.so    # ✨ OpenCL library
│
├── OPENCL_GPU_SETUP.md    # 📖 Detailed guide
└── QUICK_START.md         # 🚀 This file
```

---

## 🎮 UI Controls

| Button | Function |
|--------|----------|
| **🎮 GPU Info** | Show OpenCL device info |
| **⚡ Enable GPU (32)** | Enable 32 GPU layers |
| **🔴 CPU Only** | Disable GPU |
| **Send** | Send message to model |
| **Bench** | Run performance test |
| **🎯 InterPlayThink-3B** | Download GPU-optimized model |

---

## 💡 Tips

1. **Always enable GPU BEFORE loading model**
2. **Use Q4_0 quantized models for best GPU performance**
3. **32 layers is optimal for 3B models**
4. **Check logcat for detailed info**: `adb logcat | grep llama-android`

---

**That's it! You now have GPU-accelerated llama.cpp on Android! 🎉**

