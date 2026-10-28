package com.whalestorm.chestsorter.core;

/**
 * Top-level states from the design doc. Pure Java: nothing in the core package may import net.minecraft or cpw.mods,
 * so it can be compiled and unit-tested without a Minecraft install (see tools/check-core.sh).
 */
public enum SorterState {

    IDLE,
    SCAN,
    SURVEY,
    ASSIGN,
    PLAN,
    EXECUTE;

    /** Hotkey behaviour from the design doc: IDLE starts a run at SCAN, anything else stops. */
    public SorterState toggled() {
        return this == IDLE ? SCAN : IDLE;
    }
}
