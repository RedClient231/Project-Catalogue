// syscall_hook_arm64.S - AArch64 syscall interception trampolines
// Hooks openat, read, write, mmap, mprotect, process_vm_readv/writev
// Preserves all registers per AAPCS64, uses scratch x16/x17

    .text
    .align 4
    .globl hook_trampoline_openat
    .globl hook_trampoline_mmap
    .globl hook_trampoline_mprotect
    .globl hook_trampoline_vmread
    .globl hook_trampoline_vmwrite
    .globl asm_inject_hook
    .globl asm_restore_hook

// AArch64 syscall numbers
.equ NR_openat, 56
.equ NR_mmap, 222
.equ NR_mprotect, 226
.equ NR_process_vm_readv, 270
.equ NR_process_vm_writev, 271

// hook_trampoline_openat - intercepts openat for path redirection
// x0 = dirfd, x1 = pathname, x2 = flags, x3 = mode
hook_trampoline_openat:
    STP     x29, x30, [sp, #-16]!
    MOV     x29, sp
    STP     x0, x1, [sp, #-16]!
    STP     x2, x3, [sp, #-16]!
    STP     x4, x5, [sp, #-16]!
    STP     x6, x7, [sp, #-16]!
    STR     x8, [sp, #-16]!

    // Call Rust handler: check if path needs redirection
    // x0 already has dirfd, x1 has pathname
    BL      rust_handle_openat
    // Result in x0 = redirected pathname or original

    LDR     x8, [sp], #16
    LDP     x4, x5, [sp], #16
    LDP     x2, x3, [sp], #16
    LDP     x0, x1, [sp], #16
    MOV     x1, x0          // replace pathname with result
    MOV     x0, x4          // restore dirfd
    LDP     x29, x30, [sp], #16

    // Execute original syscall
    MOV     x8, #NR_openat
    SVC     #0
    RET

// hook_trampoline_mmap - intercepts mmap for region tracking
// x0 = addr, x1 = length, x2 = prot, x3 = flags, x4 = fd, x5 = offset
hook_trampoline_mmap:
    STP     x29, x30, [sp, #-16]!
    MOV     x29, sp

    // Execute syscall first
    MOV     x8, #NR_mmap
    SVC     #0

    // If success, track the region
    CMP     x0, #0
    B.LT    1f

    // Save result
    MOV     x16, x0
    // Call Rust: rust_track_mmap_region(addr, length, prot, fd)
    MOV     x0, x16
    // x1 already has length, x2 has prot
    BL      rust_track_mmap_region
    MOV     x0, x16

1:
    LDP     x29, x30, [sp], #16
    RET

// hook_trampoline_mprotect - intercepts mprotect
// x0 = addr, x1 = len, x2 = prot
hook_trampoline_mprotect:
    STP     x29, x30, [sp, #-16]!
    MOV     x29, sp

    // Execute syscall
    MOV     x8, #NR_mprotect
    SVC     #0

    // Update tracking on success
    CMP     x0, #0
    B.NE    1f
    BL      rust_update_mprotect_region

1:
    LDP     x29, x30, [sp], #16
    RET

// hook_trampoline_vmread - emulates process_vm_readv for same-process
hook_trampoline_vmread:
    STP     x29, x30, [sp, #-16]!
    MOV     x29, sp

    // Check if pid == getpid()
    MRS     x16, TPIDR_EL0
    LDR     x16, [x16, #0x10]   // getpid cache
    CMP     x0, x16
    B.NE    1f

    // Fast path: same process, use memcpy
    BL      rust_vmread_same_process
    B       2f

1:
    // Fallback to real syscall
    MOV     x8, #NR_process_vm_readv
    SVC     #0

2:
    LDP     x29, x30, [sp], #16
    RET

// hook_trampoline_vmwrite - emulates process_vm_writev for same-process
hook_trampoline_vmwrite:
    STP     x29, x30, [sp, #-16]!
    MOV     x29, sp

    MRS     x16, TPIDR_EL0
    LDR     x16, [x16, #0x10]
    CMP     x0, x16
    B.NE    1f

    BL      rust_vmwrite_same_process
    B       2f

1:
    MOV     x8, #NR_process_vm_writev
    SVC     #0

2:
    LDP     x29, x30, [sp], #16
    RET

// asm_inject_hook - installs inline hook at target address
// x0 = target_func, x1 = hook_func, x2 = trampoline_buf
asm_inject_hook:
    STP     x29, x30, [sp, #-16]!
    MOV     x29, sp

    // Build trampoline: save original instructions + branch to hook
    // AArch64: 16 bytes for branch + nop
    LDR     x3, [x0]        // orig instruction 1
    STR     x3, [x2]
    LDR     x3, [x0, #8]    // orig instruction 2
    STR     x3, [x2, #8]

    // Write branch to hook: LDR x16, #8 ; BR x16
    MOVZ    x3, #0xD61F0200, LSL #0  // BR x16 instruction
    MOVK    x3, #0x58000050, LSL #0  // LDR x16, #8
    STR     x3, [x2, #16]
    STR     x1, [x2, #24]   // hook address

    // Overwrite target with branch to trampoline
    // LDR x16, #8 ; BR x16 ; trampoline_addr
    MOVZ    x3, #0x58000050, LSL #0
    MOVK    x3, #0xD61F0200, LSL #32
    STLXR   w3, w3, [x0]    // atomic write
    DMB     ISH             // memory barrier

    // Clear instruction cache
    DC      CIVAC, x0
    DSB     ISH
    ISB

    MOV     x0, #0          // success
    LDP     x29, x30, [sp], #16
    RET

// asm_restore_hook - removes inline hook
// x0 = target_func, x1 = original_bytes
asm_restore_hook:
    STP     x29, x30, [sp, #-16]!
    MOV     x29, sp

    LDR     x2, [x1]
    STLXR   w2, w2, [x0]
    LDR     x2, [x1, #8]
    STLXR   w2, w2, [x0, #8]
    DMB     ISH

    DC      CIVAC, x0
    DSB     ISH
    ISB

    MOV     x0, #0
    LDP     x29, x30, [sp], #16
    RET
