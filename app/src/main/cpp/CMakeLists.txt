# Native code: a JNI bridge to Cactus's needle engine, which runs Whistle (speech to text).
cmake_minimum_required(VERSION 3.22)
project(lumen_speech CXX)

add_library(needle STATIC IMPORTED)
set_target_properties(needle PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/needle/${ANDROID_ABI}/libneedle.a)

add_library(lumen_speech SHARED speech.cpp)
target_include_directories(lumen_speech PRIVATE needle/include)
target_link_libraries(lumen_speech needle log m)
# The engine is C++ with exceptions; libc++ comes statically (ANDROID_STL in build.gradle.kts).
