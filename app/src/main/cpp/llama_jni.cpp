#include <jni.h>
#include <string>

extern "C" JNIEXPORT jboolean JNICALL
Java_com_volodapatik_offlineassistant_engine_LlamaNative_init(
        JNIEnv *env,
        jobject /* this */,
        jstring modelPath,
        jint contextSize,
        jint threads) {
    (void)env;
    (void)modelPath;
    (void)contextSize;
    (void)threads;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_volodapatik_offlineassistant_engine_LlamaNative_generate(
        JNIEnv *env,
        jobject /* this */,
        jstring prompt,
        jint maxTokens) {
    (void)maxTokens;
    const char *promptChars = env->GetStringUTFChars(prompt, nullptr);
    std::string reply = "Stub LLM runtime active. Prompt received: ";
    reply += promptChars ? promptChars : "";
    env->ReleaseStringUTFChars(prompt, promptChars);
    return env->NewStringUTF(reply.c_str());
}
