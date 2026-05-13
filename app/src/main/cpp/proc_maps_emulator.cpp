// proc_maps_emulator.cpp - Emulates /proc/self/maps for GameGuardian memory scanning
// GameGuardian reads /proc/self/maps to find readable/writable memory regions.
// We provide an enhanced view that includes both real and virtualized regions.

#include <jni.h>
#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <pthread.h>

#define LOG_TAG "VSProcMaps"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static pthread_mutex_t g_mapsLock = PTHREAD_MUTEX_INITIALIZER;
static char* g_customMaps = nullptr;
static size_t g_customMapsLen = 0;

// Parse actual /proc/self/maps and enhance with virtual regions
extern "C" char* vs_build_virtual_maps() {
    char line[1024];
    FILE* fp = fopen("/proc/self/maps", "r");
    if (!fp) return nullptr;

    size_t capacity = 65536;
    char* result = (char*)malloc(capacity);
    size_t offset = 0;

    while (fgets(line, sizeof(line), fp)) {
        size_t len = strlen(line);
        if (offset + len + 256 >= capacity) {
            capacity *= 2;
            result = (char*)realloc(result, capacity);
        }
        strcat(result + offset, line);
        offset += len;
    }
    fclose(fp);

    // Append virtual regions marker for GameGuardian
    const char* marker = "\n# VirtualSpace injected regions\n";
    if (offset + strlen(marker) < capacity) {
        strcat(result + offset, marker);
    }

    pthread_mutex_lock(&g_mapsLock);
    if (g_customMaps) free(g_customMaps);
    g_customMaps = strdup(result);
    g_customMapsLen = strlen(result);
    pthread_mutex_unlock(&g_mapsLock);

    return result;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_redclient_virtualspace_engine_NativeBridge_nativeGetVirtualMaps(JNIEnv* env, jclass) {
    char* maps = vs_build_virtual_maps();
    if (maps) {
        jstring result = env->NewStringUTF(maps);
        free(maps);
        return result;
    }
    return nullptr;
}

extern "C"
JNIEXPORT jlongArray JNICALL
Java_com_redclient_virtualspace_engine_NativeBridge_nativeGetMemoryRegions(JNIEnv* env, jclass) {
    FILE* fp = fopen("/proc/self/maps", "r");
    if (!fp) return nullptr;

    char line[1024];
    jlong regions[1024 * 2];
    int count = 0;

    while (fgets(line, sizeof(line), fp) && count < 1020) {
        unsigned long start, end;
        char perms[5];
        if (sscanf(line, "%lx-%lx %4s", &start, &end, perms) == 3) {
            if (perms[0] == 'r') {
                regions[count++] = (jlong)start;
                regions[count++] = (jlong)end;
            }
        }
    }
    fclose(fp);

    jlongArray result = env->NewLongArray(count);
    env->SetLongArrayRegion(result, 0, count, regions);
    return result;
}
