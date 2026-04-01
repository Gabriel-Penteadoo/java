package com.supdevinci.celeste;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;

/**
 * Thin abstraction over LibGDX raw input.
 * Centralises all key bindings so they're easy to remap.
 * Arrow keys and WASD are both supported for movement.
 * Z / Space = jump, X / Shift = dash.
 */
public class InputHandler {

    // ---------- Movement --------------------------------------------------

    public boolean isLeftHeld() {
        return Gdx.input.isKeyPressed(Keys.LEFT) || Gdx.input.isKeyPressed(Keys.A);
    }

    public boolean isRightHeld() {
        return Gdx.input.isKeyPressed(Keys.RIGHT) || Gdx.input.isKeyPressed(Keys.D);
    }

    public boolean isUpHeld() {
        return Gdx.input.isKeyPressed(Keys.UP) || Gdx.input.isKeyPressed(Keys.W);
    }

    public boolean isDownHeld() {
        return Gdx.input.isKeyPressed(Keys.DOWN) || Gdx.input.isKeyPressed(Keys.S);
    }

    // ---------- Jump -------------------------------------------------------

    /**
     * True for every frame the jump key is held (used for variable jump height).
     */
    public boolean isJumpHeld() {
        return Gdx.input.isKeyPressed(Keys.SPACE) || Gdx.input.isKeyPressed(Keys.Z);
    }

    /**
     * True only on the frame the jump key was first pressed (used for jump
     * buffering).
     */
    public boolean isJumpJustPressed() {
        return Gdx.input.isKeyJustPressed(Keys.SPACE) || Gdx.input.isKeyJustPressed(Keys.Z);
    }

    // ---------- Dash -------------------------------------------------------

    /** True only on the frame the dash key was first pressed. */
    public boolean isDashJustPressed() {
        return Gdx.input.isKeyJustPressed(Keys.X)
                || Gdx.input.isKeyJustPressed(Keys.J)
                || Gdx.input.isKeyJustPressed(Keys.SHIFT_RIGHT);
    }

    // ---------- Misc -------------------------------------------------------

    /** Restart / respawn. */
    public boolean isRestartJustPressed() {
        return Gdx.input.isKeyJustPressed(Keys.R);
    }
}
