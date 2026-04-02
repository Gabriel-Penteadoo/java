package com.supdevinci.celeste;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;

/**
 * Wraps {@link OrthographicCamera} with smooth lerp-following and a vertical
 * dead-zone so the camera doesn't chase every small jump.
 */
public class GameCamera {

    public final float vpW;
    public final float vpH;

    private final float lerp;
    private final float deadZoneY;

    private final OrthographicCamera camera;
    private float targetY;

    public GameCamera(float vpW, float vpH, float lerp, float deadZoneY) {
        this.vpW      = vpW;
        this.vpH      = vpH;
        this.lerp     = lerp;
        this.deadZoneY = deadZoneY;
        this.camera   = new OrthographicCamera();
        this.camera.setToOrtho(false, vpW, vpH);
    }

    /** Instantly centres the camera on (cx, cy) — call when the level starts. */
    public void snapTo(float cx, float cy, Level level) {
        targetY = cy;
        camera.position.set(clampX(cx, level), clampY(cy, level), 0f);
        camera.update();
    }

    /** Smooth follow; call once per frame after the player has moved. */
    public void update(float dt, float playerCx, float playerCy, Level level) {
        float targetX = playerCx;
        if (Math.abs(playerCy - targetY) > deadZoneY) {
            targetY = playerCy - Math.signum(playerCy - targetY) * deadZoneY;
        }

        camera.position.x += (clampX(targetX, level) - camera.position.x) * lerp;
        camera.position.y += (clampY(targetY, level) - camera.position.y) * lerp;
        camera.update();
    }

    /** Re-apply ortho projection after a window resize. */
    public void resize() {
        camera.setToOrtho(false, vpW, vpH);
        camera.update();
    }

    public OrthographicCamera getCamera() { return camera; }

    // ── Helpers ────────────────────────────────────────────────────────────

    private float clampX(float x, Level level) {
        return MathUtils.clamp(x, vpW * 0.5f, level.getWidth() - vpW * 0.5f);
    }

    private float clampY(float y, Level level) {
        return MathUtils.clamp(y, vpH * 0.5f, level.getHeight() - vpH * 0.5f);
    }
}
