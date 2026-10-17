@ foxhg shiny rates: the routines the "Shiny rates" patch writes to the synthetic overlay (HeartGold English).
@ Written for foxhg, GPL-3.0-or-later. Assemble with scripts/build-shiny-asm.sh (arm-none-eabi-as and ld, THUMB, linked at CODE).
@
@ Four rates are u32 words in a table at TABLE: wild encounters, scripted wild battles, gift Pokemon, trainer Pokemon.
@ A word is the chance out of 65536 that the Pokemon is made shiny (0 = never, 65536 = always). A 0 word costs nothing: the
@ routines leave at once, without drawing a random number, so the game runs as if the patch were absent.
@
@ Addresses (all runtime, THUMB bit set where called with blx):
@   gf_rand 0x0201FD44                  returns a 16 bit random number
@   GetMonData 0x0206E540, SetMonData 0x0206EC40, SetMonPersonality 0x0207235C
@   GenerateShinyPersonality 0x02070094  (otid) -> a personality that is shiny for that OT id
@   Party_AddMon 0x02074524              (party, mon)
@   generateWildShinyAndAddToParty 0x02247910, generateWildNonShinyAndAddToParty 0x02247A18 (overlay 2): same arguments, but r3 is
@   the OT id for the first and "force one perfect IV" for the second

        .syntax unified
        .thumb

        .equ    TABLE,              0x023D9D00
        .equ    RATE_WILD,          TABLE + 0
        .equ    RATE_STATIC,        TABLE + 4
        .equ    RATE_GIFT,          TABLE + 8
        .equ    RATE_TRAINER,       TABLE + 12
        .equ    GF_RAND,            0x0201FD45
        .equ    GET_MON_DATA,       0x0206E541
        .equ    SET_MON_DATA,       0x0206EC41
        .equ    SET_MON_PERSONALITY, 0x0207235D
        .equ    GEN_SHINY_PID,      0x02070095
        .equ    PARTY_ADD,          0x02074525
        .equ    SHINY_GEN,          0x02247911
        .equ    NONSHINY_GEN,       0x02247A19
        .equ    TRAINER_BOUND,      0x02073000      @ a return address from here up is in the trainer party code

        .section .text

@ Replaces the call of generateWildNonShinyAndAddToParty at the places that make wild and scripted wild Pokemon. The arguments are the
@ function's (r0 species, r1 level, r2 battler, r3 force perfect IV, then encounterGen, leadMon, battleSetup on the stack).
        .global wild_entry
        .thumb_func
wild_entry:
        push    {r0-r4, lr}
        ldr     r4, =RATE_WILD
        b       wild_common

        .global static_entry
        .thumb_func
static_entry:
        push    {r0-r4, lr}
        ldr     r4, =RATE_STATIC
wild_common:
        ldr     r4, [r4]
        ldr     r1, =NONSHINY_GEN
        ldr     r3, [sp, #12]           @ r3 as the caller had it
        cmp     r4, #0
        beq     call_wild
        ldr     r0, =GF_RAND
        blx     r0
        lsls    r0, r0, #16
        lsrs    r0, r0, #16
        cmp     r0, r4
        bhs     reload_wild
        ldr     r1, =SHINY_GEN
        ldr     r3, [sp, #24]           @ encounterGen, whose first word is the player's OT id
        ldr     r3, [r3]
        b       call_wild
reload_wild:
        ldr     r1, =NONSHINY_GEN
        ldr     r3, [sp, #12]
call_wild:
        adds    r4, r1, #0
        ldr     r0, [sp, #24]           @ the three stack arguments of the caller, copied below the new call
        ldr     r1, [sp, #28]
        ldr     r2, [sp, #32]
        push    {r0-r2}
        ldr     r0, [sp, #12]           @ species, level, battler as the caller had them
        ldr     r1, [sp, #16]
        ldr     r2, [sp, #20]
        blx     r4
        add     sp, #12
        add     sp, #16
        pop     {r4}
        pop     {pc}

@ Reached from a stub in the ARM9 instead of Party_AddMon(party, mon): shinifies the Pokemon at random, then adds it. A gift
@ Pokemon gets a shiny personality for the player's OT id. A trainer's Pokemon keeps its personality (so its nature and gender stay as
@ the trainer data says) and gets an OT id that makes that personality shiny.
        .global party_entry
        .thumb_func
party_entry:
        push    {r0, r1, r4, lr}
        ldr     r2, [sp, #12]
        ldr     r3, =TRAINER_BOUND
        ldr     r4, =RATE_GIFT
        cmp     r2, r3
        blo     party_rate
        ldr     r4, =RATE_TRAINER
party_rate:
        ldr     r4, [r4]
        cmp     r4, #0
        beq     party_done
        ldr     r0, =GF_RAND
        blx     r0
        lsls    r0, r0, #16
        lsrs    r0, r0, #16
        cmp     r0, r4
        bhs     party_done
        ldr     r2, [sp, #12]
        ldr     r3, =TRAINER_BOUND
        cmp     r2, r3
        bhs     shiny_trainer
shiny_gift:
        ldr     r0, [sp, #4]
        movs    r1, #7                  @ MON_DATA_OT_ID
        movs    r2, #0
        ldr     r4, =GET_MON_DATA
        blx     r4
        ldr     r4, =GEN_SHINY_PID
        blx     r4
        adds    r1, r0, #0
        ldr     r0, [sp, #4]
        ldr     r4, =SET_MON_PERSONALITY
        blx     r4
        b       party_done
shiny_trainer:
        ldr     r0, [sp, #4]
        movs    r1, #0                  @ MON_DATA_PERSONALITY
        movs    r2, #0
        ldr     r4, =GET_MON_DATA
        blx     r4
        lsrs    r1, r0, #16
        lsls    r0, r0, #16
        lsrs    r0, r0, #16
        eors    r0, r1                  @ pid high half xor low half
        adds    r4, r0, #0
        ldr     r0, =GF_RAND
        blx     r0
        lsls    r0, r0, #16
        lsrs    r0, r0, #16
        push    {r0}                    @ the high half of the new OT id
        ldr     r0, =GF_RAND
        blx     r0
        movs    r1, #7
        ands    r0, r1
        eors    r0, r4
        ldr     r1, [sp, #0]
        eors    r0, r1                  @ low half: high ^ pid halves ^ (0 to 7), which is below 8 when checked
        lsls    r1, r1, #16
        orrs    r1, r0
        str     r1, [sp, #0]
        ldr     r0, [sp, #8]            @ the Pokemon, one word further up now
        movs    r1, #7
        add     r2, sp, #0
        ldr     r4, =SET_MON_DATA
        blx     r4
        add     sp, #4
party_done:
        ldr     r0, [sp, #0]
        ldr     r1, [sp, #4]
        ldr     r4, =PARTY_ADD
        blx     r4
        add     sp, #8
        pop     {r4}
        pop     {pc}

        .ltorg
