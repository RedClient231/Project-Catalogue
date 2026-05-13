// syscall_hook_arm.S - ARMv7 syscall interception trampolines
// 32-bit hooks for openat, mmap2, mprotect, process_vm_readv/writev

    .syntax unified
    .arch armv7-a
    .text
    .align 4
    .globl hook_trampoline_openat_arm
    .globl hook_trampoline_mmap2_arm
    .globl hook_trampoline_mprotect_arm
    .globl hook_trampoline_vmread_arm
    .globl hook_trampoline_vmwrite_arm
    .globl asm_inject_hook_arm
    .globl asm_restore_hook_arm

.equ NR_openat, 322
.equ NR_mmap2, 192
.equ NR_mprotect, 125
.equ NR_process_vm_readv, 376
.equ NR_process_vm_writev, 377

hook_trampoline_openat_arm:
    PUSH    {r0-r7, lr}
    MOV     r7, #NR_openat
    SVC     #0
    POP     {r0-r7, pc}

hook_trampoline_mmap2_arm:
    PUSH    {r0-r7, lr}
    MOV     r7, #NR_mmap2
    SVC     #0
    CMP     r0, #0
    BLT     1f
    PUSH    {r0}
    BL      rust_track_mmap_region_arm
    POP     {r0}
1:
    POP     {r0-r7, pc}

hook_trampoline_mprotect_arm:
    PUSH    {r0-r7, lr}
    MOV     r7, #NR_mprotect
    SVC     #0
    CMP     r0, #0
    BNE     1f
    BL      rust_update_mprotect_region_arm
1:
    POP     {r0-r7, pc}

hook_trampoline_vmread_arm:
    PUSH    {r0-r7, lr}
    BL      getpid
    CMP     r0, r4
    BNE     1f
    BL      rust_vmread_same_process_arm
    B       2f
1:
    MOV     r7, #NR_process_vm_readv
    SVC     #0
2:
    POP     {r0-r7, pc}

hook_trampoline_vmwrite_arm:
    PUSH    {r0-r7, lr}
    BL      getpid
    CMP     r0, r4
    BNE     1f
    BL      rust_vmwrite_same_process_arm
    B       2f
1:
    MOV     r7, #NR_process_vm_writev
    SVC     #0
2:
    POP     {r0-r7, pc}

asm_inject_hook_arm:
    PUSH    {r4-r7, lr}
    // r0 = target, r1 = hook, r2 = trampoline buf
    LDR     r4, [r0]
    STR     r4, [r2]
    LDR     r4, [r0, #4]
    STR     r4, [r2, #4]
    // Write branch: LDR PC, [PC, #-4]
    MOV     r4, #0xE51FF004
    STR     r4, [r2, #8]
    STR     r1, [r2, #12]
    // Patch target with branch to trampoline
    MOV     r4, #0xEA000000
    // Calculate offset
    SUB     r5, r2, r0
    SUB     r5, r5, #8
    LSR     r5, r5, #2
    AND     r5, r5, #0x00FFFFFF
    ORR     r4, r4, r5
    STR     r4, [r0]
    DSB
    ISB
    MOV     r0, #0
    POP     {r4-r7, pc}

asm_restore_hook_arm:
    PUSH    {r4-r7, lr}
    LDR     r4, [r1]
    STR     r4, [r0]
    LDR     r4, [r1, #4]
    STR     r4, [r0, #4]
    DSB
    ISB
    MOV     r0, #0
    POP     {r4-r7, pc}
