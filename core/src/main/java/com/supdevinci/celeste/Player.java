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
 * • Left/right movement with acceleration and deceleration (different on
 * ground vs. air for that tight Celeste feel)
 * • Gravity with a heavier fall multiplier (falling faster than rising)
 * • Variable-height jump: release early → lower arc
 * • Coyote time: can jump a few frames after walking off a ledge
 * • Jump buffering: pressing jump slightly early still triggers the jump
 * • 8-directional dash with 1 dash charge (replenished on landing)
 * • Wall sliding (slow descent when pressed against a wall)
 * • Wall jumping (jump away from a wall with locked horizontal momentum)
 * • Hyper Dash: down-diagonal dash + land + jump → very fast horizontal skim
 * • Super Dash: horizontal dash + land + jump → fast horizontal arc
 * • Spikes → instant death / respawn
 * • Goal overlap → win flag raised (handled by GameScreen)
 */
public class Player {

    // -----------------------------------------------------------------------
    // Size (pixels)
    // -----------------------------------------------------------------------

    public static final float WIDTH = 10f;
    public static final float HEIGHT = 14f;

    // -----------------------------------------------------------------------
    // Movement constants — tweak these to change the game feel
    // -----------------------------------------------------------------------

    /** Top horizontal speed on the ground (px/s). */
    private static final float RUN_SPEED = 100f;

    /** Horizontal acceleration when input is pressed on the ground. */
    private static final float RUN_ACCEL = 1400f;

    /** Horizontal acceleration while airborne (slightly softer). */
    private static final float RUN_ACCEL_AIR = 900f;

    /** Deceleration when no horizontal input is pressed on the ground. */
    private static final float RUN_DECEL = 2500f;

    /** Deceleration when no horizontal input is pressed in the air. */
    private static final float RUN_DECEL_AIR = 400f;

    // --- Gravity ---

    /** Upward gravity (applied every frame when not dashing). */
    private static final float GRAVITY = -900f;

    /**
     * Extra multiplier applied when the player is falling (vy < 0).
     * Makes the fall feel heavier and more responsive.
     */
    private static final float FALL_GRAVITY_MULT = 2.0f;

    /**
     * Extra multiplier applied when rising but the jump button has been
     * released early — cuts the arc short for variable jump height.
     */
    private static final float EARLY_RELEASE_MULT = 2.2f;

    /** Terminal fall speed (px/s, negative = downward). */
    private static final float MAX_FALL_SPEED = -520f;

    // --- Jump ---

    /** Initial vertical velocity given on jump. */
    private static final float JUMP_SPEED = 270f;

    /**
     * How long (seconds) the player can still jump after walking off a ledge.
     * Named "coyote time" after the classic cartoon trick.
     */
    private static final float COYOTE_TIME = 0.10f;

    /**
     * How long (seconds) before landing a jump press is still remembered
     * and triggered the moment the player does land.
     */
    private static final float JUMP_BUFFER_TIME = 0.12f;

    /**
     * Window (seconds) in which holding jump keeps extra upward force.
     * Releasing the button within this window applies EARLY_RELEASE_MULT.
     */
    private static final float VARIABLE_JUMP_TIME = 0.13f;

    // --- Dash ---

    /** Speed of the dash impulse (px/s). */
    private static final float DASH_SPEED = 320f;

    /** How long the dash lasts (seconds). Gravity is ignored during this time. */
    private static final float DASH_DURATION = 0.14f;

    /**
     * Brief cooldown after a dash before another can start.
     * Prevents accidentally chaining dashes on the same frame.
     */
    private static final float DASH_COOLDOWN = 0.2f;

    // --- Wall mechanics ---

    /** Max downward speed when sliding against a wall (negative, px/s). */
    private static final float WALL_SLIDE_SPEED = -45f;

    /** Horizontal speed given when jumping off a wall. */
    private static final float WALL_JUMP_X = 200f;

