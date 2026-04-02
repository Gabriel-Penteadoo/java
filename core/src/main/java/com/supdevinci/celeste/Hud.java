package com.supdevinci.celeste;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;

public class Hud {

    private static final String HINT_TEXT =
            "ARROWS/WASD move  |  Z/SPACE jump  |  X/SHIFT dash  |  R restart";

    private final BitmapFont font;
    private final Matrix4 uiMatrix;
    private final float vpW;
    private final float vpH;

    public Hud(float vpW, float vpH) {
        this.vpW     = vpW;
        this.vpH     = vpH;
        this.font    = new BitmapFont();
        this.uiMatrix = new Matrix4().setToOrtho2D(0, 0, vpW, vpH);
    }

    public void draw(SpriteBatch batch, Player player) {
        batch.setProjectionMatrix(uiMatrix);
        batch.begin();

        font.setColor(player.canDash() ? GameColors.HUD_DASH_READY : GameColors.HUD_DASH_SPENT);
        font.draw(batch, "DASH: " + (player.canDash() ? "READY" : "SPENT"), 6, vpH - 6);

        if (player.hasWon()) {
            font.setColor(GameColors.HUD_WIN);
            font.draw(batch, "YOU REACHED THE CRYSTAL!", vpW * 0.5f - 90f, vpH * 0.5f + 10f);
            font.setColor(GameColors.HUD_RESTART);
            font.draw(batch, "Restarting...", vpW * 0.5f - 40f, vpH * 0.5f - 8f);
        }

        font.setColor(GameColors.HUD_HINT);
        font.draw(batch, HINT_TEXT, 6, 14);

        batch.end();
    }

    public void dispose() {
        font.dispose();
    }
}
