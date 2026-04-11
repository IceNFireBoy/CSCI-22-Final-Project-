/**
 * Drives all movement and gravity simulation for the Lumen Architect game world,
 * updating the {@link entities.Player}'s position each tick based on velocity,
 * gravitational acceleration, and the results of collision queries against the active
 * entity list. Separating physics from entity logic keeps each class focused on a
 * single responsibility and makes it easier to tune movement constants without
 * touching entity or renderer code.
 *
 * <p>The engine runs on a fixed 16 ms timestep. All velocity constants are expressed
 * in pixels per tick so that physics behaviour remains independent of the host
 * machine's frame timing. The game loop thread is responsible for calling
 * {@link #update(long, Player, List)} at the correct 60 fps cadence.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */


import java.util.List;

public class PhysicsEngine {

    // -------------------------------------------------------------------------
    // Constants — physics
    // -------------------------------------------------------------------------

    /** Length of one physics tick in milliseconds (targets 60 fps). */
    private static final int FIXED_TIMESTEP_MS = 16;

    /**
     * Downward acceleration added to {@code velY} every tick while the Wanderer is
     * airborne. Positive because the y-axis increases downward.
     */
    private static final float GRAVITY = 0.5f;

    /**
     * Maximum downward speed the Wanderer can reach due to gravity, in pixels per tick.
     * Prevents runaway acceleration during long falls.
     */
    private static final float TERMINAL_VELOCITY = 18f;

    // -------------------------------------------------------------------------
    // Constants — movement
    // -------------------------------------------------------------------------

    /** Horizontal walk speed in pixels per tick. */
    public static final float WALK_SPEED = 4f;

    /**
     * Initial upward velocity (negative = up) applied when a jump is started,
     * in pixels per tick.
     */
    public static final float JUMP_VELOCITY = -12f;

    /**
     * Maximum number of consecutive jumps allowed before the Wanderer must touch the
     * ground. Reset to zero on grounding.
     */
    public static final int MAX_CONSECUTIVE_JUMPS = 2;

    // -------------------------------------------------------------------------
    // Constants — dodge roll
    // -------------------------------------------------------------------------

    /** Horizontal speed boost applied when a dodge roll starts, in pixels per tick. */
    private static final float DODGE_BOOST = 10f;

    /** How long a dodge roll lasts, in milliseconds. */
    private static final long DODGE_DURATION_MS = 500L;

    /** Minimum time between dodge rolls, in milliseconds. */
    private static final long DODGE_COOLDOWN_MS = 3000L;

    // -------------------------------------------------------------------------
    // Constants — wall cling
    // -------------------------------------------------------------------------

    /**
     * Maximum downward speed while clinging to a wall and holding the direction key,
     * in pixels per tick.
     */
    private static final float WALL_CLING_FALL_SPEED = 1f;

    // -------------------------------------------------------------------------
    // Constants — shadow dash
    // -------------------------------------------------------------------------

    /** Distance of a shadow dash teleport, in pixels. */
    private static final int SHADOW_DASH_DISTANCE = 80;

    /** Minimum time between shadow dashes, in milliseconds. */
    private static final long SHADOW_DASH_COOLDOWN_MS = 5000L;

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    /** The collision resolver used to push the player out of overlapping platforms. */
    private final CollisionDetector collisionDetector;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a {@code PhysicsEngine} and its internal {@link CollisionDetector}.
     * No mutable simulation state is kept here; all data lives on the entities.
     */
    public PhysicsEngine() {
        this.collisionDetector = new CollisionDetector();
    }

    // -------------------------------------------------------------------------
    // Per-tick update
    // -------------------------------------------------------------------------

    /**
     * Runs one physics simulation step for the player against the provided entity list.
     *
     * <ol>
     *   <li>Expires any completed dodge-roll invincibility window.</li>
     *   <li>Applies gravity to vertical velocity if the player is airborne.</li>
     *   <li>Integrates velocity into position.</li>
     *   <li>Resets {@code grounded} and {@code wallTouching} flags.</li>
     *   <li>Resolves platform collisions via {@link CollisionDetector}.</li>
     *   <li>Resets the consecutive-jump counter when the player has landed.</li>
     * </ol>
     *
     * @param deltaMs  elapsed milliseconds since the last call; the engine uses the
     *                 fixed {@value #FIXED_TIMESTEP_MS} ms constant for all physics
     *                 calculations regardless of this value
     * @param player   the {@link Player} whose physics state should be stepped;
     *                 must not be {@code null}
     * @param elements the full list of active {@link GameElement} instances to test
     *                 collisions against; must not be {@code null}
     */
    public void update(long deltaMs, Player player, List<GameElement> elements) {
        long now = System.currentTimeMillis();

        // 1. Expire dodge-roll invincibility once the duration has elapsed.
        if (player.isDodging() && now >= player.getDodgeEndTime()) {
            player.setDodging(false);
            player.setInvincible(false);
        }

        // 2. Apply gravity only if the player is airborne.
        if (!player.isGrounded()) {
            applyGravity(player);
        } else {
            player.setVelY(0f);
        }

        // 3. Integrate velocity into position (round to nearest pixel).
        player.setX(player.getX() + Math.round(player.getVelX()));
        player.setY(player.getY() + Math.round(player.getVelY()));

        // 4. Reset wallTouching; use groundedThisTick to avoid single-frame flicker.
        player.setWallTouching(false);
        boolean groundedThisTick = false;

        // 5. Resolve collisions with every active platform in the element list.
        for (GameElement el : elements) {
            if (!el.isActive()) continue;
            if (el instanceof Platform) {
                if (collisionDetector.checkPlayerPlatform(player, (Platform) el)) {
                    if (player.isGrounded()) {
                        groundedThisTick = true;
                    }
                }
            }
        }

        // 6. If no top collision occurred this tick, player is airborne.
        if (!groundedThisTick) {
            player.setGrounded(false);
        }

        // 7. Reset the consecutive-jump counter the moment the player lands.
        if (player.isGrounded()) {
            player.setConsecutiveJumps(0);
        }

        // 8. Fall death — if player falls below the screen, deduct a life.
        if (player.getY() > 900) {
            player.loseLife();
        }
    }

