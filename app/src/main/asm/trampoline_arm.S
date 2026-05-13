// trampoline_arm.S - Trampoline templates for ARMv7 inline hooking

    .syntax unified
    .arch armv7-a
    .text
    .align 4
    .globl trampoline_template_arm
    .globl trampoline_template_end_arm
    .globl get_trampoline_size_arm

trampoline_template_arm:
    NOP
    NOP
    LDR     pc, [pc, #-4]
    .word   0x0             // hook address placeholder
trampoline_template_end_arm:

get_trampoline_size_arm:
    LDR     r0, =trampoline_template_end_arm
    LDR     r1, =trampoline_template_arm
    SUB     r0, r0, r1
    BX      lr
