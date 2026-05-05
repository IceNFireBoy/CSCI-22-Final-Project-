/**
 Stateless physics engine driving movement and gravity simulation. Updates Player
 position each tick based on velocity, gravity, and collision results against active
 platforms. Uses fixed 16 ms timestep; all velocity constants are pixels per tick.

 Engine holds no mutable state; all simulation state (position, velocity, grounded,
 etc.) lives on Player. Called by GameStarter once per tick during platforming phases.
 */

import java.util.List; // List interface for the elements parameter in update(); allows iteration over all active entities

public class PhysicsEngine { // Stateless physics engine; all simulation state lives on Player; called once per 16 ms tick

    // -------------------------------------------------------------------------
    // Constants — physics
    // -------------------------------------------------------------------------

    private static final int FIXED_TIMESTEP_MS = 16; // 16 ms per tick (60 fps)

    private static final float GRAVITY = 0.5f; // Downward acceleration per tick

    private static final float TERMINAL_VELOCITY = 18f; // Max fall speed (px/tick)

    /** Y-coordinate threshold below which the Wanderer is considered to have fallen off the world. */
    private static final int FALL_DEATH_Y = 900; // 132 px below the 768 px canvas; guarantees the player is off-screen before loseLife() fires

    // -------------------------------------------------------------------------
    // Constants — movement
    // -------------------------------------------------------------------------

    public static final float WALK_SPEED = 4f; // Horizontal walk speed (px/tick)

    public static final float JUMP_VELOCITY = -12f; // Initial upward velocity on jump

    public static final int MAX_CONSECUTIVE_JUMPS = 2; // Allows double-jump (with DODGE ability)

    // -------------------------------------------------------------------------
    // Constants — dodge roll
    // -------------------------------------------------------------------------

    private static final float DODGE_BOOST = 10f; // Dodge roll speed (px/tick)
    private static final long DODGE_DURATION_MS = 500L; // Invincibility window (ms)
    private static final long DODGE_COOLDOWN_MS = 3000L; // Dodge cooldown (ms)

    // -------------------------------------------------------------------------
    // Constants — wall cling
    // -------------------------------------------------------------------------

    private static final float WALL_CLING_FALL_SPEED = 1f; // Wall slide speed (px/tick)

    // -------------------------------------------------------------------------
    // Constants — shadow dash
    // -------------------------------------------------------------------------

    private static final int SHADOW_DASH_DISTANCE = 80; // Teleport distance (px)
    private static final long SHADOW_DASH_COOLDOWN_MS = 5000L; // Shadow dash cooldown (ms)

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    private final CollisionDetector collisionDetector; // Stateless collision resolver

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public PhysicsEngine() { // Creates CollisionDetector dependency
        this.collisionDetector = new CollisionDetector();        // Instantiate the stateless collision resolver; held for the session lifetime
    }

    // -------------------------------------------------------------------------
    // Per-tick update
    // -------------------------------------------------------------------------

    public void update(long deltaMs, Player player, List<GameElement> elements) { // Main physics step; called every 16 ms during platforming phases
        long now = System.currentTimeMillis();                                     // Capture current wall-clock time once for dodge expiry and cooldown comparisons

        // 1. Expire dodge-roll invincibility once the duration has elapsed.
        if (player.isDodging() && now >= player.getDodgeEndTime()) {              // Check if the player is still in a dodge and the dodge window has ended
            player.setDodging(false);                                              // Clear the dodging flag so normal physics resume
            player.setInvincible(false);                                           // Clear invincibility: the player can take damage again
        }

        // 2. Apply gravity only if the player is airborne.
        if (!player.isGrounded()) {                                               // Only apply gravity when airborne; grounded players have velY zeroed by collision resolution
            applyGravity(player);                                                  // Add GRAVITY to velY; clamp to TERMINAL_VELOCITY
        } else {                                                                   // Player is on a platform: cancel any residual downward velocity
            player.setVelY(0f);                                                   // Zero velY when grounded to prevent accumulation that would push through platforms
        }

        // 3. Integrate velocity into position (round to nearest pixel).
        player.setX(player.getX() + Math.round(player.getVelX()));               // Advance x by rounded velX; Math.round prevents sub-pixel drift accumulation
        player.setY(player.getY() + Math.round(player.getVelY()));               // Advance y by rounded velY; same reasoning

        // 4. Reset wallTouching; use groundedThisTick to avoid single-frame flicker.
        player.setWallTouching(false);                                            // Reset each tick so the flag reflects this tick's collisions only
        boolean groundedThisTick = false;                                         // Local flag tracking whether any platform collision set grounded=true this tick

        // 5. Resolve collisions with every active platform in the element list.
        for (GameElement el : elements) {                                          // Iterate all active entities in the world
            if (!el.isActive()) continue;                                          // Skip deactivated entities: they are not solid
            if (el instanceof Platform) {                                          // Only platforms participate in player-platform collision resolution
                if (collisionDetector.checkPlayerPlatform(player, (Platform) el)) { // Test and resolve AABB overlap; returns true if a solid collision occurred
                    if (player.isGrounded()) {                                      // If the collision resolution set grounded=true on the player (top-face hit)
                        groundedThisTick = true;                                    // Latch the per-tick grounded flag; used in step 6 to avoid one-frame flicker
                    }
                }
            }
        }

        // 6. If no top collision occurred this tick, player is airborne.
        if (!groundedThisTick) {                                                  // No platform set grounded=true this tick: player has left all platforms
            player.setGrounded(false);                                            // Mark airborne: gravity will apply from the next tick onwards
        }

        // 7. Reset the consecutive-jump counter the moment the player lands.
        if (player.isGrounded()) {                                                // Player is now grounded (landed on a platform or stayed on one)
            player.setConsecutiveJumps(0);                                        // Reset jump count: the player can jump again (up to MAX_CONSECUTIVE_JUMPS)
        }

        // 8. Fall death — if player falls below the screen, deduct a life.
        if (player.getY() > FALL_DEATH_Y) {                                      // Below the visible canvas height (768 px); implies fell off the bottom
            player.loseLife();                                                    // Deduct one life and respawn the player at the level start position
        }
    }

