# GenerateNano DeviceAid

An Android application integrating Adreno GPU acceleration with Whisper speech recognition and LLaMA language models for on-device AI assistance.

## 📋 Project Structure

```
GenerateNano-DeviceAid/
├── adreno-QA-sdk/          # Main SDK and build configuration
│   ├── sdk/                # Core SDK library source
│   ├── test-app/           # Android test application
│   ├── adreno-llama-cpp/   # LLaMA.cpp integration
│   └── opencl-setup/       # OpenCL configuration
```

## 🚀 Developer Build Guide


### Prerequisites

- **Android Studio** (Arctic Fox or later)
- **Android SDK** (API Level 33 or higher)
- **NDK** (r25 or later)
- **Gradle** 8.2+
- **Git LFS** (for Whisper model files)
- **Java JDK** 17+

### Step 1: Clone the Repository

```bash
git clone https://github.com/andrewson97/GenerateNano-DeviceAid.git
cd GenerateNano-DeviceAid
```

**Note:** Git LFS will automatically download the Whisper model files (~342 MB) during clone. (these whisper models are specific for 6490 devices)

### Step 2: Build the SDK

The SDK needs to be built first to generate the AAR library file.

```bash
cd adreno-QA-sdk
./gradlew :sdk:assembleRelease
```

This will generate the SDK AAR file at:
```
adreno-QA-sdk/sdk/build/outputs/aar/sdk-release.aar
```

### Step 3: Copy AAR to Test App

Copy the generated AAR file to the test app's libs directory with the correct name:

```bash
# From the adreno-QA-sdk directory
mkdir -p test-app/app/libs
cp sdk/build/outputs/aar/sdk-release.aar test-app/app/libs/Adreno_QA_SDK_V1.0.aar
```

**Important:** The AAR must be renamed to `Adreno_QA_SDK_V1.0.aar` as this is the filename expected by the test app.

### Step 4: Build the Test App APK

Now build the Android test application:

```bash
cd test-app
./gradlew assembleDebug
```

For a release build:

```bash
./gradlew assembleRelease
```

The APK will be generated at:
- **Debug:** `test-app/app/build/outputs/apk/debug/app-debug.apk`
- **Release:** `test-app/app/build/outputs/apk/release/app-release.apk`

### Step 5: Install and Run

Install the APK on your Android device:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio and run directly.

## 🔧 Build Configuration

### Local Properties

Create or update `adreno-QA-sdk/local.properties` with your Android SDK path:

```properties
sdk.dir=/path/to/your/Android/Sdk
ndk.dir=/path/to/your/Android/Sdk/ndk/25.x.x
```

### Gradle Configuration

The project uses Gradle 8.2. If you encounter build issues, ensure your `gradle-wrapper.properties` is configured correctly:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.2-bin.zip
```

## 📦 What's Included

### Whisper Models (LFS-tracked)
The following model files are tracked via Git LFS in `test-app/app/src/main/assets/whisper-models/`:
- `decoder_model_htp.bin` (214 MB)
- `encoder_model_htp.bin` (120 MB)
- `libQnnHtpV73Skel.so` (7.9 MB)
- `vocab.bin` (349 KB)

### Components
- **Adreno SDK**: GPU-accelerated inference library
- **Test App**: Android application with voice input and AI chat interface
- **LLaMA.cpp Integration**: On-device language model support
- **OpenCL Setup**: GPU compute configuration

## 🧹 Clean Build

To clean all build artifacts:

```bash
# Clean SDK
cd adreno-QA-sdk
./gradlew clean

# Clean test app
cd test-app
./gradlew clean
```

## 📝 Development Notes

- Build artifacts are gitignored to keep the repository size manageable
- Large model files use Git LFS for efficient version control
- The SDK must be rebuilt whenever you make changes to the core library
- Always copy the latest AAR to the test app before building the APK

## 🐛 Troubleshooting

**Issue:** AAR not found in test app
- **Solution:** Ensure you've run Step 3 to copy the AAR file to `test-app/app/libs/`

**Issue:** Git LFS files not downloading
- **Solution:** Install Git LFS: `brew install git-lfs` then run `git lfs pull`

**Issue:** Gradle build fails
- **Solution:** Check `local.properties` has correct SDK/NDK paths

**Issue:** NDK version mismatch
- **Solution:** Update NDK version in `build.gradle` to match your installed version

## 📄 License

[Add your license information here]

## 🤝 Contributing

[Add contribution guidelines here]
