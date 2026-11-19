#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <algorithm>
#include "whisper.h"

#define TAG "VAB_WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C"
JNIEXPORT jlong JNICALL
Java_com_aragao_vab_whisper_WhisperJni_nativeLoadModel(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jModelPath) {
    const char *model_path = env->GetStringUTFChars(jModelPath, nullptr);
    if (!model_path) {
        return 0L;
    }

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    cparams.flash_attn = false;

    LOGI("Loading Whisper model from %s", model_path);
    whisper_context *ctx = whisper_init_from_file_with_params(model_path, cparams);
    env->ReleaseStringUTFChars(jModelPath, model_path);

    if (!ctx) {
        LOGE("Failed to initialize Whisper model");
        return 0L;
    }
    return reinterpret_cast<jlong>(ctx);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_aragao_vab_whisper_WhisperJni_nativeTranscribe(
        JNIEnv *env,
        jobject /* thiz */,
        jlong ctxPtr,
        jfloatArray jPcmSamples,
        jstring jLanguage,
        jint nThreads) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    if (!ctx || !jPcmSamples) {
        return env->NewStringUTF("");
    }

    const jsize n_samples = env->GetArrayLength(jPcmSamples);
    if (n_samples <= 0) {
        return env->NewStringUTF("");
    }

    std::vector<float> pcmf32(static_cast<size_t>(n_samples));
    env->GetFloatArrayRegion(jPcmSamples, 0, n_samples, pcmf32.data());

    const char *lang = env->GetStringUTFChars(jLanguage, nullptr);

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.n_threads = std::max(1, std::min(static_cast<int>(nThreads), 6));
    wparams.language = lang ? lang : "pt";
    wparams.translate = false;
    wparams.no_context = true;
    wparams.single_segment = false;
    wparams.print_progress = false;
    wparams.print_realtime = false;
    wparams.print_special = false;
    wparams.print_timestamps = false;
    wparams.suppress_blank = true;

    const int rc = whisper_full(ctx, wparams, pcmf32.data(), static_cast<int>(pcmf32.size()));
    if (lang) {
        env->ReleaseStringUTFChars(jLanguage, lang);
    }

    if (rc != 0) {
        LOGE("whisper_full failed with error code %d", rc);
        return env->NewStringUTF("");
    }

    std::string result;
    const int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        const char *seg_text = whisper_full_get_segment_text(ctx, i);
        if (seg_text) {
            result += seg_text;
        }
    }

    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_aragao_vab_whisper_WhisperJni_nativeFreeModel(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong ctxPtr) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    if (ctx) {
        LOGI("Freeing Whisper context");
        whisper_free(ctx);
    }
}
