package com.supdevinci.celeste;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Player controller — the heart of the game.
 *
 * All movement constants are grouped at the top so tweaking them is easy.
 * Physics is fully custom (no Box2D): standard AABB vs tile-grid approach
 * with sub-stepping to prevent tunnelling at high speeds.
 *
 * Features implemented
 * --------------------
 *  • Left/right movement with acceleration and deceleration (different on
 *    ground vs. air for that tight Celeste feel)
 *  • Gravity with a heavier fall multiplier (falling faster than rising)
 *  • Variable-height jump: release early → lower arc
 *  • Coyote time: can jump a few frames after walking off a ledge
 *  • Jump buffering: pressing jump slightly early still triggers the jump
 *  • 8-directional dash with 1 dash charge (replenished on landing)
 *  • Wall sliding (slow descent when pressed against a wall)
 *  • Wall jumping (jump away from a wall with locked horizontal momentum)
 *  • Spikes → instant death / respawn
 *  • Goal overlap → win flag raised (handled by GameScreen)
 */
public class Player {

    // -----------------------------------------------------------------------
    // Size (pixels)
    // -----------------------------------------------------------------------

    public static final float WIDTH  = 10f;
    public static final float HEIGHT = 14f;

    // -----------------------------------------------------------------------
    // Movement constants — tweak these to change the game feel
    // -----------------------------------------------------------------------

    /** Top horizontal speed on the ground (px/s). */
    private static final float RUN_SPEED      = 150f;

    /** Horizontal acceleration when input is pressed on the ground. */
    private static final float RUN_ACCEL      = 1400f;

    /** Horizontal acceleration while airborne (slightly softer). */
    private static final float RUN_ACCEL_AIR  =  900f;

    /** Deceleration when no horizontal input is pressed on the ground. */
    private static final float RUN_DECEL      = 1800f;

    /** Deceleration when no horizontal input is pressed in the air. */
    private static final float RUN_DECEL_AIR  =  400f;

    // --- Gravity ---

    /** Upward gravity (applied every frame when not dashing). */
    private static final float GRAVITY              = -900f;

    /**
     * Extra multiplier applied when the player is falling (vy < 0).
     * Makes the fall feel heavier and more responsive.
     */
    private static final float FALL_GRAVITY_MULT    =   2.0f;

    /**
     * Extra multiplier applied when rising but the jump button has been
     * released early — cuts the arc short for variable jump height.
     */
    private static final float EARLY_RELEASE_MULT   =   2.2f;

    /** Terminal fall speed (px/s, negative = downward). */
    private static final float MAX_FALL_SPEED       = -520f;

    // --- Jump ---

    /** Initial vertical velocity given on jump. */
    private static final float JUMP_SPEED           =  330f;

    /**
     * How long (seconds) the player can still jump after walking off a ledge.
     * Named "coyote time" after the classic cartoon trick.
     */
    private static final float COYOTE_TIME          =  0.10f;

    /**
     * How long (seconds) before landing a jump press is still remembered
     * and triggered the moment the player does land.
     */
    private static final float JUMP_BUFFER_TIME     =  0.12f;

    /**
     * Window (seconds) in which holding jump keeps extra upward force.
     * Releasing the button within this window applies EARLY_RELEASE_MULT.
     */
    private static final float VARIABLE_JUMP_TIME   =  0.13f;

    // --- Dash ---

    /** Speed of the dash impulse (px/s). */
    private static final float DASH_SPEED           =  320f;

    /** How long the dash lasts (seconds). Gravity is ignored during this time. */
    private static final float DASH_DURATION        =  0.14f;

    /**
     * Brief cooldown after a dash before another can start.
     * Prevents accidentally chaining dashes on the same frame.
     */
    private static final float DASH_COOLDOWN        =  0.08f;

    // --- Wall mechanics ---

    /** Max downward speed when sliding against a wall (negative, px/s). */
    private static final float WALL_SLIDE_SPEED     =  -45f;

    /** Horizontal speed given when jumping off a wall. */
    private static final float WALL_JUMP_X          =  145f;

    /** Vertical speed given when jumping off a wall. */
    private static final float WALL_JUMP_Y          =  300f;

