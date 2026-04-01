package com.supdevinci.celeste;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class Player {

    public static final float WIDTH = 10f;
    public static final float HEIGHT = 14f;

    private static final float RUN_SPEED = 100f;
    private static final float RUN_ACCEL = 1400f;
    private static final float RUN_ACCEL_AIR = 900f;
    private static final float RUN_DECEL = 2500f;
    private static final float RUN_DECEL_AIR = 400f;

    private static final float GRAVITY = -900f;
    private static final float FALL_GRAVITY_MULT = 2.0f;
    private static final float EARLY_RELEASE_MULT = 2.2f;
    private static final float MAX_FALL_SPEED = -520f;

    private static final float JUMP_SPEED = 270f;
    private static final float COYOTE_TIME = 0.10f;
    private static final float JUMP_BUFFER_TIME = 0.12f;
    private static final float VARIABLE_JUMP_TIME = 0.13f;

    private static final float DASH_SPEED = 320f;
    private static final float DASH_DURATION = 0.14f;
    private static final float DASH_COOLDOWN = 0.2f;

    private static final float WALL_SLIDE_SPEED = -45f;
    private static final float WALL_JUMP_X = 200f;
    private static final float WALL_JUMP_Y = 400f;
    private static final float WALL_JUMP_LOCK_TIME = 0.16f;

    private static final float HYPER_SPEED = 580f;
    private static final float HYPER_VERTICAL = 260f;

    private static final float SUPER_SPEED = 450f;
    private static final float SUPER_JUMP_FORCE = 300f;

    private static final float DASH_JUMP_WINDOW = 0.10f;
    private static final float ULTRA_THRESHOLD = 250f;
    private static final float ULTRA_LANDING_MULT = 1.1f;

    private float x, y; // bottom-left of AABB
    private float vx, vy; // px/s

    private float spawnX, spawnY;

    private boolean grounded;
    private boolean onWallLeft;
    private boolean onWallRight;

    private float coyoteTimer;
    private float jumpBufferTimer;
    private float variableJumpTimer;

    private boolean canDash = true;
    private boolean dashing = false;
    private float dashTimer = 0f;
    private float dashCooldownTimer = 0f;
    private float dashDirX, dashDirY;

    private float wallJumpLockTimer = 0f;

    // hyper/super dash state
    private boolean dashJumpIsHyper = false; // true=hyper (down-diag), false=super (horiz)
    private int dashJumpDirSign = 0;
    private float dashJumpWindowTimer = 0f;

    // ultra dash state
    private boolean ultraDashActive = false;
    private boolean ultraDashDown = false; // true=down-diag, false=up-diag
    private float ultraPreSpeed = 0f;
    private int ultraDashSign = 0;
    private boolean ultraLandingPending = false;

    private boolean dead = false;
    private boolean won = false;
    private int facing = 1; // 1=right, -1=left

    public Player(float spawnX, float spawnY) {
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        respawn(spawnX, spawnY);
    }

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

    public void update(float dt, InputHandler input, Level level) {
        if (dead || won)
            return;

        if (input.isJumpJustPressed())
            jumpBufferTimer = JUMP_BUFFER_TIME;

        if (input.isDashJustPressed() && canDash && !dashing && dashCooldownTimer <= 0f)
            startDash(input);

        if (dashCooldownTimer > 0f)
            dashCooldownTimer -= dt;

        if (dashing) {
            updateDash(dt, input);
        } else {
            applyGravity(dt, input);
            applyHorizontalMovement(dt, input);
            updateJump(dt, input);
        }

        moveAndCollide(dt, level);
        if (dead)
            return;

        checkWalls(level);

        if (grounded) {
            coyoteTimer = COYOTE_TIME;
            canDash = true;
        } else {
            if (coyoteTimer > 0f)
                coyoteTimer -= dt;
        }

        if (wallJumpLockTimer > 0f)
            wallJumpLockTimer -= dt;

        if (!dashing) {
            boolean wallSliding = (onWallLeft || onWallRight) && !grounded && vy < 0f;
            float maxFall = wallSliding ? WALL_SLIDE_SPEED : MAX_FALL_SPEED;
            if (vy < maxFall)
                vy = maxFall;
        }

        if (jumpBufferTimer > 0f)
            jumpBufferTimer -= dt;
        if (dashJumpWindowTimer > 0f)
            dashJumpWindowTimer -= dt;

        if (vx > 1f)
            facing = 1;
        if (vx < -1f)
            facing = -1;
    }

    private void startDash(InputHandler input) {
        dashJumpWindowTimer = 0f;
        ultraDashActive = false;
        ultraLandingPending = false;

        float dx = 0f, dy = 0f;
        if (input.isLeftHeld())
            dx = -1f;
        if (input.isRightHeld())
            dx = 1f;
        if (input.isUpHeld())
            dy = 1f;
        if (input.isDownHeld())
            dy = -1f;

        if (dx == 0f && dy == 0f)
            dx = facing; // no input → dash in facing direction

        float absVx = Math.abs(vx);
        if (absVx >= ULTRA_THRESHOLD && dx != 0f && dy != 0f) {
            ultraDashActive = true;
            ultraDashDown = dy < 0f;
            ultraPreSpeed = absVx;
            ultraDashSign = (int) Math.signum(dx);
        }

        if (dx != 0f && dy != 0f) { // normalize diagonal (1/√2)
            dashDirX = dx * 0.7071f;
            dashDirY = dy * 0.7071f;
        } else {
            dashDirX = dx;
            dashDirY = dy;
        }

        dashing = true;
        dashTimer = DASH_DURATION;
        canDash = false;
        variableJumpTimer = 0f;
    }

    private void updateDash(float dt, InputHandler input) {
        dashTimer -= dt;

        if (ultraDashActive) {
            // preserve pre-dash speed if it exceeds the dash's horizontal component
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

            if (ultraDashActive) {
                ultraDashActive = false;
                if (ultraDashDown) {
                    if (grounded) {
                        vx = ultraDashSign * ultraPreSpeed * ULTRA_LANDING_MULT;
                        Gdx.app.log("Player", "DOWN ULTRA (ground)! vx=" + vx);
                    } else {
                        ultraLandingPending = true;
                    }
                } else {
                    // up-ultra: momentum cut at dash end
                    if (Math.abs(vx) > RUN_SPEED)
                        vx = ultraDashSign * RUN_SPEED;
                    Gdx.app.log("Player", "UP ULTRA end — momentum cut. vx=" + vx);
                }
            }

            boolean isDownDiag = dashDirY < -0.5f && Math.abs(dashDirX) > 0.5f;
            boolean isHorizontal = dashDirY == 0f && dashDirX != 0f;

            if (isDownDiag || isHorizontal) {
                dashJumpIsHyper = isDownDiag;
                dashJumpDirSign = (int) Math.signum(dashDirX);
                dashJumpWindowTimer = DASH_JUMP_WINDOW;
            }
        }
    }

    private void applyGravity(float dt, InputHandler input) {
        float grav = GRAVITY;
        if (vy < 0f) {
            grav *= FALL_GRAVITY_MULT;
        } else if (vy > 0f && variableJumpTimer <= 0f) {
            grav *= EARLY_RELEASE_MULT;
        }
        vy += grav * dt;
    }

    private void applyHorizontalMovement(float dt, InputHandler input) {
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
            if (Math.abs(vx) < RUN_SPEED || Math.signum(vx) != Math.signum(inputX)) {
                vx = moveTowards(vx, target, accel * dt);
            } else {
                vx = moveTowards(vx, target, decel * dt);
            }
        } else {
            vx = moveTowards(vx, 0f, decel * dt);
        }
    }

    private void updateJump(float dt, InputHandler input) {
        if (dashJumpWindowTimer > 0f && jumpBufferTimer > 0f && grounded) {
            int jumpDirSign;
            if (input.isRightHeld())
                jumpDirSign = 1;
            else if (input.isLeftHeld())
                jumpDirSign = -1;
            else
                jumpDirSign = dashJumpDirSign;

            if (dashJumpIsHyper) {
                vx = jumpDirSign * HYPER_SPEED;
                vy = HYPER_VERTICAL;
                Gdx.app.log("Player", "HYPER DASH! vx=" + vx + " vy=" + vy
                        + (jumpDirSign != dashJumpDirSign ? " (REVERSE)" : ""));
            } else {
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

        boolean canCoyoteJump = coyoteTimer > 0f;
        boolean canWallJump = (onWallLeft || onWallRight) && !grounded;

        if (jumpBufferTimer > 0f && (canCoyoteJump || canWallJump)) {
            if (canWallJump) {
                boolean holdingAway = onWallLeft ? input.isRightHeld() : input.isLeftHeld();
                boolean holdingTowards = onWallLeft ? input.isLeftHeld() : input.isRightHeld();
                float wallJumpX = (holdingAway || holdingTowards) ? WALL_JUMP_X : WALL_JUMP_X * 0.5f;
                vy = WALL_JUMP_Y;
                vx = onWallLeft ? wallJumpX : -wallJumpX;
                wallJumpLockTimer = WALL_JUMP_LOCK_TIME;
                coyoteTimer = 0f;
            } else {
                vy = JUMP_SPEED;
                coyoteTimer = 0f;
                variableJumpTimer = VARIABLE_JUMP_TIME;
            }
            jumpBufferTimer = 0f;
            grounded = false;
        }

        if (variableJumpTimer > 0f) {
            if (input.isJumpHeld())
                variableJumpTimer -= dt;
            else
                variableJumpTimer = 0f;
        }
    }

    private void moveAndCollide(float dt, Level level) {
        boolean wasGrounded = grounded;
        grounded = false; // reset each frame; resolveY sets it back if needed

        final int STEPS = 4; // sub-step to prevent tunnelling at high speed
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

        if (grounded && !wasGrounded) {
            variableJumpTimer = 0f;

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
                if (ultraDashActive && ultraDashDown) {
                    ultraDashActive = false;
                    vx = ultraDashSign * ultraPreSpeed * ULTRA_LANDING_MULT;
                    Gdx.app.log("Player", "DOWN ULTRA (mid-dash landing)! vx=" + vx);
                } else {
                    ultraDashActive = false;
                }
            }

            if (ultraLandingPending) {
                vx = ultraDashSign * ultraPreSpeed * ULTRA_LANDING_MULT;
                ultraLandingPending = false;
                Gdx.app.log("Player", "DOWN ULTRA landing! vx=" + vx);
            }
        }
    }

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

    private boolean checkTileX(int tx, int ty, Level level, boolean movingRight) {
        if (level.isSolid(tx, ty)) {
            x = movingRight ? tx * Level.TILE_SIZE - WIDTH : (tx + 1) * Level.TILE_SIZE;
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

    private void resolveY(Level level) {
        int left = tileX(x + 2f); // +2 avoids corner-grazing
        int right = tileX(x + WIDTH - 2f);

        if (vy < 0f) {
            int bottom = tileY(y);
            for (int tx = left; tx <= right; tx++) {
                if (checkTileY(tx, bottom, level, false))
                    return;
            }
        } else if (vy > 0f) {
            int top = tileY(y + HEIGHT);
            for (int tx = left; tx <= right; tx++) {
                if (checkTileY(tx, top, level, true))
                    return;
            }
        }
    }

    private boolean checkTileY(int tx, int ty, Level level, boolean movingUp) {
        if (level.isSolid(tx, ty)) {
            if (movingUp)
                y = ty * Level.TILE_SIZE - HEIGHT;
            else {
                y = (ty + 1) * Level.TILE_SIZE;
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

    private void checkWalls(Level level) {
        onWallLeft = false;
        onWallRight = false;
        if (grounded)
            return;

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

    private int tileX(float worldX) {
        return (int) Math.floor(worldX / Level.TILE_SIZE);
    }

    private int tileY(float worldY) {
        return (int) Math.floor(worldY / Level.TILE_SIZE);
    }

    private void die() {
        dead = true;
    }

    private void win() {
        won = true;
    }

    private float moveTowards(float current, float target, float step) {
        float diff = target - current;
        if (Math.abs(diff) <= step)
            return target;
        return current + Math.signum(diff) * step;
    }

    public void render(ShapeRenderer sr) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        sr.begin(ShapeRenderer.ShapeType.Filled);

        if (dashing) {
            sr.setColor(0.3f, 0.9f, 1.0f, 0.45f);
            sr.rect(x - 5, y - 5, WIDTH + 10, HEIGHT + 10);
        }

        if (dashJumpWindowTimer > 0f) {
            sr.setColor(0.2f, 1f, 0.4f, 0.35f);
            sr.rect(x - 3, y - 3, WIDTH + 6, HEIGHT + 6);
        }

        if ((onWallLeft || onWallRight) && !grounded) {
            sr.setColor(1f, 0.5f, 0.1f, 0.5f);
            sr.rect(x - 2, y, WIDTH + 4, HEIGHT);
        }

        if (dashing) {
            sr.setColor(0.6f, 1f, 1f, 1f);
        } else {
            sr.setColor(1f, 1f, 1f, 1f);
        }
        sr.rect(x, y, WIDTH, HEIGHT);

        if (canDash) {
            sr.setColor(1f, 0.4f, 0.6f, 1f);
        } else {
            sr.setColor(0.55f, 0.1f, 0.1f, 1f);
        }
        float hairW = WIDTH * 0.6f;
        sr.rect(x + (WIDTH - hairW) * 0.5f, y + HEIGHT, hairW, 3f);

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
