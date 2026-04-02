package com.supdevinci.celeste;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Matrix4;

public class GameScreen implements Screen {

    private static final float MAX_DELTA  = 0.05f;
    private static final float CAM_LERP   = 0.12f;
    private static final float CAM_DEAD_Y = 20f;
    private static final float VP_W       = 480f;
    private static final float VP_H       = 270f;

    private GameCamera camera;
    private ShapeRenderer shapeRenderer;
    private SpriteBatch batch;
    private OrthogonalTiledMapRenderer mapRenderer;
    private Matrix4 uiMatrix;
    private int[] mapLayerIndices;
    private Hud hud;

    private Level level;
    private Player player;
    private InputHandler input;

    private final EffectTimer effects = new EffectTimer();

    @Override
    public void show() {
        camera       = new GameCamera(VP_W, VP_H, CAM_LERP, CAM_DEAD_Y);
        uiMatrix     = new Matrix4().setToOrtho2D(0, 0, VP_W, VP_H);
        shapeRenderer = new ShapeRenderer();
        batch        = new SpriteBatch();
        hud          = new Hud(VP_W, VP_H);
        input        = new InputHandler();
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
            if (layers.get(name) != null) indices.add(layers.getIndex(name));
        }
        mapLayerIndices = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) mapLayerIndices[i] = indices.get(i);

        camera.snapTo(player.getX() + player.width * 0.5f,
                      player.getY() + player.height * 0.5f, level);
        effects.reset();
    }

    @Override
    public void render(float rawDelta) {
        float dt = Math.min(rawDelta, MAX_DELTA);

        if (input.isRestartJustPressed()) { startLevel(); return; }

        if (effects.isPaused()) {
            effects.update(dt);
            if (!effects.isPaused()) player.respawn(level.getSpawnX(), level.getSpawnY());
        } else {
            player.update(dt, input, level);
            if (player.getY() < 0 || player.isDead()) effects.triggerDeath();
            else if (player.hasWon())                 effects.triggerWin();
            effects.update(dt);
        }

        camera.update(dt,
                player.getX() + player.width  * 0.5f,
                player.getY() + player.height * 0.5f,
                level);
        draw();
    }

    private void draw() {
        Gdx.gl.glClearColor(
                GameColors.BACKGROUND.r, GameColors.BACKGROUND.g,
                GameColors.BACKGROUND.b, GameColors.BACKGROUND.a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        mapRenderer.setView(camera.getCamera());
        mapRenderer.render(mapLayerIndices);

        shapeRenderer.setProjectionMatrix(camera.getCamera().combined);
        drawGoal();
        player.render(shapeRenderer);

        if (effects.isFlashing()) drawFlash();
        hud.draw(batch, player);
    }

    private void drawGoal() {
        float cx = level.getGoalX() + level.tileSize * 0.5f;
        float cy = level.getGoalY() + level.tileSize * 0.5f;
        float r  = level.tileSize * 0.45f;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(GameColors.GOAL);
        shapeRenderer.triangle(cx, cy + r, cx - r, cy, cx, cy - r);
        shapeRenderer.triangle(cx, cy + r, cx + r, cy, cx, cy - r);
        shapeRenderer.end();
    }

    private void drawFlash() {
        shapeRenderer.setProjectionMatrix(uiMatrix);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        com.badlogic.gdx.graphics.Color fc = effects.getFlashColor();
        shapeRenderer.setColor(fc.r, fc.g, fc.b, effects.getFlashAlpha());
        shapeRenderer.rect(0, 0, VP_W, VP_H);
        shapeRenderer.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(camera.getCamera().combined);
    }

    @Override
    public void resize(int width, int height) {
        if (width > 0 && height > 0) camera.resize();
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
        hud.dispose();
    }
}