    /**
     * After a wall jump the horizontal input is locked for this many seconds.
     * This prevents the player from immediately steering back into the wall
     * and makes wall-jumping feel powerful.
     */
    private static final float WALL_JUMP_LOCK_TIME  =  0.16f;

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private float x, y;          // world-space bottom-left corner of AABB
    private float vx, vy;        // velocity (px/s)

    private float spawnX, spawnY;

    // Ground / wall contact flags — refreshed every frame
    private boolean grounded;
    private boolean onWallLeft;
    private boolean onWallRight;

    // Jump mechanics
    private float coyoteTimer;
    private float jumpBufferTimer;
    private float variableJumpTimer;

    // Dash mechanics
    private boolean canDash    = true;
    private boolean dashing    = false;
    private float   dashTimer  = 0f;
    private float   dashCooldownTimer = 0f;
    private float   dashDirX, dashDirY; // unit-ish vector of dash direction

    // Wall-jump horizontal lock
    private float wallJumpLockTimer = 0f;

    // Status flags read by GameScreen
    private boolean dead = false;
    private boolean won  = false;

    // Which direction the player faces (for eye rendering)
    private int facing = 1; // 1 = right, -1 = left

    // -----------------------------------------------------------------------
    // Constructor / respawn
    // -----------------------------------------------------------------------

    public Player(float spawnX, float spawnY) {
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        respawn(spawnX, spawnY);
    }

    /** Teleport back to spawn and reset all state. */
    public void respawn(float sx, float sy) {
        x  = sx;
        y  = sy;
        vx = vy = 0f;

        grounded    = false;
        onWallLeft  = false;
        onWallRight = false;

        canDash          = true;
        dashing          = false;
        dashTimer        = 0f;
        dashCooldownTimer= 0f;

        coyoteTimer      = 0f;
        jumpBufferTimer  = 0f;
        variableJumpTimer= 0f;
        wallJumpLockTimer= 0f;

        dead = false;
        won  = false;
    }

    // -----------------------------------------------------------------------
    // Main update — called once per game frame
    // -----------------------------------------------------------------------

    public void update(float dt, InputHandler input, Level level) {
        if (dead || won) return;

        // 1. Register jump-buffer (must happen before jump handling)
        if (input.isJumpJustPressed()) {
            jumpBufferTimer = JUMP_BUFFER_TIME;
        }

        // 2. Initiate dash if the button was just pressed and we have a charge
        if (input.isDashJustPressed() && canDash && !dashing && dashCooldownTimer <= 0f) {
            startDash(input);
        }

        // 3. Tick cooldown timer
        if (dashCooldownTimer > 0f) dashCooldownTimer -= dt;

        // 4. Either dash physics or normal physics
        if (dashing) {
            updateDash(dt, input);
        } else {
            applyGravity(dt, input);
            applyHorizontalMovement(dt, input);
            updateJump(dt, input);
        }

        // 5. Sub-stepped movement + collision (prevents tunnelling)
        moveAndCollide(dt, level);
        if (dead) return;

        // 6. Post-collision bookkeeping
        checkWalls(level);

        // 7. Manage coyote timer & dash replenishment
        if (grounded) {
            coyoteTimer = COYOTE_TIME;
            canDash     = true;          // landing recharges the dash
        } else {
            if (coyoteTimer > 0f) coyoteTimer -= dt;
        }

        // 8. Tick wall-jump horizontal lock
        if (wallJumpLockTimer > 0f) wallJumpLockTimer -= dt;

        // 9. Clamp fall speed
        if (!dashing) {
            boolean wallSliding = (onWallLeft || onWallRight) && !grounded && vy < 0f;
            float maxFall = wallSliding ? WALL_SLIDE_SPEED : MAX_FALL_SPEED;
            if (vy < maxFall) vy = maxFall;
        }

        // 10. Tick jump-buffer timer
        if (jumpBufferTimer > 0f) jumpBufferTimer -= dt;

        // 11. Update facing direction
        if (vx > 1f)  facing =  1;
        if (vx < -1f) facing = -1;
    }

