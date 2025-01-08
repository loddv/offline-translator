#include <jni.h>
#include <string>
#include "translator/byte_array_util.h"
#include "translator/parser.h"
#include "translator/response.h"
#include "translator/response_options.h"
#include "translator/service.h"
#include "translator/utils.h"

//extern std::string func(char* cfg2, char *input2);

std::string func(const char* cfg, const char *input) {
    using namespace marian::bergamot;
    ConfigParser<AsyncService> configParser("Bergamot CLI", //multiOpMode
                                            false);
    //configParser.parseArgs(argc, argv);
    auto &config = configParser.getConfig();

    AsyncService service(config.serviceConfig);
;
    auto validate = true;
    auto pathsDir = "";
    std::string cfg_s(cfg);
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

    std::string s = func(c_cfg, c_data);
    const char* out = s.c_str();

    env->ReleaseStringUTFChars(cfg, c_cfg);
    env->ReleaseStringUTFChars(data, c_data);
    return env->NewStringUTF(out);

}
