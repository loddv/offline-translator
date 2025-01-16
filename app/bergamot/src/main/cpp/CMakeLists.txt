# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("bergamot-sys")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        bergamot.cpp)

set(USE_WASM_COMPATIBLE_SOURCES OFF CACHE BOOL "" FORCE)
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
set(COMPILE_WASM OFF CACHE BOOL "" FORCE)
set(COMPILE_TEST OFF CACHE BOOL "" FORCE)
set(USE_THREADS OFF CACHE BOOL "" FORCE)
set(CMAKE_BUILD_TYPE Release)

if(ANDROID)
	#set(CMAKE_THREAD_LIBS_INIT "-lxpthread")
	set(CMAKE_THREAD_LIBS_INIT "")
	set(CMAKE_HAVE_THREADS_LIBRARY 0)
	set(CMAKE_USE_WIN32_THREADS_INIT 0)
	set(CMAKE_USE_PTHREADS_INIT 0)
	set(THREADS_PREFER_PTHREAD_FLAG OFF)
endif()

set(INTGEMM_DONT_BUILD_TESTS ON)
#set(CMAKE_BUILD_TYPE Debug)
#set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -g")
# marian does autodetection otherwise
#set(BUILD_ARCH "x86-64-v3")
# This string is in marian-dev:   set(BUILD_ARCH native CACHE STRING "Compile for this CPU architecture.")
# does it mean that it ignores me? do I need to force it? patch?

set(USE_DOXYGEN OFF)
set(USE_MKL OFF)
set(COMPILE_WITHOUT_EXCEPTIONS ON)

# this is missing pcre2
set(SSPLIT_COMPILE_LIBRARY_ONLY ON)
set(SSPLIT_USE_INTERNAL_PCRE2 ON)
#set(BUILD_ARCH "${ANDROID_ABI}")
set(AUTO_CPU_DETECT OFF CACHE BOOL "Disable CPU feature detection" FORCE)
message(STATUS "ANDROID_ABI: ${ANDROID_ABI}")
message(STATUS "CMAKE_SYSTEM_PROCESSOR: ${CMAKE_SYSTEM_PROCESSOR}")
message(STATUS "CMAKE_ANDROID_ARCH_ABI: ${CMAKE_ANDROID_ARCH_ABI}")
message(STATUS "CMAKE_SYSTEM_NAME: ${CMAKE_SYSTEM_NAME}")

if(ANDROID_ABI STREQUAL "arm64-v8a")
	set(BUILD_ARCH "arm64-v8a" CACHE STRING "Build architecture" FORCE)
#	set(CC "${ANDROID_TOOLCHAIN_ROOT}/bin/aarch64-linux-android${ANDROID_NATIVE_API_LEVEL}-clang")

	set(CMAKE_C_COMPILER "${ANDROID_TOOLCHAIN_ROOT}/bin/aarch64-linux-android${ANDROID_NATIVE_API_LEVEL}-clang")
	set(CMAKE_CXX_COMPILER "${ANDROID_TOOLCHAIN_ROOT}/bin/aarch64-linux-android${ANDROID_NATIVE_API_LEVEL}-clang++")
#	set(CXX "${ANDROID_TOOLCHAIN_ROOT}/bin/aarch64-linux-android${ANDROID_NATIVE_API_LEVEL}-clang++")
	message(STATUS "compiler ${CMAKE_C_COMPILER}")

	#	set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8-a")
endif()
# "blas compatible with wasm" means i don't need to figure out how to get openblas on android
#set(USE_ONNX_SGEMM ON)
#set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mno-avx -mno-sse") // doesn't help, still sigill
message(STATUS "Building for arch: ${BUILD_ARCH}")
add_subdirectory("bergamot-translator/")


include(${CMAKE_SOURCE_DIR}/cmake/cld2.cmake)
#
#target_include_directories(cld2 PRIVATE
#		${CMAKE_SOURCE_DIR}/third_party/cld2/public
#)
#add_library(bergamot SHARED IMPORTED)
# this works, but the .so is 41MB, good, but depends on libm
# the .a is just 600KiB, so garbage.
#set_target_properties(bergamot PROPERTIES
#        IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/libs/libbergamot.so"
#)
# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        bergamot-translator
        android
        log
		cld2
       # "/home/david/git/OpenBLAS/libopenblas.so"
)
