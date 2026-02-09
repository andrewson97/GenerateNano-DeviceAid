# 🎯 llama.android with OpenCL GPU Acceleration

## Overview

This is the **enhanced llama.android app** with **full OpenCL GPU acceleration** for Adreno GPUs. It includes support for the **InterPlayThink model** and real-time streaming with GPU-accelerated inference.

## 🚀 Key Features

✅ **OpenCL GPU Acceleration** - Full Adreno GPU support with optimized kernels  
✅ **InterPlayThink-3B Model** - Pre-configured Q4_0 quantized model  
✅ **Real-time Streaming** - Fast token generation with GPU offloading  
✅ **GPU Configuration UI** - Toggle between CPU/GPU modes  
✅ **GPU Device Info** - View OpenCL device specifications  
✅ **Multiple Models** - Support for Phi-2, TinyLlama, and more  

## 📦 What Was Changed

### 1. **Build Configuration** (`llama/build.gradle.kts`)
```kotlin
arguments += "-DGGML_OPENCL=ON"
arguments += "-DGGML_OPENCL_USE_ADRENO_KERNELS=ON"
arguments += "-DGGML_OPENCL_EMBED_KERNELS=ON"
```

### 2. **CMake Configuration** (`llama/src/main/cpp/CMakeLists.txt`)
```cmake
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../../../../../opencl-setup/OpenCL-Headers)
target_link_libraries(${CMAKE_PROJECT_NAME} ... OpenCL)
```

### 3. **OpenCL Library**
- Copied `libOpenCL.so` to `llama/src/main/jniLibs/arm64-v8a/`
- Added OpenCL headers from `opencl-setup/OpenCL-Headers`

### 4. **Kotlin API Extensions** (`LLamaAndroid.kt`)
```kotlin
suspend fun getGpuInfo(): String
suspend fun configureGpu(gpuLayers: Int = 32)
```

### 5. **Native JNI Methods** (`llama-android.cpp`)
```cpp
Java_android_llama_cpp_LLamaAndroid_get_1gpu_1info()
Java_android_llama_cpp_LLamaAndroid_set_1gpu_1layers()
```

### 6. **Enhanced UI** (`MainActivity.kt`)
- Added GPU Info button
- Added GPU configuration buttons (32 layers / CPU only)
- Added InterPlayThink model to download list
- Enhanced ViewModel with GPU methods

## 🎮 UI Controls

### Main Controls
- **Send** - Send message to the model
- **Bench** - Run performance benchmark
- **Clear** - Clear message history
- **Copy** - Copy messages to clipboard

### GPU Controls (New!)
- **🎮 GPU Info** - Display OpenCL device information
- **⚡ Enable GPU (32)** - Enable GPU with 32 layers
- **🔴 CPU Only** - Disable GPU, use CPU only

### Model Downloads
- **🎯 InterPlayThink-3B** - GPU-optimized Q4_0 model
- **Phi-2 7B** - Alternative model
- **TinyLlama 1.1B** - Smaller model for testing
- **Phi 2 DPO** - Fine-tuned variant

## 📱 How to Use

### 1. **Open Project in Android Studio**
```bash
Open: /Users/kirit/Meet/Iterate/Adreno-Sdk-final/Adreno_apk/adreno-llama-cpp/examples/llama.android
```

### 2. **Build the Project**
```bash
# Clean build (recommended first time)
./gradlew clean
./gradlew build
```

### 3. **Run on Device**
- Connect Adreno GPU-enabled Android device (Snapdragon 8 Gen 3/Elite)
- Click "Run" in Android Studio
- Wait for app to launch

### 4. **Check GPU Status**
1. Tap **"🎮 GPU Info"** button
2. You should see:
   ```
   ✅ OpenCL Backend: ENABLED
   🎯 Adreno Optimizations: ACTIVE
   
   GPU Device: Adreno (TM) 750
   Vendor: QUALCOMM
   OpenCL Version: OpenCL 3.0
   Compute Units: 8
   Clock Frequency: 680 MHz
   Global Memory: 7892 MB
   
   GPU Layers: 0
   Status: 🔴 CPU Only Mode
   ```

### 5. **Enable GPU Acceleration**
1. Tap **"⚡ Enable GPU (32)"** button
2. You should see: `⚡ GPU enabled with 32 layers (OpenCL + Adreno)`
3. Tap **"🎮 GPU Info"** again to verify:
   ```
   GPU Layers: 32
   Status: ⚡ GPU Acceleration ACTIVE
   ```

### 6. **Download InterPlayThink Model**
1. Tap **"🎯 InterPlayThink-3B (Q4_0, GPU Optimized)"** button
2. Wait for download to complete
3. Model will be saved to: `/sdcard/Android/data/com.example.llama/files/`

### 7. **Load and Test Model**
1. After download completes, model loads automatically
2. Type a message in the text field
3. Tap **"Send"** button
4. Watch real-time streaming output with GPU acceleration! 🚀

