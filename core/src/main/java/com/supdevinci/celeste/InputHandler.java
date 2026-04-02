package com.supdevinci.celeste;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector2;

public class InputHandler {

    private final Vector2 direction = new Vector2();

    /** Returns a reused instance — do not store the reference. */
    public Vector2 getDirection() {
        float x = 0f, y = 0f;
        if (isLeftHeld())  x = -1f;
        if (isRightHeld()) x =  1f;
        if (isUpHeld())    y =  1f;
        if (isDownHeld())  y = -1f;
        return direction.set(x, y);
    }

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

    public boolean isJumpHeld() {
        return Gdx.input.isKeyPressed(Keys.SPACE) || Gdx.input.isKeyPressed(Keys.Z);
    }

    public boolean isJumpJustPressed() {
        return Gdx.input.isKeyJustPressed(Keys.SPACE) || Gdx.input.isKeyJustPressed(Keys.Z);
    }

    public boolean isDashJustPressed() {
        return Gdx.input.isKeyJustPressed(Keys.X)
                || Gdx.input.isKeyJustPressed(Keys.J)
                || Gdx.input.isKeyJustPressed(Keys.SHIFT_RIGHT);
    }

    public boolean isRestartJustPressed() {
        return Gdx.input.isKeyJustPressed(Keys.R);
    }
}
