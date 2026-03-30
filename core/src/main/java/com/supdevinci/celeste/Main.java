package com.supdevinci.celeste;

import com.badlogic.gdx.Game;

/**
 * Application entry-point.
 *
 * LibGDX calls {@link #create()} once after the window and OpenGL context are
 * ready.  We simply hand off to {@link GameScreen}, which owns the game loop.
 */
public class Main extends Game {

    @Override
    public void create() {
        setScreen(new GameScreen());
    }
}
