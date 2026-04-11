/**
 * Represents the Wanderer — the keyboard-controlled protagonist of Lumen Architect —
 * storing all positional data, health state, physics velocity, and the set of abilities
 * the character has unlocked through collected {@link LoreFragment} items. The nested
 * {@link CoreHealthBar} class handles rendering a visual representation of the four
 * Core health values directly onto the HUD.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */



import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;

public class Player implements Damageable, Renderable {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** The maximum health the Wanderer can have at any point in the game. */
    private static final int MAX_HEALTH = 5;

    // -------------------------------------------------------------------------
    // Position fields
    // -------------------------------------------------------------------------

    /** The x-coordinate of the Wanderer's left edge in world space. */
    private int x;

    /** The y-coordinate of the Wanderer's top edge in world space. */
    private int y;

    // -------------------------------------------------------------------------
    // Health field
    // -------------------------------------------------------------------------

    /** The current health of the Wanderer; clamped between 0 and {@value #MAX_HEALTH}. */
    private int health;

    // -------------------------------------------------------------------------
    // Physics velocity fields
    // -------------------------------------------------------------------------

    /** Horizontal velocity in pixels per physics tick. */
    private float velX;

    /** Vertical velocity in pixels per physics tick. Positive values move downward. */
    private float velY;

    // -------------------------------------------------------------------------
    // Physics state flags
    // -------------------------------------------------------------------------

    /** Whether the Wanderer is currently standing on a solid surface. */
    private boolean grounded;

    /** Whether the Wanderer is currently pressing against a wall tile. */
    private boolean wallTouching;

    /** Whether the Wanderer is currently invincible (e.g. during a dodge roll). */
    private boolean invincible;

    /**
     * The absolute system-clock time in milliseconds at which invincibility expires.
     * Compared against {@link System#currentTimeMillis()}.
     */
    private long invincibleTimer;

    /**
     * The direction the Wanderer is currently facing.
     * {@code 1} = right, {@code -1} = left.
     */
    private int facingDirection;

    /**
     * The number of jumps performed since the Wanderer last touched the ground.
     * Reset to zero on grounding; capped by the engine at
     * {@link physics.PhysicsEngine#MAX_CONSECUTIVE_JUMPS}.
     */
    private int consecutiveJumps;

    // -------------------------------------------------------------------------
    // Dodge-roll state
    // -------------------------------------------------------------------------

    /** Whether a dodge roll is currently in progress. */
    private boolean dodging;

    /** Absolute ms timestamp when the current dodge roll ends. */
    private long dodgeEndTime;

    /** Absolute ms timestamp before which another dodge roll cannot begin. */
    private long dodgeCooldownEnd;

    // -------------------------------------------------------------------------
    // Shadow-dash state
    // -------------------------------------------------------------------------

    /** Absolute ms timestamp before which another shadow dash cannot begin. */
    private long shadowDashCooldownEnd;

    // -------------------------------------------------------------------------
    // Ability flags
    // -------------------------------------------------------------------------

    /**
     * Whether the Wanderer has unlocked the close-range melee attack, granted by the
     * corresponding {@link LoreFragment.AbilityUnlock#MELEE} fragment.
     */
    private boolean hasMelee;

    /**
     * Whether the Wanderer has unlocked the ranged projectile ability, granted by the
     * {@link LoreFragment.AbilityUnlock#PROJECTILE} fragment.
     */
    private boolean hasProjectile;

    /**
     * Whether the Wanderer has unlocked the dodge-roll evasion, granted by the
     * {@link LoreFragment.AbilityUnlock#DODGE} fragment.
     */
    private boolean hasDodge;

    /**
     * Whether the Wanderer has unlocked wall-clinging and climbing, granted by the
     * {@link LoreFragment.AbilityUnlock#WALL_CLING} fragment.
     */
    private boolean hasWallCling;

    /**
     * Whether the Wanderer has unlocked the shadow-dash ability, granted by the
     * {@link LoreFragment.AbilityUnlock#SHADOW_DASH} fragment.
     */
    private boolean hasShadowDash;

    // -------------------------------------------------------------------------
    // HUD component
    // -------------------------------------------------------------------------

    /** The HUD element that displays the health of all four Cores. */
    private CoreHealthBar coreHealthBar;

    // -------------------------------------------------------------------------
    // Combat and ability state
    // -------------------------------------------------------------------------

