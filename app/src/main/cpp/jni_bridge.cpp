// jni_bridge.cpp - JNI bridge between Kotlin UI and Rust engine
// Handles: APK parsing, virtual env setup, memory hooks, logcat, native lib loading

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <unistd.h>
#include <sys/mman.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>
#include <fcntl.h>
#include <dirent.h>

#define LOG_TAG "VirtualSpaceJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// External Rust FFI declarations
extern "C" {
    int8_t rust_init_virtual_env(const char* base_path);
    int8_t rust_install_apk(const char* apk_path, const char* install_dir);
    int8_t rust_install_xapk(const char* xapk_path, const char* install_dir);
    int8_t rust_launch_virtual_app(const char* package_name, const char* install_dir);
    int8_t rust_setup_memory_hooks();
    int8_t rust_enable_gameguardian_compat();
    int8_t rust_start_logcat_capture(const char* output_dir);
    void   rust_stop_logcat_capture();
    char*  rust_get_native_lib_dir(const char* apk_path);
    void   rust_free_string(char* s);
    int8_t rust_extract_native_libs(const char* apk_path, const char* output_dir, const char* abi);
    int8_t rust_setup_proc_maps_emulation();
    int8_t rust_inject_syscalls();
    char*  rust_parse_apk_info(const char* apk_path);
}

static JavaVM* g_vm = nullptr;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_vm = vm;
    LOGD("JNI_OnLoad: VirtualSpace native library loaded");
    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_redclient_virtualspace_engine_NativeBridge_nativeInitVirtualEnv(
        JNIEnv* env, jclass clazz, jstring basePath) {
    const char* path = env->GetStringUTFChars(basePath, nullptr);
    int8_t result = rust_init_virtual_env(path);
    env->ReleaseStringUTFChars(basePath, path);
    LOGD("nativeInitVirtualEnv: %s", result ? "success" : "failed");
    return result != 0;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_redclient_virtualspace_engine_NativeBridge_nativeInstallApk(
        JNIEnv* env, jclass clazz, jstring apkPath, jstring installDir) {
    const char* apk = env->GetStringUTFChars(apkPath, nullptr);
    const char* dir = env->GetStringUTFChars(installDir, nullptr);
    int8_t result = rust_install_apk(apk, dir);
    env->ReleaseStringUTFChars(apkPath, apk);
    env->ReleaseStringUTFChars(installDir, dir);
    return result != 0;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_redclient_virtualspace_engine_NativeBridge_nativeInstallXapk(
        JNIEnv* env, jclass clazz, jstring xapkPath, jstring installDir) {
    const char* xapk = env->GetStringUTFChars(xapkPath, nullptr);
    const char* dir = env->GetStringUTFChars(installDir, nullptr);
    int8_t result = rust_install_xapk(xapk, dir);
    env->ReleaseStringUTFChars(xapkPath, xapk);
    env->ReleaseStringUTFChars(installDir, dir);
    return result != 0;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_redclient_virtualspace_engine_NativeBridge_nativeLaunchApp(
        JNIEnv* env, jclass clazz, jstring packageName, jstring installDir) {
    const char* pkg = env->GetStringUTFChars(packageName, nullptr);
    const char* dir = env->GetStringUTFChars(installDir, nullptr);
    int8_t result = rust_launch_virtual_app(pkg, dir);
    env->ReleaseStringUTFChars(packageName, pkg);
    env->ReleaseStringUTFChars(installDir, dir);
    return result != 0;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_redclient_virtualspace_engine_NativeBridge_nativeSetupMemoryHooks(
        JNIEnv* env, jclass clazz) {
    int8_t result = rust_setup_memory_hooks();
    LOGD("nativeSetupMemoryHooks: %s", result ? "success" : "failed");
    return result != 0;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_redclient_virtualspace_engine_NativeBridge_nativeEnableGameGuardianCompat(
        JNIEnv* env, jclass clazz) {
    int8_t result = rust_enable_gameguardian_compat();
    if (result) {
        rust_setup_proc_maps_emulation();
        rust_inject_syscalls();
    }
    LOGD("nativeEnableGameGuardianCompat: %s", result ? "success" : "failed");
    return result != 0;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_redclient_virtualspace_service_LogcatService_nativeStartLogcatCapture(
        JNIEnv* env, jclass clazz, jstring outputDir) {
    const char* dir = env->GetStringUTFChars(outputDir, nullptr);
    int8_t result = rust_start_logcat_capture(dir);
    env->ReleaseStringUTFChars(outputDir, dir);
    LOGD("nativeStartLogcatCapture: %s", result ? "success" : "failed");
    return result != 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_redclient_virtualspace_service_LogcatService_nativeStopLogcatCapture(
        JNIEnv* env, jclass clazz) {
    rust_stop_logcat_capture();
    LOGD("nativeStopLogcatCapture: stopped");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_redclient_virtualspace_engine_NativeBridge_nativeGetNativeLibDir(
        JNIEnv* env, jclass clazz, jstring apkPath) {
    const char* apk = env->GetStringUTFChars(apkPath, nullptr);
    char* result = rust_get_native_lib_dir(apk);
    env->ReleaseStringUTFChars(apkPath, apk);
    if (result) {
        jstring jresult = env->NewStringUTF(result);
        rust_free_string(result);
        return jresult;
    }
    return nullptr;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_redclient_virtualspace_engine_NativeBridge_nativeExtractNativeLibs(
        JNIEnv* env, jclass clazz, jstring apkPath, jstring outputDir, jstring abi) {
    const char* apk = env->GetStringUTFChars(apkPath, nullptr);
    const char* dir = env->GetStringUTFChars(outputDir, nullptr);
    const char* abiStr = env->GetStringUTFChars(abi, nullptr);
    int8_t result = rust_extract_native_libs(apk, dir, abiStr);
    env->ReleaseStringUTFChars(apkPath, apk);
    env->ReleaseStringUTFChars(outputDir, dir);
    env->ReleaseStringUTFChars(abi, abiStr);
    return result != 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_redclient_virtualspace_util_ApkParser_nativeParseApkInfo(
        JNIEnv* env, jclass clazz, jstring apkPath) {
    const char* apk = env->GetStringUTFChars(apkPath, nullptr);
    char* result = rust_parse_apk_info(apk);
    env->ReleaseStringUTFChars(apkPath, apk);
    if (result) {
        jstring jresult = env->NewStringUTF(result);
        rust_free_string(result);
        return jresult;
    }
    return nullptr;
}
