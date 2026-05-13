// ffi_utils.rs - FFI helper utilities for C string conversion

use alloc::string::String;
use alloc::vec::Vec;
use core::ffi::{c_char, CStr};
use core::ptr;

/// Convert C string pointer to Rust String
pub unsafe fn cstr_to_string(ptr: *const c_char) -> String {
    if ptr.is_null() {
        return String::new();
    }
    match CStr::from_ptr(ptr).to_str() {
        Ok(s) => String::from(s),
        Err(_) => String::new(),
    }
}

/// Convert Rust String to C string (heap allocated, must be freed with rust_free_string)
pub fn rust_string_to_c(s: &str) -> *mut c_char {
    let bytes: Vec<u8> = s.bytes().chain(core::iter::once(0)).collect();
    let layout = alloc::alloc::Layout::from_size_align(bytes.len(), 1).unwrap();
    let ptr = unsafe { alloc::alloc::alloc(layout) };
    if ptr.is_null() {
        return ptr::null_mut();
    }
    unsafe {
        core::ptr::copy_nonoverlapping(bytes.as_ptr(), ptr, bytes.len());
    }
    ptr as *mut c_char
}

/// Free a string allocated by rust_string_to_c
pub fn free_rust_string(s: *mut c_char) {
    if s.is_null() {
        return;
    }
    unsafe {
        let len = CStr::from_ptr(s).to_bytes_with_nul().len();
        let layout = alloc::alloc::Layout::from_size_align(len, 1).unwrap();
        alloc::alloc::dealloc(s as *mut u8, layout);
    }
}
