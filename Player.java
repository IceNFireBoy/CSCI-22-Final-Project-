/**
 The Wanderer — the keyboard-controlled protagonist of Lumen Architect. Stores position,
 health, physics velocity, ability unlock flags (melee, projectile, dodge, wall-cling),
 and faithful meter. The nested CoreHealthBar class renders the four Core health values
 on the HUD. PhysicsEngine moves the player each tick; CollisionDetector checks bounds;
 InputRouter dispatches ability input.
 */



import java.awt.AlphaComposite;      // Used in render() for the death fade-out and invincibility blink effects
import java.awt.BasicStroke;         // Imported for future HUD stroke effects; not directly used in current code
import java.awt.Color;               // AWT colour for placeholder rect fallback rendering in render()
import java.awt.Composite;           // Imported for possible future save/restore of composite in HUD rendering
import java.awt.Graphics2D;          // 2D rendering context passed to render() and CoreHealthBar.render()
import java.awt.Rectangle;           // AABB rectangle returned by getBounds() for collision detection
import java.awt.Stroke;              // Imported for possible future HUD stroke customisation
import java.awt.geom.Ellipse2D;      // Imported for possible future circular ability-indicator rendering
import java.awt.image.BufferedImage; // Sprite frame image loaded by SpriteLoader and drawn in render()
import java.util.ArrayList;          // Default backing list for activeEntities; used in melee hit detection
import java.util.Collections;        // Provides unmodifiableSet wrapper for getActiveBoosts()
import java.util.EnumSet;            // O(1) enum-keyed set for activeBoosts; avoids boxing overhead on per-attack hot path
import java.util.List;               // Interface type for the active entity list passed to setActiveEntities()
import java.util.Set;                // Interface type returned by getActiveBoosts()

public class Player implements Damageable, Renderable { // Implements Damageable (health contract) and Renderable (render contract)

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private int maxHealth; // Dynamic max health cap; starts at 60 (lives * 20), raised by 10 on POWER_SURGE altar choice

    private int x; // World-space X coordinate (left edge); set by PhysicsEngine each tick; read by Camera and render()
    private int y; // World-space Y coordinate (top edge); set by PhysicsEngine each tick; read by Camera and render()

    private int health; // Current health; clamped between 0 and maxHealth; decremented by takeDamage(), incremented by heal()

    private float velX; // Horizontal velocity in pixels per tick; set by PhysicsEngine.walk(); zeroed by stopWalking()
    private float velY; // Vertical velocity in pixels per tick; positive moves downward; accumulates GRAVITY each tick

    private boolean grounded; // True when standing on solid surface; set by CollisionDetector on top-face hit; reset by PhysicsEngine each tick
    private boolean wallTouching; // True when pressing against a wall; set by CollisionDetector on side-face hit; enables wall-cling

    /** Whether the Wanderer is currently invincible (e.g. during a dodge roll). */
    private boolean invincible; // Suppresses incoming damage while true; set by executeDodge(), executeShadowDash(), and i-frame grants

    /**
     * The absolute system-clock time in milliseconds at which invincibility expires.
     * Compared against {@link System#currentTimeMillis()}.
     */
    private long invincibleTimer; // Absolute expiry timestamp; compared against System.currentTimeMillis() in updateCooldowns()

    /**
     * The direction the Wanderer is currently facing.
     * {@code 1} = right, {@code -1} = left.
     */
    private int facingDirection; // Updated by PhysicsEngine#walk() based on velX sign; used in melee hitbox placement and sprite flip

    /**
     * The number of jumps performed since the Wanderer last touched the ground.
     * Reset to zero on grounding; capped by the engine at
     * {@link PhysicsEngine#MAX_CONSECUTIVE_JUMPS}.
     */
    private int consecutiveJumps; // Incremented in PhysicsEngine#jump(); reset to 0 in PhysicsEngine when grounded

    // -------------------------------------------------------------------------
    // Dodge-roll state
    // -------------------------------------------------------------------------

    /** Whether a dodge roll is currently in progress. */
    private boolean dodging; // True while the roll is active; syncs with dodgeActive for legacy compatibility

    /** Absolute ms timestamp when the current dodge roll ends. */
    private long dodgeEndTime; // When System.currentTimeMillis() >= this, the dodge roll ends and invincibility clears

    /** Absolute ms timestamp before which another dodge roll cannot begin. */
    private long dodgeCooldownEnd; // Cooldown expiry: executeDodge() sets this 3 s after the roll begins

    // -------------------------------------------------------------------------
    // Shadow-dash state
    // -------------------------------------------------------------------------

    /** Absolute ms timestamp before which another shadow dash cannot begin. */
    private long shadowDashCooldownEnd; // Cooldown expiry: executeShadowDash() sets this 5 s after the dash

    // -------------------------------------------------------------------------
    // Ability flags
    // -------------------------------------------------------------------------

    /**
     * Whether the Wanderer has unlocked the close-range melee attack, granted by the
     * corresponding {@link LoreFragment.AbilityUnlock#MELEE} fragment.
     */
    private boolean hasMelee; // Unlocked via unlockAbility(MELEE); gates executeMelee()

    /**
     * Whether the Wanderer has unlocked the ranged projectile ability, granted by the
     * {@link LoreFragment.AbilityUnlock#PROJECTILE} fragment.
     */
    private boolean hasProjectile; // Unlocked via unlockAbility(PROJECTILE); gates startCharge() and releaseProjectile()

    /**
     * Whether the Wanderer has unlocked the dodge-roll evasion, granted by the
     * {@link LoreFragment.AbilityUnlock#DODGE} fragment.
     */
    private boolean hasDodge; // Unlocked via unlockAbility(DODGE); gates executeDodge()

    /**
     * Whether the Wanderer has unlocked wall-clinging and climbing, granted by the
     * {@link LoreFragment.AbilityUnlock#WALL_CLING} fragment.
     */
    private boolean hasWallCling; // Unlocked via unlockAbility(WALL_CLING); enables wall-slide and wall-jump in PhysicsEngine

    /**
     * Whether the Wanderer has unlocked the shadow-dash ability, granted by the
     * {@link LoreFragment.AbilityUnlock#SHADOW_DASH} fragment.
     */
    private boolean hasShadowDash; // Unlocked via unlockAbility(SHADOW_DASH); gates executeShadowDash()

    // P9.1' — four additional ability flags introduced alongside the new
    // VEIL / ECHO / TETHER / SHADOW_STEP fragments. They start false and are
    // flipped by {@link #unlockAbility(LoreFragment.AbilityUnlock)} on
    // collection. Combat / traversal hot paths query the matching getter to
    // branch on presence; the actual mechanics are wired up by the systems
    // that consume each flag.

    /** P9.1' — Veil unlock: stealth-while-unlit windows for the Wanderer. */
    private boolean hasVeil; // Set true by unlockAbility(VEIL); read by stealth and crawler-hostility code

    /** P9.1' — Echo unlock: one-tile audible ping that briefly reveals nearby hazards. */
    private boolean hasEcho; // Set true by unlockAbility(ECHO); consumed by the echo-ping ability

    /** P9.1' — Tether unlock: short directional dash anchored to a placed block. */
    private boolean hasTether; // Set true by unlockAbility(TETHER); enables tether-dash combat option

    /** P9.1' — Shadow-step unlock: traversal blink, gated behind both VEIL and TETHER. */
    private boolean hasShadowStep; // Set true by unlockAbility(SHADOW_STEP); requires hasVeil && hasTether to actually fire

    /**
     * Set to {@code true} when the Wanderer accepts the Sight Restriction altar
     * offering in P8.6. While active, the boss-phase light radius is shrunk by
     * 25% in {@link GameCanvas}.
     */
    private boolean sightRestricted; // Altar penalty: shrinks visible light radius in boss phase by 25%

    // -------------------------------------------------------------------------
    // P8.7 — Faithful meter
    // -------------------------------------------------------------------------

    /** Maximum value of the faithful meter. */
    public static final int FAITHFUL_MAX = 5; // Ceiling for the faithful score; scores above are clamped in setFaithful()

    /**
     * Current faithful score in {@code [0, FAITHFUL_MAX]}. Managed exclusively on
     * the Wanderer client; relayed to the Apprentice via {@link NetworkProtocol.PlayerStatePacket}.
     * Increments on fragment collection (+1), SIGHT_RESTRICTION altar (+1), and
     * surviving a boss attack cycle (+1). Decrements by 2 on death.
     */
    private int faithful = 0; // Starts at 0; affects StunMinigame difficulty (higher = slower marker)

    // -------------------------------------------------------------------------
    // P8.9 — Boss fragment boosts + Radiant Collapse
    // -------------------------------------------------------------------------

    /**
     * P8.9 — The set of boost-class ability unlocks currently applied to this
     * Wanderer. Populated from {@link FragmentLibrary#collect(LoreFragment)}
     * via {@link #activateBoost(LoreFragment.AbilityUnlock)}; queried by the
     * combat code to branch on {@link LoreFragment.AbilityUnlock#EMBER}
     * (+1 melee damage to Cores) and {@link LoreFragment.AbilityUnlock#IRON}
     * (20% damage reduction on incoming hits). Purely narrative fragments
     * never enter this set — only the enum values that carry a mechanical
     * combat effect. {@link EnumSet} keeps membership checks O(1) and avoids
     * boxing overhead on the per-attack hot path.
     */
    private final Set<LoreFragment.AbilityUnlock> activeBoosts =
            EnumSet.noneOf(LoreFragment.AbilityUnlock.class); // Empty EnumSet at start; EMBER and IRON added via activateBoost()

    /**
     * P8.9 — Radiant Collapse finite-state machine.
     *
     * <ul>
     *   <li>{@link #IDLE} — default; SHIFT-pressed in BOSS transitions to CHARGING.</li>
     *   <li>{@link #CHARGING} — SHIFT held down for 2 s transitions to ACTIVE;
     *       releasing SHIFT early drops back to IDLE.</li>
     *   <li>{@link #ACTIVE} — 3 s of full-arena illumination; auto-transitions to
     *       COOLDOWN.</li>
     *   <li>{@link #COOLDOWN} — 60 s lockout; auto-transitions back to IDLE.</li>
     * </ul>
     */
    public enum RadiantState { IDLE, CHARGING, ACTIVE, COOLDOWN } // Four-state FSM for the Radiant Collapse ability

    /** Duration of the SHIFT-hold charge window before Radiant Collapse activates. */
    public static final long RADIANT_CHARGE_MS   = 2_000L;  // 2 seconds of continuous SHIFT hold required to fire

    /** Duration of the full-arena illumination window. */
    public static final long RADIANT_ACTIVE_MS   = 3_000L;  // 3-second arena reveal window; suppresses darkness overlay

