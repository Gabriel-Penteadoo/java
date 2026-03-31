package com.supdevinci.celeste;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;

/**
 * Holds the tile grid for the single game level and exposes
 * helpers for collision queries and rendering.
 *
 * Coordinate system
 * -----------------
 * worldX = col * TILE_SIZE
 * worldY = (ROWS - 1 - row) * TILE_SIZE (row 0 = top, row ROWS-1 = bottom)
 *
 * Tile types
 * ----------
 * TILE_NONE (0) – passable air
 * TILE_SOLID (1) – solid wall / platform
 * TILE_SPIKE (2) – instant-kill hazard
 * TILE_GOAL (3) – level-end crystal
 */
public class Level {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    public static final int TILE_SIZE = 16;

    private static final int TILE_NONE = 0;
    private static final int TILE_SOLID = 1;
    private static final int TILE_SPIKE = 2;
    private static final int TILE_GOAL = 3;

    // -----------------------------------------------------------------------
    // Map file
    // -----------------------------------------------------------------------

    /**
     * Path (relative to the assets folder) of the plain-text map file.
     *
     * Format — each character is one tile:
     *   '#'  solid wall / platform
     *   'S'  spike (instant-kill hazard)
     *   'G'  goal crystal
     *   'P'  spawn point (treated as empty air)
     *   ' '  empty air
     *
     * All rows should be the same width; shorter rows are padded with air.
     * The file is loaded at runtime so you can edit it without recompiling.
     * Open it in any text editor — a monospace font makes the grid easy to read.
     */
    private static final String MAP_FILE = "map.txt";

    // -----------------------------------------------------------------------
    // Fields (dimensions are derived from the file at load time)
    // -----------------------------------------------------------------------

    public static int COLS = 50;
    public static int ROWS = 22;

    // Allocated after the file is read and dimensions are known
    private int[][] tiles;

    /** World-space bottom-left of the spawn tile. */
    private float spawnX, spawnY;

    /** World-space bottom-left of the goal tile. */
    private float goalX, goalY;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public Level() {
        parseTiles();
    }

    private void parseTiles() {
        // Read every line from the map file
        String raw = Gdx.files.internal(MAP_FILE).readString("UTF-8");
        // Normalise line endings and split
        String[] lines = raw.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);

        // Derive dimensions from the file content
        ROWS = lines.length;
        COLS = 0;
        for (String line : lines) {
            if (line.length() > COLS) COLS = line.length();
        }
        if (ROWS == 0 || COLS == 0) {
            Gdx.app.error("Level", "map.txt is empty or malformed");
            ROWS = 1; COLS = 1;
        }

        tiles = new int[ROWS][COLS];

        for (int row = 0; row < ROWS; row++) {
            String line = row < lines.length ? lines[row] : "";

            for (int col = 0; col < COLS; col++) {
                char c = col < line.length() ? line.charAt(col) : ' ';
                float worldX = col * TILE_SIZE;
                float worldY = (ROWS - 1 - row) * TILE_SIZE;

                switch (c) {
                    case '#':
                        tiles[row][col] = TILE_SOLID;
                        break;
                    case 'S':
                        tiles[row][col] = TILE_SPIKE;
                        break;
                    case 'G':
                        tiles[row][col] = TILE_GOAL;
                        goalX = worldX;
                        goalY = worldY;
                        break;
                    case 'P':
                        tiles[row][col] = TILE_NONE;
                        spawnX = worldX;
                        spawnY = worldY;
                        break;
                    default:
                        tiles[row][col] = TILE_NONE;
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Collision queries (all take tile coordinates)
    // -----------------------------------------------------------------------

    /** Returns true if the tile at (tileX, tileY) blocks movement. */
    public boolean isSolid(int tileX, int tileY) {
        if (outOfBounds(tileX, tileY))
            return true; // treat OOB as solid walls
        return tiles[ROWS - 1 - tileY][tileX] == TILE_SOLID;
    }

    /** Returns true if the tile at (tileX, tileY) is a lethal spike. */
    public boolean isSpike(int tileX, int tileY) {
        if (outOfBounds(tileX, tileY))
            return false;
        return tiles[ROWS - 1 - tileY][tileX] == TILE_SPIKE;
    }

    /** Returns true if the tile at (tileX, tileY) is the goal crystal. */
    public boolean isGoal(int tileX, int tileY) {
        if (outOfBounds(tileX, tileY))
            return false;
        return tiles[ROWS - 1 - tileY][tileX] == TILE_GOAL;
    }

    private boolean outOfBounds(int tx, int ty) {
        return tx < 0 || ty < 0 || tx >= COLS || ty >= ROWS;
    }

    // -----------------------------------------------------------------------
    // World-space goal rectangle (for player overlap check)
    // -----------------------------------------------------------------------

    public Rectangle getGoalRect() {
        return new Rectangle(goalX, goalY, TILE_SIZE, TILE_SIZE);
    }

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    public void render(ShapeRenderer sr) {
        sr.begin(ShapeRenderer.ShapeType.Filled);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int tile = tiles[row][col];
                if (tile == TILE_NONE)
                    continue;

                float wx = col * TILE_SIZE;
                float wy = (ROWS - 1 - row) * TILE_SIZE;

                switch (tile) {
                    case TILE_SOLID:
                        // Deep blue-grey, lighter edge to suggest depth
                        sr.setColor(0.22f, 0.25f, 0.35f, 1f);
                        sr.rect(wx, wy, TILE_SIZE, TILE_SIZE);
                        // Bright inner highlight (top + left 1-pixel bevel)
                        sr.setColor(0.32f, 0.36f, 0.50f, 1f);
                        sr.rect(wx, wy + TILE_SIZE - 2, TILE_SIZE, 2); // top edge
                        sr.rect(wx, wy, 2, TILE_SIZE); // left edge
                        break;

                    case TILE_SPIKE:
                        // Red triangle pointing upward
                        sr.setColor(0.85f, 0.15f, 0.15f, 1f);
                        // Draw as a filled triangle using a thin rect + triangle
                        sr.triangle(
                                wx, wy,
                                wx + TILE_SIZE, wy,
                                wx + TILE_SIZE * 0.5f, wy + TILE_SIZE);
                        break;

                    case TILE_GOAL:
                        // Pulsing yellow crystal – constant color (no time access here)
                        sr.setColor(1f, 0.85f, 0.1f, 1f);
                        // Diamond shape
                        float cx = wx + TILE_SIZE * 0.5f;
                        float cy = wy + TILE_SIZE * 0.5f;
                        float r = TILE_SIZE * 0.45f;
                        sr.triangle(cx, cy + r, cx - r, cy, cx, cy - r);
                        sr.triangle(cx, cy + r, cx + r, cy, cx, cy - r);
                        break;
                }
            }
        }

        sr.end();

        // Thin grid lines on solid tiles for a subtle pixel aesthetic
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(0.15f, 0.17f, 0.25f, 1f);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                if (tiles[row][col] == TILE_SOLID) {
                    float wx = col * TILE_SIZE;
                    float wy = (ROWS - 1 - row) * TILE_SIZE;
                    sr.rect(wx, wy, TILE_SIZE, TILE_SIZE);
                }
            }
        }
        sr.end();
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public float getSpawnX() {
        return spawnX;
    }

    public float getSpawnY() {
        return spawnY;
    }

    public float getGoalX() {
        return goalX;
    }

    public float getGoalY() {
        return goalY;
    }

    /** Total level width in pixels. */
    public float getWidth() {
        return COLS * TILE_SIZE;
    }

    /** Total level height in pixels. */
    public float getHeight() {
        return ROWS * TILE_SIZE;
    }
}
