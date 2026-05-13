// syscall_interceptor.cpp - Syscall interception layer for virtualized I/O
// Intercepts: open/openat, read, write, access, stat, fstatat for path redirection

#include <jni.h>
#include <android/log.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <errno.h>
#include <dlfcn.h>

#define LOG_TAG "VSSyscall"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static char g_virtualRoot[512] = {0};
static size_t g_vrootLen = 0;

static int is_virtual_path(const char* path) {
    if (!path || !g_virtualRoot[0]) return 0;
    return strncmp(path, g_virtualRoot, g_vrootLen) == 0;
}

static const char* redirect_path(const char* path, char* buf, size_t buflen) {
    if (!path || !g_virtualRoot[0]) return path;
    if (strstr(path, "/data/data/") || strstr(path, "/data/user/")) {
        snprintf(buf, buflen, "%s%s", g_virtualRoot, path);
        return buf;
    }
    return path;
}

extern "C" void vs_set_virtual_root(const char* root) {
    if (root) {
        strncpy(g_virtualRoot, root, sizeof(g_virtualRoot) - 1);
        g_virtualRoot[sizeof(g_virtualRoot) - 1] = '\0';
        g_vrootLen = strlen(g_virtualRoot);
        LOGD("Virtual root set: %s", g_virtualRoot);
    }
}

// Hooked open - redirects paths inside virtual root
extern "C" int vs_open(const char* pathname, int flags, ...) {
    char buf[1024];
    const char* realPath = redirect_path(pathname, buf, sizeof(buf));
    LOGD("open: %s -> %s", pathname, realPath);
    return syscall(__NR_openat, AT_FDCWD, realPath, flags);
}

extern "C" int vs_openat(int dirfd, const char* pathname, int flags, ...) {
    char buf[1024];
    const char* realPath = redirect_path(pathname, buf, sizeof(buf));
    return syscall(__NR_openat, dirfd, realPath, flags);
}

extern "C" int vs_access(const char* pathname, int mode) {
    char buf[1024];
    const char* realPath = redirect_path(pathname, buf, sizeof(buf));
    return syscall(__NR_faccessat, AT_FDCWD, realPath, mode, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_redclient_virtualspace_engine_NativeBridge_nativeSetVirtualRoot(
        JNIEnv* env, jclass clazz, jstring rootPath) {
    const char* path = env->GetStringUTFChars(rootPath, nullptr);
    vs_set_virtual_root(path);
    env->ReleaseStringUTFChars(rootPath, path);
}