    /** Vertical speed given when jumping off a wall. */
    private static final float WALL_JUMP_Y = 400f;

    /**
     * After a wall jump the horizontal input is locked for this many seconds.
     * This prevents the player from immediately steering back into the wall
     * and makes wall-jumping feel powerful.
     */
    private static final float WALL_JUMP_LOCK_TIME = 0.16f;

    // --- Hyper Dash ---

    /**
     * Horizontal speed applied by a Hyper Dash (down-diagonal dash → land → jump).
     * Much faster than a normal dash exit; the trajectory skims the ground because
     * the vertical component is intentionally tiny.
     */
    private static final float HYPER_SPEED = 580f;

    /**
     * Small upward boost given by a Hyper Dash.
     * Kept low so the player stays close to the ground (the defining feel).
     * No variable-jump extension is granted — the arc is meant to be flat.
     */
    private static final float HYPER_VERTICAL = 260f;

    // --- Super Dash ---

    /**
     * Horizontal speed applied by a Super Dash (horizontal dash → land → jump).
     * Lower than HYPER_SPEED but still well above normal run speed.
     */
    private static final float SUPER_SPEED = 450f;

    /**
     * Vertical force applied by a Super Dash.
     * Produces a forward arc that is taller than a normal jump.
     * Variable-jump extension IS granted so the player can control the peak.
     */
    private static final float SUPER_JUMP_FORCE = 300f;

    /**
     * How long (seconds) after landing from a qualifying dash the player can
     * trigger a Hyper or Super Dash by pressing jump.
     * Pairs with the existing JUMP_BUFFER_TIME so the input can come slightly
     * before OR after the landing moment.
     */
    private static final float DASH_JUMP_WINDOW = 0.10f;

    // --- Ultra Dash ---

    /**
     * Minimum absolute horizontal speed (px/s) the player must already have
     * when starting an oblique dash for it to count as an Ultra Dash.
     * This is well above RUN_SPEED so it only triggers after a hyper/super.
     */
    private static final float ULTRA_THRESHOLD = 250f;

    /**
     * Speed multiplier applied to vx when the player lands from a
     * down-diagonal Ultra Dash. The momentum is preserved through the
     * landing and given a small boost.
     */
    private static final float ULTRA_LANDING_MULT = 1.1f;

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private float x, y; // world-space bottom-left corner of AABB
    private float vx, vy; // velocity (px/s)

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
    private boolean canDash = true;
    private boolean dashing = false;
    private float dashTimer = 0f;
    private float dashCooldownTimer = 0f;
    private float dashDirX, dashDirY; // unit-ish vector of dash direction

    // Wall-jump horizontal lock
    private float wallJumpLockTimer = 0f;

    // -------------------------------------------------------------------
    // Hyper / Super Dash state
    //
    // When a qualifying dash ends (down-diagonal or horizontal), we record
    // what kind it was and open a time window on the next landing.
    // If jump is buffered (pressed just before landing) or pressed fresh
    // inside the window, the dash-jump triggers instead of a normal jump.
    // -------------------------------------------------------------------

    /**
     * Set when a qualifying dash ends while the player is still airborne.
     * Cleared the moment the player lands (which opens dashJumpWindowTimer).
     */
    private boolean dashJumpPending = false;

    /**
     * true → pending/active action is a Hyper Dash (down-diagonal)
     * false → pending/active action is a Super Dash (horizontal)
     */
    private boolean dashJumpIsHyper = false;

    /**
     * Horizontal direction (+1 / -1) inherited from the triggering dash.
     * Used to set vx when the dash-jump fires.
     */
    private int dashJumpDirSign = 0;

    /**
     * Counts down after landing from a qualifying dash.
     * While > 0, a buffered (or fresh) jump triggers a Hyper or Super Dash
     * instead of a normal jump.
     */
    private float dashJumpWindowTimer = 0f;