    /** Lockout duration after the active window ends before SHIFT is accepted again. */
    public static final long RADIANT_COOLDOWN_MS = 60_000L; // 60-second cooldown; ability is rare and powerful

    /** Current Radiant Collapse FSM state. */
    private RadiantState radiantState = RadiantState.IDLE; // Starts IDLE; advanced by updateRadiantFsm() each tick in BOSS phase

    /**
     * Absolute {@link System#currentTimeMillis()} timestamp at which the current
     * FSM state was entered. Used together with the {@code RADIANT_*_MS}
     * constants to drive auto-transitions.
     */
    private long radiantStateStartMs = 0L; // Stamped on each state entry; compared against now to detect timeout

    /**
     * Absolute {@link System#currentTimeMillis()} timestamp at which the
     * current Radiant ACTIVE window ends. Stamped on CHARGING → ACTIVE on the
     * Wanderer, and mirrored on the Apprentice via the
     * {@link Protocol#RADIANT_ACTIVE} broadcast so the full-arena-reveal mask
     * override stays in sync on both screens without relying on FSM state
     * replication. Queried by {@link #isRadiantActive()}.
     */
    private volatile long radiantActiveUntilMs = 0L; // Volatile: written by Wanderer game-loop thread, read by NetworkIO thread for broadcast

    // -------------------------------------------------------------------------
    // HUD component
    // -------------------------------------------------------------------------

    /** The HUD element that displays the health of all four Cores. */
    private CoreHealthBar coreHealthBar; // Created in constructor; rendered by GameCanvas each frame during boss/act phases

    // -------------------------------------------------------------------------
    // Combat and ability state
    // -------------------------------------------------------------------------

    /** Animation state key for the current sprite clip. */
    private String animState; // Legacy state string (e.g. "wanderer_idle"); kept in sync with currentAnimState by update()

    /** Reference to the audio manager for SFX playback. */
    private AudioManager audioManager; // Injected via setAudioManager(); used in executeMelee(), executeShadowDash(), releaseProjectile()

    /** Cooldown remaining for melee attack in milliseconds. */
    private long meleeCooldown; // Decremented each tick in updateCooldowns(); executeMelee() blocked while > 0

    /**
     * Set to the zero-based index of any {@link Core} struck by the current melee
     * swing. Reset to {@code -1} by {@link #consumePendingCoreHit()} once the
     * NetworkIO send loop has forwarded the event as a
     * {@link NetworkProtocol.CoreHitPacket}. {@code volatile} so that the
     * NetworkIO thread observes writes made on the game-loop thread immediately.
     */
    public volatile int pendingCoreHitIndex = -1; // -1 = no pending hit; set in executeMelee() when a Core is struck; read by NetworkIO thread

    /** Whether the charge key is currently held for projectile. */
    private boolean chargeHeld; // True while K key is held; drives "charge" animation; checked in releaseProjectile()

    /** System.nanoTime() when charge began. */
    private long chargeStartTime; // Nanosecond timestamp of charge start; used to compute hold duration in releaseProjectile()

    /** Whether the dodge roll is currently active. */
    private boolean dodgeActive; // True during the active dodge roll window; cleared by updateCooldowns() when dodgeEndTime elapses

    /** Remaining cooldown for dodge roll in milliseconds. */
    private long dodgeCooldownRemaining; // Decremented each tick; executeDodge() blocked while > 0

    /** Remaining cooldown for shadow dash in milliseconds. */
    private long dashCooldownRemaining; // Decremented each tick; executeShadowDash() blocked while > 0

    /** Whether the heal flash overlay is active. */
    private boolean healFlash; // True for 300 ms after heal(); triggers white flash overlay in render()

    /** Absolute ms timestamp when heal flash expires. */
    private long healFlashEnd; // heal() sets this to now + 300 ms; updateCooldowns() clears healFlash when this elapses

    /** Whether the collect flash overlay is active. */
    private boolean collectFlash; // True for 400 ms after triggerCollectFlash(); indicates a fragment was just picked up

    /** Absolute ms timestamp when the collect flash started. */
    private long collectFlashStart; // Stamped by triggerCollectFlash(); checked by isCollectFlashActive() to determine elapsed time

    /** List of active game entities for collision queries. */
    private List<GameElement> activeEntities; // Injected by setActiveEntities(); iterated in executeMelee() for hit detection

    // -------------------------------------------------------------------------
    // Lives and respawn state
    // -------------------------------------------------------------------------

    /** Number of lives remaining. Starts at 3. */
    private int lives; // Decremented in loseLife(); when it reaches 0 an attempt is consumed and lives reset to 3

    /** X-coordinate of the respawn point. */
    private int respawnX; // Set by setRespawn() when the Wanderer passes a checkpoint; used by updateRespawn()

    /** Y-coordinate of the respawn point. */
    private int respawnY; // Set by setRespawn() when the Wanderer passes a checkpoint; used by updateRespawn()

    /** Whether the Wanderer is currently in the death animation. */
    private boolean isDead; // True after loseLife(); cleared by updateRespawn() after 1.2 s; blocks input routing

    /** Timer tracking elapsed ms since death, for respawn delay. */
    private long deathTimer; // Incremented each tick in updateRespawn() while isDead; respawn fires at 1200 ms

    /** Whether a total game over is pending (return to menu). */
    private boolean gameOverPending = false; // Set true when totalAttempts reaches 0; causes GameStarter to trigger the game-over screen

    /** Total attempts remaining across the entire run. Starts at 3. */
    private int totalAttempts = 3; // Decremented when all 3 lives are lost; hitting 0 sets gameOverPending

    // -------------------------------------------------------------------------
    // Animation state machine
    // -------------------------------------------------------------------------

    /** Rendered sprite width in pixels (matches full bounding box). */
    private static final int SPRITE_WIDTH  = 64; // Wanderer sprite is 64 px wide; bounds offset by 20 for the collision AABB

    /** Rendered sprite height in pixels (matches full bounding box). */
    private static final int SPRITE_HEIGHT = 96; // Wanderer sprite is 96 px tall; bounds offset by 8 for the collision AABB

    /** Ticks between animation frame advances. */
    private static final int FRAME_DELAY = 6; // 6 ticks per frame at 60 fps ≈ 10 fps animation; snappy but not jittery

    /** Current animation frame index within the active clip. */
    private int frameIndex = 0; // Incremented every FRAME_DELAY ticks; resets when the animation state changes

    /** Tick counter used to advance {@link #frameIndex} at {@link #FRAME_DELAY}. */
    private int frameTick = 0; // Counts ticks since last frame advance; compared against FRAME_DELAY in update()

    /** Short animation state key, e.g. {@code "idle"}, {@code "run"}, {@code "jump"}. */
    private String currentAnimState = "idle"; // Updated each tick in update(); drives getSpritePathForState() lookup

    /** Previous animation state — used to reset {@link #frameIndex} on state change. */
    private String lastAnimState = ""; // Compared against currentAnimState each tick; mismatch triggers frameIndex reset

    /** X-coordinate where the Wanderer died (for death animation rendering). */
    private int deathX; // Snapshotted in loseLife() so the death animation plays at the exact death position

    /** Y-coordinate where the Wanderer died (for death animation rendering). */
    private int deathY; // Snapshotted and clamped to 800 in loseLife() so the animation is always visible

    // -------------------------------------------------------------------------
    // Coyote-time ground confirmation (PART 1 — flicker fix)
    // -------------------------------------------------------------------------

    /**
     * Confirmed-grounded flag used by the animation state machine.
     * Differs from {@link #grounded} (physics) — allows COYOTE_MAX frames of
     * grace before the animation switches to the fall state.
     */
    private boolean wasOnGround = false; // Animation-layer grounded state; lags one COYOTE_MAX frame behind the physics grounded flag

    /** Frames the Wanderer has been off the ground without being confirmed airborne. */
    private int coyoteFrames = 0; // Counts frames since the Wanderer left the ground; when >= COYOTE_MAX, animation switches to fall