    // -------------------------------------------------------------------------
    // Gravity
    // -------------------------------------------------------------------------

    /**
     * Adds {@value #GRAVITY} to the player's vertical velocity if they are airborne,
     * then clamps the result to {@value #TERMINAL_VELOCITY} pixels per tick.
     * This method is called by {@link #update} before position integration.
     *
     * @param player the {@link Player} whose vertical velocity should be increased;
     *               must not be {@code null}
     */
    public void applyGravity(Player player) {
        if (!player.isGrounded()) {
            float newVelY = player.getVelY() + GRAVITY;
            player.setVelY(Math.min(newVelY, TERMINAL_VELOCITY));
        }
    }

    // -------------------------------------------------------------------------
    // Movement actions — called by InputRouter in response to WASD input
    // -------------------------------------------------------------------------

    /**
     * Sets the player's horizontal velocity to {@value #WALK_SPEED} in the given
     * direction and updates the facing direction.
     *
     * @param player    the {@link Player} to move; must not be {@code null}
     * @param direction {@code 1} to walk right, {@code -1} to walk left
     */
    public void walk(Player player, int direction) {
        player.setFacingDirection(direction);
        player.setVelX(WALK_SPEED * direction);
    }

    /**
     * Zeroes the player's horizontal velocity when the movement key is released.
     *
     * @param player the {@link Player} to stop; must not be {@code null}
     */
    public void stopWalking(Player player) {
        player.setVelX(0f);
    }

    /**
     * Applies an upward impulse if the player is grounded or within their consecutive-
     * jump allowance. The jump count is incremented; it resets when the player lands.
     *
     * @param player the {@link Player} to jump; must not be {@code null}
     */
    public void jump(Player player) {
        if (player.isGrounded() || player.getConsecutiveJumps() < MAX_CONSECUTIVE_JUMPS) {
            player.setVelY(JUMP_VELOCITY);
            player.setGrounded(false);
            player.setConsecutiveJumps(player.getConsecutiveJumps() + 1);
        }
    }

    // -------------------------------------------------------------------------
    // Special movement — dodge roll
    // -------------------------------------------------------------------------

    /**
     * Initiates a dodge roll if the player has the dodge ability and the cooldown has
     * elapsed. Applies a horizontal speed boost in the facing direction, marks the
     * player as dodging and invincible for {@value #DODGE_DURATION_MS} ms, and starts
     * the {@value #DODGE_COOLDOWN_MS} ms cooldown.
     *
     * @param player the {@link Player} to roll; must not be {@code null}
     */
    public void dodgeRoll(Player player) {
        if (!player.isHasDodge()) return;

        long now = System.currentTimeMillis();
        if (now < player.getDodgeCooldownEnd()) return;

        player.setVelX(DODGE_BOOST * player.getFacingDirection());
        player.setDodging(true);
        player.setInvincible(true);
        player.setDodgeEndTime(now + DODGE_DURATION_MS);
        player.setInvincibleTimer(now + DODGE_DURATION_MS);
        player.setDodgeCooldownEnd(now + DODGE_COOLDOWN_MS);
    }

    // -------------------------------------------------------------------------
    // Special movement — wall cling
    // -------------------------------------------------------------------------

    /**
     * Clamps the player's downward fall speed to {@value #WALL_CLING_FALL_SPEED}
     * pixels per tick while they are pressing into a wall and holding the wall-cling
     * ability. Should be called each tick that the relevant direction key is held.
     *
     * @param player the {@link Player} to cling; must not be {@code null}
     */
    public void wallCling(Player player) {
        if (!player.isHasWallCling()) return;
        if (player.isWallTouching() && !player.isGrounded()) {
            if (player.getVelY() > WALL_CLING_FALL_SPEED) {
                player.setVelY(WALL_CLING_FALL_SPEED);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Special movement — shadow dash
    // -------------------------------------------------------------------------

    /**
     * Teleports the player {@value #SHADOW_DASH_DISTANCE} pixels in their facing
     * direction if the shadow-dash ability is unlocked and the cooldown has elapsed.
     * The teleport is instantaneous (no animation arc); the renderer is responsible
     * for playing the visual effect.
     *
     * @param player the {@link Player} to dash; must not be {@code null}
     */
    public void shadowDash(Player player) {
        if (!player.isHasShadowDash()) return;

        long now = System.currentTimeMillis();
        if (now < player.getShadowDashCooldownEnd()) return;

        player.setX(player.getX() + SHADOW_DASH_DISTANCE * player.getFacingDirection());
        player.setShadowDashCooldownEnd(now + SHADOW_DASH_COOLDOWN_MS);
    }
}