    /** Animation state key for the current sprite clip. */
    private String animState;

    /** Reference to the audio manager for SFX playback. */
    private AudioManager audioManager;

    /** Cooldown remaining for melee attack in milliseconds. */
    private long meleeCooldown;

    /**
     * Set to the zero-based index of any {@link Core} struck by the current melee
     * swing. Reset to {@code -1} by {@link #consumePendingCoreHit()} once the
     * NetworkIO send loop has forwarded the event as a
     * {@link server.NetworkProtocol.CoreHitPacket}. {@code volatile} so that the
     * NetworkIO thread observes writes made on the game-loop thread immediately.
     */
    public volatile int pendingCoreHitIndex = -1;

    /** Whether the charge key is currently held for projectile. */
    private boolean chargeHeld;

    /** System.nanoTime() when charge began. */
    private long chargeStartTime;

    /** Whether the dodge roll is currently active. */
    private boolean dodgeActive;

    /** Remaining cooldown for dodge roll in milliseconds. */
    private long dodgeCooldownRemaining;

    /** Remaining cooldown for shadow dash in milliseconds. */
    private long dashCooldownRemaining;

    /** Whether the heal flash overlay is active. */
    private boolean healFlash;

    /** Absolute ms timestamp when heal flash expires. */
    private long healFlashEnd;

    /** Whether the collect flash overlay is active. */
    private boolean collectFlash;

    /** Absolute ms timestamp when the collect flash started. */
    private long collectFlashStart;

    /** List of active game entities for collision queries. */
    private List<GameElement> activeEntities;

    // -------------------------------------------------------------------------
    // Lives and respawn state
    // -------------------------------------------------------------------------

    /** Number of lives remaining. Starts at 3. */
    private int lives;

    /** X-coordinate of the respawn point. */
    private int respawnX;

    /** Y-coordinate of the respawn point. */
    private int respawnY;

    /** Whether the Wanderer is currently in the death animation. */
    private boolean isDead;

    /** Timer tracking elapsed ms since death, for respawn delay. */
    private long deathTimer;

    /** Whether a total game over is pending (return to menu). */
    private boolean gameOverPending = false;

    /** Total attempts remaining across the entire run. Starts at 3. */
    private int totalAttempts = 3;

    /** X-coordinate where the Wanderer died (for death animation rendering). */
    private int deathX;

