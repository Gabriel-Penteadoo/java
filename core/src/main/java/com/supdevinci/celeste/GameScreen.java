package com.supdevinci.celeste;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public class GameScreen implements Screen {

    public static final float VP_W = 480f;
    public static final float VP_H = 270f;

    private static final float CAM_LERP   = 0.12f;
    private static final float CAM_DEAD_Y = 20f; // vertical dead-zone (px)
    private static final float DEATH_PAUSE = 0.6f;

    private OrthographicCamera camera;
    private ShapeRenderer      shapeRenderer;
    private SpriteBatch        batch;
    private BitmapFont         font;
    private OrthogonalTiledMapRenderer mapRenderer;
    private int[] mapLayerIndices;

    private Level        level;
    private Player       player;
    private InputHandler input;

    private float camTargetX, camTargetY;
    private float flashTimer = 0f;
    private Color flashColor = new Color();
    private float deathTimer = 0f;

    @Override
    public void show() {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, VP_W, VP_H);
        shapeRenderer = new ShapeRenderer();
        batch         = new SpriteBatch();
        font          = new BitmapFont();
        font.setColor(Color.WHITE);
        input = new InputHandler();
        startLevel();
    }

    private void startLevel() {
        if (level != null) level.dispose();
        level  = new Level();
        player = new Player(level.getSpawnX(), level.getSpawnY());

        if (mapRenderer == null) {
            mapRenderer = new OrthogonalTiledMapRenderer(level.getTiledMap(), 1f, batch);
        } else {
            mapRenderer.setMap(level.getTiledMap());
        }

        String[] layerNames = {"background", "walls", "spikes"};
        java.util.List<Integer> indices = new java.util.ArrayList<>();
        com.badlogic.gdx.maps.MapLayers layers = level.getTiledMap().getLayers();
        for (String name : layerNames) {
            if (layers.get(name) != null) {
                indices.add(layers.getIndex(name));
            }
        }
        mapLayerIndices = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) mapLayerIndices[i] = indices.get(i);

        camTargetX = player.getX() + Player.WIDTH  * 0.5f;
        camTargetY = player.getY() + Player.HEIGHT * 0.5f;
        camera.position.set(
            MathUtils.clamp(camTargetX, VP_W * 0.5f, level.getWidth()  - VP_W * 0.5f),
            MathUtils.clamp(camTargetY, VP_H * 0.5f, level.getHeight() - VP_H * 0.5f),
            0f
        );
        camera.update();
        deathTimer = 0f;
        flashTimer = 0f;
    }

    @Override
    public void render(float rawDelta) {
        float dt = Math.min(rawDelta, 0.05f); // cap to avoid physics explosions after stall

        if (input.isRestartJustPressed()) {
            startLevel();
            return;
        }

        if (deathTimer > 0f) {
            deathTimer -= dt;
            if (deathTimer <= 0f) {
                player.respawn(level.getSpawnX(), level.getSpawnY());
            }
        } else {
            player.update(dt, input, level);
            if (player.getY() < 0)  triggerDeath();
            if (player.isDead())    triggerDeath();
            if (player.hasWon())    triggerWin();
        }

        if (flashTimer > 0f) flashTimer -= dt;
        updateCamera(dt);
        draw();
    }

    private void triggerDeath() {
        flashColor.set(1f, 0.2f, 0.2f, 1f);
        flashTimer = 0.35f;
        deathTimer = DEATH_PAUSE;
    }

    private void triggerWin() {
        flashColor.set(1f, 0.9f, 0.3f, 1f);
        flashTimer = 1.0f;
        deathTimer = 1.2f;
    }

    private void updateCamera(float dt) {
        float px = player.getX() + Player.WIDTH  * 0.5f;
        float py = player.getY() + Player.HEIGHT * 0.5f;

        camTargetX = px;
        if (Math.abs(py - camTargetY) > CAM_DEAD_Y) {
            camTargetY = py - Math.signum(py - camTargetY) * CAM_DEAD_Y;
        }

        float clampedX = MathUtils.clamp(camTargetX, VP_W * 0.5f, level.getWidth()  - VP_W * 0.5f);
        float clampedY = MathUtils.clamp(camTargetY, VP_H * 0.5f, level.getHeight() - VP_H * 0.5f);

        camera.position.x += (clampedX - camera.position.x) * CAM_LERP;
        camera.position.y += (clampedY - camera.position.y) * CAM_LERP;
        camera.update();
    }

    private void draw() {
        Gdx.gl.glClearColor(0.06f, 0.03f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shapeRenderer.setProjectionMatrix(camera.combined);
        drawBackground();

        mapRenderer.setView(camera);
        mapRenderer.render(mapLayerIndices);

        shapeRenderer.setProjectionMatrix(camera.combined);
        drawGoal();
        player.render(shapeRenderer);

        if (flashTimer > 0f) drawFlash();
        drawHud();
    }

    private void drawGoal() {
        float cx = level.getGoalX() + Level.TILE_SIZE * 0.5f;
        float cy = level.getGoalY() + Level.TILE_SIZE * 0.5f;
        float r  = Level.TILE_SIZE * 0.45f;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(1f, 0.85f, 0.1f, 1f);
        shapeRenderer.triangle(cx, cy + r, cx - r, cy, cx, cy - r);
        shapeRenderer.triangle(cx, cy + r, cx + r, cy, cx, cy - r);
        shapeRenderer.end();
    }

    private void drawBackground() {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(1f, 1f, 1f, 0.5f);

        long seed = 0x9e3779b97f4a7c15L;
        for (int i = 0; i < 120; i++) {
            seed ^= seed << 13; seed ^= seed >> 7; seed ^= seed << 17;
            float sx = (Math.abs((int) seed % (int) level.getWidth()));
            seed ^= seed << 13; seed ^= seed >> 7; seed ^= seed << 17;
            float sy = (Math.abs((int) seed % (int) level.getHeight()));
            shapeRenderer.rect(sx, sy, 1.5f, 1.5f);
        }

        shapeRenderer.end();
    }

    private void drawFlash() {
        shapeRenderer.setProjectionMatrix(
            new com.badlogic.gdx.math.Matrix4().setToOrtho2D(0, 0, VP_W, VP_H)
        );

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        float alpha = (flashTimer / (player.hasWon() ? 1.0f : 0.35f)) * 0.55f;
        shapeRenderer.setColor(flashColor.r, flashColor.g, flashColor.b, alpha);
        shapeRenderer.rect(0, 0, VP_W, VP_H);
        shapeRenderer.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(camera.combined);
    }

    private void drawHud() {
        batch.setProjectionMatrix(
            new com.badlogic.gdx.math.Matrix4().setToOrtho2D(0, 0, VP_W, VP_H)
        );
        batch.begin();

        if (player.canDash()) {
            font.setColor(1f, 0.4f, 0.8f, 1f);
        } else {
            font.setColor(0.4f, 0.2f, 0.25f, 1f);
        }
        font.draw(batch, "DASH: " + (player.canDash() ? "READY" : "SPENT"), 6, VP_H - 6);

        if (player.hasWon()) {
            font.setColor(1f, 0.9f, 0.2f, 1f);
            font.draw(batch, "YOU REACHED THE CRYSTAL!", VP_W * 0.5f - 90f, VP_H * 0.5f + 10f);
            font.setColor(0.8f, 0.8f, 0.8f, 1f);
            font.draw(batch, "Restarting...", VP_W * 0.5f - 40f, VP_H * 0.5f - 8f);
        }

        font.setColor(0.5f, 0.5f, 0.6f, 0.8f);
        font.draw(batch, "ARROWS/WASD move  |  Z/SPACE jump  |  X/SHIFT dash  |  R restart",
            6, 14);

        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        camera.setToOrtho(false, VP_W, VP_H);
        camera.update();
    }

    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}

    @Override
    public void dispose() {
        if (mapRenderer != null) mapRenderer.dispose();
        if (level != null)       level.dispose();
        shapeRenderer.dispose();
        batch.dispose();
        font.dispose();
    }
}