    // -------------------------------------------------------------------------
    // Gravity
    // -------------------------------------------------------------------------

    public void applyGravity(Player player) { // Accelerates player downward; clamped to TERMINAL_VELOCITY
        if (!player.isGrounded()) {                                      // Guard: only apply gravity when airborne; grounded path is handled in update()
            float newVelY = player.getVelY() + GRAVITY;                  // Increase downward velocity by GRAVITY (0.5f px/tick)
            player.setVelY(Math.min(newVelY, TERMINAL_VELOCITY));        // Clamp to TERMINAL_VELOCITY (18f) to prevent infinite acceleration on long falls
        }
    }

    // -------------------------------------------------------------------------
    // Movement actions — called by InputRouter in response to WASD input
    // -------------------------------------------------------------------------

    public void walk(Player player, int direction) { // Sets horizontal velocity and facing direction
        player.setFacingDirection(direction);                    // Update facing: used by rendering to flip the sprite and by dodge/dash to determine blast direction
        player.setVelX(WALK_SPEED * direction);                  // Set velX: WALK_SPEED in the movement direction; replaces any previous velocity (no acceleration model)
    }

    public void stopWalking(Player player) { // Zeroes horizontal velocity on key-release
        player.setVelX(0f);                  // Clear horizontal velocity; the player slides to an instant stop (no friction model)
    }

    public void jump(Player player) { // Applies upward impulse if grounded or within jump allowance
        if (player.isGrounded() || player.getConsecutiveJumps() < MAX_CONSECUTIVE_JUMPS) {       // Allow jump if grounded (first jump) OR within consecutive-jump allowance (double-jump)
            player.setVelY(JUMP_VELOCITY);                                                       // Apply JUMP_VELOCITY (-12f px/tick) upward; positive y is down, so negative is up
            player.setGrounded(false);                                                           // Immediately unground the player so gravity applies on the next tick
            player.setConsecutiveJumps(player.getConsecutiveJumps() + 1);                        // Increment counter; checked next jump to enforce MAX_CONSECUTIVE_JUMPS limit
        }
    }

    // -------------------------------------------------------------------------
    // Special movement — dodge roll
    // -------------------------------------------------------------------------

    public void dodgeRoll(Player player) { // Initiates dodge if ability unlocked and cooldown elapsed
        if (!player.isHasDodge()) return;                                             // Ability gate: DODGE lore fragment must be collected before this move is available

        long now = System.currentTimeMillis();                                        // Capture wall-clock time for cooldown comparison
        if (now < player.getDodgeCooldownEnd()) return;                              // Cooldown check: if within the 3-second cooldown window, block the roll

        player.setVelX(DODGE_BOOST * player.getFacingDirection());                   // Apply horizontal boost (10f px/tick) in the player's current facing direction
        player.setDodging(true);                                                      // Set dodging flag so the renderer plays the roll animation
        player.setInvincible(true);                                                   // Grant invincibility frames: damage is blocked during the roll window
        player.setDodgeEndTime(now + DODGE_DURATION_MS);                             // Record when the roll expires (500 ms from now); checked by update() each tick
        player.setInvincibleTimer(now + DODGE_DURATION_MS);                          // Invincibility expires at the same time as the roll duration
        player.setDodgeCooldownEnd(now + DODGE_COOLDOWN_MS);                         // Start the 3-second cooldown; next dodge roll cannot start before this time
    }

    // -------------------------------------------------------------------------
    // Special movement — wall cling
    // -------------------------------------------------------------------------

    public void wallCling(Player player) { // Reduces fall speed against wall; requires WALL_CLING ability
        if (!player.isHasWallCling()) return;                                       // Ability gate: WALL_CLING lore fragment must be collected
        if (player.isWallTouching() && !player.isGrounded()) {                     // Only apply when touching a wall AND airborne (not already on the ground)
            if (player.getVelY() > WALL_CLING_FALL_SPEED) {                        // Only reduce speed if falling faster than the cling cap
                player.setVelY(WALL_CLING_FALL_SPEED);                             // Cap fall speed to WALL_CLING_FALL_SPEED (1 px/tick) for the slow-slide effect
            }
        }
    }

    // -------------------------------------------------------------------------
    // Special movement — shadow dash
    // -------------------------------------------------------------------------

    public void shadowDash(Player player) { // Teleports 80 px in facing direction; requires SHADOW_DASH ability and cooldown
        if (!player.isHasShadowDash()) return;                                         // Ability gate: SHADOW_DASH lore fragment must be collected

        long now = System.currentTimeMillis();                                          // Capture wall-clock time for cooldown check
        if (now < player.getShadowDashCooldownEnd()) return;                           // Cooldown check: 5-second window must have elapsed since the last dash

        player.setX(player.getX() + SHADOW_DASH_DISTANCE * player.getFacingDirection()); // Instantaneous x teleport: 80 px in the current facing direction (+1 right, -1 left)
        player.setShadowDashCooldownEnd(now + SHADOW_DASH_COOLDOWN_MS);                // Start the 5-second cooldown timer; next shadow dash cannot begin before this time
    }
}
