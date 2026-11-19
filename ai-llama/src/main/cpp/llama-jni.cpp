#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <clocale>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"

#define TAG "VAB_LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {
std::once_flag g_backend_once;

void ensure_backend_initialized() {
    std::call_once(g_backend_once, []() {
        std::setlocale(LC_NUMERIC, "C");
        llama_backend_init();
        LOGI("llama_backend_init completed");
    });
}

void batch_set_tokens(
        llama_batch_ext *batch,
        const llama_token *tokens,
        int32_t n_tokens,
        llama_pos pos_0) {
    llama_batch_ext_clear(batch);
    for (int32_t i = 0; i < n_tokens; ++i) {
        const int32_t idx = llama_batch_ext_add_token(batch, 0, tokens[i]);
        const llama_pos pos = pos_0 + i;
        llama_batch_ext_set_pos(batch, idx, &pos);
    }
    llama_batch_ext_set_output_logits(batch, n_tokens - 1, true);
}

std::string format_chat_prompt(
        const llama_model *model,
        const std::string &system_prompt,
        const std::string &user_prompt) {
    const char *tmpl = llama_model_chat_template(model, nullptr);
    llama_chat_message msgs[2] = {
        {"system", system_prompt.c_str()},
        {"user", user_prompt.c_str()}
    };

    if (tmpl != nullptr) {
        int32_t alloc_len = static_cast<int32_t>((system_prompt.size() + user_prompt.size()) * 2 + 256);
        std::vector<char> buf(static_cast<size_t>(alloc_len));
        int32_t res = llama_chat_apply_template(tmpl, msgs, 2, true, buf.data(), alloc_len);
        if (res > alloc_len) {
            buf.resize(static_cast<size_t>(res + 32));
            res = llama_chat_apply_template(tmpl, msgs, 2, true, buf.data(), static_cast<int32_t>(buf.size()));
        }
        if (res > 0) {
            return std::string(buf.data(), static_cast<size_t>(res));
        }
    }

    // Fallback ChatML padrão da família Qwen
    return "<|im_start|>system\n" + system_prompt + "<|im_end|>\n"
           "<|im_start|>user\n" + user_prompt + "<|im_end|>\n"
           "<|im_start|>assistant\n";
}
} // namespace

extern "C"
JNIEXPORT jlong JNICALL
Java_com_aragao_vab_llama_LlamaJni_nativeLoadModel(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jModelPath) {
    ensure_backend_initialized();

    const char *model_path = env->GetStringUTFChars(jModelPath, nullptr);
    if (!model_path) {
        return 0L;
    }

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;

    LOGI("Loading GGUF model from %s", model_path);
    llama_model *model = llama_model_load_from_file(model_path, mparams);
    env->ReleaseStringUTFChars(jModelPath, model_path);

    if (!model) {
        LOGE("Failed to load GGUF model");
        return 0L;
    }
    return reinterpret_cast<jlong>(model);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_aragao_vab_llama_LlamaJni_nativeGenerateConstrainedJson(
        JNIEnv *env,
        jobject /* thiz */,
        jlong modelPtr,
        jstring jSystemPrompt,
        jstring jUserPrompt,
        jstring jGrammar,
        jint maxTokens,
        jfloat temperature,
        jint nThreads) {
    std::setlocale(LC_NUMERIC, "C");
    auto *model = reinterpret_cast<llama_model *>(modelPtr);
    if (!model) {
        return env->NewStringUTF("");
    }

    const char *c_sys = env->GetStringUTFChars(jSystemPrompt, nullptr);
    const char *c_usr = env->GetStringUTFChars(jUserPrompt, nullptr);
    const char *c_gbnf = env->GetStringUTFChars(jGrammar, nullptr);

    std::string sys_str = c_sys ? c_sys : "";
    std::string usr_str = c_usr ? c_usr : "";
    std::string gbnf_str = c_gbnf ? c_gbnf : "";

    if (c_sys) env->ReleaseStringUTFChars(jSystemPrompt, c_sys);
    if (c_usr) env->ReleaseStringUTFChars(jUserPrompt, c_usr);
    if (c_gbnf) env->ReleaseStringUTFChars(jGrammar, c_gbnf);

    const std::string prompt = format_chat_prompt(model, sys_str, usr_str);
    const llama_vocab *vocab = llama_model_get_vocab(model);

    const int32_t n_prompt = -llama_tokenize(
        vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), nullptr, 0, true, true);
    if (n_prompt <= 0) {
        LOGE("Tokenize count failed: %d", n_prompt);
        return env->NewStringUTF("");
    }

    std::vector<llama_token> prompt_tokens(static_cast<size_t>(n_prompt));
    if (llama_tokenize(
            vocab,
            prompt.c_str(),
            static_cast<int32_t>(prompt.size()),
            prompt_tokens.data(),
            static_cast<int32_t>(prompt_tokens.size()),
            true,
            true) < 0) {
        LOGE("Failed to tokenize prompt");
        return env->NewStringUTF("");
    }

    const int32_t n_predict = std::max(32, static_cast<int32_t>(maxTokens));
    const int32_t threads = std::max(1, std::min(static_cast<int32_t>(nThreads), 6));

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(std::max(2048, n_prompt + n_predict + 32));
    cparams.n_batch = static_cast<uint32_t>(std::max(512, n_prompt));
    cparams.n_ubatch = 512;
    cparams.n_threads = threads;
    cparams.n_threads_batch = threads;

    llama_context *ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        LOGE("Failed to init llama_context");
        return env->NewStringUTF("");
    }

    auto sparams = llama_sampler_chain_default_params();
    llama_sampler *smpl = llama_sampler_chain_init(sparams);

    if (!gbnf_str.empty()) {
        llama_sampler *grammar_smpl = llama_sampler_init_grammar(vocab, gbnf_str.c_str(), "root");
        if (grammar_smpl != nullptr) {
            llama_sampler_chain_add(smpl, grammar_smpl);
        } else {
            LOGE("Failed to parse GBNF grammar! Falling back without grammar.");
        }
    }

    if (temperature <= 0.01f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_min_p(0.05f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(42));
    }

    llama_batch_ext *batch = llama_batch_ext_init(ctx);
    batch_set_tokens(batch, prompt_tokens.data(), n_prompt, 0);

    std::string output;
    output.reserve(512);

    int32_t n_tokens = n_prompt;
    int32_t n_pos = 0;

    for (int32_t step = 0; step < n_predict; ++step) {
        const int32_t rc = llama_process(ctx, LLAMA_PROCESS_TYPE_DECODE, batch);
        if (rc != 0) {
            LOGE("llama_process failed with code %d at step %d", rc, step);
            break;
        }
        n_pos += n_tokens;

        const llama_token token_id = llama_sampler_sample(smpl, ctx, -1);
        if (llama_vocab_is_eog(vocab, token_id)) {
            break;
        }

        char piece[256];
        const int32_t piece_len = llama_token_to_piece(vocab, token_id, piece, sizeof(piece), 0, false);
        if (piece_len > 0) {
            output.append(piece, static_cast<size_t>(piece_len));
        }

        batch_set_tokens(batch, &token_id, 1, n_pos);
        n_tokens = 1;
    }

    llama_batch_ext_free(batch);
    llama_sampler_free(smpl);
    llama_free(ctx);

    return env->NewStringUTF(output.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_aragao_vab_llama_LlamaJni_nativeFreeModel(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong modelPtr) {
    auto *model = reinterpret_cast<llama_model *>(modelPtr);
    if (model) {
        LOGI("Freeing GGUF model");
        llama_model_free(model);
    }
}
