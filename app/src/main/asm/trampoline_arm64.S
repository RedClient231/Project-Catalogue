// trampoline_arm64.S - Trampoline templates for AArch64 inline hooking
// Provides reusable trampolines for hook injection

    .text
    .align 4
    .globl trampoline_template_aarch64
    .globl trampoline_template_end_aarch64
    .globl get_trampoline_size

// Template: [orig_insn1:8][orig_insn2:8][ldr_x16_8:4][br_x16:4][hook_addr:8]
trampoline_template_aarch64:
    NOP
    NOP
    LDR     x16, #8
    BR      x16
    .quad   0x0             // hook address placeholder
trampoline_template_end_aarch64:

get_trampoline_size:
    LDR     x0, =trampoline_template_end_aarch64
    LDR     x1, =trampoline_template_aarch64
    SUB     x0, x0, x1
    RET
