// JNI bridge to the needle engine (needle.h) for Whistle, Lumen's speech-to-text model.
// The engine holds one process-global speech model and isn't thread-safe: the Kotlin side
// (data/ai/Whistle.kt) calls these one at a time.
#include <jni.h>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include "needle.h"

namespace {
// The engine reads the model from this memory; it lives as long as the process.
unsigned char* model = nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_subhashh_lumen_data_ai_Whistle_nativeLoad(JNIEnv* env, jclass, jbyteArray bytes) {
    if (model != nullptr) return nullptr;
    const jsize n = env->GetArrayLength(bytes);
    model = static_cast<unsigned char*>(std::malloc(n));
    if (model == nullptr) return env->NewStringUTF("Out of memory for the speech model");
    env->GetByteArrayRegion(bytes, 0, n, reinterpret_cast<jbyte*>(model));
    if (needle_load(model, static_cast<unsigned long long>(n)) < 0) {
        std::string error = needle_last_error();
        std::free(model);
        model = nullptr;
        return env->NewStringUTF(error.c_str());
    }
    return nullptr;
}

// Returns the engine's JSON (text, language, words with start/end/probability) as UTF-8 bytes
// (JNI's own strings mangle characters outside the BMP), or null on failure (see nativeLastError).
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_subhashh_lumen_data_ai_Whistle_nativeTranscribe(JNIEnv* env, jclass, jfloatArray pcm, jstring language) {
    const jsize samples = env->GetArrayLength(pcm);
    std::vector<float> audio(samples);
    env->GetFloatArrayRegion(pcm, 0, samples, audio.data());
    const char* lang = language != nullptr ? env->GetStringUTFChars(language, nullptr) : nullptr;
    // Words with times run long: room for ~100 bytes a word, a few words a second.
    std::vector<char> out(static_cast<size_t>(samples / 16000 + 30) * 1024 + 4096);
    const int result = needle_transcribe(audio.data(), samples, lang, nullptr, 1, out.data(), static_cast<int>(out.size()));
    if (lang != nullptr) env->ReleaseStringUTFChars(language, lang);
    if (result < 0) return nullptr;
    const auto length = static_cast<jsize>(std::strlen(out.data()));
    jbyteArray json = env->NewByteArray(length);
    env->SetByteArrayRegion(json, 0, length, reinterpret_cast<const jbyte*>(out.data()));
    return json;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_subhashh_lumen_data_ai_Whistle_nativeLastError(JNIEnv* env, jclass) {
    const char* error = needle_last_error();
    return env->NewStringUTF(error != nullptr ? error : "");
}