    /** Y-coordinate where the Wanderer died (for death animation rendering). */
    private int deathY;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code Player} (Wanderer) at the given spawn position with
     * full health, zeroed velocity, facing right, and no abilities unlocked.
     * A {@link CoreHealthBar} is created and attached automatically.
     *
     * @param x the initial x-coordinate of the Wanderer's left edge in world space
     * @param y the initial y-coordinate of the Wanderer's top edge in world space
     */
    public Player(int x, int y) {
        this.x = x;
        this.y = y;
        this.health = MAX_HEALTH;

        // Physics velocity
        this.velX = 0f;
        this.velY = 0f;

        // Physics state
        this.grounded         = false;
        this.wallTouching     = false;
        this.invincible       = false;
        this.invincibleTimer  = 0L;
        this.facingDirection  = 1;
        this.consecutiveJumps = 0;

        // Dodge state
        this.dodging          = false;
        this.dodgeEndTime     = 0L;
        this.dodgeCooldownEnd = 0L;

        // Shadow dash state
        this.shadowDashCooldownEnd = 0L;

        // Ability flags
        this.hasMelee      = false;
        this.hasProjectile = false;
        this.hasDodge      = false;
        this.hasWallCling  = false;
        this.hasShadowDash = false;

        this.coreHealthBar = new CoreHealthBar();

        // Combat and ability state
        this.animState = "wanderer_idle";
        this.audioManager = null;
        this.meleeCooldown = 0L;
        this.chargeHeld = false;
        this.chargeStartTime = 0L;
        this.dodgeActive = false;
        this.dodgeCooldownRemaining = 0L;
        this.dashCooldownRemaining = 0L;
        this.healFlash = false;
        this.healFlashEnd = 0L;
        this.collectFlash = false;
        this.collectFlashStart = 0L;
        this.activeEntities = new ArrayList<>();

        // Lives and respawn
        this.lives = 3;
        this.respawnX = x;
        this.respawnY = y;
        this.isDead = false;
        this.deathTimer = 0L;
        this.deathX = x;
        this.deathY = y;
    }

    // -------------------------------------------------------------------------
    // Renderable implementation
    // -------------------------------------------------------------------------

    /**
     * Renders the Wanderer sprite and overlays the {@link CoreHealthBar} HUD element
     * onto the provided graphics context. Stub implementation to be filled in a later
     * phase.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    @Override
    public void render(Graphics2D g) {
        // Stub — sprite and HUD rendering to be implemented in a later phase.
    }

    /**
     * Returns the axis-aligned bounding rectangle of the Wanderer at its current
     * position, used for collision detection with platforms and hazards.
     *
     * @return a {@link Rectangle} representing the Wanderer's current bounds
     */
    @Override
    public Rectangle getBounds() {
        return new Rectangle(x + 20, y + 8, 24, 80);
    }

    public Rectangle getFullBounds() {
        return new Rectangle(x, y, 64, 96);
    }

    public Rectangle getHorizontalBounds() {
        return new Rectangle(x + 10, y + 12, 44, 36);
    }

    // -------------------------------------------------------------------------
    // Damageable implementation
    // -------------------------------------------------------------------------

    /**
     * Reduces the Wanderer's health by the specified amount, clamping the result to a
     * minimum of zero. Triggers a death event when health reaches zero.
     *
     * @param amount the amount of damage to apply; must be non-negative
     */
    @Override
    public void takeDamage(int amount) {
        // Stub — damage application and death-event logic to be implemented later.
    }

    /**
     * Sets the Wanderer's health directly, clamping the value between 0 and
     * {@value #MAX_HEALTH}. Used for effects such as instant-kill mechanics.
     *
     * @param health the new health value
     */
    public void setHealth(int health) {
        this.health = Math.max(0, Math.min(MAX_HEALTH, health));
    }

    /**
     * Restores the Wanderer's health by the specified amount, clamping the result
     * to {@value #MAX_HEALTH}. Used by boss-fight mechanics when a DarkCrawler is
     * destroyed.
     *
     * @param amount the amount of health to restore; must be non-negative
     */
    public void heal(int amount) {
        this.health = Math.min(MAX_HEALTH, this.health + amount);
        this.healFlash = true;
        this.healFlashEnd = System.currentTimeMillis() + 300;
    }

    /**
     * Returns the Wanderer's current health.
     *
     * @return the current health value, in the range {@code [0, 5]}
     */
    @Override
    public int getHealth() {
        return health;
    }

    /**
     * Returns the maximum health the Wanderer can have.
     *
     * @return {@value #MAX_HEALTH}
     */
    @Override
    public int getMaxHealth() {
        return MAX_HEALTH;
    }

    /**
     * Determines whether the Wanderer is still alive.
     *
     * @return {@code true} if {@code health > 0}; {@code false} otherwise
     */
    @Override
    public boolean isAlive() {
        return health > 0;
    }

    // -------------------------------------------------------------------------
    // Position accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the Wanderer's current x-coordinate in world space.
     *
     * @return x-coordinate in pixels
     */
    public int getX() {
        return x;
    }

    /**
     * Sets the Wanderer's x-coordinate in world space. Called by the physics engine
     * to apply integrated position or teleportation effects.
     *
     * @param x the new x-coordinate in pixels
     */
    public void setX(int x) {
        this.x = x;
    }

    /**
     * Returns the Wanderer's current y-coordinate in world space.
     *
     * @return y-coordinate in pixels
     */
    public int getY() {
        return y;
    }

    /**
     * Sets the Wanderer's y-coordinate in world space. Called by the physics engine
     * to apply integrated position or collision resolution.
     *
     * @param y the new y-coordinate in pixels
     */
    public void setY(int y) {
        this.y = y;
    }

    // -------------------------------------------------------------------------
    // Physics velocity accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the Wanderer's current horizontal velocity in pixels per tick.
     *
     * @return horizontal velocity; negative = moving left
     */
    public float getVelX() {
        return velX;
    }

    /**
     * Sets the Wanderer's horizontal velocity in pixels per tick.
     *
     * @param velX horizontal velocity; negative = moving left
     */
    public void setVelX(float velX) {
        this.velX = velX;
    }

    /**
     * Returns the Wanderer's current vertical velocity in pixels per tick.
     *
     * @return vertical velocity; positive = falling, negative = rising
     */
    public float getVelY() {
        return velY;
    }

    /**
     * Sets the Wanderer's vertical velocity in pixels per tick.
     *
     * @param velY vertical velocity; positive = falling, negative = rising
     */
    public void setVelY(float velY) {
        this.velY = velY;
    }

    // -------------------------------------------------------------------------
    // Physics state accessors
    // -------------------------------------------------------------------------

    /**
     * Returns whether the Wanderer is currently on a solid surface.
     *
     * @return {@code true} if grounded
     */
    public boolean isGrounded() {
        return grounded;
    }

    /**
     * Sets the Wanderer's grounded state. Set to {@code true} by the collision
     * resolver on a top-face platform hit; reset to {@code false} each physics tick.
     *
     * @param grounded {@code true} if the Wanderer is on the ground
     */
    public void setGrounded(boolean grounded) {
        this.grounded = grounded;
    }

    /**
     * Returns whether the Wanderer is currently touching a wall.
     *
     * @return {@code true} if pressing against a wall tile
     */
    public boolean isWallTouching() {
        return wallTouching;
    }

    /**
     * Sets the Wanderer's wall-contact state. Set to {@code true} by the collision
     * resolver on a side-face platform hit; reset each tick.
     *
     * @param wallTouching {@code true} if touching a wall
     */
    public void setWallTouching(boolean wallTouching) {
        this.wallTouching = wallTouching;
    }

    /**
     * Returns whether the Wanderer is currently invincible.
     *
     * @return {@code true} during an active dodge roll or other invincibility window
     */
    public boolean isInvincible() {
        return invincible;
    }

    /**
     * Sets the Wanderer's invincibility flag.
     *
     * @param invincible {@code true} to grant invincibility; {@code false} to remove it
     */
    public void setInvincible(boolean invincible) {
        this.invincible = invincible;
    }

    /**
     * Returns the absolute system-clock time at which the current invincibility window
     * expires.
     *
     * @return expiry timestamp in milliseconds
     */
    public long getInvincibleTimer() {
        return invincibleTimer;
    }

    /**
     * Sets the invincibility expiry timestamp.
     *
     * @param invincibleTimer absolute ms timestamp (from {@link System#currentTimeMillis()})
     */
    public void setInvincibleTimer(long invincibleTimer) {
        this.invincibleTimer = invincibleTimer;
    }

    /**
     * Returns the direction the Wanderer is currently facing.
     *
     * @return {@code 1} for right, {@code -1} for left
     */
    public int getFacingDirection() {
        return facingDirection;
    }

    /**
     * Sets the direction the Wanderer is facing. Used by abilities that fire or
     * move in the facing direction.
     *
     * @param facingDirection {@code 1} for right, {@code -1} for left
     */
    public void setFacingDirection(int facingDirection) {
        this.facingDirection = facingDirection;
    }

    /**
     * Returns the number of jumps performed since the Wanderer last touched the ground.
     *
     * @return consecutive jump count; reset to zero on grounding
     */
    public int getConsecutiveJumps() {
        return consecutiveJumps;
    }

    /**
     * Sets the consecutive jump counter.
     *
     * @param consecutiveJumps the new jump count value
     */
    public void setConsecutiveJumps(int consecutiveJumps) {
        this.consecutiveJumps = consecutiveJumps;
    }

    // -------------------------------------------------------------------------
    // Dodge-roll accessors
    // -------------------------------------------------------------------------

    /**
     * Returns whether a dodge roll is currently active.
     *
     * @return {@code true} while rolling
     */
    public boolean isDodging() {
        return dodging;
    }

    /**
     * Sets the dodge-roll active flag.
     *
     * @param dodging {@code true} to mark the roll as in progress
     */
    public void setDodging(boolean dodging) {
        this.dodging = dodging;
    }

    /**
     * Returns the absolute timestamp when the current dodge roll ends.
     *
     * @return end timestamp in milliseconds
     */
    public long getDodgeEndTime() {
        return dodgeEndTime;
    }

    /**
     * Sets the absolute timestamp when the current dodge roll ends.
     *
     * @param dodgeEndTime end timestamp in milliseconds
     */
    public void setDodgeEndTime(long dodgeEndTime) {
        this.dodgeEndTime = dodgeEndTime;
    }

    /**
     * Returns the absolute timestamp before which a new dodge roll cannot begin.
     *
     * @return cooldown expiry in milliseconds
     */
    public long getDodgeCooldownEnd() {
        return dodgeCooldownEnd;
    }

    /**
     * Sets the absolute timestamp before which a new dodge roll cannot begin.
     *
     * @param dodgeCooldownEnd cooldown expiry in milliseconds
     */
    public void setDodgeCooldownEnd(long dodgeCooldownEnd) {
        this.dodgeCooldownEnd = dodgeCooldownEnd;
    }

    // -------------------------------------------------------------------------
    // Shadow-dash accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the absolute timestamp before which a new shadow dash cannot begin.
     *
     * @return cooldown expiry in milliseconds
     */
    public long getShadowDashCooldownEnd() {
        return shadowDashCooldownEnd;
    }

    /**
     * Sets the absolute timestamp before which a new shadow dash cannot begin.
     *
     * @param shadowDashCooldownEnd cooldown expiry in milliseconds
     */
    public void setShadowDashCooldownEnd(long shadowDashCooldownEnd) {
        this.shadowDashCooldownEnd = shadowDashCooldownEnd;
    }

    // -------------------------------------------------------------------------
    // Ability accessors
    // -------------------------------------------------------------------------

    /**
     * Returns whether the Wanderer has unlocked the melee attack ability.
     *
     * @return {@code true} if melee is unlocked; {@code false} otherwise
     */
    public boolean isHasMelee() {
        return hasMelee;
    }

    /**
     * Sets the Wanderer's melee ability unlock state.
     *
     * @param hasMelee {@code true} to grant the melee ability; {@code false} to revoke it
     */
    public void setHasMelee(boolean hasMelee) {
        this.hasMelee = hasMelee;
    }

    /**
     * Returns the index of the Core hit by the last melee strike and resets
     * {@link #pendingCoreHitIndex} to {@code -1}. Called once per NetworkIO send
     * cycle on the NetworkIO thread so each hit is forwarded exactly once.
     *
     * @return the zero-based Core index [0–3], or {@code -1} if no Core was hit
     */
    public int consumePendingCoreHit() {
        int idx = pendingCoreHitIndex;
        pendingCoreHitIndex = -1;
        return idx;
    }

    /**
     * Returns whether the Wanderer has unlocked the projectile ability.
     *
     * @return {@code true} if projectile firing is unlocked; {@code false} otherwise
     */
    public boolean isHasProjectile() {
        return hasProjectile;
    }

    /**
     * Sets the Wanderer's projectile ability unlock state.
     *
     * @param hasProjectile {@code true} to grant the projectile ability; {@code false}
     *                      to revoke it
     */
    public void setHasProjectile(boolean hasProjectile) {
        this.hasProjectile = hasProjectile;
    }

    /**
     * Returns whether the Wanderer has unlocked the dodge-roll ability.
     *
     * @return {@code true} if dodging is unlocked; {@code false} otherwise
     */
    public boolean isHasDodge() {
        return hasDodge;
    }

    /**
     * Sets the Wanderer's dodge ability unlock state.
     *
     * @param hasDodge {@code true} to grant the dodge ability; {@code false} to revoke it
     */
    public void setHasDodge(boolean hasDodge) {
        this.hasDodge = hasDodge;
    }

    /**
     * Returns whether the Wanderer has unlocked the wall-cling ability.
     *
     * @return {@code true} if wall-clinging is unlocked; {@code false} otherwise
     */
    public boolean isHasWallCling() {
        return hasWallCling;
    }

    /**
     * Sets the Wanderer's wall-cling ability unlock state.
     *
     * @param hasWallCling {@code true} to grant wall-clinging; {@code false} to revoke it
     */
    public void setHasWallCling(boolean hasWallCling) {
        this.hasWallCling = hasWallCling;
    }

    /**
     * Returns whether the Wanderer has unlocked the shadow-dash ability.
     *
     * @return {@code true} if shadow-dash is unlocked; {@code false} otherwise
     */
    public boolean isHasShadowDash() {
        return hasShadowDash;
    }

    /**
     * Sets the Wanderer's shadow-dash ability unlock state.
     *
     * @param hasShadowDash {@code true} to grant shadow-dash; {@code false} to revoke it
     */
    public void setHasShadowDash(boolean hasShadowDash) {
        this.hasShadowDash = hasShadowDash;
    }

    /**
     * Unlocks the ability corresponding to the given {@link LoreFragment.AbilityUnlock}
     * constant. Does nothing if the unlock type is {@link LoreFragment.AbilityUnlock#NONE}.
     *
     * @param unlock the ability to unlock; must not be {@code null}
     */
    public void unlockAbility(LoreFragment.AbilityUnlock unlock) {
        switch (unlock) {
            case MELEE:       hasMelee      = true; break;
            case PROJECTILE:  hasProjectile = true; break;
            case DODGE:       hasDodge      = true; break;
            case WALL_CLING:  hasWallCling  = true; break;
            case SHADOW_DASH: hasShadowDash = true; break;
            default: break;
        }
        System.out.println("Ability unlocked: " + unlock.name());
    }

    // -------------------------------------------------------------------------
    // Combat methods
    // -------------------------------------------------------------------------

    /**
     * Injects the audio manager dependency for SFX playback.
     *
     * @param audioManager the audio manager instance; must not be {@code null}
     */
    public void setAudioManager(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    /**
     * Sets the list of active game entities used for melee hit detection.
     *
     * @param entities the current active entity list; must not be {@code null}
     */
    public void setActiveEntities(List<GameElement> entities) {
        this.activeEntities = entities;
    }

    /**
     * Executes a melee strike in the Wanderer's facing direction. Creates a 40x40
     * hitbox 48 pixels in front of the player and damages all intersecting
     * {@link Damageable} entities by 1 hit point.
     */
    public void executeMelee() {
        if (!hasMelee) {
            System.out.println("DEBUG: melee not yet unlocked");
            return;
        }
        if (meleeCooldown > 0) {
            return;
        }

        // Create hitbox 48px in front of player centre
        int hitboxX = (facingDirection == 1) ? (x + 24) : (x - 40);
        int hitboxY = y - 4;
        Rectangle hitbox = new Rectangle(hitboxX, hitboxY, 40, 40);

        // Check all active entities for Damageable targets
        for (GameElement elem : activeEntities) {
            if (!elem.isActive()) {
                continue;
            }
            if (elem instanceof Damageable) {
                if (hitbox.intersects(elem.getBounds())) {
                    ((Damageable) elem).takeDamage(1);
                    // Record Core hits so the NetworkIO send loop can forward them
                    if (elem instanceof Core) {
                        pendingCoreHitIndex = ((Core) elem).getCoreIndex();
                    }
                }
            }
        }

        meleeCooldown = 400L;
        animState = "wanderer_melee";

        if (audioManager != null) {
            audioManager.playSFX("sfx_melee_strike");
        }
    }

    /**
     * Begins charging a projectile. The charge must be held for at least 1 second
     * before a valid projectile can be released.
     */
    public void startCharge() {
        if (!hasProjectile) {
            return;
        }
        chargeHeld = true;
        chargeStartTime = System.nanoTime();
        animState = "wanderer_charge";
    }

    /**
     * Releases a charged projectile if the charge was held long enough (>= 1 second).
     * The projectile travels horizontally in the facing direction at 6 px/tick with
     * 2 damage and 600 px max range.
     *
     * @return the newly created {@link Projectile}, or {@code null} if the charge
     *         was insufficient or the ability is locked
     */
    public Projectile releaseProjectile() {
        if (!hasProjectile || !chargeHeld) {
            return null;
        }
        chargeHeld = false;

        long heldTime = (System.nanoTime() - chargeStartTime) / 1_000_000;
        if (heldTime < 1000) {
            animState = "wanderer_idle";
            return null;
        }

        // Fire projectile from player centre
        int projX = x + 12 - 4;  // centre x minus half projectile width
        int projY = y + 16 - 4;  // centre y minus half projectile height
        float projVelX = facingDirection * 6f;
        Projectile proj = new Projectile(projX, projY, projVelX, 0f, 2, 600);

        if (audioManager != null) {
            audioManager.playSFX("sfx_projectile_fire");
        }

        animState = "wanderer_idle";
        return proj;
    }

    /**
     * Executes a dodge roll granting 0.5 seconds of invincibility with a 3-second
     * cooldown. The Wanderer lunges in the facing direction at 10 px/tick.
     */
    public void executeDodge() {
        if (!hasDodge || dodgeCooldownRemaining > 0) {
            return;
        }

        velX = facingDirection * 10f;
        invincible = true;
        dodgeActive = true;

        long now = System.currentTimeMillis();
        dodgeEndTime = now + 500;
        invincibleTimer = now + 500;

        dodgeCooldownRemaining = 3000L;
        animState = "wanderer_dodge";
    }

    /**
     * Creates a visual pulse effect at the Wanderer's position and returns it so
     * the game loop can add it to the active entity list. Also sends a network
     * event so the Apprentice's screen shows a marker.
     *
     * @return a new {@link PulseEffect} entity at the player's centre
     */
    public PulseEffect emitPulse() {
        PulseEffect pulse = new PulseEffect(x + 12, y + 16);
        // Network transmission of pulse event handled by caller using CutscenePacket "PULSE"
        return pulse;
    }

    /**
     * Teleports the Wanderer 80 pixels in the facing direction with 200 ms of
     * invincibility and a 5-second cooldown.
     */
    public void executeShadowDash() {
        if (!hasShadowDash || dashCooldownRemaining > 0) {
            return;
        }

        x += facingDirection * 80;
        invincible = true;

        long now = System.currentTimeMillis();
        invincibleTimer = now + 200;

        dashCooldownRemaining = 5000L;
        animState = "wanderer_dodge";

        if (audioManager != null) {
            audioManager.playSFX("sfx_projectile_charge");
        }
    }

    /**
     * Decrements all active cooldown timers by the elapsed time. Should be called
     * once per game-loop tick.
     *
     * @param deltaMs the time elapsed since the last tick, in milliseconds
     */
    public void updateCooldowns(long deltaMs) {
        if (meleeCooldown > 0) {
            meleeCooldown -= deltaMs;
        }
        if (dodgeCooldownRemaining > 0) {
            dodgeCooldownRemaining -= deltaMs;
        }
        if (dashCooldownRemaining > 0) {
            dashCooldownRemaining -= deltaMs;
        }

        // Expire dodge invincibility
        long now = System.currentTimeMillis();
        if (dodgeActive && now >= dodgeEndTime) {
            dodgeActive = false;
            invincible = false;
        }

        // Expire general invincibility
        if (invincible && invincibleTimer > 0 && now >= invincibleTimer) {
            invincible = false;
        }

        // Expire heal flash
        if (healFlash && now >= healFlashEnd) {
            healFlash = false;
        }
    }

    // -------------------------------------------------------------------------
    // Lives and respawn methods
    // -------------------------------------------------------------------------

    /**
     * Deducts one life and enters the death state. If all lives are lost, resets
     * to 3 lives (full game-over screen comes in Phase 8). Does nothing if
     * already in the death state to prevent multiple triggers per fall.
     */
    public void loseLife() {
        if (isDead) return;
        lives--;
        isDead = true;
        deathTimer = 0;
        deathX = x;
        deathY = Math.min(y, 800); // clamp so death anim is visible
        if (lives <= 0) {
            totalAttempts--;
            if (totalAttempts <= 0) {
                gameOverPending = true;
                System.out.println("TOTAL GAME OVER — no attempts remaining, returning to menu");
            } else {
                lives = 3; // restore lives for new attempt
                System.out.println("Attempt failed — " + totalAttempts + " attempt(s) remaining — restarting level");
            }
        }
    }

    /**
     * Updates death/respawn state each tick. While dead, increments the death
     * timer. After 1.2 seconds, respawns the Wanderer at the respawn point.
     *
     * @param deltaMs the time elapsed since the last tick, in milliseconds
     */
    public void updateRespawn(long deltaMs) {
        if (isDead) {
            deathTimer += deltaMs;
            if (deathTimer >= 1200) {
                x = respawnX;
                y = respawnY;
                velX = 0;
                velY = 0;
                grounded = false;
                isDead = false;
                deathTimer = 0;
            }
        }
    }

    /**
     * Sets the respawn coordinates for this player.
     *
     * @param rx the x-coordinate of the respawn point
     * @param ry the y-coordinate of the respawn point
     */
    public void setRespawn(int rx, int ry) {
        this.respawnX = rx;
        this.respawnY = ry;
    }

    /** Returns the number of lives remaining. */
    public int getLives() {
        return lives;
    }

    /** Sets the number of lives. */
    public void setLives(int lives) {
        this.lives = lives;
    }

    /** Returns whether the player is currently in the death state. */
    public boolean isDead() {
        return isDead;
    }

    /** Sets the death state. */
    public void setDead(boolean dead) {
        this.isDead = dead;
    }

    /** Returns whether a total game over is pending. */
    public boolean isGameOverPending() { return gameOverPending; }

    /** Sets the game-over pending flag. */
    public void setGameOverPending(boolean b) { gameOverPending = b; }

    /** Returns the total attempts remaining across the entire run. */
    public int getTotalAttempts() { return totalAttempts; }

    /** Returns the x-coordinate where the player died. */
    public int getDeathX() {
        return deathX;
    }

    /** Returns the y-coordinate where the player died. */
    public int getDeathY() {
        return deathY;
    }

    /** Returns the death timer value in milliseconds. */
    public long getDeathTimer() {
        return deathTimer;
    }

    /** Triggers the collect flash overlay effect. */
    public void triggerCollectFlash() {
        this.collectFlash = true;
        this.collectFlashStart = System.currentTimeMillis();
    }

    /** Returns whether the collect flash is currently active (within 400ms). */
    public boolean isCollectFlashActive() {
        if (!collectFlash) return false;
        if (System.currentTimeMillis() - collectFlashStart > 400L) {
            collectFlash = false;
            return false;
        }
        return true;
    }

    /** Returns the system time in ms when the collect flash started. */
    public long getCollectFlashStart() {
        return collectFlashStart;
    }

    // -------------------------------------------------------------------------
    // New field accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the current animation state key.
     *
     * @return the animation state string
     */
    public String getAnimState() {
        return animState;
    }

    /**
     * Sets the animation state key.
     *
     * @param animState the new animation state
     */
    public void setAnimState(String animState) {
        this.animState = animState;
    }

    /**
     * Returns whether the heal flash overlay is active.
     *
     * @return {@code true} if the white flash is showing
     */
    public boolean isHealFlash() {
        return healFlash;
    }

    /**
     * Returns whether the dodge roll is currently active.
     *
     * @return {@code true} while rolling
     */
    public boolean isDodgeActive() {
        return dodgeActive;
    }

    /**
     * Returns whether a projectile charge is currently held.
     *
     * @return {@code true} while the charge key is held
     */
    public boolean isChargeHeld() {
        return chargeHeld;
    }

    /**
     * Returns the {@link CoreHealthBar} HUD component attached to this player.
     *
     * @return the core health bar; never {@code null}
     */
    public CoreHealthBar getCoreHealthBar() {
        return coreHealthBar;
    }

    // =========================================================================
    // Inner class — CoreHealthBar
    // =========================================================================

    /**
     * A HUD component that tracks and renders the individual health values of all four
     * Cores onto the game canvas. It is owned exclusively by the {@link Player} and
     * updated by the game loop whenever a Core absorbs damage.
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-21
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public class CoreHealthBar {

        /**
         * An array storing the current health of each of the four Cores, indexed from
         * 0 to 3 to match each {@link Core}'s {@code coreIndex}. Each value starts at
         * 3 (the Core maximum) and decrements as Cores take damage.
         */
        private int[] coreHealth;

        /**
         * Constructs a new {@code CoreHealthBar} with all four Core health values
         * initialised to their maximum of 3.
         */
        public CoreHealthBar() {
            this.coreHealth = new int[]{3, 3, 3, 3};
        }

        /**
         * Draws the Core health indicators onto the provided graphics context at their
         * fixed HUD position. This stub will later render four labelled health pips or
         * bar segments corresponding to each Core's remaining health.
         *
         * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
         */
        public void render(Graphics2D g) {
            // Stub — HUD rendering to be implemented in a later phase.
        }

        /**
         * Returns the current health array for all four Cores.
         *
         * @return a four-element integer array indexed by Core index [0–3]
         */
        public int[] getCoreHealth() {
            return coreHealth;
        }

        /**
         * Updates the stored health value for a specific Core, clamping the value
         * between 0 and 3.
         *
         * @param coreIndex the zero-based index of the Core to update; must be in [0, 3]
         * @param health    the new health value for that Core
         */
        public void setCoreHealth(int coreIndex, int health) {
            if (coreIndex >= 0 && coreIndex < coreHealth.length) {
                this.coreHealth[coreIndex] = Math.max(0, Math.min(3, health));
            }
        }
    }
}
