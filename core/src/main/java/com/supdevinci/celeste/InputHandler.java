package com.supdevinci.celeste;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;

public class InputHandler {

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
