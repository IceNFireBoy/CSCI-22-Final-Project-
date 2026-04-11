/**
 * Represents the primary enemy entity in Lumen Architect — a shadow creature whose
 * behaviour shifts dramatically based on ambient light and its proximity to the
 * Wanderer. The {@link CrawlerAI} inner class encapsulates all state-machine logic,
 * keeping the entity data cleanly separated from its decision-making routine.
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
import java.awt.image.BufferedImage;
import java.util.List;

public class DarkCrawler extends Hazard {

    // =========================================================================
    // Constants
    // =========================================================================

    /** Gravitational acceleration applied each tick (pixels per tick squared). */
    private static final float GRAVITY = 0.5f;

    /** Horizontal speed in pixels per tick while in {@link CrawlerState#TRACKING}. */
    private static final float TRACKING_SPEED = 1.8f;

    /** Horizontal speed in pixels per tick while in {@link CrawlerState#AGGRESSION}. */
    private static final float AGGRESSION_SPEED = 5.5f;

    /** Milliseconds of continuous darkness required to transition from DORMANT to TRACKING. */
    private static final long DARK_THRESHOLD_MS = 2000L;

    /** Distance in pixels below which the crawler enters AGGRESSION from TRACKING. */
    private static final double AGGRO_DISTANCE = 120.0;

    /** Distance in pixels above which the crawler drops from AGGRESSION back to TRACKING. */
    private static final double DEAGGRO_DISTANCE = 200.0;

    /** Duration of the stun effect in milliseconds. */
    private static final long STUN_DURATION_MS = 4000L;

    /** Duration of the death animation in milliseconds before entity removal. */
    private static final long DEATH_ANIM_MS = 500L;

    /** Horizontal knockback applied to the Wanderer on contact in Act 3+. */
    private static final int KNOCKBACK_PX = 40;

    /** Duration of invincibility granted to the Wanderer after a crawler hit. */
    private static final long INVINCIBLE_DURATION_MS = 1000L;

    // =========================================================================
    // Enum — CrawlerState
    // =========================================================================

    /**
     * Enumerates the discrete behavioural states the {@link CrawlerAI} can occupy.
     * Transitions between states are driven by light exposure, distance to the
     * Wanderer, and stun events.
     */
    public enum CrawlerState {

        /**
         * The crawler is idle and not yet aware of the Wanderer. It remains motionless
         * and deals no damage in this state.
         */
        DORMANT,

        /**
         * The crawler has detected the Wanderer and is navigating toward them. Movement
         * speed is moderate and it does not yet attack.
         */
        TRACKING,

        /**
         * The crawler is in close range and actively attempting to deal damage. Movement
         * speed and attack frequency are at their peak.
         */
        AGGRESSION,

        /**
         * The crawler has been hit by a light-based attack and is temporarily
         * incapacitated. All movement and attacks are suspended until the stun expires.
         */
        STUN
    }

    // =========================================================================
    // Inner class — CrawlerAI
    // =========================================================================

    /**
     * Encapsulates the state-machine logic that drives a {@link DarkCrawler}'s
     * decision-making each game tick. Separating AI logic into its own class keeps
     * the parent entity clean and makes the behaviour easier to test and extend.
     *
     * <p>State transitions:
     * <ul>
     *   <li>DORMANT → TRACKING: {@code darkTimer} exceeds {@value #DARK_THRESHOLD_MS} ms.</li>
     *   <li>TRACKING → AGGRESSION: distance to Wanderer falls below {@value #AGGRO_DISTANCE} px.</li>
     *   <li>AGGRESSION → TRACKING: distance to Wanderer exceeds {@value #DEAGGRO_DISTANCE} px.</li>
     *   <li>Any → DORMANT: light overlaps this crawler (resets {@code darkTimer}).</li>
     *   <li>Any → STUN: hit by charged projectile (via {@link #applyStun()}).</li>
     *   <li>STUN → previous state: after {@value #STUN_DURATION_MS} ms.</li>
     * </ul>
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-21
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public class CrawlerAI {

        /**
         * The current behavioural state of this AI instance. Drives movement speed,
         * attack triggers, and animation selection.
         */
        private CrawlerState state;

        /**
         * Accumulates the number of milliseconds the crawler has spent in darkness
         * since last transitioning into {@link CrawlerState#DORMANT}. Used to trigger
         * the DORMANT → TRACKING transition after extended dark exposure.
         */
        private long darkTimer;

        /**
         * Counts down the remaining milliseconds of the active stun. When this value
         * reaches zero the crawler transitions out of {@link CrawlerState#STUN}.
         */
        private long stunTimer;

        /**
         * The state the crawler occupied before being stunned, so it can return to
         * that state once the stun expires.
         */
        private CrawlerState previousState;

        /**
         * Constructs a new {@code CrawlerAI} initialised to the
         * {@link CrawlerState#DORMANT} state with all timers reset.
         */
        public CrawlerAI() {
            this.state = CrawlerState.DORMANT;
            this.darkTimer = 0L;
            this.stunTimer = 0L;
            this.previousState = CrawlerState.DORMANT;
        }

        /**
         * Evaluates the current light and proximity conditions and advances the state
         * machine accordingly. Should be called once per game tick from the owning
         * {@link DarkCrawler#update(long)} method.
         *
         * @param deltaMs           the time elapsed since the last tick, in milliseconds
         * @param inLight           {@code true} if the crawler is currently inside a
         *                          lit area; {@code false} if it is in darkness
         * @param distanceToWanderer the straight-line distance in pixels between this
         *                          crawler and the Wanderer entity
         */
        public void update(long deltaMs, boolean inLight, double distanceToWanderer) {
            // --- STUN countdown (no other transitions allowed while stunned) ---
            if (state == CrawlerState.STUN) {
                stunTimer -= deltaMs;
                if (stunTimer <= 0) {
                    stunTimer = 0;
                    transitionTo(previousState);
                }
                return;
            }

            // --- Light check: any active state → DORMANT ---
            if (inLight) {
                darkTimer = 0L;
                if (state != CrawlerState.DORMANT) {
                    transitionTo(CrawlerState.DORMANT);
                }
                return;
            }

            // --- In darkness: accumulate dark timer ---
            darkTimer += deltaMs;

            // --- DORMANT → TRACKING ---
            if (state == CrawlerState.DORMANT) {
                if (darkTimer >= DARK_THRESHOLD_MS) {
                    transitionTo(CrawlerState.TRACKING);
                }
                return;
            }

            // --- TRACKING → AGGRESSION ---
            if (state == CrawlerState.TRACKING) {
                if (distanceToWanderer < AGGRO_DISTANCE) {
                    transitionTo(CrawlerState.AGGRESSION);
                }
                return;
            }

            // --- AGGRESSION → TRACKING ---
            if (state == CrawlerState.AGGRESSION) {
                if (distanceToWanderer > DEAGGRO_DISTANCE) {
                    transitionTo(CrawlerState.TRACKING);
                }
            }
        }

        /**
         * Applies a stun to this crawler, saving the current state and transitioning
         * to {@link CrawlerState#STUN}. Called when a charged projectile hits.
         */
        public void applyStun() {
            if (state != CrawlerState.STUN) {
                previousState = state;
            }
            stunTimer = STUN_DURATION_MS;
            transitionTo(CrawlerState.STUN);
        }

        /**
         * Transitions to the given state and triggers audio changes on the owning
         * {@link DarkCrawler}.
         *
         * @param newState the state to transition to
         */
        private void transitionTo(CrawlerState newState) {
            CrawlerState oldState = this.state;
            this.state = newState;
            onStateChanged(oldState, newState);
        }

        /**
         * Returns the current behavioural state of this AI.
         *
         * @return the active {@link CrawlerState}
         */
        public CrawlerState getState() {
            return state;
        }

        /**
         * Returns the accumulated darkness timer in milliseconds.
         *
         * @return the dark timer value
         */
        public long getDarkTimer() {
            return darkTimer;
        }

        /**
         * Returns the remaining stun timer in milliseconds.
         *
         * @return the stun timer value
         */
        public long getStunTimer() {
            return stunTimer;
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** The AI controller that manages this crawler's state-machine logic. */
    private CrawlerAI ai;

    /** Horizontal velocity in pixels per tick. */
    private float velX;

    /** Vertical velocity in pixels per tick. Positive values move downward. */
    private float velY;

    /** Whether the crawler is currently standing on a solid surface. */
    private boolean grounded;

    /** Whether the crawler is playing its death animation before removal. */
    private boolean dying;

    /** Countdown timer for the death animation in milliseconds. */
    private long deathTimer;

    /** The current animation sprite name, derived from crawler state. */
    private String animState;

    /** Reference to the Wanderer for distance and contact checks. */
    private Player player;

    /** Reference to the current level state for phase-dependent behaviour. */
    private LevelState levelState;

    /** Reference to the audio manager for state-change sound effects. */
    private AudioManager audioManager;

    /** Reference to the animation controller for sprite frame retrieval. */
    // AnimationController removed — using SpriteLoader
    private Object animController; // stub

    /** The list of platforms this crawler can collide with. */
    private List<Platform> platforms;

    /** Elapsed game time in milliseconds, used for animation frame selection. */
    private long gameTimeMs;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code DarkCrawler} at the given world position, initialised
     * with default dimensions (32x32), 1 contact damage, and 3 health. A fresh
     * {@link CrawlerAI} is created and attached automatically.
     *
     * @param x the x-coordinate of the crawler's left edge in world space
     * @param y the y-coordinate of the crawler's top edge in world space
     */
    public DarkCrawler(int x, int y) {
        super(x, y, 32, 32, 1, 3);
        this.ai = new CrawlerAI();
        this.velX = 0f;
        this.velY = 0f;
        this.grounded = false;
        this.dying = false;
        this.deathTimer = 0L;
        this.animState = "crawler_dormant";
        this.gameTimeMs = 0L;
    }

    // -------------------------------------------------------------------------
    // Dependency injection
    // -------------------------------------------------------------------------

    /**
     * Injects the runtime dependencies required for full AI, physics, and rendering
     * behaviour. Must be called after construction and before the first
     * {@link #update(long)} tick.
     *
     * @param player        the Wanderer entity; must not be {@code null}
     * @param levelState    the current level state; must not be {@code null}
     * @param audioManager  the audio manager; must not be {@code null}
     * @param animController the animation controller; must not be {@code null}
     * @param platforms     the list of platforms for collision; must not be {@code null}
     */
    public void init(Player player, LevelState levelState, AudioManager audioManager,
                     Object animController, List<Platform> platforms) {
        this.player = player;
        this.levelState = levelState;
        this.audioManager = audioManager;
        this.animController = animController;
        this.platforms = platforms;
    }

    /**
     * Sets the player reference for AI distance checks and contact damage.
     *
     * @param player the Wanderer entity
     */
    public void setPlayer(Player player) {
        this.player = player;
    }

    // -------------------------------------------------------------------------
    // GameElement overrides
    // -------------------------------------------------------------------------

    /**
     * Updates this crawler's position, animation, and AI state for the current tick.
     * Delegates state-machine decisions to the embedded {@link CrawlerAI}, then
     * applies movement, gravity, platform collision, and contact damage checks.
     *
     * @param deltaMs the time elapsed since the last update, in milliseconds
     */
    @Override
    public void update(long deltaMs) {
        if (!active) {
            return;
        }

        gameTimeMs += deltaMs;

        // --- Death animation countdown ---
        if (dying) {
            deathTimer -= deltaMs;
            if (deathTimer <= 0) {
                setActive(false);
            }
            return;
        }

        // --- Guard: player reference must be injected before AI runs ---
        if (player == null) return;

        // --- Compute distance to Wanderer ---
        double dx = (player.getX() + 12) - (x + width / 2.0);
        double dy = (player.getY() + 16) - (y + height / 2.0);
        double distanceToWanderer = Math.sqrt(dx * dx + dy * dy);

        // --- Determine if this crawler is in a lit area ---
        // inLight is determined externally and passed here; for now we check if the
        // Wanderer's light radius overlaps this crawler. The light system will set
        // this via the update call from the game loop. We use a simple heuristic:
        // the crawler is "in light" if its centre is within the light source radius.
        // This will be replaced by the actual light system check when available.
        boolean inLight = isInLightRadius();

        // --- Update AI state machine ---
        ai.update(deltaMs, inLight, distanceToWanderer);

        CrawlerState currentState = ai.getState();

        // --- Movement based on state ---
        if (currentState == CrawlerState.STUN) {
            velX = 0f;
        } else if (currentState == CrawlerState.TRACKING) {
            velX = (dx > 0) ? TRACKING_SPEED : (dx < 0) ? -TRACKING_SPEED : 0f;
        } else if (currentState == CrawlerState.AGGRESSION) {
            velX = (dx > 0) ? AGGRESSION_SPEED : (dx < 0) ? -AGGRESSION_SPEED : 0f;
        } else {
            // DORMANT: no movement
            velX = 0f;
        }

        // --- Gravity ---
        velY += GRAVITY;

        // --- Apply velocity ---
        x += (int) velX;
        y += (int) velY;

        // --- Platform collision ---
        grounded = false;
        resolvePlatformCollisions();

        // --- Update animation state ---
        updateAnimState();

        // --- Contact damage check ---
        checkContactDamage();
    }

    /**
     * Renders this crawler onto the provided graphics context. Draws the appropriate
     * sprite frame based on the current {@link CrawlerState}, plus state-dependent
     * overlays: a red aperture glow for TRACKING/AGGRESSION and a white ring for STUN.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    @Override
    public void render(Graphics2D g) {
        if (!active) {
            return;
        }

        // --- Draw sprite frame using SpriteLoader ---
        java.awt.image.BufferedImage frame = SpriteLoader.getInstance().load(
            "resources/sprites/crawler/crawler_" + animState + ".png");
        if (frame != null) {
            g.drawImage(frame, x, y, width, height, null);
        }

        CrawlerState currentState = ai.getState();
        Composite originalComposite = g.getComposite();
        Stroke originalStroke = g.getStroke();

        // --- Aperture glow overlay ---
        int centreX = x + width / 2;
        int centreY = y + height / 2;

        if (currentState == CrawlerState.TRACKING) {
            // Small red ellipse, opacity 0.3, colour #8b0000
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
            g.setColor(new Color(0x8b, 0x00, 0x00));
            g.fillOval(centreX - 8, centreY - 6, 16, 12);
        } else if (currentState == CrawlerState.AGGRESSION) {
            // Larger red ellipse, opacity 0.7, colour #e05c5c
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
            g.setColor(new Color(0xe0, 0x5c, 0x5c));
            g.fillOval(centreX - 14, centreY - 10, 28, 20);
        }

        // --- Stun ring overlay ---
        if (currentState == CrawlerState.STUN) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2f));
            g.drawOval(x - 2, y - 2, width + 4, height + 4);
        }

        // --- Restore graphics state ---
        g.setComposite(originalComposite);
        g.setStroke(originalStroke);
    }

    // -------------------------------------------------------------------------
    // Damageable override
    // -------------------------------------------------------------------------

    /**
     * Reduces this crawler's health by the specified amount. If health drops to zero
     * or below, triggers the death sequence: plays the death animation and sound,
     * schedules removal after {@value #DEATH_ANIM_MS} ms, and — if in the boss
     * phase — heals the Wanderer by 1 hp.
     *
     * @param amount the amount of damage to apply; must be non-negative
     */
    @Override
    public void takeDamage(int amount) {
        if (!active || dying) {
            return;
        }

        health -= amount;
        if (health <= 0) {
            health = 0;
            dying = true;
            deathTimer = DEATH_ANIM_MS;
            animState = "crawler_death";

            // Play death sound
            if (audioManager != null) {
                audioManager.stopLoopSFX();
                audioManager.playSFX("sfx_crawler_death");
            }

            // Boss phase: killing a crawler heals the Wanderer by 1
            if (levelState != null
                    && levelState.currentPhase == LevelState.GamePhase.BOSS) {
                player.heal(1);
            }
        }
    }

    /**
     * Applies a stun to this crawler, typically from a charged projectile hit.
     * Delegates to the AI's {@link CrawlerAI#applyStun()} method.
     */
    public void applyStun() {
        if (!dying) {
            ai.applyStun();
        }
    }

    // -------------------------------------------------------------------------
    // Contact damage
    // -------------------------------------------------------------------------

    /**
     * Checks whether this crawler's bounds intersect the Wanderer's bounds while
     * in {@link CrawlerState#AGGRESSION}. If so, applies act-dependent damage:
     * instant kill in Act 2, or 1 damage with knockback in Act 3+.
     */
    private void checkContactDamage() {
        if (player == null || levelState == null) {
            return;
        }

        CrawlerState currentState = ai.getState();
        if (currentState != CrawlerState.AGGRESSION) {
            return;
        }

        if (player.isInvincible() || !player.isAlive()) {
            return;
        }

        Rectangle crawlerBounds = getBounds();
        Rectangle playerBounds = player.getBounds();

        if (!crawlerBounds.intersects(playerBounds)) {
            return;
        }

        LevelState.GamePhase phase = levelState.currentPhase;

        if (phase == LevelState.GamePhase.ACT2) {
            // Instant kill in Act 2
            player.setHealth(0);
        } else if (phase == LevelState.GamePhase.ACT3
                || phase == LevelState.GamePhase.BOSS
                || phase == LevelState.GamePhase.FINAL_CORRIDOR) {
            // 1 damage + knockback in Act 3+
            player.takeDamage(1);

            // Knockback: push Wanderer away from crawler
            int direction = (player.getX() >= x) ? 1 : -1;
            player.setX(player.getX() + direction * KNOCKBACK_PX);
        }

        // Grant invincibility frames
        player.setInvincible(true);
        player.setInvincibleTimer(System.currentTimeMillis() + INVINCIBLE_DURATION_MS);
    }

    // -------------------------------------------------------------------------
    // Platform collision
    // -------------------------------------------------------------------------

    /**
     * Resolves collisions between this crawler and all platforms, applying simple
     * AABB push-out for grounding and horizontal blocking. The crawler does not
     * path-find — it walks toward the Wanderer's x and falls off ledges.
     */
    private void resolvePlatformCollisions() {
        if (platforms == null) {
            return;
        }

        Rectangle cb = getBounds();

        for (Platform pl : platforms) {
            if (!pl.isSolid() || !pl.isActive()) {
                continue;
            }

            Rectangle plb = pl.getBounds();
            if (!cb.intersects(plb)) {
                continue;
            }

            // Penetration depths
            int overlapTop    = (cb.y + cb.height) - plb.y;
            int overlapBottom = (plb.y + plb.height) - cb.y;
            int overlapLeft   = (cb.x + cb.width)  - plb.x;
            int overlapRight  = (plb.x + plb.width) - cb.x;

            int minOverlap = Math.min(Math.min(overlapTop, overlapBottom),
                                      Math.min(overlapLeft, overlapRight));

            if (minOverlap == overlapTop && overlapTop > 0) {
                // Landed on top of platform
                y = plb.y - height;
                velY = 0f;
                grounded = true;
            } else if (minOverlap == overlapBottom && overlapBottom > 0) {
                // Hit underside of platform
                y = plb.y + plb.height;
                velY = 0f;
            } else if (minOverlap == overlapLeft && overlapLeft > 0) {
                // Blocked on the left side of platform
                x = plb.x - width;
                velX = 0f;
            } else if (minOverlap == overlapRight && overlapRight > 0) {
                // Blocked on the right side of platform
                x = plb.x + plb.width;
                velX = 0f;
            }

            // Refresh bounds after resolution for subsequent platform checks
            cb = getBounds();
        }
    }

    // -------------------------------------------------------------------------
    // Audio state changes
    // -------------------------------------------------------------------------

    /**
     * Called by the {@link CrawlerAI} when a state transition occurs. Stops the
     * previous audio loop and starts the appropriate one for the new state.
     *
     * @param oldState the state being exited
     * @param newState the state being entered
     */
    private void onStateChanged(CrawlerState oldState, CrawlerState newState) {
        if (audioManager == null) {
            return;
        }

        // Stop previous loop
        audioManager.stopLoopSFX();

        // Start new audio
        switch (newState) {
            case DORMANT:
                audioManager.loopSFX("sfx_crawler_dormant");
                break;
            case TRACKING:
                audioManager.loopSFX("sfx_crawler_tracking");
                break;
            case AGGRESSION:
                audioManager.loopSFX("sfx_crawler_aggression");
                break;
            case STUN:
                // One-shot, no loop
                audioManager.playSFX("sfx_crawler_stun");
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Animation helpers
    // -------------------------------------------------------------------------

    /**
     * Updates the {@link #animState} string to match the current {@link CrawlerState},
     * mapping each state to its registered animation name in the
     * {@link AnimationController}.
     */
    private void updateAnimState() {
        if (dying) {
            animState = "crawler_death";
            return;
        }

        switch (ai.getState()) {
            case DORMANT:    animState = "crawler_dormant"; break;
            case TRACKING:   animState = "crawler_track";   break;
            case AGGRESSION: animState = "crawler_aggress"; break;
            case STUN:       animState = "crawler_stun";    break;
        }
    }

    // -------------------------------------------------------------------------
    // Light detection (placeholder)
    // -------------------------------------------------------------------------

    /** Whether this crawler is currently illuminated, set by the lighting system. */
    private boolean externalInLight = false;

    /**
     * Determines whether this crawler is currently within a light source radius.
     * Reads the value set by the lighting system via {@link #setInLight(boolean)}.
     *
     * @return {@code true} if the crawler is inside a lit area
     */
    private boolean isInLightRadius() {
        return externalInLight;
    }

    /**
     * Sets the external light state for this crawler. Called by the game loop's
     * lighting pass before the update tick.
     *
     * @param inLight {@code true} if the crawler is within a light radius
     */
    public void setInLight(boolean inLight) {
        this.externalInLight = inLight;
    }

    // -------------------------------------------------------------------------
    // Accessor
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link CrawlerAI} instance managing this crawler's behaviour.
     *
     * @return the AI controller; never {@code null}
     */
    public CrawlerAI getAI() {
        return ai;
    }
}