    // -------------------------------------------------------------------
    // Ultra Dash state
    //
    // An Ultra Dash occurs when the player performs a diagonal dash while
    // already moving at high horizontal speed (|vx| >= ULTRA_THRESHOLD).
    //
    // Down-diagonal Ultra: pre-dash speed is preserved during the dash
    // (instead of being cut to dashDirX * DASH_SPEED). On landing the
    // speed is preserved and given a small multiplier boost.
    //
    // Up-diagonal Ultra: same preservation during the dash, but when the
    // dash ends the horizontal momentum is cut back to RUN_SPEED — the
    // speed boost does NOT carry after the dash finishes.
    // -------------------------------------------------------------------

    /** True while an ultra-qualifying diagonal dash is executing. */
    private boolean ultraDashActive = false;

    /** true = down-diagonal ultra; false = up-diagonal ultra. */
    private boolean ultraDashDown = false;

    /** Absolute horizontal speed captured at the moment the ultra dash started. */
    private float ultraPreSpeed = 0f;

    /** Horizontal direction (+1 / -1) of the ultra dash. */
    private int ultraDashSign = 0;

    /**
     * Set when a down-diagonal ultra dash ends while the player is still
     * airborne. The landing boost is applied the moment the player touches
     * the ground.
     */
    private boolean ultraLandingPending = false;

    // Status flags read by GameScreen
    private boolean dead = false;
    private boolean won = false;

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
        x = sx;
        y = sy;
        vx = vy = 0f;

        grounded = false;
        onWallLeft = false;
        onWallRight = false;

        canDash = true;
        dashing = false;
        dashTimer = 0f;
        dashCooldownTimer = 0f;

        coyoteTimer = 0f;
        jumpBufferTimer = 0f;
        variableJumpTimer = 0f;
        wallJumpLockTimer = 0f;

        dashJumpPending = false;
        dashJumpIsHyper = false;
        dashJumpDirSign = 0;
        dashJumpWindowTimer = 0f;

        ultraDashActive = false;
        ultraDashDown = false;
        ultraPreSpeed = 0f;
        ultraDashSign = 0;
        ultraLandingPending = false;

