package com.whalestorm.chestsorter.client;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;

/**
 * Replaces the player's keyboard movement input for the length of a run. Vanilla calls updatePlayerMoveState()
 * every tick; we write our own values, so the player moves through normal physics (collisions, gravity, step-up)
 * and never sneaks. The real keyboard is still readable, which is how {@link Safety} notices you taking over.
 */
public final class AutoInput extends MovementInput {

    public float forward;
    public boolean jumpNext;

    private MovementInput original;
    private EntityPlayerSP player;

    @Override
    public void updatePlayerMoveState() {
        moveStrafe = 0f;
        moveForward = forward;
        jump = jumpNext;
        sneak = false;
    }

    public void install(EntityPlayerSP p) {
        if (player != null) return;
        player = p;
        original = p.movementInput;
        p.movementInput = this;
        forward = 0f;
        jumpNext = false;
    }

    /** Puts the keyboard input back. Safe to call more than once. */
    public void uninstall() {
        if (player != null && player.movementInput == this) player.movementInput = original;
        player = null;
        original = null;
        forward = 0f;
        jumpNext = false;
    }

    public boolean installed() {
        return player != null;
    }
}