    // -----------------------------------------------------------------------
    // Dash
    // -----------------------------------------------------------------------

    private void startDash(InputHandler input) {
        // Build 8-directional vector from current input
        float dx = 0f, dy = 0f;
        if (input.isLeftHeld())  dx = -1f;
        if (input.isRightHeld()) dx =  1f;
        if (input.isUpHeld())    dy =  1f;
        if (input.isDownHeld())  dy = -1f;

        // No input → dash in the direction the player is facing
        if (dx == 0f && dy == 0f) dx = facing;

        // Normalize diagonal so speed is consistent
        if (dx != 0f && dy != 0f) {
            dashDirX = dx * 0.7071f;
            dashDirY = dy * 0.7071f;
        } else {
            dashDirX = dx;
            dashDirY = dy;
        }

        // Clear velocity so the dash impulse is clean
        vx = 0f;
        vy = 0f;

        dashing   = true;
        dashTimer = DASH_DURATION;
        canDash   = false;

        variableJumpTimer = 0f; // cancel any active variable-jump
    }

    private void updateDash(float dt, InputHandler input) {
        dashTimer -= dt;

        // Lock velocity to dash direction (gravity ignored)
        vx = dashDirX * DASH_SPEED;
        vy = dashDirY * DASH_SPEED;

        if (dashTimer <= 0f) {
            dashing           = false;
            dashCooldownTimer = DASH_COOLDOWN;

            // Transition velocity: keep horizontal momentum but cap it
            if (dashDirY == 0f) {
                // Pure horizontal dash → bleed down to run speed
                vx = dashDirX * RUN_SPEED;
            }
            // Diagonal / vertical dashes retain full velocity; gravity takes over naturally
        }
    }

    // -----------------------------------------------------------------------
    // Gravity
    // -----------------------------------------------------------------------

    private void applyGravity(float dt, InputHandler input) {
        float grav = GRAVITY;

        if (vy < 0f) {
            // Falling → extra gravity for a heavier, more responsive feel
            grav *= FALL_GRAVITY_MULT;
        } else if (vy > 0f && variableJumpTimer <= 0f) {
            // Rising but the jump-hold window has expired → cut the arc
            grav *= EARLY_RELEASE_MULT;
        }

        vy += grav * dt;
    }

    // -----------------------------------------------------------------------
    // Horizontal movement
    // -----------------------------------------------------------------------

    private void applyHorizontalMovement(float dt, InputHandler input) {
        // Horizontal input is locked briefly after a wall jump
        float inputX = 0f;
        if (wallJumpLockTimer <= 0f) {
            if (input.isLeftHeld())  inputX = -1f;
            if (input.isRightHeld()) inputX =  1f;
        }

        float accel  = grounded ? RUN_ACCEL     : RUN_ACCEL_AIR;
        float decel  = grounded ? RUN_DECEL     : RUN_DECEL_AIR;
        float target = inputX * RUN_SPEED;

        if (inputX != 0f) {
            // If we're slower than max speed or going the wrong way → accelerate
            if (Math.abs(vx) < RUN_SPEED || Math.signum(vx) != Math.signum(inputX)) {
                vx = moveTowards(vx, target, accel * dt);
            } else {
                // Already at or above max speed → don't slow down
                vx = moveTowards(vx, target, decel * dt);
            }
        } else {
            vx = moveTowards(vx, 0f, decel * dt);
        }
    }

    // -----------------------------------------------------------------------
    // Jump (coyote time + buffering + variable height + wall jump)
    // -----------------------------------------------------------------------

