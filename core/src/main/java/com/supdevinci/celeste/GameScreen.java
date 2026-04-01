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

/**
 * The single game screen — orchestrates the game loop.
 *
 * Responsibilities
 * ----------------
 *  • Own the camera and drive smooth following
 *  • Create / restart the level and player
 *  • Tick input → player → (death / win checks) each frame
 *  • Render: background gradient, level, player, HUD
 *
 * Viewport: 480 × 270 pixels (16:9, pixel-perfect at 2× for 960×540 windows).
 */
public class GameScreen implements Screen {

    // -----------------------------------------------------------------------
    // Viewport
    // -----------------------------------------------------------------------

    /** Width of the logical viewport in pixels. */
    public static final float VP_W = 480f;

    /** Height of the logical viewport in pixels. */
    public static final float VP_H = 270f;

    // -----------------------------------------------------------------------
    // Camera follow tuning
    // -----------------------------------------------------------------------

    /** How quickly the camera lerps toward the target (0–1 per frame at 60fps). */
    private static final float CAM_LERP = 0.12f;

    /**
     * Vertical dead-zone: the camera only follows the player vertically when
     * they move outside this band (in world pixels). Keeps the view stable
     * while running on flat ground.
     */
    private static final float CAM_DEAD_Y = 20f;

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private OrthographicCamera      camera;
    private ShapeRenderer           shapeRenderer;
    private SpriteBatch             batch;
    private BitmapFont              font;
    private OrthogonalTiledMapRenderer mapRenderer;

    private int[] mapLayerIndices;

    private Level        level;
    private Player       player;
    private InputHandler input;

    // Camera target (world-space centre)
    private float camTargetX, camTargetY;

    // Respawn / win flash timer
    private float flashTimer  = 0f;
    private Color flashColor  = new Color();

    // Brief pause after death before auto-respawn
    private float deathTimer  = 0f;
    private static final float DEATH_PAUSE = 0.6f;

    // -----------------------------------------------------------------------
    // Screen lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void show() {
        camera        = new OrthographicCamera();
        camera.setToOrtho(false, VP_W, VP_H);

        shapeRenderer = new ShapeRenderer();
        batch         = new SpriteBatch();
        font          = new BitmapFont(); // LibGDX built-in default font
        font.setColor(Color.WHITE);

