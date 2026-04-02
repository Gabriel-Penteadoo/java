package com.supdevinci.celeste;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.math.Rectangle;

public class Level {

    // ── Constants ──────────────────────────────────────────────────────────
    public final int tileSize = 16;

    private final int tileSolid = 1;
    private final int tileSpike = 2;
    private final int tileGoal  = 3;

    private final String mapFile = "level1.tmx";

    // ── State ──────────────────────────────────────────────────────────────
    private int cols = 212;
    private int rows = 20;

    private TiledMap tiledMap;
    private int[][] tiles; // [row][col], row 0 = top

    private float spawnX, spawnY;
    private float goalX, goalY;

    public Level() {
        loadMap();
    }

    // ── Loading ────────────────────────────────────────────────────────────

    private void loadMap() {
        tiledMap = new TmxMapLoader().load(mapFile);
        cols = tiledMap.getProperties().get("width",  Integer.class);
        rows = tiledMap.getProperties().get("height", Integer.class);
        tiles = new int[rows][cols];

        fillLayer(tiledMap, "walls",  tileSolid);
        fillLayer(tiledMap, "spikes", tileSpike);

        spawnX = 2 * tileSize;
        spawnY = 3 * tileSize;

        int goalTileX = cols - 8;
        int goalTileY = 2;
        goalX = goalTileX * tileSize;
        goalY = goalTileY * tileSize;

        int goalRow = rows - 1 - goalTileY;
        if (goalRow >= 0 && goalRow < rows && goalTileX >= 0 && goalTileX < cols) {
            tiles[goalRow][goalTileX] = tileGoal;
        }
    }

    /** LibGDX tile layers use y=0 at bottom; tiles[][] uses row 0 at top. */
    private void fillLayer(TiledMap map, String layerName, int tileType) {
        TiledMapTileLayer layer = (TiledMapTileLayer) map.getLayers().get(layerName);
        if (layer == null) return;
        for (int gdxRow = 0; gdxRow < rows; gdxRow++) {
            int tilesRow = rows - 1 - gdxRow;
            for (int col = 0; col < cols; col++) {
                TiledMapTileLayer.Cell cell = layer.getCell(col, gdxRow);
                if (cell != null && cell.getTile() != null) {
                    tiles[tilesRow][col] = tileType;
                }
            }
        }
    }

    // ── Tile queries ───────────────────────────────────────────────────────

    public boolean isSolid(int tileX, int tileY) {
        if (outOfBounds(tileX, tileY)) return true; // treat OOB as solid
        return tiles[rows - 1 - tileY][tileX] == tileSolid;
    }

    public boolean isSpike(int tileX, int tileY) {
        if (outOfBounds(tileX, tileY)) return false;
        return tiles[rows - 1 - tileY][tileX] == tileSpike;
    }

    public boolean isGoal(int tileX, int tileY) {
        if (outOfBounds(tileX, tileY)) return false;
        return tiles[rows - 1 - tileY][tileX] == tileGoal;
    }

    private boolean outOfBounds(int tx, int ty) {
        return tx < 0 || ty < 0 || tx >= cols || ty >= rows;
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public TiledMap getTiledMap() { return tiledMap; }

    public Rectangle getGoalRect() { return new Rectangle(goalX, goalY, tileSize, tileSize); }

    public float getSpawnX() { return spawnX; }
    public float getSpawnY() { return spawnY; }
    public float getGoalX()  { return goalX;  }
    public float getGoalY()  { return goalY;  }
    public float getWidth()  { return cols * tileSize; }
    public float getHeight() { return rows * tileSize; }

    public void dispose() {
        if (tiledMap != null) {
            tiledMap.dispose();
            tiledMap = null;
        }
    }
}