### 8. **Run Benchmark**
1. Tap **"Bench"** button
2. See GPU-accelerated performance metrics

## 🔧 GPU Configuration Details

### GPU Layers (`n_gpu_layers`)
- **0** = CPU only (no GPU acceleration)
- **1-32** = Offload N layers to GPU
- **32** = Recommended for 3B models (optimal balance)
- **Higher** = More GPU offloading (may not always be faster)

### When to Use GPU
- ✅ Large models (3B+)
- ✅ Real-time streaming inference
- ✅ Batch processing
- ✅ Production deployments

### When to Use CPU
- ⚠️ Very small models (< 1B parameters)
- ⚠️ When GPU memory is limited
- ⚠️ Testing/debugging

## 📊 Expected Performance

### With GPU (32 layers)
- **Prompt Processing**: 50-100 tokens/sec
- **Token Generation**: 20-40 tokens/sec
- **Latency**: < 50ms per token
- **Memory**: GPU memory used

### CPU Only
- **Prompt Processing**: 10-20 tokens/sec
- **Token Generation**: 5-10 tokens/sec
- **Latency**: 100-200ms per token
- **Memory**: RAM only

## 🐛 Troubleshooting

### "GPU Info not available"
**Cause**: OpenCL not enabled or device doesn't support it  
**Fix**: 
1. Check that you're using Snapdragon 8 Gen 3 or newer
2. Verify build configuration has `-DGGML_OPENCL=ON`
3. Check logcat for OpenCL errors

### "No OpenCL platform found"
**Cause**: Device doesn't have OpenCL support  
**Fix**: 
1. Use a device with Adreno 750+ GPU
2. Ensure Android 13+ (API 33+)

### Build Errors
**Error**: `OpenCL not found`  
**Fix**: 
```bash
# Verify OpenCL headers exist
ls opencl-setup/OpenCL-Headers/CL/

# Verify libOpenCL.so exists
ls llama/src/main/jniLibs/arm64-v8a/libOpenCL.so
```

### Slow Streaming
**Cause**: GPU not enabled  
**Fix**: 
1. Tap "⚡ Enable GPU (32)" BEFORE loading model
2. Reload the model
3. Check GPU Info shows "ACTIVE"

### Model Won't Load
**Cause**: Insufficient memory or wrong format  
**Fix**: 
1. Use Q4_0 quantized models (recommended)
2. Enable GPU to offload memory
3. Close other apps to free RAM

## 📝 Build Requirements

- **Android Studio**: Flamingo or newer
- **NDK**: 26.3.11579264
- **CMake**: 3.22.1+
- **Gradle**: 8.2.0+
- **Android SDK**: API 33+
- **Device**: Snapdragon 8 Gen 3/Elite with Adreno 750+

## 🎯 Model Recommendations

### InterPlayThink-3B (Best for GPU)
- **Size**: ~2GB (Q4_0)
- **Performance**: Excellent with 32 GPU layers
- **Use Case**: General-purpose, chat, reasoning

### Phi-2 (Alternative)
- **Size**: ~1.6GB (Q4_0)
- **Performance**: Good with GPU
- **Use Case**: Code generation, chat

### TinyLlama (Fast Testing)
- **Size**: ~600MB (Q4_0)
- **Performance**: Fast, less accurate
- **Use Case**: Quick testing, low-resource devices

## 🔍 Verifying GPU Acceleration

### Check Logcat
```bash
adb logcat | grep "llama-android"
```

Look for:
```
⚡ GPU Acceleration enabled with 32 layers (OpenCL + Adreno)
```

### Check GPU Info Output
Should show:
- ✅ OpenCL Backend: ENABLED
- 🎯 Adreno Optimizations: ACTIVE
- GPU Device: Adreno (TM) 750 or 830
- Status: ⚡ GPU Acceleration ACTIVE

### Performance Test
1. Load model with CPU only (0 layers)
2. Send message, note tokens/sec
3. Enable GPU (32 layers)
4. Reload model
5. Send same message, note tokens/sec
6. GPU should be 3-5x faster!

## 📖 Additional Resources

- [llama.cpp OpenCL Documentation](../../docs/backend/OPENCL.md)
- [Adreno GPU SDK](../../../README.md)
- [Android NDK Guide](https://developer.android.com/ndk)

## 🎉 Success Indicators

When everything is working correctly, you should see:

1. ✅ GPU Info shows OpenCL enabled
2. ✅ Adreno device detected
3. ✅ GPU layers set to 32
4. ✅ Model loads successfully
5. ✅ Streaming is fast and smooth (20+ tokens/sec)
6. ✅ No errors in logcat

---

**🚀 You now have a fully GPU-accelerated llama.cpp Android app with OpenCL support for Adreno GPUs!**

For questions or issues, check the main Adreno SDK documentation or examine logcat output.

