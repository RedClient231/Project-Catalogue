// VirtualSpace Engine Core Library
// Rust-native virtualization engine for Android
// Handles: APK/XAPK parsing, virtual env management, memory hooks, logcat, native lib loading

#![allow(non_snake_case)]

use std::ffi::{c_char, c_void, CStr};
use std::ptr;
use std::slice;

mod apk_parser;
mod virtual_env;
mod memory_hook;
mod native_loader;
mod logcat_capture;
mod syscall_hook;
mod ffi_utils;

use apk_parser::{parse_apk_manifest, extract_apk_resources};
use virtual_env::{init_virtual_environment, VirtualEnvConfig};
use memory_hook::{setup_memory_hooks as mem_setup, track_mmap_region, update_mprotect_region};
use native_loader::extract_native_libraries;
use logcat_capture::{start_logcat_to_file, stop_logcat};
use syscall_hook::{inject_syscalls as inject_sys, setup_proc_maps_emulation as setup_proc_maps};
use ffi_utils::{rust_string_to_c, cstr_to_string, free_rust_string};

// FFI: Initialize virtual environment
#[no_mangle]
pub extern "C" fn rust_init_virtual_env(base_path: *const c_char) -> i8 {
    if base_path.is_null() {
        return 0;
    }
    let path = unsafe { cstr_to_string(base_path) };
    let config = VirtualEnvConfig {
        base_path: path,
        sdk_version: 33,
        abi: detect_abi(),
    };
    match init_virtual_environment(config) {
        Ok(_) => 1,
        Err(_) => 0,
    }
}

// FFI: Install APK into virtual space
#[no_mangle]
pub extern "C" fn rust_install_apk(apk_path: *const c_char, install_dir: *const c_char) -> i8 {
    if apk_path.is_null() || install_dir.is_null() {
        return 0;
    }
    let apk = unsafe { cstr_to_string(apk_path) };
    let dir = unsafe { cstr_to_string(install_dir) };

    match parse_apk_manifest(&apk) {
        Ok(manifest) => {
            let abi = detect_abi();
            if let Err(_) = extract_apk_resources(&apk, &dir, &manifest) {
                return 0;
            }
            if let Err(_) = extract_native_libraries(&apk, &dir, &abi) {
                // Non-fatal: app may not have native libs
            }
            1
        }
        Err(_) => 0,
    }
}

// FFI: Install XAPK (APK + OBB data)
#[no_mangle]
pub extern "C" fn rust_install_xapk(xapk_path: *const c_char, install_dir: *const c_char) -> i8 {
    if xapk_path.is_null() || install_dir.is_null() {
        return 0;
    }
    let xapk = unsafe { cstr_to_string(xapk_path) };
    let dir = unsafe { cstr_to_string(install_dir) };

    match apk_parser::parse_xapk(&xapk) {
        Ok(xapk_info) => {
            if let Some(base_apk) = xapk_info.base_apk {
                if rust_install_apk(
                    rust_string_to_c(&base_apk) as *const c_char,
                    install_dir,
                ) == 0 {
                    return 0;
                }
            }
            // Extract OBB files
            for obb in &xapk_info.obb_files {
                let _ = apk_parser::extract_obb(&xapk, &obb, &format!("{}/obb", dir));
            }
            1
        }
        Err(_) => 0,
    }
}

// FFI: Launch virtual app
#[no_mangle]
pub extern "C" fn rust_launch_virtual_app(
    package_name: *const c_char,
    install_dir: *const c_char,
) -> i8 {
    if package_name.is_null() || install_dir.is_null() {
        return 0;
    }
    let pkg = unsafe { cstr_to_string(package_name) };
    let _dir = unsafe { cstr_to_string(install_dir) };

    // Setup process isolation and launch
    match virtual_env::launch_virtual_app(&pkg) {
        Ok(_) => 1,
        Err(_) => 0,
    }
}

// FFI: Setup memory hooks for GameGuardian
#[no_mangle]
pub extern "C" fn rust_setup_memory_hooks() -> i8 {
    match mem_setup() {
        Ok(_) => 1,
        Err(_) => 0,
    }
}

// FFI: Enable GameGuardian compatibility mode
#[no_mangle]
pub extern "C" fn rust_enable_gameguardian_compat() -> i8 {
    match memory_hook::enable_gameguardian_mode() {
        Ok(_) => 1,
        Err(_) => 0,
    }
}

// FFI: Setup /proc/maps emulation
#[no_mangle]
pub extern "C" fn rust_setup_proc_maps_emulation() -> i8 {
    match setup_proc_maps() {
        Ok(_) => 1,
        Err(_) => 0,
    }
}

// FFI: Inject syscall hooks
#[no_mangle]
pub extern "C" fn rust_inject_syscalls() -> i8 {
    match inject_sys() {
        Ok(_) => 1,
        Err(_) => 0,
    }
}

