// memory_hook.rs - Memory hook management for GameGuardian compatibility
// Tracks memory regions, provides virtual /proc/self/maps, handles rw access

use std::vec::Vec;
use std::sync::atomic::{AtomicBool, Ordering};

static HOOKS_INITIALIZED: AtomicBool = AtomicBool::new(false);
static GG_MODE: AtomicBool = AtomicBool::new(false);

/// Memory region tracking entry
#[derive(Clone)]
pub struct MemRegion {
    pub start: usize,
    pub end: usize,
    pub prot: i32,
    pub flags: u32,
}

static mut REGIONS: Vec<MemRegion> = Vec::new();

/// Initialize memory hook subsystem
pub fn setup_memory_hooks() -> Result<(), &'static str> {
    if HOOKS_INITIALIZED.load(Ordering::SeqCst) {
        return Ok(());
    }

    // Initialize region tracking
    unsafe {
        REGIONS = Vec::with_capacity(256);
    }

    HOOKS_INITIALIZED.store(true, Ordering::SeqCst);
    Ok(())
}

/// Enable GameGuardian specific compatibility mode
pub fn enable_gameguardian_mode() -> Result<(), &'static str> {
    setup_memory_hooks()?;
    GG_MODE.store(true, Ordering::SeqCst);

    // GameGuardian requires:
    // 1. Readable /proc/self/maps with all regions visible
    // 2. process_vm_readv/writev working for self-process
    // 3. mmap/mprotect hooks for tracking new allocations
    // 4. ptrace_ATTACH emulation (return success without actual ptrace)

    Ok(())
}

/// Track a new mmap region
pub fn track_mmap_region(addr: usize, length: usize, prot: i32) -> Result<(), &'static str> {
    if !HOOKS_INITIALIZED.load(Ordering::SeqCst) {
        return Err("Hooks not initialized");
    }

    unsafe {
        // Remove any overlapping regions
        REGIONS.retain(|r| r.end <= addr || r.start >= addr + length);

        REGIONS.push(MemRegion {
            start: addr,
            end: addr + length,
            prot,
            flags: 0,
        });
    }

    Ok(())
}

/// Update region protection after mprotect
pub fn update_mprotect_region(addr: usize, _len: usize, prot: i32) -> Result<(), &'static str> {
    unsafe {
        for region in REGIONS.iter_mut() {
            if region.start == addr {
                region.prot = prot;
                break;
            }
        }
    }
    Ok(())
}

/// Get all tracked memory regions
pub fn get_regions() -> Vec<MemRegion> {
    unsafe { REGIONS.clone() }
}

/// Check if GameGuardian mode is active
pub fn is_gg_mode() -> bool {
    GG_MODE.load(Ordering::SeqCst)
}
