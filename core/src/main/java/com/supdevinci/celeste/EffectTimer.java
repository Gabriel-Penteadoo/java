package com.supdevinci.celeste;

import com.badlogic.gdx.graphics.Color;

/**
 * Manages the paired flash-overlay and pause timers that fire on death or win.
 * <p>
 * Call {@link #triggerDeath()} / {@link #triggerWin()} to start an effect,
 * {@link #update(float)} every frame, then query {@link #isPaused()} and
 * {@link #isFlashing()} to drive game-screen behaviour.
 */
public class EffectTimer {

    private static final float DEATH_FLASH   = 0.35f;
    private static final float DEATH_PAUSE   = 0.6f;
    private static final float WIN_FLASH     = 1.0f;
    private static final float WIN_PAUSE     = 1.2f;
    private static final float FLASH_ALPHA   = 0.55f;

    private final Color flashColor = new Color();
    private float flashTimer  = 0f;
    private float pauseTimer  = 0f;
    private float totalFlash  = 1f;

    // ── Triggers ───────────────────────────────────────────────────────────

    public void triggerDeath() {
        flashColor.set(GameColors.FLASH_DEATH);
        flashTimer = totalFlash = DEATH_FLASH;
        pauseTimer = DEATH_PAUSE;
    }

    public void triggerWin() {
        flashColor.set(GameColors.FLASH_WIN);
        flashTimer = totalFlash = WIN_FLASH;
        pauseTimer = WIN_PAUSE;
    }

    public void reset() {
        flashTimer = pauseTimer = 0f;
    }

    // ── Per-frame ──────────────────────────────────────────────────────────

    public void update(float dt) {
        if (flashTimer > 0f) flashTimer -= dt;
        if (pauseTimer > 0f) pauseTimer -= dt;
    }

    // ── Queries ────────────────────────────────────────────────────────────

    public boolean isFlashing() { return flashTimer > 0f; }
    public boolean isPaused()   { return pauseTimer > 0f; }

    /** Alpha for the full-screen flash rectangle (0 → 1). */
    public float getFlashAlpha() {
        return (flashTimer / totalFlash) * FLASH_ALPHA;
    }

    public Color getFlashColor() { return flashColor; }
}