    private void updateJump(float dt, InputHandler input) {
        boolean canCoyoteJump = coyoteTimer > 0f;
        boolean canWallJump   = (onWallLeft || onWallRight) && !grounded;

        if (jumpBufferTimer > 0f && (canCoyoteJump || canWallJump)) {
            if (canWallJump) {
                // Jump away from whichever wall we're touching
                vy = WALL_JUMP_Y;
                vx = onWallLeft ? WALL_JUMP_X : -WALL_JUMP_X;
                wallJumpLockTimer = WALL_JUMP_LOCK_TIME;
                coyoteTimer       = 0f;
            } else {
                // Normal / coyote jump
                vy = JUMP_SPEED;
                coyoteTimer       = 0f;
                variableJumpTimer = VARIABLE_JUMP_TIME;
            }
            jumpBufferTimer = 0f;
            grounded        = false; // we just jumped, definitely not grounded
        }

        // Variable jump height: if still in the hold window…
        if (variableJumpTimer > 0f) {
            if (input.isJumpHeld()) {
                variableJumpTimer -= dt; // …keep the extra upward force active
            } else {
                // …releasing early applies a stronger downward gravity pull
                variableJumpTimer = 0f;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Movement + collision  (sub-stepped AABB vs tile grid)
    // -----------------------------------------------------------------------

    private void moveAndCollide(float dt, Level level) {
        boolean wasGrounded = grounded;
        grounded = false; // reset each frame; resolveY sets it back to true if needed

        // Sub-step to prevent tunnelling at high dash/jump speeds.
        // 4 sub-steps at 60fps means ~0.004 s per sub-step → max 1.3 px per step at 320 px/s.
        final int STEPS = 4;
        float subDt = dt / STEPS;

        for (int i = 0; i < STEPS; i++) {
            x += vx * subDt;
            resolveX(level);
            if (dead) return;

            y += vy * subDt;
            resolveY(level);
            if (dead) return;
        }

        // If we just landed, cancel any pending variable-jump
        if (grounded && !wasGrounded) {
            variableJumpTimer = 0f;
        }
    }

    /**
     * After moving horizontally, push the player out of any solid tile
     * on the leading edge.
     */
    private void resolveX(Level level) {
        int bottom = tileY(y + 2f);           // +2 avoids corner-grazing
        int top    = tileY(y + HEIGHT - 2f);

        if (vx > 0f) {
            int right = tileX(x + WIDTH);
            for (int ty = bottom; ty <= top; ty++) {
                if (checkTileX(right, ty, level, true)) return;
            }
        } else if (vx < 0f) {
            int left = tileX(x);
            for (int ty = bottom; ty <= top; ty++) {
                if (checkTileX(left, ty, level, false)) return;
            }
        }
    }

    /** Returns true if a collision was resolved (stops iterating). */
    private boolean checkTileX(int tx, int ty, Level level, boolean movingRight) {
        if (level.isSolid(tx, ty)) {
            x  = movingRight
                ? tx * Level.TILE_SIZE - WIDTH       // push left
                : (tx + 1) * Level.TILE_SIZE;        // push right
            vx = 0f;
            return true;
        }
        if (level.isSpike(tx, ty)) { die(); return true; }
        if (level.isGoal(tx, ty))  { win(); return true; }
        return false;
    }

    /**
     * After moving vertically, push the player out of any solid tile
     * on the leading edge and set the grounded flag.
     */
    private void resolveY(Level level) {
        int left  = tileX(x + 2f);
        int right = tileX(x + WIDTH - 2f);

        if (vy < 0f) {
            // Falling — check bottom edge
            int bottom = tileY(y);
            for (int tx = left; tx <= right; tx++) {
                if (checkTileY(tx, bottom, level, false)) return;
            }
        } else if (vy > 0f) {
            // Rising — check top edge
            int top = tileY(y + HEIGHT);
            for (int tx = left; tx <= right; tx++) {
                if (checkTileY(tx, top, level, true)) return;
            }
        }
    }

    private boolean checkTileY(int tx, int ty, Level level, boolean movingUp) {
        if (level.isSolid(tx, ty)) {
            if (movingUp) {
                y  = ty * Level.TILE_SIZE - HEIGHT;  // bonk ceiling
            } else {
                y       = (ty + 1) * Level.TILE_SIZE; // land on top
                grounded = true;
            }
            vy = 0f;
            return true;
        }
        if (level.isSpike(tx, ty)) { die(); return true; }
        if (level.isGoal(tx, ty))  { win(); return true; }
        return false;
    }

    // -----------------------------------------------------------------------
    // Wall detection (one pixel past each side of the player)
    // -----------------------------------------------------------------------

    private void checkWalls(Level level) {
        onWallLeft  = false;
        onWallRight = false;

        if (grounded) return; // no wall slide on the ground

        int bottom = tileY(y + 4f);
        int top    = tileY(y + HEIGHT - 4f);

        int leftTile  = tileX(x - 1f);
        int rightTile = tileX(x + WIDTH + 1f);

        for (int ty = bottom; ty <= top; ty++) {
            if (level.isSolid(leftTile,  ty)) { onWallLeft  = true; break; }
        }
        for (int ty = bottom; ty <= top; ty++) {
            if (level.isSolid(rightTile, ty)) { onWallRight = true; break; }
        }
    }

    // -----------------------------------------------------------------------
    // Tile coordinate helpers
    // -----------------------------------------------------------------------

    private int tileX(float worldX) { return (int) Math.floor(worldX / Level.TILE_SIZE); }
    private int tileY(float worldY) { return (int) Math.floor(worldY / Level.TILE_SIZE); }

    // -----------------------------------------------------------------------
    // Status helpers
    // -----------------------------------------------------------------------

    private void die() { dead = true; }
    private void win() { won  = true; }

    /**
     * Linear interpolation helper: moves {@code current} toward {@code target}
     * by at most {@code step}, without overshooting.
     */
    private float moveTowards(float current, float target, float step) {
        float diff = target - current;
        if (Math.abs(diff) <= step) return target;
        return current + Math.signum(diff) * step;
    }

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    public void render(ShapeRenderer sr) {
        // Enable alpha blending so the dash trail can be semi-transparent
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        sr.begin(ShapeRenderer.ShapeType.Filled);

        // --- Dash glow ---
        if (dashing) {
            // Bright cyan halo around the player during a dash
            sr.setColor(0.3f, 0.9f, 1.0f, 0.45f);
            sr.rect(x - 5, y - 5, WIDTH + 10, HEIGHT + 10);
        }

        // --- Wall-slide indicator: orange tint on the touching side ---
        if ((onWallLeft || onWallRight) && !grounded) {
            sr.setColor(1f, 0.5f, 0.1f, 0.5f);
            sr.rect(x - 2, y, WIDTH + 4, HEIGHT);
        }

        // --- Player body ---
        // White normally, tinted cyan while dashing, red briefly after death
        if (dashing) {
            sr.setColor(0.6f, 1f, 1f, 1f);
        } else {
            sr.setColor(1f, 1f, 1f, 1f);
        }
        sr.rect(x, y, WIDTH, HEIGHT);

        // --- Hair (Madeline-esque small rectangle on top) ---
        // Red when no dash charge, pink when dash available
        if (canDash) {
            sr.setColor(1f, 0.4f, 0.6f, 1f); // pink
        } else {
            sr.setColor(0.55f, 0.1f, 0.1f, 1f); // dark red (no charge)
        }
        // Hair tuft centred on top of the player
        float hairW = WIDTH * 0.6f;
        sr.rect(x + (WIDTH - hairW) * 0.5f, y + HEIGHT, hairW, 3f);

        // --- Eyes ---
        sr.setColor(0.05f, 0.05f, 0.1f, 1f);
        float eyeY = y + HEIGHT * 0.55f;
        if (facing >= 0) {
            sr.rect(x + WIDTH * 0.20f, eyeY, 2f, 2f);
            sr.rect(x + WIDTH * 0.60f, eyeY, 2f, 2f);
        } else {
            sr.rect(x + WIDTH * 0.20f, eyeY, 2f, 2f);
            sr.rect(x + WIDTH * 0.58f, eyeY, 2f, 2f);
        }

        sr.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    // -----------------------------------------------------------------------
    // Public accessors
    // -----------------------------------------------------------------------

    public float getX()         { return x; }
    public float getY()         { return y; }
    public boolean isDead()     { return dead; }
    public boolean hasWon()     { return won; }
    public boolean isGrounded() { return grounded; }
    public boolean isOnWallLeft()  { return onWallLeft; }
    public boolean isOnWallRight() { return onWallRight; }
    public boolean isDashing()  { return dashing; }
    public boolean canDash()    { return canDash; }
}