    /** Grace period in frames before the animation treats the player as airborne. */
    private static final int COYOTE_MAX = 4; // 4-frame coyote window at 60 fps ≈ 67 ms; prevents flickering on tile edges

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code Player} (Wanderer) at the given spawn position with
     * full health, zeroed velocity, facing right, and no abilities unlocked.
     * A {@link CoreHealthBar} is created and attached automatically.
     *
     * <p>Architecture role: Called by {@link GameStarter} at game-load time after
     * {@link LevelLoader} provides the spawn position. Dependencies (audioManager,
     * activeEntities) are injected separately because they are available later in
     * the initialisation sequence.</p>
     *
     * @param x the initial x-coordinate of the Wanderer's left edge in world space
     * @param y the initial y-coordinate of the Wanderer's top edge in world space
     */
    public Player(int x, int y) {
        this.x = x; // Store spawn x; updated each physics tick by PhysicsEngine
        this.y = y; // Store spawn y; updated each physics tick by PhysicsEngine

        // Physics velocity
        this.velX = 0f; // Start stationary horizontally
        this.velY = 0f; // Start with no vertical velocity; gravity will accumulate on first tick

        // Physics state
        this.grounded         = false; // Not grounded until first platform collision
        this.wallTouching     = false; // Not touching a wall at spawn
        this.invincible       = false; // Vulnerable at spawn
        this.invincibleTimer  = 0L;    // No invincibility timer active
        this.facingDirection  = 1;     // Start facing right
        this.consecutiveJumps = 0;     // No jumps taken yet

        // Dodge state
        this.dodging          = false; // Not rolling at spawn
        this.dodgeEndTime     = 0L;    // No active dodge
        this.dodgeCooldownEnd = 0L;    // No dodge cooldown at spawn

        // Shadow dash state
        this.shadowDashCooldownEnd = 0L; // No shadow dash cooldown at spawn

        // Ability flags
        this.hasMelee      = false; // All abilities locked at spawn; unlocked via LoreFragment collection
        this.hasProjectile = false; // Projectile locked; unlocked by PROJECTILE fragment
        this.hasDodge      = false; // Dodge locked; unlocked by DODGE fragment
        this.hasWallCling  = false; // Wall cling locked; unlocked by WALL_CLING fragment
        this.hasShadowDash = false; // Shadow dash locked; unlocked by SHADOW_DASH fragment (gated by DODGE+WALL_CLING)

        this.coreHealthBar = new CoreHealthBar(); // Create the inner HUD bar for displaying Core health values

        // Combat and ability state
        this.animState = "wanderer_idle";  // Initial animation state key
        this.audioManager = null;          // No audio manager yet; injected via setAudioManager()
        this.meleeCooldown = 0L;           // No melee cooldown at spawn
        this.chargeHeld = false;           // Charge key not held at spawn
        this.chargeStartTime = 0L;         // No charge in progress
        this.dodgeActive = false;          // No active dodge
        this.dodgeCooldownRemaining = 0L;  // No dodge cooldown
        this.dashCooldownRemaining = 0L;   // No shadow dash cooldown
        this.healFlash = false;            // No heal flash effect
        this.healFlashEnd = 0L;            // No heal flash timer
        this.collectFlash = false;         // No collect flash effect
        this.collectFlashStart = 0L;       // No collect flash timer
        this.activeEntities = new ArrayList<>(); // Empty list; injected by setActiveEntities() before first melee

        // Lives and respawn
        this.lives     = 3;                 // Start with 3 lives; each death decrements this
        this.maxHealth = this.lives * 20;   // 60 HP at game start; altar can raise this via addMaxHealth()
        this.health    = this.maxHealth;    // Start at full health
        this.sightRestricted = false;       // No sight penalty at spawn
        this.faithful  = 0;                 // Faithful meter starts at 0
        this.respawnX  = x;                 // Respawn at spawn position until a checkpoint is reached
        this.respawnY  = y;
        this.isDead    = false;             // Alive at spawn
        this.deathTimer = 0L;              // No death timer running
        this.deathX    = x;                // Death animation position defaults to spawn
        this.deathY    = y;
    }

    // -------------------------------------------------------------------------
    // Renderable implementation
    // -------------------------------------------------------------------------

    /**
     * Renders the Wanderer sprite and overlays the {@link CoreHealthBar} HUD element
     * onto the provided graphics context. Handles death fade-out, invincibility
     * blinking, left/right sprite flipping, and fallback placeholder rendering.
     *
     * <p>Rendering pipeline per frame:
     * <ol>
     *   <li>If dead: draw a fading death sprite at the death position.</li>
     *   <li>Load the current sprite frame from {@link SpriteLoader}.</li>
     *   <li>If invincible: apply a 40% alpha blink every 4 ticks.</li>
     *   <li>Draw the sprite horizontally flipped when facing left.</li>
     *   <li>Draw a purple placeholder rectangle if the sprite file is missing.</li>
     *   <li>Restore full composite.</li>
     * </ol></p>
     *
     * <p>Architecture role: Called every frame by {@link GameCanvas#renderElements}
     * while the player is active. Sprite loading is delegated to
     * {@link SpriteLoader#load(String)} which caches results internally.</p>
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    @Override
    public void render(Graphics2D g) {
        // Death animation: fade out over 1.2 seconds at the death position
        if (isDead) {
            float alpha = Math.max(0f, 1.0f - (deathTimer / 1200f)); // Alpha decreases linearly over 1.2 s: 1.0 → 0.0
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)); // Apply fade-out opacity
            String deathPath = getSpritePathForState("death", Math.min(frameIndex, 4)); // Clamp frame to 4 (last death frame)
            BufferedImage deathSprite = SpriteLoader.getInstance().load(deathPath);     // Load the death frame
            if (deathSprite != null) {
                g.drawImage(deathSprite, deathX, deathY, SPRITE_WIDTH, SPRITE_HEIGHT, null); // Draw death sprite at snapshot position
            } else {
                g.setColor(new Color(200, 50, 50));                                    // Red placeholder if death sprite is missing
                g.fillRect(deathX + 20, deathY + 8, 24, 80);                         // Small rectangle at the AABB position
            }
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)); // Restore full opacity before returning
            return; // Skip normal rendering during death animation
        }

        String path = getSpritePathForState(currentAnimState, frameIndex); // Build path from current animation state and frame index
        BufferedImage sprite = SpriteLoader.getInstance().load(path);       // Load sprite frame; returns cached result if available

        // Invincibility flash: blink every 4 ticks
        if (invincible && (frameTick % 8 < 4)) {                                          // Blink: visible for 4 ticks, transparent for 4 ticks (8-tick cycle)
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));   // 40% alpha: partially visible during blink
        }

        if (sprite != null) {
            if (facingDirection < 0) {
                // Flip horizontally by drawing with negative width offset from right edge
                g.drawImage(sprite, x + SPRITE_WIDTH, y, -SPRITE_WIDTH, SPRITE_HEIGHT, null); // Negative width flips the sprite horizontally
            } else {
                g.drawImage(sprite, x, y, SPRITE_WIDTH, SPRITE_HEIGHT, null); // Normal right-facing draw at world position
            }
        } else {
            // Placeholder when sprite file is missing
            g.setColor(new Color(0x55, 0x44, 0x88));              // Purple placeholder colour: distinct from game elements
            g.fillRect(x + 20, y + 8, 24, 80);                   // Matches the AABB position so the placeholder aligns with collision
        }

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)); // Restore full composite so subsequent draws are unaffected
    }

    /**
     * Returns the resource path for the sprite matching the given animation state and
     * frame index, using the exact filenames present in
     * {@code resources/sprites/wanderer/}.
     *
     * <p>Architecture role: Centralises path construction so both render() and
     * any debug/test code can call this method rather than building paths inline.</p>
     *
     * @param state the animation state key (e.g. {@code "run"}, {@code "idle"})
     * @param frame the zero-based frame index within the clip
     * @return the relative resource path string passed to {@link SpriteLoader#load(String)}
     */
    private String getSpritePathForState(String state, int frame) {
        switch (state) {
            case "run":    return "resources/sprites/wanderer/wanderer_run_f"    + (frame + 1) + ".png"; // 4-frame run cycle; files 1-indexed
            case "death":  return "resources/sprites/wanderer/wanderer_death_f"  + (frame + 1) + ".png"; // 5-frame death sequence
            case "melee":  return "resources/sprites/wanderer/wanderer_melee_f"  + (frame + 1) + ".png"; // 2-frame melee swing
            case "dodge":  return "resources/sprites/wanderer/wanderer_dodge_f"  + (frame + 1) + ".png"; // 2-frame dodge roll
            case "charge": return "resources/sprites/wanderer/wanderer_charge_f" + (frame + 1) + ".png"; // 3-frame charge hold
            case "jump":   return "resources/sprites/wanderer/wanderer_jump.png";    // Single jump frame (no animation)
            case "fall":   return "resources/sprites/wanderer/wanderer_fall.png";    // Single fall frame (no animation)
            default:       return "resources/sprites/wanderer/wanderer_idle.png";    // Default idle: covers "idle" and any unknown state
        }
    }

    /**
     * Returns the total number of frames in the given animation clip.
     *
     * <p>Architecture role: Called by {@link #update(long)} to wrap or clamp
     * {@link #frameIndex}. Centralising frame counts here keeps
     * {@link #getSpritePathForState(String, int)} and {@link #update(long)} in sync.</p>
     *
     * @param state the animation state key
     * @return the number of frames in the clip
     */
    private int getFrameCount(String state) {
        switch (state) {
            case "run":    return 4; // 4-frame run loop: wanderer_run_f1 through f4
            case "death":  return 5; // 5-frame death sequence: played once, clamped at last frame
            case "melee":  return 2; // 2-frame attack: wanderer_melee_f1, f2
            case "dodge":  return 2; // 2-frame dodge roll: wanderer_dodge_f1, f2
            case "charge": return 3; // 3-frame charge buildup: wanderer_charge_f1 through f3
            default:       return 1; // Idle, jump, fall: single-frame states
        }
    }

    /**
     * Returns the axis-aligned bounding rectangle of the Wanderer at its current
     * position, used for collision detection with platforms and hazards.
     *
     * <p>The AABB is inset from the full sprite bounds (20 px left, 8 px top, 24 px
     * wide, 80 px tall) to match the visible character body and avoid collisions
     * with transparent sprite regions.</p>
     *
     * @return a {@link Rectangle} representing the Wanderer's current hitbox
     */
    @Override
    public Rectangle getBounds() {
        return new Rectangle(x + 20, y + 8, 24, 80); // Inset hitbox: 20 px from left, 8 px from top; 24×80 px collision area
    }

    /**
     * Returns the full 64×96 bounding rectangle covering the entire sprite, used
     * for broad-phase checks and rendering queries.
     *
     * @return a {@link Rectangle} covering the full sprite footprint
     */
    public Rectangle getFullBounds() {
        return new Rectangle(x, y, 64, 96); // Full sprite bounds; used for camera framing and broad checks
    }

    /**
     * Returns a narrower horizontal AABB used for horizontal-axis collision
     * resolution to prevent the Wanderer from catching on platform corners.
     *
     * @return a {@link Rectangle} representing the horizontal collision region
     */
    public Rectangle getHorizontalBounds() {
        return new Rectangle(x + 10, y + 12, 44, 36); // Wider horizontal slice for smoother corner traversal
    }

    // -------------------------------------------------------------------------
    // Damageable implementation
    // -------------------------------------------------------------------------

    /**
     * Reduces the Wanderer's health by the specified amount, clamping the result to a
     * minimum of zero. Triggers a death event when health reaches zero.
     *
     * <p>P8.9 Iron boost: reduces incoming damage by 20% with a floor of 1 hp,
     * so even boosted Wanderers still lose health per hit.</p>
     *
     * <p>Architecture role: Called by hazard subclasses (CorruptedSpike,
     * CorruptedWall, etc.) and by boss attacks that directly damage the
     * Wanderer. The invincibility guard prevents rapid consecutive hits.</p>
     *
     * @param amount the amount of damage to apply; must be non-negative
     */
    @Override
    public void takeDamage(int amount) {
        if (amount <= 0) return;         // Guard: zero/negative damage is a no-op
        if (invincible || isDead) return; // Guard: invincibility frames and death state prevent further damage

        // P8.9 — Iron boost reduces incoming damage by 20% (floor 1 hp) so the
        // Wanderer still loses at least a point per hit, preventing immortality.
        if (hasBoost(LoreFragment.AbilityUnlock.IRON)) {
            int reduced = (int) Math.floor(amount * 0.8); // 80% of original damage (floored): 20% reduction
            amount = Math.max(1, reduced);                  // Floor at 1 so even 1 hp of damage still hurts
        }

        health = Math.max(0, health - amount); // Deduct damage, clamping health at 0 to prevent negative values
        if (health <= 0) {
            loseLife(); // Health depleted: trigger death sequence (deduct life, enter death animation)
        }
    }

    /**
     * Sets the Wanderer's health directly, clamping the value between 0 and
     * {@link #maxHealth}. Used for effects such as instant-kill mechanics.
     *
     * <p>Architecture role: Called by hazard contact handlers and by the server
     * on reconnect to restore health state.</p>
     *
     * @param health the new health value
     */
    public void setHealth(int health) {
        this.health = Math.max(0, Math.min(maxHealth, health)); // Clamp to [0, maxHealth]; prevents over-healing and negative health
    }

    /**
     * Restores the Wanderer's health by the specified amount, clamping the result
     * to {@link #maxHealth}. Activates the heal flash overlay for 300 ms.
     *
     * <p>Architecture role: Called by P8.6 altar effects and by future heal
     * pickups; previously also called by DarkCrawler-on-death rewards which
     * are no longer in scope (P9.3' removed crawlers entirely).</p>
     *
     * @param amount the amount of health to restore; must be non-negative
     */
    public void heal(int amount) {
        this.health = Math.min(maxHealth, this.health + amount); // Add healing, capping at maxHealth
        this.healFlash  = true;                                   // Activate the white flash overlay to confirm healing
        this.healFlashEnd = System.currentTimeMillis() + 300;    // Flash lasts 300 ms
    }

    /**
     * Returns the Wanderer's current health.
     *
     * @return the current health value, in the range {@code [0, maxHealth]}
     */
    @Override
    public int getHealth() {
        return health; // Current HP; read by HUD rendering and network state packets
    }

    /**
     * Returns the maximum health the Wanderer can have.
     *
     * @return the current maximum health cap (may have been raised by altar)
     */
    @Override
    public int getMaxHealth() {
        return maxHealth; // Dynamic cap; may be higher than initial 60 if POWER_SURGE altar was accepted
    }

    /**
     * Determines whether the Wanderer is still alive.
     *
     * @return {@code true} if {@code health > 0}; {@code false} otherwise
     */
    @Override
    public boolean isAlive() {
        return health > 0; // True = alive and processing; false = dead and respawning
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
        return x; // World-space X; used by Camera, and protocol messages
    }

    /**
     * Sets the Wanderer's x-coordinate in world space. Called by the physics engine
     * to apply integrated position or teleportation effects.
     *
     * @param x the new x-coordinate in pixels
     */
    public void setX(int x) {
        this.x = x; // Applied by PhysicsEngine after velocity integration or knockback
    }

    /**
     * Returns the Wanderer's current y-coordinate in world space.
     *
     * @return y-coordinate in pixels
     */
    public int getY() {
        return y; // World-space Y; used by Camera, and protocol messages
    }

    /**
     * Sets the Wanderer's y-coordinate in world space. Called by the physics engine
     * to apply integrated position or collision resolution.
     *
     * @param y the new y-coordinate in pixels
     */
    public void setY(int y) {
        this.y = y; // Applied by PhysicsEngine after velocity integration or collision push-out
    }

    /**
     * Sets the Wanderer's position from a float pair (for network sync on the
     * Apprentice client). The values are truncated to int world-space pixels.
     *
     * <p>Architecture role: Called by {@link GameSession} when a
     * {@link NetworkProtocol.PlayerStatePacket} arrives on the Apprentice client
     * with the server-authoritative position.</p>
     *
     * @param x the new x-coordinate
     * @param y the new y-coordinate
     */
    public void setPosition(float x, float y) {
        this.x = (int) x; // Truncate float to int; sub-pixel precision is not needed on the display client
        this.y = (int) y; // Truncate float to int
    }

    /**
     * Sets the animation state directly from a network-received state string.
     * Resets the frame counter when the state changes so the clip starts cleanly.
     *
     * <p>Architecture role: Called by the Apprentice client when a
     * {@link NetworkProtocol.PlayerStatePacket} carries a new animation state from
     * the Wanderer, ensuring the displayed sprite matches the authoritative client.</p>
     *
     * @param state short state key, e.g. {@code "run"}, {@code "idle"}
     */
    public void setAnimationState(String state) {
        if (state == null || state.isEmpty()) return; // Guard: ignore null or empty state strings from malformed packets
        if (!state.equals(currentAnimState)) {        // Only reset frames if the state actually changed
            currentAnimState = state;                  // Accept the new state from the network
            lastAnimState    = state;                  // Update last so the next update() does not reset again
            frameIndex       = 0;                      // Restart the clip from the first frame
            frameTick        = 0;                      // Reset the tick counter
            animState        = "wanderer_" + state;    // Sync legacy state string
        }
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
        return velX; // Read by animation state machine to distinguish run from idle
    }

    /**
     * Sets the Wanderer's horizontal velocity in pixels per tick.
     *
     * @param velX horizontal velocity; negative = moving left
     */
    public void setVelX(float velX) {
        this.velX = velX; // Set by PhysicsEngine#walk() and stopWalking()
    }

    /**
     * Returns the Wanderer's current vertical velocity in pixels per tick.
     *
     * @return vertical velocity; positive = falling, negative = rising
     */
    public float getVelY() {
        return velY; // Read by animation state machine to detect jump vs fall state
    }

    /**
     * Sets the Wanderer's vertical velocity in pixels per tick.
     *
     * @param velY vertical velocity; positive = falling, negative = rising
     */
    public void setVelY(float velY) {
        this.velY = velY; // Set by PhysicsEngine#jump() (negative) and gravity accumulation (positive)
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
        return grounded; // Read by PhysicsEngine to decide whether gravity applies
    }

    /**
     * Sets the Wanderer's grounded state. Set to {@code true} by the collision
     * resolver on a top-face platform hit; reset to {@code false} each physics tick.
     *
     * @param grounded {@code true} if the Wanderer is on the ground
     */
    public void setGrounded(boolean grounded) {
        this.grounded = grounded; // Written each tick by CollisionDetector; read by jump logic and animation FSM
    }

    /**
     * Returns whether the Wanderer is currently touching a wall.
     *
     * @return {@code true} if pressing against a wall tile
     */
    public boolean isWallTouching() {
        return wallTouching; // Read by PhysicsEngine to enable wall-slide gravity reduction when hasWallCling is true
    }

    /**
     * Sets the Wanderer's wall-contact state. Set to {@code true} by the collision
     * resolver on a side-face platform hit; reset each tick.
     *
     * @param wallTouching {@code true} if touching a wall
     */
    public void setWallTouching(boolean wallTouching) {
        this.wallTouching = wallTouching; // Written each tick by CollisionDetector
    }

    /**
     * Returns whether the Wanderer is currently invincible.
     *
     * @return {@code true} during an active dodge roll or other invincibility window
     */
    public boolean isInvincible() {
        return invincible; // Read by takeDamage() and hazard contact handlers to skip damage
    }

    /**
     * Sets the Wanderer's invincibility flag.
     *
     * @param invincible {@code true} to grant invincibility; {@code false} to remove it
     */
    public void setInvincible(boolean invincible) {
        this.invincible = invincible; // Written by executeDodge(), executeShadowDash(), and post-damage i-frame grants
    }

    /**
     * Returns the absolute system-clock time at which the current invincibility window
     * expires.
     *
     * @return expiry timestamp in milliseconds
     */
    public long getInvincibleTimer() {
        return invincibleTimer; // Read by hazard handlers to set the expiry; also read by updateCooldowns() for expiry check
    }

    /**
     * Sets the invincibility expiry timestamp.
     *
     * @param invincibleTimer absolute ms timestamp (from {@link System#currentTimeMillis()})
     */
    public void setInvincibleTimer(long invincibleTimer) {
        this.invincibleTimer = invincibleTimer; // Written by hazard handlers, executeDodge(), and executeShadowDash()
    }

    /**
     * Returns the direction the Wanderer is currently facing.
     *
     * @return {@code 1} for right, {@code -1} for left
     */
    public int getFacingDirection() {
        return facingDirection; // Read by executeMelee() for hitbox placement and by render() for sprite flip
    }

    /**
     * Sets the direction the Wanderer is facing. Used by abilities that fire or
     * move in the facing direction.
     *
     * @param facingDirection {@code 1} for right, {@code -1} for left
     */
    public void setFacingDirection(int facingDirection) {
        this.facingDirection = facingDirection; // Written by PhysicsEngine#walk() based on velX sign
    }

    /**
     * Returns the number of jumps performed since the Wanderer last touched the ground.
     *
     * @return consecutive jump count; reset to zero on grounding
     */
    public int getConsecutiveJumps() {
        return consecutiveJumps; // Read by PhysicsEngine to enforce the MAX_CONSECUTIVE_JUMPS cap
    }

    /**
     * Sets the consecutive jump counter.
     *
     * @param consecutiveJumps the new jump count value
     */
    public void setConsecutiveJumps(int consecutiveJumps) {
        this.consecutiveJumps = consecutiveJumps; // Reset to 0 by PhysicsEngine when grounded; incremented on each jump
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
        return dodging; // Read by PhysicsEngine to apply roll velocity
    }

    /**
     * Sets the dodge-roll active flag.
     *
     * @param dodging {@code true} to mark the roll as in progress
     */
    public void setDodging(boolean dodging) {
        this.dodging = dodging; // Written by executeDodge() and PhysicsEngine
    }

    /**
     * Returns the absolute timestamp when the current dodge roll ends.
     *
     * @return end timestamp in milliseconds
     */
    public long getDodgeEndTime() {
        return dodgeEndTime; // Read by updateCooldowns() to determine when to clear dodgeActive and invincibility
    }

    /**
     * Sets the absolute timestamp when the current dodge roll ends.
     *
     * @param dodgeEndTime end timestamp in milliseconds
     */
    public void setDodgeEndTime(long dodgeEndTime) {
        this.dodgeEndTime = dodgeEndTime; // Set by executeDodge() to now + 500 ms
    }

    /**
     * Returns the absolute timestamp before which a new dodge roll cannot begin.
     *
     * @return cooldown expiry in milliseconds
     */
    public long getDodgeCooldownEnd() {
        return dodgeCooldownEnd; // Read by PhysicsEngine to enforce the 3-second cooldown
    }

    /**
     * Sets the absolute timestamp before which a new dodge roll cannot begin.
     *
     * @param dodgeCooldownEnd cooldown expiry in milliseconds
     */
    public void setDodgeCooldownEnd(long dodgeCooldownEnd) {
        this.dodgeCooldownEnd = dodgeCooldownEnd; // Set by executeDodge() to now + 3000 ms
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
        return shadowDashCooldownEnd; // Read by PhysicsEngine to enforce the 5-second dash cooldown
    }

    /**
     * Sets the absolute timestamp before which a new shadow dash cannot begin.
     *
     * @param shadowDashCooldownEnd cooldown expiry in milliseconds
     */
    public void setShadowDashCooldownEnd(long shadowDashCooldownEnd) {
        this.shadowDashCooldownEnd = shadowDashCooldownEnd; // Set by executeShadowDash() to now + 5000 ms
    }

    // -------------------------------------------------------------------------
    // Ability accessors
    // -------------------------------------------------------------------------

    /**
     * Returns whether the Wanderer has unlocked the melee attack ability.
     *
     * @return {@code true} if melee is unlocked
     */
    public boolean isHasMelee() {
        return hasMelee; // Read by HUD and executeMelee() to confirm ability availability
    }

    /**
     * Sets the Wanderer's melee ability unlock state.
     *
     * @param hasMelee {@code true} to grant the melee ability
     */
    public void setHasMelee(boolean hasMelee) {
        this.hasMelee = hasMelee; // May be called directly for testing or server-sync
    }

    /**
     * Returns the index of the Core hit by the last melee strike and resets
     * {@link #pendingCoreHitIndex} to {@code -1}. Called once per NetworkIO send
     * cycle on the NetworkIO thread so each hit is forwarded exactly once.
     *
     * <p>Architecture role: The NetworkIO thread polls this method each tick; when
     * it returns a non-negative value it sends a
     * {@link NetworkProtocol.CoreHitPacket} to the server. The volatile field
     * ensures visibility between the game-loop thread and the NetworkIO thread.</p>
     *
     * @return the zero-based Core index [0–3], or {@code -1} if no Core was hit
     */
    public int consumePendingCoreHit() {
        int idx = pendingCoreHitIndex; // Read the pending hit index before resetting
        pendingCoreHitIndex = -1;      // Reset immediately so each hit is consumed exactly once
        return idx;                    // Return the index to the caller (NetworkIO thread)
    }

    /**
     * Returns whether the Wanderer has unlocked the projectile ability.
     *
     * @return {@code true} if projectile firing is unlocked
     */
    public boolean isHasProjectile() {
        return hasProjectile; // Read by HUD and startCharge() / releaseProjectile()
    }

    /**
     * Sets the Wanderer's projectile ability unlock state.
     *
     * @param hasProjectile {@code true} to grant the projectile ability
     */
    public void setHasProjectile(boolean hasProjectile) {
        this.hasProjectile = hasProjectile; // May be called directly for testing or server-sync
    }

    /**
     * Returns whether the Wanderer has unlocked the dodge-roll ability.
     *
     * @return {@code true} if dodging is unlocked
     */
    public boolean isHasDodge() {
        return hasDodge; // Read by executeDodge() and the HUD
    }

    /**
     * Sets the Wanderer's dodge ability unlock state.
     *
     * @param hasDodge {@code true} to grant the dodge ability
     */
    public void setHasDodge(boolean hasDodge) {
        this.hasDodge = hasDodge; // May be called directly for testing or server-sync
    }

    /**
     * Returns whether the Wanderer has unlocked the wall-cling ability.
     *
     * @return {@code true} if wall-clinging is unlocked
     */
    public boolean isHasWallCling() {
        return hasWallCling; // Read by PhysicsEngine for wall-slide gravity reduction
    }

    /**
     * Sets the Wanderer's wall-cling ability unlock state.
     *
     * @param hasWallCling {@code true} to grant wall-clinging
     */
    public void setHasWallCling(boolean hasWallCling) {
        this.hasWallCling = hasWallCling; // May be called directly for testing or server-sync
    }

    /**
     * Returns whether the Wanderer has unlocked the shadow-dash ability.
     *
     * @return {@code true} if shadow-dash is unlocked
     */
    public boolean isHasShadowDash() {
        return hasShadowDash; // Read by executeShadowDash() and the HUD
    }

    /**
     * Sets the Wanderer's shadow-dash ability unlock state.
     *
     * @param hasShadowDash {@code true} to grant shadow-dash
     */
    public void setHasShadowDash(boolean hasShadowDash) {
        this.hasShadowDash = hasShadowDash; // May be called directly for testing or server-sync
    }

    /**
     * Unlocks the ability corresponding to the given {@link LoreFragment.AbilityUnlock}
     * constant. Does nothing if the unlock type is {@link LoreFragment.AbilityUnlock#NONE}.
     *
     * <p>Architecture role: Called by {@link FragmentLibrary#collect(LoreFragment)}
     * after confirming that the fragment's prerequisites are satisfied. Each unlock
     * type maps to a specific field or system call.</p>
     *
     * @param unlock the ability to unlock; must not be {@code null}
     */
    public void unlockAbility(LoreFragment.AbilityUnlock unlock) {
        switch (unlock) {
            case MELEE:       hasMelee      = true; break; // Enable close-range melee strike
            case PROJECTILE:  hasProjectile = true; break; // Enable charged projectile firing
            case DODGE:       hasDodge      = true; break; // Enable dodge-roll evasion
            case WALL_CLING:  hasWallCling  = true; break; // Enable wall-slide and wall-jump
            case SHADOW_DASH: hasShadowDash = true; break; // Enable instant 80 px dash with i-frames

            // P8.9 — boost fragments feed the activeBoosts set so executeMelee
            // / takeDamage can branch on their presence without plumbing extra
            // flags into every combat call site.
            case EMBER:
            case IRON:
                activateBoost(unlock); // Add to the EnumSet; combat code queries hasBoost() on the hot path
                break;

            // P8.9 — Radiant Collapse persists on GameSession (not Player) so
            // the unlock survives level reloads within a run.
            case RADIANT_COLLAPSE:
                GameSession.getInstance().setRadiantCollapseUnlocked(true); // Persist on session singleton so level reloads do not lose this flag
                break;

            // P9.1' — four new fragment unlocks. SHADOW_STEP gates on
            // VEIL && TETHER per its Javadoc; the flag is set here regardless
            // (the gate is enforced by hot-path consumers via hasShadowStep()
            // && hasVeil && hasTether), so collecting SHADOW_STEP first then
            // VEIL+TETHER works exactly as collecting them in any other order.
            case VEIL:        hasVeil       = true; break;
            case ECHO:        hasEcho       = true; break;
            case TETHER:      hasTether     = true; break;
            case SHADOW_STEP: hasShadowStep = true; break;

            default: break; // NONE and any unrecognised unlock: no action
        }
        System.out.println("Ability unlocked: " + unlock.name()); // Debug log: confirms the unlock registered successfully
    }

    /**
     * Read accessor for any ability flag set by {@link #unlockAbility}.
     * Provided for systems that need to query unlock state without each
     * needing its own dedicated getter (P9.2' altar concealments and future
     * gating systems consume this).
     *
     * @param unlock the ability to check; never {@code null}
     * @return {@code true} if the matching flag has been set; {@code false}
     *         for {@code NONE}, unrecognised values, and unlocks that live on
     *         other singletons (e.g. {@code RADIANT_COLLAPSE} lives on
     *         {@code GameSession})
     */
    public boolean hasUnlock(LoreFragment.AbilityUnlock unlock) {
        if (unlock == null) return false;
        switch (unlock) {
            case MELEE:        return hasMelee;
            case PROJECTILE:   return hasProjectile;
            case DODGE:        return hasDodge;
            case WALL_CLING:   return hasWallCling;
            case SHADOW_DASH:  return hasShadowDash;
            case EMBER:        return hasBoost(LoreFragment.AbilityUnlock.EMBER);
            case IRON:         return hasBoost(LoreFragment.AbilityUnlock.IRON);
            case RADIANT_COLLAPSE: return GameSession.getInstance().isRadiantCollapseUnlocked();
            case VEIL:         return hasVeil;
            case ECHO:         return hasEcho;
            case TETHER:       return hasTether;
            case SHADOW_STEP:  return hasShadowStep;
            case NONE:
            default:           return false;
        }
    }

    // -------------------------------------------------------------------------
    // Combat methods
    // -------------------------------------------------------------------------

    /**
     * Injects the audio manager dependency for SFX playback.
     *
     * <p>Architecture role: Called by {@link GameStarter} after the
     * {@link AudioManager} singleton is available. Without this reference,
     * ability SFX are silently skipped.</p>
     *
     * @param audioManager the audio manager instance; must not be {@code null}
     */
    public void setAudioManager(AudioManager audioManager) {
        this.audioManager = audioManager; // Store audio manager reference; used in executeMelee, executeShadowDash, releaseProjectile
    }

    /**
     * Sets the list of active game entities used for melee hit detection.
     *
     * <p>Architecture role: Called by {@link GameStarter} each tick (or on entity
     * list change) so that {@link #executeMelee()} always iterates the current
     * entity list without holding a stale reference.</p>
     *
     * @param entities the current active entity list; must not be {@code null}
     */
    public void setActiveEntities(List<GameElement> entities) {
        this.activeEntities = entities; // Store reference; iterated by executeMelee() to find Damageable targets in the hitbox
    }

    /**
     * Executes a melee strike in the Wanderer's facing direction. Creates a 40×40
     * hitbox 48 pixels in front of the player and damages all intersecting
     * {@link Damageable} entities by 1 hit point (or 2 with the Ember boost against
     * {@link Core} entities).
     *
     * <p>Architecture role: Called by {@link InputRouter#routeKeyEvent} when the
     * J key edge is consumed. Records any Core hit in {@link #pendingCoreHitIndex}
     * for the NetworkIO thread to relay as a {@link NetworkProtocol.CoreHitPacket}.</p>
     */
    public void executeMelee() {
        if (!hasMelee) {
            System.out.println("DEBUG: melee not yet unlocked"); // Debug: informs the developer that J was pressed without the fragment
            return; // Melee ability not yet unlocked; ignore the press
        }
        if (meleeCooldown > 0) {
            return; // Cooldown active: prevent spam by waiting for 400 ms to elapse
        }

        // Create hitbox 48px in front of player centre
        int hitboxX = (facingDirection == 1) ? (x + 24) : (x - 40); // Right-facing: hitbox starts 24 px past the left edge; left-facing: extends 40 px left of left edge
        int hitboxY = y - 4;                                          // 4 px above the player top; covers chest-height targets
        Rectangle hitbox = new Rectangle(hitboxX, hitboxY, 40, 40);  // 40×40 px melee hitbox

        // Check all active entities for Damageable targets
        // P8.9 — Ember boost adds +1 damage to Core melee hits.
        boolean ember = hasBoost(LoreFragment.AbilityUnlock.EMBER); // Check Ember boost once before the loop (O(1))
        for (GameElement elem : activeEntities) {                    // Iterate every active entity for hit detection
            if (!elem.isActive()) {
                continue; // Skip deactivated entities
            }
            if (elem instanceof Damageable) {                       // Only entities that can take damage are valid targets
                if (hitbox.intersects(elem.getBounds())) {          // AABB intersection: entity is in the melee range
                    int dmg = 1;                                    // Default 1 hp of damage per melee hit
                    if (ember && elem instanceof Core) {
                        dmg = 2; // 1 base + 1 Ember bonus: Ember boost adds extra damage to Core entities only
                    }
                    ((Damageable) elem).takeDamage(dmg);            // Apply damage to the entity
                    // Record Core hits so the NetworkIO send loop can forward them
                    if (elem instanceof Core) {
                        pendingCoreHitIndex = ((Core) elem).getCoreIndex(); // Set volatile flag; NetworkIO thread reads this and sends a CoreHitPacket
                    }
                }
            }
        }

        meleeCooldown = 400L;        // Set 400 ms cooldown before the next swing can fire
        animState = "wanderer_melee"; // Switch to melee animation clip

        if (audioManager != null) {
            audioManager.playSFX("sfx_melee_strike"); // Play the melee swing sound effect
        }
    }

    /**
     * Begins charging a projectile. The charge must be held for at least 1 second
     * before a valid projectile can be released.
     *
     * <p>Architecture role: Called by {@link InputRouter} when the K key is first
     * pressed. The charge animation plays while the key is held; the release is
     * handled by {@link #releaseProjectile()}.</p>
     */
    public void startCharge() {
        if (!hasProjectile) {
            return; // Projectile ability not yet unlocked; ignore K-press
        }
        chargeHeld      = true;                    // Flag the key as held so releaseProjectile() knows a charge is in progress
        chargeStartTime = System.nanoTime();        // Snapshot nanosecond time; hold duration = (now - chargeStartTime) / 1_000_000 ms
        animState       = "wanderer_charge";        // Switch to charge animation while the key is held
    }

    /**
     * Releases a charged projectile if the charge was held long enough (≥ 1 second).
     * The projectile travels horizontally in the facing direction at 6 px/tick with
     * 2 damage and 600 px max range.
     *
     * <p>Architecture role: Called by {@link InputRouter} on K-key release. Returns
     * the new {@link Projectile} to the caller so {@link GameStarter} can add it to
     * the active entity list. Returns {@code null} if the charge was too short.</p>
     *
     * @return the newly created {@link Projectile}, or {@code null} if the charge
     *         was insufficient or the ability is locked
     */
    public Projectile releaseProjectile() {
        if (!hasProjectile || !chargeHeld) {
            return null; // Guard: ability locked or no charge was started; return null to indicate no projectile
        }
        chargeHeld = false; // Clear the charge flag; even a short release exits charge mode

        long heldTime = (System.nanoTime() - chargeStartTime) / 1_000_000; // Convert nanosecond difference to milliseconds
        if (heldTime < 1000) {                                               // Under 1 second: insufficient charge
            animState = "wanderer_idle";                                     // Return to idle animation without firing
            return null;                                                     // No projectile for short presses
        }

        // Fire projectile from player centre
        int   projX    = x + 12 - 4;         // Centre X of the 64 px sprite minus half projectile width (4 px)
        int   projY    = y + 16 - 4;         // Mid-body Y of the 96 px sprite minus half projectile height
        float projVelX = facingDirection * 6f; // 6 px/tick in the facing direction
        Projectile proj = new Projectile(projX, projY, projVelX, 0f, 2, 600); // 2 hp damage, 600 px max range

        if (audioManager != null) {
            audioManager.playSFX("sfx_projectile_fire"); // Play the projectile launch sound effect
        }

        animState = "wanderer_idle"; // Return to idle animation after firing
        return proj;                 // Return the projectile for the caller to add to the active entity list
    }

    /**
     * Executes a dodge roll granting 500 ms of invincibility with a 3-second
     * cooldown. The Wanderer lunges in the facing direction at 10 px/tick.
     *
     * <p>Architecture role: Called by {@link InputRouter} when the L key edge is
     * consumed. The cooldown prevents the Wanderer from chain-dodging through
     * every hazard.</p>
     */
    public void executeDodge() {
        if (!hasDodge || dodgeCooldownRemaining > 0) {
            return; // Guard: ability locked or cooldown active; ignore L-press
        }

        velX        = facingDirection * 10f;               // Apply lunge velocity in the facing direction
        invincible  = true;                                 // Grant invincibility for the roll window
        dodgeActive = true;                                 // Flag the roll as active for animation and cooldown tracking

        long now    = System.currentTimeMillis();
        dodgeEndTime   = now + 500;                        // Roll and invincibility last 500 ms
        invincibleTimer = now + 500;                       // Invincibility expires at the same time as the roll

        dodgeCooldownRemaining = 3000L;                    // 3-second cooldown before the next dodge
        animState = "wanderer_dodge";                      // Switch to dodge animation clip
    }

    /**
     * Creates a visual pulse effect at the Wanderer's position and returns it so
     * the game loop can add it to the active entity list. Also sends a network
     * event so the Apprentice's screen shows a marker.
     *
     * <p>Architecture role: Called by {@link InputRouter} when the E key edge is
     * consumed. The returned {@link PulseEffect} is added to the entity list by
     * {@link GameStarter}; network broadcast is handled by the caller.</p>
     *
     * @return a new {@link PulseEffect} entity at the player's centre
     */
    public PulseEffect emitPulse() {
        PulseEffect pulse = new PulseEffect(x + 12, y + 16); // Create pulse at the Wanderer's chest centre (12, 16 offsets within the sprite)
        // Network transmission of pulse event handled by caller using CutscenePacket "PULSE"
        return pulse; // Return to caller for addition to the active entity list
    }

    /**
     * Teleports the Wanderer 80 pixels in the facing direction with 200 ms of
     * invincibility and a 5-second cooldown.
     *
     * <p>Architecture role: Called by {@link InputRouter} on SHIFT-press outside
     * the BOSS phase. The teleport happens instantly (no animation path); the
     * shadow-dash animation uses the same "dodge" clip for visual feedback.</p>
     */
    public void executeShadowDash() {
        if (!hasShadowDash || dashCooldownRemaining > 0) {
            return; // Guard: ability locked or cooldown active; ignore SHIFT-press
        }

        x += facingDirection * 80;    // Instantly move 80 px in the facing direction (teleport)
        invincible = true;             // Grant brief i-frames during the phase-through

        long now = System.currentTimeMillis();
        invincibleTimer = now + 200;   // 200 ms invincibility window

        dashCooldownRemaining = 5000L; // 5-second cooldown before the next dash
        animState = "wanderer_dodge";  // Reuse the dodge animation for visual feedback during the dash

        if (audioManager != null) {
            audioManager.playSFX("sfx_projectile_charge"); // Play the dash sound (reuses charge SFX for the phase-through whoosh)
        }
    }

    /**
     * Decrements all active cooldown timers by the elapsed time. Should be called
     * once per game-loop tick.
     *
     * <p>Architecture role: Called by {@link GameStarter}'s game loop on the
     * Wanderer client after physics updates. Centralising cooldown management here
     * prevents each ability from needing its own update call.</p>
     *
     * @param deltaMs the time elapsed since the last tick, in milliseconds
     */
    public void updateCooldowns(long deltaMs) {
        if (meleeCooldown > 0) {
            meleeCooldown -= deltaMs; // Decrement melee cooldown; executeMelee() becomes available when this reaches 0
        }
        if (dodgeCooldownRemaining > 0) {
            dodgeCooldownRemaining -= deltaMs; // Decrement dodge cooldown; executeDodge() becomes available when this reaches 0
        }
        if (dashCooldownRemaining > 0) {
            dashCooldownRemaining -= deltaMs; // Decrement dash cooldown; executeShadowDash() becomes available when this reaches 0
        }

        // Expire dodge invincibility
        long now = System.currentTimeMillis(); // Read wall clock once for all expiry checks
        if (dodgeActive && now >= dodgeEndTime) {
            dodgeActive = false;   // Roll animation window ended
            invincible  = false;   // Clear invincibility granted by the dodge roll
        }

        // Expire general invincibility
        if (invincible && invincibleTimer > 0 && now >= invincibleTimer) {
            invincible = false; // General i-frame timer expired; Wanderer is now vulnerable
        }

        // Expire heal flash
        if (healFlash && now >= healFlashEnd) {
            healFlash = false; // 300 ms flash window ended; clear the overlay flag
        }
    }

    // -------------------------------------------------------------------------
    // Per-tick update — animation and network position send
    // -------------------------------------------------------------------------

    /**
     * Updates the animation state machine for one game tick and sends the Wanderer's
     * current position to the server via {@link GameSession#sendToServer(String)}.
     * Must be called once per tick on the Wanderer client; no-op on the Apprentice
     * client (server-session filtering is handled by the caller).
     *
     * <p>Coyote-time ground confirmation: the physics {@link #grounded} flag is the
     * raw per-tick value from the collision system. {@code animGrounded} adds
     * {@link #COYOTE_MAX} frames of grace so the animation does not flicker when
     * the Wanderer briefly leaves the ground while crossing a platform edge.</p>
     *
     * <p>Architecture role: Called by {@link GameStarter}'s game loop after physics
     * and ability updates. Also sends a {@link Protocol#PLAYER_POS} message each
     * tick so the Apprentice client stays in sync.</p>
     *
     * @param deltaMs the time elapsed since the last tick, in milliseconds
     */
    public void update(long deltaMs) {
        // --- Coyote-time ground confirmation ---
        // grounded is the raw physics value (set by PhysicsEngine after collision).
        // animGrounded adds COYOTE_MAX frames of grace before declaring airborne,
        // eliminating single-frame flicker when the player crosses a platform edge.
        boolean animGrounded;
        if (grounded) {
            animGrounded = true;   // Definitively on the ground this tick
            coyoteFrames = 0;      // Reset the coyote counter; no grace frames needed
        } else if (wasOnGround) {
            coyoteFrames++;        // Was on ground last tick but not this tick: start the grace period
            animGrounded = (coyoteFrames <= COYOTE_MAX); // Still "animated-grounded" within the grace window
        } else {
            animGrounded = false;  // Off ground for more than COYOTE_MAX frames: definitively airborne
        }
        wasOnGround = animGrounded; // Store for next tick's grace check

        // Determine the animation state for this tick
        String newState;
        if (isDead)                               newState = "death";  // Death overrides everything
        else if (chargeHeld)                      newState = "charge"; // Charging overrides movement
        else if (meleeCooldown > 0)               newState = "melee";  // Melee swing in progress
        else if (dodgeActive)                     newState = "dodge";  // Dodge roll in progress
        else if (!animGrounded && velY < -0.5f)   newState = "jump";   // Moving upward while airborne
        else if (!animGrounded && velY >= -0.5f)  newState = "fall";   // Moving downward while airborne
        else if (Math.abs(velX) > 0.5f)           newState = "run";    // Moving horizontally while grounded
        else                                      newState = "idle";   // Stationary and grounded

        // Reset frame index when the state changes
        if (!newState.equals(lastAnimState)) {  // State changed: current frame is no longer valid for the new clip
            frameIndex    = 0;                   // Restart the clip from the first frame
            frameTick     = 0;                   // Reset the tick delay counter
            lastAnimState = newState;            // Record new state for next-tick change detection
        }
        currentAnimState = newState; // Commit the new animation state

        // Sync legacy animState string so getAnimState() is still accurate
        animState = "wanderer_" + currentAnimState; // e.g. "wanderer_run" for the legacy string format

        // Advance frame counter
        frameTick++;                                 // Count ticks toward the next frame advance
        if (frameTick >= FRAME_DELAY) {             // FRAME_DELAY ticks elapsed: advance the frame
            frameTick = 0;                           // Reset tick counter
            int maxFrames = getFrameCount(currentAnimState); // Get total frames for this clip
            if (currentAnimState.equals("death")) {
                // Death does not loop — clamp at last frame
                if (frameIndex < maxFrames - 1) frameIndex++; // Stay on the last frame once reached
            } else {
                frameIndex = (frameIndex + 1) % maxFrames; // Loop back to 0 after the last frame
            }
        }

        // Send authoritative position to server (relay to Apprentice client)
        GameSession.getInstance().sendToServer(
            Protocol.PLAYER_POS + "|WANDERER|"
            + x + "|" + y + "|" + velX + "|" + velY + "|" + currentAnimState
            + "|" + health); // Broadcast x, y, velocity, animation state, and health each tick
    }

    // -------------------------------------------------------------------------
    // Lives and respawn methods
    // -------------------------------------------------------------------------

    /**
     * Deducts one life and enters the death state. If all lives are lost, either
     * restores lives for a new attempt (if attempts remain) or sets
     * {@link #gameOverPending} for a final game-over screen. Does nothing if
     * already in the death state to prevent multiple triggers per fall.
     *
     * <p>Architecture role: Called by {@link #takeDamage(int)} when health reaches
     * zero, and directly by out-of-bounds detection in {@link GameStarter}. The
     * faithful meter loses 2 points on each death.</p>
     */
    public void loseLife() {
        if (isDead) return;              // Guard: already dead; prevent double-death from same frame
        lives--;                         // Deduct one life
        isDead    = true;                // Enter death animation state
        deathTimer = 0;                  // Reset death countdown
        addFaithful(-2);                 // Penalise faithful meter by 2 for dying
        deathX = x;                      // Snapshot X for death animation position
        deathY = Math.min(y, 800);       // Clamp Y to 800 so the animation is always visible on screen
        if (lives <= 0) {               // All lives exhausted: consume one attempt
            totalAttempts--;
            if (totalAttempts <= 0) {
                gameOverPending = true;  // No attempts remain: full game over, return to menu
                System.out.println("TOTAL GAME OVER — no attempts remaining, returning to menu");
            } else {
                lives = 3;              // Restore full lives for the next attempt
                System.out.println("Attempt failed — " + totalAttempts + " attempt(s) remaining — restarting level");
            }
        }
    }

    /**
     * Updates death/respawn state each tick. While dead, increments the death
     * timer. After 1.2 seconds, respawns the Wanderer at the respawn point.
     *
     * <p>Architecture role: Called by {@link GameStarter}'s game loop each tick.
     * Separated from the main {@link #update(long)} path so the respawn logic
     * runs independently of animation updates.</p>
     *
     * @param deltaMs the time elapsed since the last tick, in milliseconds
     */
    public void updateRespawn(long deltaMs) {
        if (isDead) {
            deathTimer += deltaMs;      // Accumulate elapsed time since death
            if (deathTimer >= 1200) {   // 1.2 second death animation window complete
                x        = respawnX;   // Teleport to respawn point
                y        = respawnY;
                velX     = 0;          // Clear velocity so the Wanderer doesn't carry momentum into the respawn
                velY     = 0;
                grounded = false;      // Not grounded at respawn; gravity will handle the drop if in the air
                isDead   = false;      // Exit death state; normal gameplay resumes
                deathTimer = 0;        // Reset timer for the next death
            }
        }
    }

    /**
     * Sets the respawn coordinates for this player.
     *
     * <p>Architecture role: Called by {@link GameStarter} when the Wanderer reaches
     * a checkpoint or portal, so subsequent deaths respawn at the correct location
     * rather than the level's initial spawn position.</p>
     *
     * @param rx the x-coordinate of the respawn point
     * @param ry the y-coordinate of the respawn point
     */
    public void setRespawn(int rx, int ry) {
        this.respawnX = rx; // Store new respawn X; used by updateRespawn() after death
        this.respawnY = ry; // Store new respawn Y
    }

    // -------------------------------------------------------------------------
    // P8.6 — altar-granted stat modifiers
    // -------------------------------------------------------------------------

    /**
     * Increases the maximum health cap by {@code amount}. Called on POWER_SURGE altar.
     *
     * <p>Architecture role: Called by {@link GameSession} when the Wanderer accepts
     * the POWER_SURGE altar offering. The higher cap applies from the next heal()
     * call onward.</p>
     *
     * @param amount the number of HP to add to the cap
     */
    public void addMaxHealth(int amount) {
        maxHealth += amount; // Raise the health ceiling; does not change current health
    }

    /**
     * Returns whether the Sight Restriction altar penalty is active.
     *
     * @return {@code true} if the boss light radius is shrunk by 25%
     */
    public boolean isSightRestricted() {
        return sightRestricted; // Read by GameCanvas when rendering the boss-phase light mask
    }

    /**
     * Enables or disables the Sight Restriction penalty.
     *
     * @param sightRestricted {@code true} to apply the 25% radius reduction
     */
    public void setSightRestricted(boolean sightRestricted) {
        this.sightRestricted = sightRestricted; // Set by GameSession when the SIGHT_RESTRICTION altar is accepted
    }

    // -------------------------------------------------------------------------
    // P8.7 — faithful meter accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the current faithful score.
     *
     * @return faithful value in {@code [0, FAITHFUL_MAX]}
     */
    public int getFaithful() { return faithful; } // Read by StunMinigame to scale marker frequency; relayed in PlayerStatePacket

    /**
     * Sets the faithful score, clamping to {@code [0, FAITHFUL_MAX]}.
     *
     * @param v the new faithful score
     */
    public void setFaithful(int v) { faithful = Math.max(0, Math.min(FAITHFUL_MAX, v)); } // Clamp on write; prevents over/underflow

    /**
     * Adjusts the faithful score by {@code delta}, clamping the result to
     * {@code [0, FAITHFUL_MAX]}.
     *
     * @param delta the amount to add (positive) or subtract (negative)
     */
    public void addFaithful(int delta) { setFaithful(faithful + delta); } // Delegates to setFaithful() for consistent clamping

    // -------------------------------------------------------------------------
    // P8.9 — Boss fragment boosts + Radiant Collapse
    // -------------------------------------------------------------------------

    /**
     * P8.9 — Registers a boost-class fragment unlock (EMBER / IRON) on this
     * Wanderer so the combat code can branch on its presence. Silently
     * ignores non-boost unlock types so the call site can pass the raw
     * {@link LoreFragment.AbilityUnlock} value straight from the fragment.
     *
     * <p>Architecture role: Called by {@link #unlockAbility(LoreFragment.AbilityUnlock)}
     * for EMBER and IRON unlock types. Backed by {@link EnumSet} for O(1) membership
     * checks on the per-attack hot path.</p>
     *
     * @param boost the boost ability to add; must not be {@code null}
     */
    public void activateBoost(LoreFragment.AbilityUnlock boost) {
        if (boost == LoreFragment.AbilityUnlock.EMBER
                || boost == LoreFragment.AbilityUnlock.IRON) {
            activeBoosts.add(boost); // Add to the EnumSet; O(1) via bit-field under the hood
        }
        // Non-boost types (NONE, MELEE, DODGE, etc.) are silently ignored
    }

    /**
     * P8.9 — Returns whether the given boost is currently active on this
     * Wanderer. Hot-path query: used once per melee hit and once per incoming
     * damage event.
     *
     * @param boost the boost to query
     * @return {@code true} if the boost has been collected; {@code false} otherwise
     */
    public boolean hasBoost(LoreFragment.AbilityUnlock boost) {
        return activeBoosts.contains(boost); // O(1) EnumSet membership check
    }

    /**
     * P8.9 — Returns an unmodifiable view of the currently active boost set.
     * Intended for HUD readout and debug panels.
     *
     * @return unmodifiable view of active boosts; never {@code null}
     */
    public Set<LoreFragment.AbilityUnlock> getActiveBoosts() {
        return Collections.unmodifiableSet(activeBoosts); // Unmodifiable wrapper: prevents external mutation of the boost set
    }

    /**
     * P8.9 — Returns whether the Radiant Collapse fragment has been collected.
     * The flag lives on {@link GameSession} (process-singleton) so it survives
     * the Player rebuild that happens on level reload.
     *
     * @return {@code true} if RADIANT_COLLAPSE fragment was collected this run
     */
    public boolean isRadiantCollapseUnlocked() {
        return GameSession.getInstance().isRadiantCollapseUnlocked(); // Delegate to session singleton so the flag persists across level reloads
    }

    /**
     * P8.9 — Returns the current Radiant Collapse FSM state.
     *
     * @return the active {@link RadiantState}
     */
    public RadiantState getRadiantState() { return radiantState; } // Read by HUD to show charge/cooldown progress indicator

    /**
     * P8.9 — Returns whether the Radiant Collapse full-arena reveal is currently
     * active. Both clients stamp {@link #radiantActiveUntilMs} from the
     * {@link Protocol#RADIANT_ACTIVE} broadcast and compare against
     * {@link System#currentTimeMillis()} locally, so no per-tick state sync is
     * needed for the visual override.
     *
     * @return {@code true} for the 3-second reveal window
     */
    public boolean isRadiantActive() {
        return System.currentTimeMillis() < radiantActiveUntilMs; // Time-based check: true during the active window
    }

    /**
     * P8.9 — Returns the absolute-ms timestamp when the ACTIVE window ends.
     *
     * @return absolute wall-clock time when the reveal ends, or 0 if inactive
     */
    public long getRadiantActiveUntilMs() { return radiantActiveUntilMs; } // Read by LightRenderer to decide whether to skip the darkness mask

    /**
     * P8.9 — Sets the absolute-ms timestamp when the ACTIVE window ends.
     * Called on the Wanderer when CHARGING → ACTIVE transitions, and on the
     * Apprentice when {@link Protocol#RADIANT_ACTIVE} is received.
     *
     * @param endMs absolute wall-clock timestamp when the reveal ends
     */
    public void setRadiantActiveUntilMs(long endMs) { radiantActiveUntilMs = endMs; } // Volatile write: visible to NetworkIO thread immediately

    /**
     * P8.9 — Returns remaining cooldown lockout in ms, or {@code 0} if SHIFT
     * would be accepted right now. Computed from the entry timestamp rather
     * than a counted-down timer so it survives pauses and frame-rate hiccups.
     *
     * @return remaining cooldown ms, or 0 if not in COOLDOWN
     */
    public long getRadiantCooldownRemainingMs() {
        if (radiantState != RadiantState.COOLDOWN) return 0L; // Not in cooldown: SHIFT is freely available
        long elapsed = System.currentTimeMillis() - radiantStateStartMs; // Time spent in COOLDOWN state
        return Math.max(0L, RADIANT_COOLDOWN_MS - elapsed);              // Remaining ms; clamped at 0
    }

    /**
     * P8.9 — Drives the Radiant Collapse FSM for one tick on the Wanderer
     * client. Callers are expected to gate entry on
     * {@code LevelState.GamePhase.BOSS} and
     * {@link #isRadiantCollapseUnlocked()} so this method can assume both
     * preconditions are met.
     *
     * <p>Architecture role: Called by {@link InputRouter#routeKeyEvent} during
     * the BOSS phase each tick with the current shiftHeld value. Broadcasts
     * {@link Protocol#RADIANT_ACTIVE} to the server on CHARGING → ACTIVE so the
     * Apprentice client can mirror the full-arena-reveal mask override.</p>
     *
     * @param nowMs     current wall-clock time in ms (from System.currentTimeMillis())
     * @param shiftHeld whether SHIFT is held down this tick
     * @return {@code true} if a state transition occurred this tick
     */
    public boolean updateRadiantFsm(long nowMs, boolean shiftHeld) {
        boolean transitioned = false; // Track whether a transition fired; returned to caller for potential logging
        switch (radiantState) {
            case IDLE:
                if (shiftHeld) {                                // SHIFT pressed while IDLE: begin charging
                    radiantState        = RadiantState.CHARGING; // Transition to CHARGING
                    radiantStateStartMs = nowMs;                 // Stamp entry time for the 2-second charge timeout
                    transitioned = true;
                }
                break;

            case CHARGING:
                if (!shiftHeld) {                               // SHIFT released before charge threshold: cancel
                    radiantState        = RadiantState.IDLE;    // Return to IDLE without firing
                    radiantStateStartMs = nowMs;
                    transitioned = true;
                } else if (nowMs - radiantStateStartMs >= RADIANT_CHARGE_MS) { // 2 seconds elapsed: fire!
                    radiantState         = RadiantState.ACTIVE;  // Transition to ACTIVE
                    radiantStateStartMs  = nowMs;                // Stamp ACTIVE entry time for the 3-second timeout
                    radiantActiveUntilMs = nowMs + RADIANT_ACTIVE_MS; // Absolute timestamp when the reveal ends

                    // Broadcast to the Apprentice so its LightRenderer mirrors
                    // the full-arena reveal for the same 3s window.
                    GameSession.getInstance().sendToServer(
                        Protocol.RADIANT_ACTIVE + "|" + radiantActiveUntilMs); // Send endMs so Apprentice can stamp its own radiantActiveUntilMs

                    transitioned = true;
                }
                break;

            case ACTIVE:
                if (nowMs - radiantStateStartMs >= RADIANT_ACTIVE_MS) { // 3 seconds elapsed: end reveal
                    radiantState        = RadiantState.COOLDOWN; // Start the 60-second cooldown
                    radiantStateStartMs = nowMs;                  // Stamp COOLDOWN entry time
                    transitioned = true;
                }
                break;

            case COOLDOWN:
                if (nowMs - radiantStateStartMs >= RADIANT_COOLDOWN_MS) { // 60 seconds elapsed: reset
                    radiantState        = RadiantState.IDLE;  // Return to IDLE; SHIFT is now available again
                    radiantStateStartMs = nowMs;
                    transitioned = true;
                }
                break;
        }
        return transitioned; // True if a state change occurred this tick
    }

    /**
     * Returns the number of lives remaining.
     *
     * @return lives count; starts at 3
     */
    public int getLives() {
        return lives; // Read by HUD to display the life counter
    }

    /**
     * Sets the number of lives.
     *
     * @param lives the new lives count
     */
    public void setLives(int lives) {
        this.lives = lives; // Used by server reconnect to restore saved lives count
    }

    /**
     * Returns whether the player is currently in the death state.
     *
     * @return {@code true} while the death animation is playing
     */
    public boolean isDead() {
        return isDead; // Read by InputRouter to suppress movement during death animation
    }

    /**
     * Sets the death state.
     *
     * @param dead {@code true} to enter the death state
     */
    public void setDead(boolean dead) {
        this.isDead = dead; // Used by server reconnect to restore saved death state
    }

    /**
     * Returns whether a total game over is pending.
     *
     * @return {@code true} when no attempts remain and the game should return to menu
     */
    public boolean isGameOverPending() { return gameOverPending; } // Read by GameStarter to trigger the game-over screen

    /**
     * Sets the game-over pending flag.
     *
     * @param b {@code true} to signal a total game over
     */
    public void setGameOverPending(boolean b) { gameOverPending = b; } // Reset to false when the player restarts from the menu

    /**
     * Returns the total attempts remaining across the entire run.
     *
     * @return total attempts; starts at 3
     */
    public int getTotalAttempts() { return totalAttempts; } // Read by HUD to display the attempt counter

    /**
     * Returns the x-coordinate where the player died.
     *
     * @return death X in world space
     */
    public int getDeathX() {
        return deathX; // Snapshotted in loseLife(); used by render() to draw the death animation at the correct position
    }

    /**
     * Returns the y-coordinate where the player died.
     *
     * @return death Y in world space (clamped to 800)
     */
    public int getDeathY() {
        return deathY; // Snapshotted and clamped in loseLife(); used by render()
    }

    /**
     * Returns the death timer value in milliseconds.
     *
     * @return elapsed ms since death began; 0 if not dead
     */
    public long getDeathTimer() {
        return deathTimer; // Read by render() to compute the fade-out alpha: alpha = 1 - (deathTimer / 1200)
    }

    /**
     * Triggers the collect flash overlay effect for 400 ms.
     *
     * <p>Architecture role: Called by {@link FragmentLibrary#collect(LoreFragment)}
     * to provide immediate visual feedback when the Wanderer picks up a fragment.</p>
     */
    public void triggerCollectFlash() {
        this.collectFlash      = true;                          // Activate the flash overlay
        this.collectFlashStart = System.currentTimeMillis();    // Snapshot start time for elapsed-time check
    }

    /**
     * Returns whether the collect flash is currently active (within 400 ms of the
     * collect event).
     *
     * @return {@code true} while the flash overlay should be visible
     */
    public boolean isCollectFlashActive() {
        if (!collectFlash) return false;                              // No flash in progress: return immediately
        if (System.currentTimeMillis() - collectFlashStart > 400L) { // 400 ms window elapsed
            collectFlash = false;                                      // Auto-clear the flag
            return false;                                             // Flash has expired
        }
        return true; // Flash is active within the 400 ms window
    }

    /**
     * Returns the system time in ms when the collect flash started.
     *
     * @return absolute timestamp of the flash start, or 0 if no flash has occurred
     */
    public long getCollectFlashStart() {
        return collectFlashStart; // Read by GameCanvas to compute flash alpha over the 400 ms window
    }

    // -------------------------------------------------------------------------
    // New field accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the current animation state key.
     *
     * @return the animation state string (e.g. {@code "wanderer_run"})
     */
    public String getAnimState() {
        return animState; // Legacy string in "wanderer_X" format; used by network packets and debug overlays
    }

    /**
     * Sets the animation state key.
     *
     * @param animState the new animation state
     */
    public void setAnimState(String animState) {
        this.animState = animState; // Direct setter; used by network sync to override the animation state
    }

    /**
     * Returns whether the heal flash overlay is active.
     *
     * @return {@code true} if the white flash is showing
     */
    public boolean isHealFlash() {
        return healFlash; // Read by GameCanvas to render the white heal flash overlay
    }

    /**
     * Returns whether the dodge roll is currently active.
     *
     * @return {@code true} while rolling
     */
    public boolean isDodgeActive() {
        return dodgeActive; // Read by PhysicsEngine for dodge-velocity maintenance
    }

    /**
     * Returns whether a projectile charge is currently held.
     *
     * @return {@code true} while the charge key is held
     */
    public boolean isChargeHeld() {
        return chargeHeld; // Read by the animation state machine to keep the "charge" clip while the key is held
    }

    /**
     * Returns the {@link CoreHealthBar} HUD component attached to this player.
     *
     * @return the core health bar; never {@code null}
     */
    public CoreHealthBar getCoreHealthBar() {
        return coreHealthBar; // Read by GameCanvas to render the four Core health indicators
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
        private int[] coreHealth; // Four-element array: coreHealth[i] mirrors Core[i].getHealth()

        /**
         * Constructs a new {@code CoreHealthBar} with all four Core health values
         * initialised to their maximum of 3.
         *
         * <p>Architecture role: Called once by {@link Player#Player(int, int)} at
         * construction time. The array is updated by {@link #setCoreHealth(int, int)}
         * when {@link NetworkProtocol.CoreStatePacket} arrives.</p>
         */
        public CoreHealthBar() {
            this.coreHealth = new int[]{3, 3, 3, 3}; // All four Cores start at 3 hp; updated on Core damage events
        }

        /**
         * Draws the Core health indicators onto the provided graphics context at their
         * fixed HUD position. This stub will later render four labelled health pips or
         * bar segments corresponding to each Core's remaining health.
         *
         * <p>Architecture role: Called each frame by {@link GameCanvas} while the
         * game is in the BOSS phase. The stub preserves the rendering contract for
         * future HUD implementation without breaking existing callers.</p>
         *
         * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
         */
        public void render(Graphics2D g) {
            // Stub — HUD rendering to be implemented in a later phase.
            // When implemented: draw four pip rows or segment bars at a fixed
            // HUD position (e.g. bottom-left of the canvas), one per Core index,
            // filled proportionally to coreHealth[i] / 3.
        }

        /**
         * Returns the current health array for all four Cores.
         *
         * <p>Architecture role: Called by {@link GameCanvas} and debug overlays to
         * read Core health without direct access to the {@link Core} entities.</p>
         *
         * @return a four-element integer array indexed by Core index [0–3]
         */
        public int[] getCoreHealth() {
            return coreHealth; // Direct array reference; callers must not modify; use setCoreHealth() for updates
        }

        /**
         * Updates the stored health value for a specific Core, clamping the value
         * between 0 and 3.
         *
         * <p>Architecture role: Called by {@link GameSession} when a
         * {@link NetworkProtocol.CoreStatePacket} arrives, so the HUD reflects the
         * server-authoritative Core health without polling the Core entities directly.</p>
         *
         * @param coreIndex the zero-based index of the Core to update; must be in [0, 3]
         * @param health    the new health value for that Core
         */
        public void setCoreHealth(int coreIndex, int health) {
            if (coreIndex >= 0 && coreIndex < coreHealth.length) {   // Bounds check: prevent ArrayIndexOutOfBoundsException
                this.coreHealth[coreIndex] = Math.max(0, Math.min(3, health)); // Clamp to [0, 3]: Core max health is 3
            }
        }
    }
}
