package com.supdevinci.celeste;

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
    // Map definition (50 cols × 22 rows)
    // Row 0 = top of world, row 21 = bottom of world
    // '#' solid | 'S' spike | 'G' goal | 'P' spawn (treated as empty) | ' ' air
    // -----------------------------------------------------------------------

    // Each string is exactly 50 characters wide.
    private static final String[] MAP_RAW = {
            "##################################################", // 0 – top ceiling
            "#                                            G  #", // 1 – goal at col 45
            "#                                      ######   #", // 2
            "#                              ######            ", // 3
            "#                       ######        SS         ", // 4 – spikes at cols 39-40
            "#                 SS         ######              ", // 5 – spikes at cols 18-19
            "#              ######              SS            ", // 6 – spikes at cols 31-32
            "#         SS          #####                      ", // 7 – spikes at cols 10-11
            "#         ###               ######               ", // 8
            "#                   SS             ######        ", // 9 – spikes at cols 20-21
            "#         ######              SS         ######  ", // 10 – spikes at cols 30-31
            "#    SS                  ###         SS          ", // 11 – spikes at cols 5-6 & 37-38
            "#    ###     SS               ###                ", // 12 – spikes at cols 13-14
            "#             ###                   ###          ", // 13
            "#   SS               ###                  SS     ", // 14 – spikes at 4-5 & 42-43
            "#   ###   ######           ####       #####      ", // 15
            "#                                                ", // 16
            "#                                                ", // 17
            "#P                                               ", // 18 – spawn at col 1
            "#                                                ", // 19
            "#                                                ", // 20
            "##################################################", // 21 – bottom ground
    };

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    public static final int COLS = 50;
    public static final int ROWS = 22;

    private final int[][] tiles = new int[ROWS][COLS];

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
        for (int row = 0; row < ROWS; row++) {
            String line = row < MAP_RAW.length ? MAP_RAW[row] : "";

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
