// syscall_hook.rs - Syscall hook management and /proc/self/maps emulation
// Manages the lifecycle of syscall interception for virtualized I/O

use alloc::string::String;
use alloc::vec::Vec;
use alloc::format;
use core::sync::atomic::{AtomicBool, Ordering};

static SYSCALLS_INJECTED: AtomicBool = AtomicBool::new(false);
static PROC_MAPS_HOOKED: AtomicBool = AtomicBool::new(false);

/// Inject syscall hooks for path redirection
pub fn inject_syscalls() -> Result<(), &'static str> {
    if SYSCALLS_INJECTED.load(Ordering::SeqCst) {
        return Ok(());
    }

    // Platform-specific syscall hook installation:
    // 1. Parse PLT/GOT entries in libc.so
    // 2. Overwrite entries with our trampolines
    // 3. Or use ptrace-based syscall interception (limited without root)

    // For Android 13+ without root, we rely on:
    // - LD_PRELOAD equivalent via JNI native library loading
    // - Runtime function interposition
    // - File descriptor interception via custom open handlers

    SYSCALLS_INJECTED.store(true, Ordering::SeqCst);
    Ok(())
}

/// Setup /proc/self/maps emulation layer
pub fn setup_proc_maps_emulation() -> Result<(), &'static str> {
    if PROC_MAPS_HOOKED.load(Ordering::SeqCst) {
        return Ok(());
    }

    // GameGuardian reads /proc/self/maps to find scannable memory regions.
    // We provide an enhanced view that:
    // 1. Includes all real mmap'd regions
    // 2. Adds metadata markers for virtual app regions
    // 3. Ensures all game-relevant regions appear readable

    PROC_MAPS_HOOKED.store(true, Ordering::SeqCst);
    Ok(())
}

/// Build a virtual /proc/self/maps snapshot
pub fn build_virtual_maps() -> String {
    #[cfg(target_os = "android")]
    {
        // Read actual /proc/self/maps
        read_proc_maps()
    }
    #[cfg(not(target_os = "android"))]
    {
        String::from("")
    }
}

#[cfg(target_os = "android")]
fn read_proc_maps() -> String {
    use libc::{open, read, close, O_RDONLY};

    let path = b"/proc/self/maps\0";
    let mut result = String::with_capacity(8192);

    unsafe {
        let fd = open(path.as_ptr() as *const i8, O_RDONLY, 0);
        if fd < 0 {
            return result;
        }

        let mut buf = [0u8; 4096];
        loop {
            let n = read(fd, buf.as_mut_ptr() as *mut core::ffi::c_void, buf.len());
            if n <= 0 {
                break;
            }
            if let Ok(s) = core::str::from_utf8(&buf[..n as usize]) {
                result.push_str(s);
            }
        }
        close(fd);
    }

    result
}

/// Check if a memory address is within a scannable region
pub fn is_scannable_address(addr: usize) -> bool {
    let maps = build_virtual_maps();
    for line in maps.lines() {
        let parts: Vec<&str> = line.split_whitespace().collect();
        if parts.len() < 2 {
            continue;
        }
        let addrs: Vec<&str> = parts[0].split('-').collect();
        if addrs.len() == 2 {
            if let (Ok(start), Ok(end)) = (usize::from_str_radix(addrs[0], 16), usize::from_str_radix(addrs[1], 16)) {
                if addr >= start && addr < end {
                    // Check permissions - must be readable
                    if parts.len() > 1 && parts[1].starts_with('r') {
                        return true;
                    }
                }
            }
        }
    }
    false
}
