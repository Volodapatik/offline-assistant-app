#include <jni.h>

#include <android/log.h>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "llama.h"

namespace {
constexpr const char *kLogTag = "llama_jni";
constexpr int kDefaultRepeatLastN = 64;
constexpr float kDefaultTemperature = 0.8f;
constexpr float kDefaultTopP = 0.95f;
constexpr int kDefaultTopK = 40;
constexpr float kDefaultRepeatPenalty = 1.1f;

std::mutex g_mutex;
llama_model *g_model = nullptr;
llama_context *g_ctx = nullptr;
int g_ctx_size = 2048;
int g_threads = 4;
bool g_backend_ready = false;
std::string g_last_error;

void log_error(const char *message) {
    if (message && message[0] != '\0') {
        g_last_error = message;
    } else {
        g_last_error = "Unknown error";
    }
    __android_log_print(ANDROID_LOG_ERROR, kLogTag, "%s", g_last_error.c_str());
}

void log_info(const char *message) {
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "%s", message);
}

int resolve_threads(int requested) {
    if (requested > 0) {
        return requested;
    }
    unsigned int cores = std::thread::hardware_concurrency();
    if (cores == 0) {
        return 4;
    }
    return static_cast<int>(cores);
}

void unload_model() {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
}

std::string token_to_piece(llama_model *model, llama_token token) {
    std::vector<char> buffer(256);
    int length = llama_token_to_piece(model, token, buffer.data(), static_cast<int>(buffer.size()), true);
    if (length < 0) {
        buffer.resize(static_cast<size_t>(-length));
        length = llama_token_to_piece(model, token, buffer.data(), static_cast<int>(buffer.size()), true);
    }
    if (length <= 0) {
        return std::string();
    }
    return std::string(buffer.data(), static_cast<size_t>(length));
}
}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_volodapatik_offlineassistant_engine_LlamaNative_init(
        JNIEnv *env,
        jobject /* this */,
        jstring modelPath,
        jint contextSize,
        jint threads) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_last_error.clear();

    const char *model_path_chars = env->GetStringUTFChars(modelPath, nullptr);
    if (!model_path_chars) {
        log_error("Failed to read model path.");
        return JNI_FALSE;
    }
    std::string model_path(model_path_chars);
    env->ReleaseStringUTFChars(modelPath, model_path_chars);

    if (model_path.empty()) {
        log_error("Model path is empty.");
        return JNI_FALSE;
    }

    if (!g_backend_ready) {
        llama_backend_init(false);
        g_backend_ready = true;
    }

    unload_model();

    g_ctx_size = contextSize > 0 ? contextSize : g_ctx_size;
    g_threads = resolve_threads(threads);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = g_ctx_size;
    ctx_params.n_threads = g_threads;
    ctx_params.n_threads_batch = g_threads;

    g_model = llama_load_model_from_file(model_path.c_str(), model_params);
    if (!g_model) {
        log_error("Failed to load model.");
        return JNI_FALSE;
    }

    g_ctx = llama_new_context_with_model(g_model, ctx_params);
    if (!g_ctx) {
        log_error("Failed to create context.");
        unload_model();
        return JNI_FALSE;
    }

    g_last_error.clear();
    log_info("llama.cpp model loaded.");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_volodapatik_offlineassistant_engine_LlamaNative_generate(
        JNIEnv *env,
        jobject /* this */,
        jstring prompt,
        jint maxTokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_ctx || !g_model) {
        log_error("Generate called before init.");
        return env->NewStringUTF("");
    }

    const char *prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_chars) {
        log_error("Failed to read prompt.");
        return env->NewStringUTF("");
    }
    std::string prompt_text(prompt_chars);
    env->ReleaseStringUTFChars(prompt, prompt_chars);

    if (prompt_text.empty()) {
        return env->NewStringUTF("");
    }

    llama_kv_cache_clear(g_ctx);

    std::vector<llama_token> tokens(prompt_text.size() + 8);
    int token_count = llama_tokenize(
            g_model,
            prompt_text.c_str(),
            tokens.data(),
            static_cast<int>(tokens.size()),
            true,
            true);
    if (token_count < 0) {
        tokens.resize(static_cast<size_t>(-token_count));
        token_count = llama_tokenize(
                g_model,
                prompt_text.c_str(),
                tokens.data(),
                static_cast<int>(tokens.size()),
                true,
                true);
    }
    if (token_count <= 0) {
        log_error("Tokenization failed.");
        return env->NewStringUTF("");
    }
    tokens.resize(static_cast<size_t>(token_count));
    if (g_ctx_size > 1 && token_count >= g_ctx_size) {
        const int keep_tokens = g_ctx_size - 1;
        std::vector<llama_token> trimmed;
        trimmed.reserve(static_cast<size_t>(keep_tokens));
        trimmed.insert(
                trimmed.end(),
                tokens.end() - keep_tokens,
                tokens.end());
        tokens.swap(trimmed);
        token_count = keep_tokens;
    }

    llama_batch batch = llama_batch_init(static_cast<int>(tokens.size()), 0, 1);
    for (size_t i = 0; i < tokens.size(); ++i) {
        llama_batch_add(batch, tokens[i], static_cast<int>(i), {0}, i == tokens.size() - 1);
    }
    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        log_error("Failed to decode prompt.");
        return env->NewStringUTF("");
    }
    llama_batch_free(batch);

    std::string output;
    output.reserve(static_cast<size_t>(maxTokens) * 4);

    std::vector<llama_token> last_tokens;
    last_tokens.reserve(kDefaultRepeatLastN);
    for (size_t i = tokens.size() > kDefaultRepeatLastN ? tokens.size() - kDefaultRepeatLastN : 0;
         i < tokens.size();
         ++i) {
        last_tokens.push_back(tokens[i]);
    }

    int n_past = token_count;
    int max_tokens = maxTokens > 0 ? maxTokens : 0;
    llama_batch gen_batch = llama_batch_init(1, 0, 1);

    for (int i = 0; i < max_tokens; ++i) {
        const float *logits = llama_get_logits(g_ctx);
        const int n_vocab = llama_n_vocab(g_model);

        std::vector<llama_token_data> candidates;
        candidates.reserve(static_cast<size_t>(n_vocab));
        for (int token_id = 0; token_id < n_vocab; ++token_id) {
            candidates.push_back({token_id, logits[token_id], 0.0f});
        }
        llama_token_data_array candidates_p = {candidates.data(), candidates.size(), false};

        if (!last_tokens.empty()) {
            llama_sample_repetition_penalty(
                    g_ctx,
                    &candidates_p,
                    last_tokens.data(),
                    static_cast<int>(last_tokens.size()),
                    kDefaultRepeatPenalty);
        }
        llama_sample_top_k(g_ctx, &candidates_p, kDefaultTopK, 1);
        llama_sample_top_p(g_ctx, &candidates_p, kDefaultTopP, 1);
        llama_sample_temperature(g_ctx, &candidates_p, kDefaultTemperature);

        llama_token new_token = llama_sample_token(g_ctx, &candidates_p);
        if (new_token == llama_token_eos(g_model)) {
            break;
        }

        output += token_to_piece(g_model, new_token);

        if (static_cast<int>(last_tokens.size()) >= kDefaultRepeatLastN) {
            last_tokens.erase(last_tokens.begin());
        }
        last_tokens.push_back(new_token);

        llama_batch_clear(gen_batch);
        llama_batch_add(gen_batch, new_token, n_past, {0}, true);
        n_past += 1;
        if (llama_decode(g_ctx, gen_batch) != 0) {
            log_error("Decode failed during generation.");
            break;
        }
    }

    llama_batch_free(gen_batch);

    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_volodapatik_offlineassistant_engine_LlamaNative_lastError(
        JNIEnv *env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return env->NewStringUTF(g_last_error.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_volodapatik_offlineassistant_engine_LlamaNative_reset(
        JNIEnv * /* env */,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) {
        llama_kv_cache_clear(g_ctx);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_volodapatik_offlineassistant_engine_LlamaNative_release(
        JNIEnv * /* env */,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    unload_model();
    if (g_backend_ready) {
        llama_backend_free();
        g_backend_ready = false;
    }
}