        input = new InputHandler();
        startLevel();
    }

    /** (Re)initialise level and player — called on first show and on restart. */
    private void startLevel() {
        if (level != null) level.dispose();
        level  = new Level();
        player = new Player(level.getSpawnX(), level.getSpawnY());

        // Create or update the tiled-map renderer (reuses the existing SpriteBatch)
        if (mapRenderer == null) {
            mapRenderer = new OrthogonalTiledMapRenderer(level.getTiledMap(), 1f, batch);
        } else {
            mapRenderer.setMap(level.getTiledMap());
        }

        // Resolve layer render indices by name so the order in the TMX doesn't matter
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

        // Snap the camera to the player immediately (no lerp on first frame)
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

    // -----------------------------------------------------------------------
    // Main loop
    // -----------------------------------------------------------------------

    @Override
    public void render(float rawDelta) {
        // Cap delta to avoid physics explosions after a stall (e.g. focus lost)
        float dt = Math.min(rawDelta, 0.05f);

        // --- Manual restart ---
        if (input.isRestartJustPressed()) {
            startLevel();
            return;
        }

        // --- Death pause ---
        if (deathTimer > 0f) {
            deathTimer -= dt;
            if (deathTimer <= 0f) {
                player.respawn(level.getSpawnX(), level.getSpawnY());
            }
        } else {
            // --- Normal update ---
            player.update(dt, input, level);

            // Player fell below the map → respawn
            if (player.getY() < 0) {
                triggerDeath();
            }

            if (player.isDead()) {
                triggerDeath();
            }

            if (player.hasWon()) {
                triggerWin();
            }
        }

        // --- Flash ---
        if (flashTimer > 0f) flashTimer -= dt;

        // --- Camera ---
        updateCamera(dt);

        // --- Draw ---
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
        // After win flash: restart the level (loop)
        deathTimer = 1.2f;
    }

    // -----------------------------------------------------------------------
    // Camera  (smooth follow with clamping to level bounds)
    // -----------------------------------------------------------------------

    private void updateCamera(float dt) {
        float px = player.getX() + Player.WIDTH  * 0.5f;
        float py = player.getY() + Player.HEIGHT * 0.5f;

        // Horizontal: always follow
        camTargetX = px;

        // Vertical: only update when outside the dead-zone
        if (Math.abs(py - camTargetY) > CAM_DEAD_Y) {
            camTargetY = py - Math.signum(py - camTargetY) * CAM_DEAD_Y;
        }

        // Clamp so the camera never shows outside the level bounds
        float clampedX = MathUtils.clamp(camTargetX, VP_W * 0.5f, level.getWidth()  - VP_W * 0.5f);
        float clampedY = MathUtils.clamp(camTargetY, VP_H * 0.5f, level.getHeight() - VP_H * 0.5f);

        // Smooth lerp toward target
        camera.position.x += (clampedX - camera.position.x) * CAM_LERP;
        camera.position.y += (clampedY - camera.position.y) * CAM_LERP;
        camera.update();
    }

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    private void draw() {
        Gdx.gl.glClearColor(0.06f, 0.03f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shapeRenderer.setProjectionMatrix(camera.combined);

        // Background stars (world-space, behind tiles)
        drawBackground();

        // Tile map: background + walls layers rendered with the tileset
        mapRenderer.setView(camera);
        mapRenderer.render(mapLayerIndices);

        // Goal crystal drawn on top of tiles
        shapeRenderer.setProjectionMatrix(camera.combined);
        drawGoal();

        // Player
        player.render(shapeRenderer);

        // Screen flash on death / win
        if (flashTimer > 0f) {
            drawFlash();
        }

        // HUD (drawn in screen space)
        drawHud();
    }

    /** Yellow diamond crystal marking the level goal. */
    private void drawGoal() {
        float wx = level.getGoalX();
        float wy = level.getGoalY();
        float cx = wx + Level.TILE_SIZE * 0.5f;
        float cy = wy + Level.TILE_SIZE * 0.5f;
        float r  = Level.TILE_SIZE * 0.45f;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(1f, 0.85f, 0.1f, 1f);
        shapeRenderer.triangle(cx, cy + r, cx - r, cy, cx, cy - r);
        shapeRenderer.triangle(cx, cy + r, cx + r, cy, cx, cy - r);
        shapeRenderer.end();
    }

    /** Cheap static star field — just draws fixed dots across the level. */
    private void drawBackground() {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(1f, 1f, 1f, 0.5f);

        // Pseudo-random but deterministic star positions
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

    /** Full-screen colour flash (death = red, win = gold). */
    private void drawFlash() {
        // Switch to screen-space projection
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

        // Restore world-space projection for subsequent frames
        shapeRenderer.setProjectionMatrix(camera.combined);
    }

    /** Simple HUD: dash indicator + controls hint. */
    private void drawHud() {
        // Convert camera position to screen-space for HUD overlay
        batch.setProjectionMatrix(
            new com.badlogic.gdx.math.Matrix4().setToOrtho2D(0, 0, VP_W, VP_H)
        );
        batch.begin();

        // --- Dash indicator dot (top-left corner) ---
        // We use font to draw a ● symbol
        if (player.canDash()) {
            font.setColor(1f, 0.4f, 0.8f, 1f); // pink = charged
        } else {
            font.setColor(0.4f, 0.2f, 0.25f, 1f); // dark = spent
        }
        font.draw(batch, "DASH: " + (player.canDash() ? "READY" : "SPENT"), 6, VP_H - 6);

        // --- Win message ---
        if (player.hasWon()) {
            font.setColor(1f, 0.9f, 0.2f, 1f);
            font.draw(batch, "YOU REACHED THE CRYSTAL!", VP_W * 0.5f - 90f, VP_H * 0.5f + 10f);
            font.setColor(0.8f, 0.8f, 0.8f, 1f);
            font.draw(batch, "Restarting...", VP_W * 0.5f - 40f, VP_H * 0.5f - 8f);
        }

        // --- Controls (bottom-left, shown only briefly) ---
        font.setColor(0.5f, 0.5f, 0.6f, 0.8f);
        font.draw(batch, "ARROWS/WASD move  |  Z/SPACE jump  |  X/SHIFT dash  |  R restart",
            6, 14);

        batch.end();
    }

    // -----------------------------------------------------------------------
    // Other Screen methods
    // -----------------------------------------------------------------------

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        // Keep the logical viewport size fixed; LibGDX handles the pixel scaling
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
