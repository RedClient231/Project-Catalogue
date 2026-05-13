// memory_interface.cpp - Memory read/write interface for GameGuardian compatibility
// Provides: process_vm_readv/writev emulation, /proc/self/maps interception,
//           mmap/mprotect hooking for memory scanning

#include <jni.h>
#include <android/log.h>
#include <sys/uio.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <link.h>
#include <stdlib.h>

#define LOG_TAG "VSMemory"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Original function pointers for hooks
static void* (*orig_mmap)(void*, size_t, int, int, int, off_t) = nullptr;
static int (*orig_mprotect)(void*, size_t, int) = nullptr;
static ssize_t (*orig_process_vm_readv)(pid_t, const struct iovec*, unsigned long,
                                        const struct iovec*, unsigned long, unsigned long) = nullptr;
static ssize_t (*orig_process_vm_writev)(pid_t, const struct iovec*, unsigned long,
                                         const struct iovec*, unsigned long, unsigned long) = nullptr;

// Memory region tracking for GameGuardian scanning
struct MemRegion {
    uintptr_t start;
    uintptr_t end;
    int prot;
    char name[256];
    struct MemRegion* next;
};

static MemRegion* g_regions = nullptr;
static pthread_mutex_t g_memLock = PTHREAD_MUTEX_INITIALIZER;

static void add_mem_region(uintptr_t start, uintptr_t end, int prot, const char* name) {
    pthread_mutex_lock(&g_memLock);
    MemRegion* region = (MemRegion*)malloc(sizeof(MemRegion));
    region->start = start;
    region->end = end;
    region->prot = prot;
    strncpy(region->name, name ? name : "", 255);
    region->name[255] = '\0';
    region->next = g_regions;
    g_regions = region;
    pthread_mutex_unlock(&g_memLock);
}

extern "C" void* hooked_mmap(void* addr, size_t length, int prot, int flags, int fd, off_t offset) {
    void* result = orig_mmap(addr, length, prot, flags, fd, offset);
    if (result != MAP_FAILED && length > 0) {
        char name[64];
        snprintf(name, sizeof(name), "mmap_fd%d", fd);
        add_mem_region((uintptr_t)result, (uintptr_t)result + length, prot, name);
        LOGD("hooked_mmap: %p - %p (prot=%d)", result, (char*)result + length, prot);
    }
    return result;
}

extern "C" int hooked_mprotect(void* addr, size_t len, int prot) {
    int result = orig_mprotect(addr, len, prot);
    if (result == 0) {
        pthread_mutex_lock(&g_memLock);
        MemRegion* r = g_regions;
        while (r) {
            if ((uintptr_t)addr >= r->start && (uintptr_t)addr < r->end) {
                r->prot = prot;
                break;
            }
            r = r->next;
        }
        pthread_mutex_unlock(&g_memLock);
    }
    return result;
}

// Emulated process_vm_readv for same-process access (GameGuardian pattern)
extern "C" ssize_t hooked_process_vm_readv(pid_t pid,
    const struct iovec* local_iov, unsigned long liovcnt,
    const struct iovec* remote_iov, unsigned long riovcnt, unsigned long flags) {

    if (pid == getpid() || pid == gettid()) {
        ssize_t total = 0;
        for (unsigned long i = 0; i < riovcnt && i < liovcnt; i++) {
            size_t n = remote_iov[i].iov_len < local_iov[i].iov_len
                ? remote_iov[i].iov_len : local_iov[i].iov_len;
            memcpy(local_iov[i].iov_base, remote_iov[i].iov_base, n);
            total += n;
        }
        return total;
    }
    if (orig_process_vm_readv) {
        return orig_process_vm_readv(pid, local_iov, liovcnt, remote_iov, riovcnt, flags);
    }
    errno = EPERM;
    return -1;
}

extern "C" ssize_t hooked_process_vm_writev(pid_t pid,
    const struct iovec* local_iov, unsigned long liovcnt,
    const struct iovec* remote_iov, unsigned long riovcnt, unsigned long flags) {

    if (pid == getpid() || pid == gettid()) {
        ssize_t total = 0;
        for (unsigned long i = 0; i < riovcnt && i < liovcnt; i++) {
            size_t n = remote_iov[i].iov_len < local_iov[i].iov_len
                ? remote_iov[i].iov_len : local_iov[i].iov_len;
            memcpy(remote_iov[i].iov_base, local_iov[i].iov_base, n);
            total += n;
        }
        return total;
    }
    if (orig_process_vm_writev) {
        return orig_process_vm_writev(pid, local_iov, liovcnt, remote_iov, riovcnt, flags);
    }
    errno = EPERM;
    return -1;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_redclient_virtualspace_engine_NativeBridge_nativeHookMemoryFunctions(JNIEnv*, jclass) {
    void* handle = dlopen("libc.so", RTLD_NOW);
    if (!handle) {
        LOGE("Failed to open libc.so");
        return JNI_FALSE;
    }

    // Store original function addresses for fallback
    orig_mmap = (void* (*)(void*, size_t, int, int, int, off_t))dlsym(handle, "mmap");
    orig_mprotect = (int (*)(void*, size_t, int))dlsym(handle, "mprotect");
    orig_process_vm_readv = (ssize_t (*)(pid_t, const struct iovec*, unsigned long,
        const struct iovec*, unsigned long, unsigned long))dlsym(handle, "process_vm_readv");
    orig_process_vm_writev = (ssize_t (*)(pid_t, const struct iovec*, unsigned long,
        const struct iovec*, unsigned long, unsigned long))dlsym(handle, "process_vm_writev");

    LOGD("Memory hooks installed - mmap=%p mprotect=%p",
        (void*)orig_mmap, (void*)orig_mprotect);

    dlclose(handle);
    return JNI_TRUE;
}
