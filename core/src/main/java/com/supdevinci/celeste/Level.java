package com.supdevinci.celeste;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.math.Rectangle;

/**
 * Holds the tile grid for the single game level and exposes
 * helpers for collision queries.
 *
 * Coordinate system
 * -----------------
 * worldX = col * TILE_SIZE
 * worldY = tileY * TILE_SIZE  (tileY=0 = bottom row, increases upward)
 *
 * Tile types (internal grid)
 * ----------
 * TILE_NONE (0) – passable air
 * TILE_SOLID (1) – solid wall / platform (any non-zero tile in the "walls" layer)
 * TILE_GOAL (3)  – level-end crystal (hardcoded position)
 *
 * The TMX map is loaded via LibGDX TmxMapLoader. The "walls" layer drives
 * collision; the "background" and "walls" layers are rendered by
 * OrthogonalTiledMapRenderer in GameScreen.
 */
public class Level {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    public static final int TILE_SIZE = 16;

    private static final int TILE_NONE  = 0;
    private static final int TILE_SOLID = 1;
    private static final int TILE_SPIKE = 2;
    private static final int TILE_GOAL  = 3;

    private static final String MAP_FILE = "level1.tmx";

    // -----------------------------------------------------------------------
    // Fields (dimensions derived from the map at load time)
    // -----------------------------------------------------------------------

    public static int COLS = 212;
    public static int ROWS = 20;

    private TiledMap tiledMap;
    private int[][] tiles;  // tiles[row][col], row 0 = top, row ROWS-1 = bottom

    private float spawnX, spawnY;
    private float goalX,  goalY;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public Level() {
        loadMap();
    }

    private void loadMap() {
        tiledMap = new TmxMapLoader().load(MAP_FILE);

        COLS = tiledMap.getProperties().get("width",  Integer.class);
        ROWS = tiledMap.getProperties().get("height", Integer.class);

        tiles = new int[ROWS][COLS];

        // ---------------------------------------------------------------
        // Build collision grid from the "walls" and "spikes" layers.
        // LibGDX TiledMapTileLayer uses y=0 at the bottom (y-up convention),
        // whereas our tiles[][] uses row 0 at the top.
        // Mapping: tiles[ROWS-1-gdxRow][col]
        // ---------------------------------------------------------------
        fillLayer(tiledMap, "walls",  TILE_SOLID);
        fillLayer(tiledMap, "spikes", TILE_SPIKE);

        // ---------------------------------------------------------------
        // Spawn: left side of the level, just above the solid floor.
        // LibGDX tileY=2 → tiles row = ROWS-1-2 = 17, col 2 — confirmed
        // empty in the walls layer; tileY=1 is the solid floor.
        // ---------------------------------------------------------------
        spawnX = 2 * TILE_SIZE;
        spawnY = 3 * TILE_SIZE;  // player falls a tile and lands on floor

        // ---------------------------------------------------------------
        // Goal: near the right end of the level, above the solid floor.
        // Marked as TILE_GOAL in the grid so Player.isGoal() finds it.
        // ---------------------------------------------------------------
        int goalTileX = COLS - 8;   // 8 tiles from the right edge
        int goalTileY = 2;          // LibGDX tile-y (from bottom)
        goalX = goalTileX * TILE_SIZE;
        goalY = goalTileY * TILE_SIZE;

        int goalRow = ROWS - 1 - goalTileY;
        if (goalRow >= 0 && goalRow < ROWS && goalTileX >= 0 && goalTileX < COLS) {
            tiles[goalRow][goalTileX] = TILE_GOAL;
        }
    }

    // -----------------------------------------------------------------------
    // TiledMap accessor (used by GameScreen to create the map renderer)
    // -----------------------------------------------------------------------

    private void fillLayer(TiledMap map, String layerName, int tileType) {
        TiledMapTileLayer layer = (TiledMapTileLayer) map.getLayers().get(layerName);
        if (layer == null) return;
        for (int gdxRow = 0; gdxRow < ROWS; gdxRow++) {
            int tilesRow = ROWS - 1 - gdxRow;
            for (int col = 0; col < COLS; col++) {
                TiledMapTileLayer.Cell cell = layer.getCell(col, gdxRow);
                if (cell != null && cell.getTile() != null) {
                    tiles[tilesRow][col] = tileType;
                }
            }
        }
    }

    public TiledMap getTiledMap() {
        return tiledMap;
    }

    // -----------------------------------------------------------------------
    // Collision queries (tile coordinates: tileX=col, tileY from bottom)
    // -----------------------------------------------------------------------

    public boolean isSolid(int tileX, int tileY) {
        if (outOfBounds(tileX, tileY)) return true; // treat OOB as solid walls
        return tiles[ROWS - 1 - tileY][tileX] == TILE_SOLID;
    }

    public boolean isSpike(int tileX, int tileY) {
        if (outOfBounds(tileX, tileY)) return false;
        return tiles[ROWS - 1 - tileY][tileX] == TILE_SPIKE;
    }

    public boolean isGoal(int tileX, int tileY) {
        if (outOfBounds(tileX, tileY)) return false;
        return tiles[ROWS - 1 - tileY][tileX] == TILE_GOAL;
    }

    private boolean outOfBounds(int tx, int ty) {
        return tx < 0 || ty < 0 || tx >= COLS || ty >= ROWS;
    }

    // -----------------------------------------------------------------------
    // World-space goal rectangle (used by player overlap check)
    // -----------------------------------------------------------------------

    public Rectangle getGoalRect() {
        return new Rectangle(goalX, goalY, TILE_SIZE, TILE_SIZE);
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public float getSpawnX() { return spawnX; }
    public float getSpawnY() { return spawnY; }
    public float getGoalX()  { return goalX; }
    public float getGoalY()  { return goalY; }

    public float getWidth()  { return COLS * TILE_SIZE; }
    public float getHeight() { return ROWS * TILE_SIZE; }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void dispose() {
        if (tiledMap != null) {
            tiledMap.dispose();
            tiledMap = null;
        }
    }
}
