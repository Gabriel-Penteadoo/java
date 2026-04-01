package com.supdevinci.celeste;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.math.Rectangle;

public class Level {

    public static final int TILE_SIZE = 16;

    private static final int TILE_NONE  = 0;
    private static final int TILE_SOLID = 1;
    private static final int TILE_SPIKE = 2;
    private static final int TILE_GOAL  = 3;

    private static final String MAP_FILE = "level1.tmx";

    public static int COLS = 212;
    public static int ROWS = 20;

    private TiledMap tiledMap;
    private int[][] tiles; // [row][col], row 0 = top

    private float spawnX, spawnY;
    private float goalX,  goalY;

    public Level() {
        loadMap();
    }

    private void loadMap() {
        tiledMap = new TmxMapLoader().load(MAP_FILE);

        COLS = tiledMap.getProperties().get("width",  Integer.class);
        ROWS = tiledMap.getProperties().get("height", Integer.class);

        tiles = new int[ROWS][COLS];

        fillLayer(tiledMap, "walls",  TILE_SOLID);
        fillLayer(tiledMap, "spikes", TILE_SPIKE);

        spawnX = 2 * TILE_SIZE;
        spawnY = 3 * TILE_SIZE;

        int goalTileX = COLS - 8;
        int goalTileY = 2;
        goalX = goalTileX * TILE_SIZE;
        goalY = goalTileY * TILE_SIZE;

        int goalRow = ROWS - 1 - goalTileY;
        if (goalRow >= 0 && goalRow < ROWS && goalTileX >= 0 && goalTileX < COLS) {
            tiles[goalRow][goalTileX] = TILE_GOAL;
        }
    }

    // LibGDX tile layers use y=0 at bottom; tiles[][] uses row 0 at top
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

    public boolean isSolid(int tileX, int tileY) {
        if (outOfBounds(tileX, tileY)) return true; // treat OOB as solid
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

    public Rectangle getGoalRect() {
        return new Rectangle(goalX, goalY, TILE_SIZE, TILE_SIZE);
    }

    public float getSpawnX() { return spawnX; }
    public float getSpawnY() { return spawnY; }
    public float getGoalX()  { return goalX; }
    public float getGoalY()  { return goalY; }

    public float getWidth()  { return COLS * TILE_SIZE; }
    public float getHeight() { return ROWS * TILE_SIZE; }

    public void dispose() {
        if (tiledMap != null) {
            tiledMap.dispose();
            tiledMap = null;
        }
    }
}
