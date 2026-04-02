package com.supdevinci.celeste;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class Player {

    // ── Dimensions (accessible to GameCamera) ──────────────────────────────
    public final float width  = 10f;
    public final float height = 14f;

    // ── Movement ───────────────────────────────────────────────────────────
    private final float runSpeed    = 100f;
    private final float runAccel    = 1400f;
    private final float runAccelAir = 900f;
    private final float runDecel    = 2500f;
    private final float runDecelAir = 400f;

    // ── Gravity ────────────────────────────────────────────────────────────
    private final float gravity          = -900f;
    private final float fallGravityMult  = 2.0f;
    private final float earlyReleaseMult = 2.2f;
    private final float maxFallSpeed     = -520f;

    // ── Jump ───────────────────────────────────────────────────────────────
    private final float jumpSpeed        = 270f;
    private final float coyoteTime       = 0.10f;
    private final float jumpBufferTime   = 0.12f;
    private final float variableJumpTime = 0.13f;

    // ── Dash ───────────────────────────────────────────────────────────────
    private final float dashSpeed    = 320f;
    private final float dashDuration = 0.14f;
    private final float dashCooldown = 0.2f;

    // ── Wall mechanics ─────────────────────────────────────────────────────
    private final float wallSlideSpeed   = -45f;
    private final float wallJumpX        = 200f;
    private final float wallJumpY        = 400f;
    private final float wallJumpLockTime = 0.16f;

    // ── Advanced combos ────────────────────────────────────────────────────
    private final float hyperSpeed    = 580f;
    private final float hyperVertical = 260f;
    private final float superSpeed    = 450f;
    private final float superJumpForce = 300f;

    private final float dashJumpWindow  = 0.10f;
    private final float ultraThreshold  = 250f;
    private final float ultraLandingMult = 1.1f;

    // ── Physics state ──────────────────────────────────────────────────────
    private float x, y;   // bottom-left of AABB
    private float vx, vy; // px/s
    private float spawnX, spawnY;

    private boolean grounded;
    private boolean onWallLeft;
    private boolean onWallRight;

    // ── Timers ─────────────────────────────────────────────────────────────
    private float coyoteTimer;
    private float jumpBufferTimer;
    private float variableJumpTimer;
    private float dashCooldownTimer = 0f;
    private float wallJumpLockTimer = 0f;
    private float dashJumpWindowTimer = 0f;

    // ── Dash state ─────────────────────────────────────────────────────────
    private boolean canDash  = true;
    private boolean dashing  = false;
    private float   dashTimer = 0f;
    private float   dashDirX, dashDirY;

    // ── Hyper / super dash ─────────────────────────────────────────────────
    private boolean dashJumpIsHyper = false;
    private int     dashJumpDirSign = 0;

    // ── Ultra dash ─────────────────────────────────────────────────────────
    private boolean ultraDashActive    = false;
    private boolean ultraDashDown      = false;
    private float   ultraPreSpeed      = 0f;
    private int     ultraDashSign      = 0;
    private boolean ultraLandingPending = false;

    // ── Status ─────────────────────────────────────────────────────────────
    private boolean     dead   = false;
    private boolean     won    = false;
    private int         facing = 1; // 1=right, -1=left
    private PlayerState state  = PlayerState.IDLE;

    // ── Constructor ────────────────────────────────────────────────────────

    public Player(float spawnX, float spawnY) {
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        respawn(spawnX, spawnY);
    }

    public void respawn(float sx, float sy) {
        x = sx; y = sy;
        vx = vy = 0f;

        grounded = false; onWallLeft = false; onWallRight = false;

        canDash = true; dashing = false;
        dashTimer = 0f; dashCooldownTimer = 0f;

        coyoteTimer = 0f; jumpBufferTimer = 0f;
        variableJumpTimer = 0f; wallJumpLockTimer = 0f;

        dashJumpIsHyper = false; dashJumpDirSign = 0; dashJumpWindowTimer = 0f;

        ultraDashActive = false; ultraDashDown = false;
        ultraPreSpeed = 0f; ultraDashSign = 0; ultraLandingPending = false;

        dead = false; won = false;
        state = PlayerState.IDLE;
    }

    // ── Main update ────────────────────────────────────────────────────────

    public void update(float dt, InputHandler input, Level level) {
        if (dead || won) return;

        if (input.isJumpJustPressed())
            jumpBufferTimer = jumpBufferTime;

        if (input.isDashJustPressed() && canDash && !dashing && dashCooldownTimer <= 0f)
            startDash(input);

        if (dashCooldownTimer > 0f) dashCooldownTimer -= dt;

        if (dashing) {
            updateDash(dt);
        } else {
            applyGravity(dt, input);
            applyHorizontalMovement(dt, input);
            updateJump(dt, input);
        }

        moveAndCollide(dt, level);
        if (dead) return;

        checkWalls(level);

        if (grounded) {
            coyoteTimer = coyoteTime;
            canDash = true;
        } else if (coyoteTimer > 0f) {
            coyoteTimer -= dt;
        }

        if (wallJumpLockTimer  > 0f) wallJumpLockTimer  -= dt;
        if (jumpBufferTimer    > 0f) jumpBufferTimer     -= dt;
        if (dashJumpWindowTimer > 0f) dashJumpWindowTimer -= dt;

        if (!dashing) {
            boolean wallSliding = (onWallLeft || onWallRight) && !grounded && vy < 0f;
            float maxFall = wallSliding ? wallSlideSpeed : maxFallSpeed;
            if (vy < maxFall) vy = maxFall;
        }

        if (vx > 1f)  facing = 1;
        if (vx < -1f) facing = -1;

        updateState();
    }

    // ── State machine ──────────────────────────────────────────────────────

    private void updateState() {
        if (dead)    { state = PlayerState.DEAD;         return; }
        if (won)     { state = PlayerState.WON;          return; }
        if (dashing) { state = PlayerState.DASHING;      return; }
        if (!grounded && (onWallLeft || onWallRight) && vy < 0f) {
            state = PlayerState.WALL_SLIDING; return;
        }
        if (!grounded && vy > 0f)  { state = PlayerState.JUMPING; return; }
        if (!grounded)             { state = PlayerState.FALLING;  return; }
        if (Math.abs(vx) > 1f)    { state = PlayerState.RUNNING;  return; }
        state = PlayerState.IDLE;
    }

    // ── Dash ───────────────────────────────────────────────────────────────

    private void startDash(InputHandler input) {
        dashJumpWindowTimer = 0f;
        ultraDashActive = false;
        ultraLandingPending = false;

        com.badlogic.gdx.math.Vector2 d = input.getDirection();
        float dx = d.x == 0f && d.y == 0f ? facing : d.x;
        float dy = d.y;

        float absVx = Math.abs(vx);
        if (absVx >= ultraThreshold && dx != 0f && dy != 0f) {
            ultraDashActive = true;
            ultraDashDown   = dy < 0f;
            ultraPreSpeed   = absVx;
            ultraDashSign   = (int) Math.signum(dx);
        }

        if (dx != 0f && dy != 0f) {
            dashDirX = dx * 0.7071f;
            dashDirY = dy * 0.7071f;
        } else {
            dashDirX = dx;
            dashDirY = dy;
        }

        dashing  = true;
        dashTimer = dashDuration;
        canDash  = false;
        variableJumpTimer = 0f;
    }

    private void updateDash(float dt) {
        dashTimer -= dt;

        if (ultraDashActive) {
            float dashHx = Math.abs(dashDirX) * dashSpeed;
            vx = ultraDashSign * Math.max(ultraPreSpeed, dashHx);
            vy = dashDirY * dashSpeed;
        } else {
            vx = dashDirX * dashSpeed;
            vy = dashDirY * dashSpeed;
        }

        if (dashTimer <= 0f) {
            dashing = false;
            dashCooldownTimer = dashCooldown;

            if (ultraDashActive) {
                ultraDashActive = false;
                if (ultraDashDown) {
                    if (grounded) vx = ultraDashSign * ultraPreSpeed * ultraLandingMult;
                    else          ultraLandingPending = true;
                } else if (Math.abs(vx) > runSpeed) {
                    vx = ultraDashSign * runSpeed;
                }
            }

            tryInitDashJumpWindow();
        }
    }

    /** Shared logic: set up the hyper/super window when dash ends or the player lands mid-dash. */
    private void tryInitDashJumpWindow() {
        boolean isDownDiag   = dashDirY < -0.5f && Math.abs(dashDirX) > 0.5f;
        boolean isHorizontal = dashDirY == 0f && dashDirX != 0f;
        if (isDownDiag || isHorizontal) {
            dashJumpIsHyper    = isDownDiag;
            dashJumpDirSign    = (int) Math.signum(dashDirX);
            dashJumpWindowTimer = dashJumpWindow;
        }
    }

    // ── Physics ────────────────────────────────────────────────────────────

    private void applyGravity(float dt, InputHandler input) {
        float grav = gravity;
        if (vy < 0f) {
            grav *= fallGravityMult;
        } else if (vy > 0f && variableJumpTimer <= 0f) {
            grav *= earlyReleaseMult;
        }
        vy += grav * dt;
    }

    private void applyHorizontalMovement(float dt, InputHandler input) {
        float inputX = wallJumpLockTimer <= 0f ? input.getDirection().x : 0f;

        float accel  = grounded ? runAccel    : runAccelAir;
        float decel  = grounded ? runDecel    : runDecelAir;
        float target = inputX * runSpeed;

        if (inputX != 0f) {
            if (Math.abs(vx) < runSpeed || Math.signum(vx) != Math.signum(inputX))
                vx = moveTowards(vx, target, accel * dt);
            else
                vx = moveTowards(vx, target, decel * dt);
        } else {
            vx = moveTowards(vx, 0f, decel * dt);
        }
    }

    private void updateJump(float dt, InputHandler input) {
        // Hyper / super dash jump
        if (dashJumpWindowTimer > 0f && jumpBufferTimer > 0f && grounded) {
            float dx = input.getDirection().x;
            int dir = dx > 0 ? 1 : dx < 0 ? -1 : dashJumpDirSign;

            if (dashJumpIsHyper) {
                vx = dir * hyperSpeed;
                vy = hyperVertical;
            } else {
                vx = dir * superSpeed;
                vy = superJumpForce;
                variableJumpTimer = variableJumpTime;
            }

            jumpBufferTimer    = 0f;
            dashJumpWindowTimer = 0f;
            grounded = false;
            return;
        }

        // Coyote / wall jump
        boolean canCoyote  = coyoteTimer > 0f;
        boolean canWallJump = (onWallLeft || onWallRight) && !grounded;

        if (jumpBufferTimer > 0f && (canCoyote || canWallJump)) {
            if (canWallJump) {
                float wjX = input.getDirection().x != 0 ? wallJumpX : wallJumpX * 0.5f;
                vy = wallJumpY;
                vx = onWallLeft ? wjX : -wjX;
                wallJumpLockTimer = wallJumpLockTime;
                coyoteTimer = 0f;
            } else {
                vy = jumpSpeed;
                coyoteTimer = 0f;
                variableJumpTimer = variableJumpTime;
            }
            jumpBufferTimer = 0f;
            grounded = false;
        }

        // Variable jump height
        if (variableJumpTimer > 0f) {
            if (input.isJumpHeld()) variableJumpTimer -= dt;
            else                    variableJumpTimer  = 0f;
        }
    }

    // ── Collision ──────────────────────────────────────────────────────────

    private void moveAndCollide(float dt, Level level) {
        boolean wasGrounded = grounded;
        grounded = false;

        final int steps = 4; // sub-step to prevent tunnelling at high speed
        float subDt = dt / steps;

        for (int i = 0; i < steps; i++) {
            x += vx * subDt;
            resolveX(level);
            if (dead) return;

            y += vy * subDt;
            resolveY(level);
            if (dead) return;
        }

        // Landing
        if (grounded && !wasGrounded) {
            variableJumpTimer = 0f;

            if (dashing) {
                dashing = false;
                dashCooldownTimer = dashCooldown;
                tryInitDashJumpWindow();
                if (ultraDashActive && ultraDashDown) {
                    ultraDashActive = false;
                    vx = ultraDashSign * ultraPreSpeed * ultraLandingMult;
                } else {
                    ultraDashActive = false;
                }
            }

            if (ultraLandingPending) {
                vx = ultraDashSign * ultraPreSpeed * ultraLandingMult;
                ultraLandingPending = false;
            }
        }
    }

    private void resolveX(Level level) {
        int bottom = tileCoord(y + 2f, level.tileSize);
        int top    = tileCoord(y + height - 2f, level.tileSize);

        if (vx > 0f) {
            int right = tileCoord(x + width, level.tileSize);
            for (int ty = bottom; ty <= top; ty++)
                if (checkTileX(right, ty, level, true)) return;
        } else if (vx < 0f) {
            int left = tileCoord(x, level.tileSize);
            for (int ty = bottom; ty <= top; ty++)
                if (checkTileX(left, ty, level, false)) return;
        }
    }

    private boolean checkTileX(int tx, int ty, Level level, boolean movingRight) {
        if (level.isSolid(tx, ty)) {
            x  = movingRight ? tx * level.tileSize - width : (tx + 1) * level.tileSize;
            vx = 0f;
            return true;
        }
        if (level.isSpike(tx, ty)) { die(); return true; }
        if (level.isGoal(tx, ty))  { win(); return true; }
        return false;
    }

    private void resolveY(Level level) {
        int left  = tileCoord(x + 2f, level.tileSize);
        int right = tileCoord(x + width - 2f, level.tileSize);

        if (vy < 0f) {
            int bottom = tileCoord(y, level.tileSize);
            for (int tx = left; tx <= right; tx++)
                if (checkTileY(tx, bottom, level, false)) return;
        } else if (vy > 0f) {
            int top = tileCoord(y + height, level.tileSize);
            for (int tx = left; tx <= right; tx++)
                if (checkTileY(tx, top, level, true)) return;
        }
    }

    private boolean checkTileY(int tx, int ty, Level level, boolean movingUp) {
        if (level.isSolid(tx, ty)) {
            if (movingUp) y = ty * level.tileSize - height;
            else { y = (ty + 1) * level.tileSize; grounded = true; }
            vy = 0f;
            return true;
        }
        if (level.isSpike(tx, ty)) { die(); return true; }
        if (level.isGoal(tx, ty))  { win(); return true; }
        return false;
    }

    private void checkWalls(Level level) {
        onWallLeft = false; onWallRight = false;
        if (grounded) return;

        int bottom    = tileCoord(y + 4f, level.tileSize);
        int top       = tileCoord(y + height - 4f, level.tileSize);
        int leftTile  = tileCoord(x - 1f, level.tileSize);
        int rightTile = tileCoord(x + width + 1f, level.tileSize);

        for (int ty = bottom; ty <= top; ty++) {
            if (level.isSolid(leftTile, ty))  { onWallLeft  = true; break; }
        }
        for (int ty = bottom; ty <= top; ty++) {
            if (level.isSolid(rightTile, ty)) { onWallRight = true; break; }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private int tileCoord(float world, int tileSize) {
        return (int) Math.floor(world / tileSize);
    }

    private void die() { dead = true; }
    private void win() { won  = true; }

    private float moveTowards(float current, float target, float step) {
        float diff = target - current;
        if (Math.abs(diff) <= step) return target;
        return current + Math.signum(diff) * step;
    }

    // ── Rendering ──────────────────────────────────────────────────────────

    public void render(ShapeRenderer sr) {
        com.badlogic.gdx.Gdx.gl.glEnable(GL20.GL_BLEND);
        com.badlogic.gdx.Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        // Auras (back to front)
        if (dashing) {
            sr.setColor(GameColors.PLAYER_DASH_AURA);
            sr.rect(x - 5, y - 5, width + 10, height + 10);
        }
        if (dashJumpWindowTimer > 0f) {
            sr.setColor(GameColors.PLAYER_JUMP_WINDOW_AURA);
            sr.rect(x - 3, y - 3, width + 6, height + 6);
        }
        if ((onWallLeft || onWallRight) && !grounded) {
            sr.setColor(GameColors.PLAYER_WALL_AURA);
            sr.rect(x - 2, y, width + 4, height);
        }

        // Body
        sr.setColor(dashing ? GameColors.PLAYER_DASHING : GameColors.PLAYER_BODY);
        sr.rect(x, y, width, height);

        // Hair
        sr.setColor(canDash ? GameColors.PLAYER_HAIR_READY : GameColors.PLAYER_HAIR_SPENT);
        float hairW = width * 0.6f;
        sr.rect(x + (width - hairW) * 0.5f, y + height, hairW, 3f);

        // Eyes
        sr.setColor(GameColors.PLAYER_EYE);
        float eyeY    = y + height * 0.55f;
        float eyeOffX = facing >= 0 ? width * 0.60f : width * 0.58f;
        sr.rect(x + width * 0.20f, eyeY, 2f, 2f);
        sr.rect(x + eyeOffX, eyeY, 2f, 2f);

        sr.end();
        com.badlogic.gdx.Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public float       getX()          { return x; }
    public float       getY()          { return y; }
    public boolean     isDead()        { return dead; }
    public boolean     hasWon()        { return won; }
    public boolean     isGrounded()    { return grounded; }
    public boolean     isOnWallLeft()  { return onWallLeft; }
    public boolean     isOnWallRight() { return onWallRight; }
    public boolean     isDashing()     { return dashing; }
    public boolean     canDash()       { return canDash; }
    public PlayerState getState()      { return state; }
}
