package com.supdevinci.celeste;

import com.badlogic.gdx.graphics.Color;

public final class GameColors {
    private GameColors() {}

    public static final Color BACKGROUND = new Color(0.06f, 0.03f, 0.12f, 1f);
    public static final Color GOAL       = new Color(1f, 0.85f, 0.1f,  1f);

    public static final Color FLASH_DEATH = new Color(1f, 0.2f, 0.2f, 1f);
    public static final Color FLASH_WIN   = new Color(1f, 0.9f, 0.3f, 1f);

    public static final Color HUD_DASH_READY = new Color(1f,  0.4f, 0.8f, 1f);
    public static final Color HUD_DASH_SPENT = new Color(0.4f, 0.2f, 0.25f, 1f);
    public static final Color HUD_WIN        = new Color(1f,  0.9f, 0.2f, 1f);
    public static final Color HUD_RESTART    = new Color(0.8f, 0.8f, 0.8f, 1f);
    public static final Color HUD_HINT       = new Color(0.5f, 0.5f, 0.6f, 0.8f);

    public static final Color PLAYER_BODY             = new Color(1f,   1f,   1f,   1f);
    public static final Color PLAYER_DASHING          = new Color(0.6f, 1f,   1f,   1f);
    public static final Color PLAYER_DASH_AURA        = new Color(0.3f, 0.9f, 1.0f, 0.45f);
    public static final Color PLAYER_JUMP_WINDOW_AURA = new Color(0.2f, 1f,   0.4f, 0.35f);
    public static final Color PLAYER_WALL_AURA        = new Color(1f,   0.5f, 0.1f, 0.5f);
    public static final Color PLAYER_HAIR_READY       = new Color(1f,   0.4f, 0.6f, 1f);
    public static final Color PLAYER_HAIR_SPENT       = new Color(0.55f,0.1f, 0.1f, 1f);
    public static final Color PLAYER_EYE              = new Color(0.05f,0.05f,0.1f, 1f);
}
