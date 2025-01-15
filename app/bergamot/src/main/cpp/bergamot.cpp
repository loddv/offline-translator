#include <jni.h>
#include <string>
#include "translator/byte_array_util.h"
#include "translator/parser.h"
#include "translator/response.h"
#include "translator/response_options.h"
#include "translator/service.h"
#include "translator/utils.h"
#include "third_party/cld2/public/compact_lang_det.h"
#include <string>

std::string func(const char* cfg, const char *input) {
    using namespace marian::bergamot;
    ConfigParser<AsyncService> configParser("Bergamot CLI", //multiOpMode
                                            false);
    auto &config = configParser.getConfig();

    AsyncService service(config.serviceConfig);
;
    auto validate = true;
    auto pathsDir = "";
    std::string cfg_s(cfg);
    // This "parseOptionsFromString" throws/aborts
    auto options = parseOptionsFromString(cfg_s, validate, pathsDir);

    std::shared_ptr<TranslationModel> model = service.createCompatibleModel(options);

    ResponseOptions responseOptions;
    std::string input_str(input);

    // Create a barrier using future/promise.
    std::promise<Response> promise;
    std::future<Response> future = promise.get_future();
    auto callback = [&promise](Response &&response) {
        // Fulfill promise.
        promise.set_value(std::move(response));
    };

    service.translate(model, std::move(input), callback, responseOptions);

    // Wait until promise sets the response.
    Response response = future.get();

    // Print (only) translated text.
    return response.target.text;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_bergamot_NativeLib_stringFromJNI(
        JNIEnv* env,
        jobject /* this */,
        jstring cfg,
        jstring data) {

    const char* c_cfg = env->GetStringUTFChars(cfg, nullptr);
    const char* c_data = env->GetStringUTFChars(data, nullptr);

    const char* out;
    try {
        std::string s = func(c_cfg, c_data);
        out = s.c_str();
    } catch(const std::exception &e) {
        out = e.what();
    }
    env->ReleaseStringUTFChars(cfg, c_cfg);
    env->ReleaseStringUTFChars(data, c_data);
    return env->NewStringUTF(out);

}


struct DetectionResult {
    std::string language;
    bool isReliable;
    int confidence;
};

DetectionResult detectLanguage(const char* text) {
    bool is_reliable;
    int text_bytes = strlen(text);
    bool is_plain_text = true;

    CLD2::CLDHints hints = {NULL, NULL, 0, CLD2::UNKNOWN_LANGUAGE};
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

    return DetectionResult{
            CLD2::LanguageCode(language3[0]),
            is_reliable,
            percent3[0]
    };
}



extern "C" JNIEXPORT jobject JNICALL
Java_com_example_bergamot_LangDetect_detectLanguage(
        JNIEnv* env,
        jobject /* this */,
        jstring text) {

    const char* c_text = env->GetStringUTFChars(text, nullptr);

    // Find the Result class and its constructor
    jclass resultClass = env->FindClass("com/example/bergamot/DetectionResult");
    jmethodID constructor = env->GetMethodID(resultClass, "<init>",
                                             "(Ljava/lang/String;ZI)V");

    try {
        DetectionResult result = detectLanguage(c_text);

        // Convert C++ string to jstring
        jstring j_language = env->NewStringUTF(result.language.c_str());

        // Create new Result object
        jobject j_result = env->NewObject(resultClass, constructor,
                                          j_language,
                                          result.isReliable,
                                          result.confidence);

        env->ReleaseStringUTFChars(text, c_text);
        return j_result;

    } catch(const std::exception &e) {
        env->ReleaseStringUTFChars(text, c_text);
        // Handle error
        jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exceptionClass, e.what());
        return nullptr;
    }
}
