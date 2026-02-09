# CMakeLists.txt for llama.android with PRE-BUILT OpenCL GPU libraries
cmake_minimum_required(VERSION 3.22.1)
project("llama-android")

# 🎯 USE PRE-BUILT LIBRARIES FROM WORKING ADRENO SDK
# Instead of building llama.cpp from source, we use pre-compiled libraries
# that were built with OpenCL GPU support and Adreno optimizations

set(PREBUILT_LIB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")

# Import pre-built llama library (compiled with OpenCL support)
add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES IMPORTED_LOCATION "${PREBUILT_LIB_DIR}/libllama.so")

# Import pre-built GGML libraries
add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES IMPORTED_LOCATION "${PREBUILT_LIB_DIR}/libggml.so")

add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES IMPORTED_LOCATION "${PREBUILT_LIB_DIR}/libggml-base.so")

add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES IMPORTED_LOCATION "${PREBUILT_LIB_DIR}/libggml-cpu.so")

# 🎯 Import pre-built OpenCL GPU backend (THIS IS THE KEY!)
add_library(ggml-opencl SHARED IMPORTED)
set_target_properties(ggml-opencl PROPERTIES IMPORTED_LOCATION "${PREBUILT_LIB_DIR}/libggml-opencl.so")

# Import OpenCL ICD Loader
add_library(OpenCL SHARED IMPORTED)
set_target_properties(OpenCL PROPERTIES IMPORTED_LOCATION "${PREBUILT_LIB_DIR}/libOpenCL.so")

# Import OpenMP for threading
add_library(omp SHARED IMPORTED)
set_target_properties(omp PROPERTIES IMPORTED_LOCATION "${PREBUILT_LIB_DIR}/libomp.so")

# Build our JNI wrapper (llama-android.cpp)
add_library(${CMAKE_PROJECT_NAME} SHARED
        llama-android.cpp)

# Include directories for llama.cpp headers
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../include
        ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../common
        ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../src
        ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../ggml/include
)

# Link against all pre-built libraries
target_link_libraries(${CMAKE_PROJECT_NAME}
        llama           # Main llama.cpp library
        ggml            # GGML core
        ggml-base       # GGML base functions
        ggml-cpu        # CPU backend
        ggml-opencl     # 🎯 OpenCL GPU backend (PRE-BUILT with Adreno optimizations!)
        OpenCL          # OpenCL ICD Loader
        omp             # OpenMP threading
        android
        log
)
