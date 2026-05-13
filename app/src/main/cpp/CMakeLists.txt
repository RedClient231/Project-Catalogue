cmake_minimum_required(VERSION 3.22.1)
project("virtualspace_native")

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -fvisibility=hidden -ffunction-sections -fdata-sections")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -fvisibility=hidden -ffunction-sections -fdata-sections")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -Wl,-s")

# Assembly sources for syscall hooks
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(ASM_SOURCES
        ${CMAKE_SOURCE_DIR}/../asm/syscall_hook_arm64.S
        ${CMAKE_SOURCE_DIR}/../asm/trampoline_arm64.S
    )
elseif(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(ASM_SOURCES
        ${CMAKE_SOURCE_DIR}/../asm/syscall_hook_arm.S
        ${CMAKE_SOURCE_DIR}/../asm/trampoline_arm.S
    )
endif()

# Import the pre-built Rust library
add_library(virtualspace SHARED IMPORTED)
set_target_properties(virtualspace PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libvirtualspace.so
)

# JNI bridge and native hooks
add_library(vsnative SHARED
    jni_bridge.cpp
    memory_interface.cpp
    syscall_interceptor.cpp
    proc_maps_emulator.cpp
    ${ASM_SOURCES}
)

target_link_libraries(vsnative
    virtualspace
    log
    android
    dl
)

# Set visibility and strip symbols for release
set_target_properties(vsnative PROPERTIES
    C_VISIBILITY_PRESET hidden
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)