// FFI: Start logcat capture
#[no_mangle]
pub extern "C" fn rust_start_logcat_capture(output_dir: *const c_char) -> i8 {
    if output_dir.is_null() {
        return 0;
    }
    let dir = unsafe { cstr_to_string(output_dir) };
    match start_logcat_to_file(&dir) {
        Ok(_) => 1,
        Err(_) => 0,
    }
}

// FFI: Stop logcat capture
#[no_mangle]
pub extern "C" fn rust_stop_logcat_capture() {
    stop_logcat();
}

// FFI: Get native library directory for an APK
#[no_mangle]
pub extern "C" fn rust_get_native_lib_dir(apk_path: *const c_char) -> *mut c_char {
    if apk_path.is_null() {
        return ptr::null_mut();
    }
    let apk = unsafe { cstr_to_string(apk_path) };
    match native_loader::get_preferred_lib_dir(&apk) {
        Ok(dir) => rust_string_to_c(&dir),
        Err(_) => ptr::null_mut(),
    }
}

// FFI: Extract native libraries
#[no_mangle]
pub extern "C" fn rust_extract_native_libs(
    apk_path: *const c_char,
    output_dir: *const c_char,
    abi: *const c_char,
) -> i8 {
    if apk_path.is_null() || output_dir.is_null() || abi.is_null() {
        return 0;
    }
    let apk = unsafe { cstr_to_string(apk_path) };
    let out = unsafe { cstr_to_string(output_dir) };
    let abi_str = unsafe { cstr_to_string(abi) };

    match extract_native_libraries(&apk, &out, &abi_str) {
        Ok(_) => 1,
        Err(_) => 0,
    }
}

// FFI: Parse APK info (package name, version, permissions, activities)
#[no_mangle]
pub extern "C" fn rust_parse_apk_info(apk_path: *const c_char) -> *mut c_char {
    if apk_path.is_null() {
        return ptr::null_mut();
    }
    let apk = unsafe { cstr_to_string(apk_path) };
    match parse_apk_manifest(&apk) {
        Ok(info) => match serde_json::to_string(&info) {
            Ok(json) => rust_string_to_c(&json),
            Err(_) => ptr::null_mut(),
        },
        Err(_) => ptr::null_mut(),
    }
}

// Memory hook callbacks from assembly
#[no_mangle]
pub extern "C" fn rust_handle_openat(pathname: *const c_char) -> *const c_char {
    pathname // Return as-is; path redirection handled in C++ layer
}

#[no_mangle]
pub extern "C" fn rust_track_mmap_region(addr: usize, length: usize, prot: i32, _fd: i32) {
    let _ = track_mmap_region(addr, length, prot);
}

#[no_mangle]
pub extern "C" fn rust_update_mprotect_region(addr: usize, len: usize, prot: i32) {
    let _ = update_mprotect_region(addr, len, prot);
}

#[no_mangle]
pub extern "C" fn rust_vmread_same_process(
    local_buf: *mut c_void,
    remote_addr: *const c_void,
    size: usize,
) -> isize {
    unsafe {
        std::ptr::copy_nonoverlapping(remote_addr as *const u8, local_buf as *mut u8, size);
    }
    size as isize
}

#[no_mangle]
pub extern "C" fn rust_vmwrite_same_process(
    remote_addr: *mut c_void,
    local_buf: *const c_void,
    size: usize,
) -> isize {
    unsafe {
        std::ptr::copy_nonoverlapping(local_buf as *const u8, remote_addr as *mut u8, size);
    }
    size as isize
}

// 32-bit ARM variants
#[no_mangle]
pub extern "C" fn rust_track_mmap_region_arm(addr: u32, length: u32, prot: i32) {
    let _ = track_mmap_region(addr as usize, length as usize, prot);
}

#[no_mangle]
pub extern "C" fn rust_update_mprotect_region_arm(addr: u32, len: u32, prot: i32) {
    let _ = update_mprotect_region(addr as usize, len as usize, prot);
}

#[no_mangle]
pub extern "C" fn rust_vmread_same_process_arm(
    local_buf: *mut c_void,
    remote_addr: *const c_void,
    size: u32,
) -> i32 {
    unsafe {
        std::ptr::copy_nonoverlapping(remote_addr as *const u8, local_buf as *mut u8, size as usize);
    }
    size as i32
}

#[no_mangle]
pub extern "C" fn rust_vmwrite_same_process_arm(
    remote_addr: *mut c_void,
    local_buf: *const c_void,
    size: u32,
) -> i32 {
    unsafe {
        std::ptr::copy_nonoverlapping(local_buf as *const u8, remote_addr as *mut u8, size as usize);
    }
    size as i32
}

// Utility: Free a Rust-allocated string
#[no_mangle]
pub extern "C" fn rust_free_string(s: *mut c_char) {
    if !s.is_null() {
        free_rust_string(s);
    }
}

// Detect host ABI
fn detect_abi() -> &'static str {
    #[cfg(target_arch = "aarch64")]
    return "arm64-v8a";
    #[cfg(target_arch = "arm")]
    return "armeabi-v7a";
    #[cfg(not(any(target_arch = "aarch64", target_arch = "arm")))]
    return "arm64-v8a";
}