        dead = false;
        won = false;
    }

    // -----------------------------------------------------------------------
    // Main update — called once per game frame
    // -----------------------------------------------------------------------

    public void update(float dt, InputHandler input, Level level) {
        if (dead || won)
            return;

        // 1. Register jump-buffer (must happen before jump handling)
        if (input.isJumpJustPressed()) {
            jumpBufferTimer = JUMP_BUFFER_TIME;
        }

        // 2. Initiate dash if the button was just pressed and we have a charge
        if (input.isDashJustPressed() && canDash && !dashing && dashCooldownTimer <= 0f) {
            startDash(input);
        }

        // 3. Tick cooldown timer
        if (dashCooldownTimer > 0f)
            dashCooldownTimer -= dt;

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
        if (dead)
            return;

        // 6. Post-collision bookkeeping
        checkWalls(level);

        // 7. Manage coyote timer & dash replenishment
        if (grounded) {
            coyoteTimer = COYOTE_TIME;
            canDash = true; // landing recharges the dash
        } else {
            if (coyoteTimer > 0f)
                coyoteTimer -= dt;
        }

        // 8. Tick wall-jump horizontal lock
        if (wallJumpLockTimer > 0f)
            wallJumpLockTimer -= dt;

        // 9. Clamp fall speed
        if (!dashing) {
            boolean wallSliding = (onWallLeft || onWallRight) && !grounded && vy < 0f;
            float maxFall = wallSliding ? WALL_SLIDE_SPEED : MAX_FALL_SPEED;
            if (vy < maxFall)
                vy = maxFall;
        }

        // 10. Tick jump-buffer and dash-jump-window timers
        if (jumpBufferTimer > 0f)
            jumpBufferTimer -= dt;
        if (dashJumpWindowTimer > 0f)
            dashJumpWindowTimer -= dt;

        // 11. Update facing direction
        if (vx > 1f)
            facing = 1;
        if (vx < -1f)
            facing = -1;
    }

    // -----------------------------------------------------------------------
    // Dash
    // -----------------------------------------------------------------------

    private void startDash(InputHandler input) {
        // Starting a new dash cancels any pending hyper/super and ultra state.
        dashJumpPending = false;
        dashJumpWindowTimer = 0f;
        ultraDashActive = false;
        ultraLandingPending = false;

        // Build 8-directional vector from current input
        float dx = 0f, dy = 0f;
        if (input.isLeftHeld())
            dx = -1f;
        if (input.isRightHeld())
            dx = 1f;
        if (input.isUpHeld())
            dy = 1f;
        if (input.isDownHeld())
            dy = -1f;

        // No input → dash in the direction the player is facing
        if (dx == 0f && dy == 0f)
            dx = facing;

        // --- Ultra Dash detection (check raw dx/dy before normalization) ---
        // Requires diagonal input AND high horizontal speed already.
        float absVx = Math.abs(vx);
        if (absVx >= ULTRA_THRESHOLD && dx != 0f && dy != 0f) {
            ultraDashActive = true;
            ultraDashDown = dy < 0f; // true = down-diagonal, false = up-diagonal
            ultraPreSpeed = absVx;
            ultraDashSign = (int) Math.signum(dx);
        }

        // Normalize diagonal so speed is consistent
        if (dx != 0f && dy != 0f) {
            dashDirX = dx * 0.7071f;
            dashDirY = dy * 0.7071f;
        } else {
            dashDirX = dx;
            dashDirY = dy;
        }

        dashing = true;
        dashTimer = DASH_DURATION;
        canDash = false;

        variableJumpTimer = 0f; // cancel any active variable-jump
    }

    private void updateDash(float dt, InputHandler input) {
        dashTimer -= dt;

        // Lock velocity to dash direction (gravity ignored during dash).
        // Ultra Dash: if the player was already moving faster than the dash's
        // horizontal component, preserve that higher speed instead of slowing
        // them down.
        if (ultraDashActive) {
            float dashHx = Math.abs(dashDirX) * DASH_SPEED;
            vx = ultraDashSign * Math.max(ultraPreSpeed, dashHx);
            vy = dashDirY * DASH_SPEED;
        } else {
            vx = dashDirX * DASH_SPEED;
            vy = dashDirY * DASH_SPEED;
        }

        if (dashTimer <= 0f) {
            dashing = false;
            dashCooldownTimer = DASH_COOLDOWN;

            // --- Ultra Dash end ---
            if (ultraDashActive) {
                ultraDashActive = false;
                if (ultraDashDown) {
                    // Down-ultra: speed boost is applied when landing.
                    if (grounded) {
                        // Rare case: dash expired while already on the ground.
                        vx = ultraDashSign * ultraPreSpeed * ULTRA_LANDING_MULT;
                        Gdx.app.log("Player", "DOWN ULTRA (ground)! vx=" + vx);
                    } else {
                        ultraLandingPending = true;
                    }
                } else {
                    // Up-ultra: momentum does NOT carry after the dash ends.
                    if (Math.abs(vx) > RUN_SPEED) {
                        vx = ultraDashSign * RUN_SPEED;
                    }
                    Gdx.app.log("Player", "UP ULTRA end — momentum cut. vx=" + vx);
                }
            }

            // Classify the dash direction so we know which dash-jump to grant.
            // Down-diagonal: both axes are non-zero and Y is downward.
            // Horizontal: no vertical component at all.
            boolean isDownDiag = dashDirY < -0.5f && Math.abs(dashDirX) > 0.5f;
            boolean isHorizontal = dashDirY == 0f && dashDirX != 0f;

            if (isDownDiag || isHorizontal) {
                dashJumpIsHyper = isDownDiag;
                dashJumpDirSign = (int) Math.signum(dashDirX);
                // Window starts as soon as the dash ends, whether airborne or not.
                // The hyper/super still only fires once the player is grounded.
                dashJumpWindowTimer = DASH_JUMP_WINDOW;
            }
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
            if (input.isLeftHeld())
                inputX = -1f;
            if (input.isRightHeld())
                inputX = 1f;
        }

        float accel = grounded ? RUN_ACCEL : RUN_ACCEL_AIR;
        float decel = grounded ? RUN_DECEL : RUN_DECEL_AIR;
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
    // Jump (coyote time + buffering + variable height + wall jump +
    // Hyper Dash + Super Dash)
    // -----------------------------------------------------------------------

    private void updateJump(float dt, InputHandler input) {

        // --- Hyper / Super Dash jump (checked before normal jump) -----------
        //
        // Requires:
        // • dashJumpWindowTimer > 0 (recently landed from a qualifying dash)
        // • jumpBufferTimer > 0 (jump was pressed within the buffer window)
        // • grounded (sanity: must be on the ground)
        //
        // Velocity is OVERRIDDEN (not added) to guarantee consistent speed.
        // The direction comes from dashJumpDirSign, which was saved when the
        // triggering dash ended.
        if (dashJumpWindowTimer > 0f && jumpBufferTimer > 0f && grounded) {
            // Reverse dash support: if the player is holding a horizontal direction
            // at the moment the jump fires, use that as the output direction instead
            // of the dash direction. This allows e.g. dashing down-left and jumping
            // right to get a reverse hyper/super.
            int jumpDirSign;
            if (input.isRightHeld()) {
                jumpDirSign = 1;
            } else if (input.isLeftHeld()) {
                jumpDirSign = -1;
            } else {
                jumpDirSign = dashJumpDirSign; // no input → same direction as dash
            }

            if (dashJumpIsHyper) {
                // Hyper Dash: extremely fast horizontal, tiny vertical boost.
                // The flat trajectory is the whole point — stay close to the ground.
                // No variable-jump extension so the player can't "cheat" more height.
                vx = jumpDirSign * HYPER_SPEED;
                vy = HYPER_VERTICAL;
                Gdx.app.log("Player", "HYPER DASH! vx=" + vx + " vy=" + vy
                        + (jumpDirSign != dashJumpDirSign ? " (REVERSE)" : ""));
            } else {
                // Super Dash: fast horizontal + a solid forward arc.
                // Variable-jump IS granted so the player can control the peak height.
                vx = jumpDirSign * SUPER_SPEED;
                vy = SUPER_JUMP_FORCE;
                variableJumpTimer = VARIABLE_JUMP_TIME;
                Gdx.app.log("Player", "SUPER DASH! vx=" + vx + " vy=" + vy
                        + (jumpDirSign != dashJumpDirSign ? " (REVERSE)" : ""));
            }
            jumpBufferTimer = 0f;
            dashJumpWindowTimer = 0f;
            grounded = false;
            return;
        }

        // --- Normal / coyote / wall jump ------------------------------------
        boolean canCoyoteJump = coyoteTimer > 0f;
        boolean canWallJump = (onWallLeft || onWallRight) && !grounded;

        if (jumpBufferTimer > 0f && (canCoyoteJump || canWallJump)) {
            if (canWallJump) {
                // Jump away from whichever wall we're touching.
                // Full speed if holding any horizontal direction, half if neutral.
                boolean holdingAway = onWallLeft ? input.isRightHeld() : input.isLeftHeld();
                boolean holdingTowards = onWallLeft ? input.isLeftHeld() : input.isRightHeld();
                float wallJumpX = (holdingAway || holdingTowards) ? WALL_JUMP_X : WALL_JUMP_X * 0.5f;
                vy = WALL_JUMP_Y;
                vx = onWallLeft ? wallJumpX : -wallJumpX;
                wallJumpLockTimer = WALL_JUMP_LOCK_TIME;
                coyoteTimer = 0f;
            } else {
                // Normal / coyote jump
                vy = JUMP_SPEED;
                coyoteTimer = 0f;
                variableJumpTimer = VARIABLE_JUMP_TIME;
            }
            jumpBufferTimer = 0f;
            grounded = false; // we just jumped, definitely not grounded
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
    // Movement + collision (sub-stepped AABB vs tile grid)
    // -----------------------------------------------------------------------

    private void moveAndCollide(float dt, Level level) {
        boolean wasGrounded = grounded;
        grounded = false; // reset each frame; resolveY sets it back to true if needed

        // Sub-step to prevent tunnelling at high dash/jump speeds.
        final int STEPS = 4;
        float subDt = dt / STEPS;

        for (int i = 0; i < STEPS; i++) {
            x += vx * subDt;
            resolveX(level);
            if (dead)
                return;

            y += vy * subDt;
            resolveY(level);
            if (dead)
                return;
        }

        // Just landed this frame
        if (grounded && !wasGrounded) {
            variableJumpTimer = 0f;

            // If we land while the dash is still active (dashing into the floor),
            // cut the dash short and treat it as a qualifying landing.
            if (dashing) {
                boolean isDownDiag = dashDirY < -0.5f && Math.abs(dashDirX) > 0.5f;
                boolean isHorizontal = dashDirY == 0f && dashDirX != 0f;
                dashing = false;
                dashCooldownTimer = DASH_COOLDOWN;
                if (isDownDiag || isHorizontal) {
                    dashJumpIsHyper = isDownDiag;
                    dashJumpDirSign = (int) Math.signum(dashDirX);
                    dashJumpWindowTimer = DASH_JUMP_WINDOW;
                }
                // Down-ultra that hit the ground while still dashing
                if (ultraDashActive && ultraDashDown) {
                    ultraDashActive = false;
                    vx = ultraDashSign * ultraPreSpeed * ULTRA_LANDING_MULT;
                    Gdx.app.log("Player", "DOWN ULTRA (mid-dash landing)! vx=" + vx);
                } else {
                    ultraDashActive = false;
                }
            }
            // Down-Ultra landing: preserve pre-dash speed and apply the boost multiplier.
            // This runs independently of (and alongside) any hyper/super window that
            // may have also opened — the ultra gives instant horizontal momentum while
            // the hyper window still lets the player chain a hyper jump.
            if (ultraLandingPending) {
                vx = ultraDashSign * ultraPreSpeed * ULTRA_LANDING_MULT;
                ultraLandingPending = false;
                Gdx.app.log("Player", "DOWN ULTRA landing! vx=" + vx);
            }
        }
    }

    /**
     * After moving horizontally, push the player out of any solid tile
     * on the leading edge.
     */
    private void resolveX(Level level) {
        int bottom = tileY(y + 2f); // +2 avoids corner-grazing
        int top = tileY(y + HEIGHT - 2f);

        if (vx > 0f) {
            int right = tileX(x + WIDTH);
            for (int ty = bottom; ty <= top; ty++) {
                if (checkTileX(right, ty, level, true))
                    return;
            }
        } else if (vx < 0f) {
            int left = tileX(x);
            for (int ty = bottom; ty <= top; ty++) {
                if (checkTileX(left, ty, level, false))
                    return;
            }
        }
    }

    /** Returns true if a collision was resolved (stops iterating). */
    private boolean checkTileX(int tx, int ty, Level level, boolean movingRight) {
        if (level.isSolid(tx, ty)) {
            x = movingRight
                    ? tx * Level.TILE_SIZE - WIDTH // push left
                    : (tx + 1) * Level.TILE_SIZE; // push right
            vx = 0f;
            return true;
        }
        if (level.isSpike(tx, ty)) {
            die();
            return true;
        }
        if (level.isGoal(tx, ty)) {
            win();
            return true;
        }
        return false;
    }

    /**
     * After moving vertically, push the player out of any solid tile
     * on the leading edge and set the grounded flag.
     */
    private void resolveY(Level level) {
        int left = tileX(x + 2f);
        int right = tileX(x + WIDTH - 2f);

        if (vy < 0f) {
            // Falling — check bottom edge
            int bottom = tileY(y);
            for (int tx = left; tx <= right; tx++) {
                if (checkTileY(tx, bottom, level, false))
                    return;
            }
        } else if (vy > 0f) {
            // Rising — check top edge
            int top = tileY(y + HEIGHT);
            for (int tx = left; tx <= right; tx++) {
                if (checkTileY(tx, top, level, true))
                    return;
            }
        }
    }

    private boolean checkTileY(int tx, int ty, Level level, boolean movingUp) {
        if (level.isSolid(tx, ty)) {
            if (movingUp) {
                y = ty * Level.TILE_SIZE - HEIGHT; // bonk ceiling
            } else {
                y = (ty + 1) * Level.TILE_SIZE; // land on top
                grounded = true;
            }
            vy = 0f;
            return true;
        }
        if (level.isSpike(tx, ty)) {
            die();
            return true;
        }
        if (level.isGoal(tx, ty)) {
            win();
            return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Wall detection (one pixel past each side of the player)
    // -----------------------------------------------------------------------

    private void checkWalls(Level level) {
        onWallLeft = false;
        onWallRight = false;

        if (grounded)
            return; // no wall slide on the ground

        int bottom = tileY(y + 4f);
        int top = tileY(y + HEIGHT - 4f);
        int leftTile = tileX(x - 1f);
        int rightTile = tileX(x + WIDTH + 1f);

        for (int ty = bottom; ty <= top; ty++) {
            if (level.isSolid(leftTile, ty)) {
                onWallLeft = true;
                break;
            }
        }
        for (int ty = bottom; ty <= top; ty++) {
            if (level.isSolid(rightTile, ty)) {
                onWallRight = true;
                break;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Tile coordinate helpers
    // -----------------------------------------------------------------------

    private int tileX(float worldX) {
        return (int) Math.floor(worldX / Level.TILE_SIZE);
    }

    private int tileY(float worldY) {
        return (int) Math.floor(worldY / Level.TILE_SIZE);
    }

    // -----------------------------------------------------------------------
    // Status helpers
    // -----------------------------------------------------------------------

    private void die() {
        dead = true;
    }

    private void win() {
        won = true;
    }

    /**
     * Linear interpolation helper: moves {@code current} toward {@code target}
     * by at most {@code step}, without overshooting.
     */
    private float moveTowards(float current, float target, float step) {
        float diff = target - current;
        if (Math.abs(diff) <= step)
            return target;
        return current + Math.signum(diff) * step;
    }

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    public void render(ShapeRenderer sr) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        sr.begin(ShapeRenderer.ShapeType.Filled);

        // --- Dash glow ---
        if (dashing) {
            sr.setColor(0.3f, 0.9f, 1.0f, 0.45f);
            sr.rect(x - 5, y - 5, WIDTH + 10, HEIGHT + 10);
        }

        // --- Hyper/Super Dash window indicator: green tint on the ground ---
        if (dashJumpWindowTimer > 0f) {
            sr.setColor(0.2f, 1f, 0.4f, 0.35f);
            sr.rect(x - 3, y - 3, WIDTH + 6, HEIGHT + 6);
        }

        // --- Wall-slide indicator: orange tint on the touching side ---
        if ((onWallLeft || onWallRight) && !grounded) {
            sr.setColor(1f, 0.5f, 0.1f, 0.5f);
            sr.rect(x - 2, y, WIDTH + 4, HEIGHT);
        }

        // --- Player body ---
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

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public boolean isDead() {
        return dead;
    }

    public boolean hasWon() {
        return won;
    }

    public boolean isGrounded() {
        return grounded;
    }

    public boolean isOnWallLeft() {
        return onWallLeft;
    }

    public boolean isOnWallRight() {
        return onWallRight;
    }

    public boolean isDashing() {
        return dashing;
    }

    public boolean canDash() {
        return canDash;
    }
}
