#include <jni.h>
#include <string>
#include <android/log.h>
#include "translator/byte_array_util.h"
#include "translator/parser.h"
#include "translator/response.h"
#include "translator/response_options.h"
#include "translator/service.h"
#include "translator/utils.h"
#include "third_party/cld2/public/compact_lang_det.h"
#include <string>
using namespace marian::bergamot;

#include <unordered_map>
#include <mutex>
static std::unordered_map<std::string, std::shared_ptr<TranslationModel>> model_cache;
static std::unique_ptr<AsyncService> global_service = nullptr;
static std::mutex service_mutex;

void initializeService() {
    std::lock_guard<std::mutex> lock(service_mutex);

    if (global_service == nullptr) {
        ConfigParser<AsyncService> configParser("Bergamot CLI", false);
        auto &config = configParser.getConfig();
        global_service = std::make_unique<AsyncService>(config.serviceConfig);
    }
}

void loadModelIntoCache(const std::string& cfg, const std::string& key) {
    std::lock_guard<std::mutex> lock(service_mutex);

    auto validate = true;
    auto pathsDir = "";

    if (model_cache.find(key) == model_cache.end()) {
        auto options = parseOptionsFromString(cfg, validate, pathsDir);
        model_cache[key] = global_service->createCompatibleModel(options);
    }
}

std::string func(const char* cfg, const char *input, const char* key) {
    // Initialize service if not already done
    initializeService();

    std::string key_str(key);
    std::string cfg_s(cfg);

    // Load model into cache if not already present
    loadModelIntoCache(cfg_s, key_str);

    // NB: this assumes a thread has not unloaded the model from the cache
    // as we don't implement unloading, it's fine
    std::shared_ptr<TranslationModel> model = model_cache[key_str];

    ResponseOptions responseOptions;
    std::string input_str(input);

    // Create a barrier using future/promise.
    std::promise<Response> promise;
    std::future<Response> future = promise.get_future();
    auto callback = [&promise](Response &&response) {
        // Fulfill promise.
        promise.set_value(std::move(response));
    };

    // Pass the model via shared_ptr and move the input string.
    global_service->translate(model, std::move(input_str), callback, responseOptions);

    // Wait until promise sets the response.
    Response response = future.get();

    // Print (only) translated text.
    return response.target.text;
}

extern "C" __attribute__((visibility("default"))) JNIEXPORT void JNICALL
Java_dev_davidv_bergamot_NativeLib_initializeService(
        JNIEnv* env,
        jobject /* this */) {
    try {
        initializeService();
    } catch(const std::exception &e) {
        jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exceptionClass, e.what());
    }
}

extern "C" __attribute__((visibility("default"))) JNIEXPORT void JNICALL
Java_dev_davidv_bergamot_NativeLib_loadModelIntoCache(
        JNIEnv* env,
        jobject /* this */,
        jstring cfg,
        jstring key) {

    const char* c_cfg = env->GetStringUTFChars(cfg, nullptr);
    const char* c_key = env->GetStringUTFChars(key, nullptr);

    try {
        std::string cfg_str(c_cfg);
        std::string key_str(c_key);
        loadModelIntoCache(cfg_str, key_str);
    } catch(const std::exception &e) {
        jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exceptionClass, e.what());
    }

    env->ReleaseStringUTFChars(cfg, c_cfg);
    env->ReleaseStringUTFChars(key, c_key);
}

extern "C" __attribute__((visibility("default"))) JNIEXPORT jstring JNICALL
Java_dev_davidv_bergamot_NativeLib_stringFromJNI(
        JNIEnv* env,
        jobject /* this */,
        jstring cfg,
        jstring data,
        jstring key) {

    const char* c_cfg = env->GetStringUTFChars(cfg, nullptr);
    const char* c_data = env->GetStringUTFChars(data, nullptr);
    const char* c_key = env->GetStringUTFChars(key, nullptr);

    jstring result = nullptr;
    try {
        std::string s = func(c_cfg, c_data, c_key);
        result = env->NewStringUTF(s.c_str());
    } catch(const std::exception &e) {
        jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exceptionClass, e.what());
    }

    env->ReleaseStringUTFChars(cfg, c_cfg);
    env->ReleaseStringUTFChars(data, c_data);
    env->ReleaseStringUTFChars(key, c_key);

    return result;
}

// Cleanup function to be called when the library is unloaded
extern "C" __attribute__((visibility("default"))) JNIEXPORT void JNICALL
Java_dev_davidv_bergamot_NativeLib_cleanup(JNIEnv* env, jobject /* this */) {
    std::lock_guard<std::mutex> lock(service_mutex);
    global_service.reset();
    model_cache.clear();
}


struct DetectionResult {
    std::string language;
    bool isReliable;
    int confidence;
};

DetectionResult detectLanguage(const char *text, const char *language_hint = nullptr) {
    bool is_reliable;
    int text_bytes = strlen(text);
    bool is_plain_text = true;

    CLD2::Language hint_lang = CLD2::UNKNOWN_LANGUAGE;
    if (language_hint != nullptr && strlen(language_hint) > 0) {
        hint_lang = CLD2::GetLanguageFromName(language_hint);
    }

    CLD2::CLDHints hints = {NULL, NULL, 0, hint_lang};
    CLD2::Language language3[3];
    int percent3[3];
    double normalized_score3[3];
    int chunk_bytes;

    CLD2::ExtDetectLanguageSummary(
            text,
            text_bytes,
            is_plain_text,
            &hints,
            0,
            language3,
            percent3,
            normalized_score3,
            NULL,
            &chunk_bytes,
            &is_reliable
    );

    __android_log_print(ANDROID_LOG_DEBUG, "Bergamot", "Language detection results:");
    for (int i = 0; i < 3; i++) {
        __android_log_print(ANDROID_LOG_DEBUG, "Bergamot", "  %d: %s - %d%% (score: %.3f)",
                            i + 1,
                            CLD2::LanguageCode(language3[i]),
                            percent3[i],
                            normalized_score3[i]);
    }

    return DetectionResult{
            CLD2::LanguageCode(language3[0]),
            is_reliable,
            percent3[0]
    };
}



extern "C" __attribute__((visibility("default"))) JNIEXPORT jobject JNICALL
Java_dev_davidv_bergamot_LangDetect_detectLanguage(
        JNIEnv* env,
        jobject /* this */,
        jstring text,
        jstring hint) {

    const char* c_text = env->GetStringUTFChars(text, nullptr);
    const char *c_hint = nullptr;
    if (hint != nullptr) {
        c_hint = env->GetStringUTFChars(hint, nullptr);
    }

    // Find the Result class and its constructor
    jclass resultClass = env->FindClass("dev/davidv/bergamot/DetectionResult");
    jmethodID constructor = env->GetMethodID(resultClass, "<init>",
                                             "(Ljava/lang/String;ZI)V");

    try {
        DetectionResult result = detectLanguage(c_text, c_hint);

        // Convert C++ string to jstring
        jstring j_language = env->NewStringUTF(result.language.c_str());

        // Create new Result object
        jobject j_result = env->NewObject(resultClass, constructor,
                                          j_language,
                                          result.isReliable,
                                          result.confidence);

        env->ReleaseStringUTFChars(text, c_text);
        if (c_hint != nullptr) {
            env->ReleaseStringUTFChars(hint, c_hint);
        }
        return j_result;

    } catch(const std::exception &e) {
        env->ReleaseStringUTFChars(text, c_text);
        if (c_hint != nullptr) {
            env->ReleaseStringUTFChars(hint, c_hint);
        }
        // Handle error
        jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exceptionClass, e.what());
        return nullptr;
    }
}
