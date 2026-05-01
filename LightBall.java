/**
 * Server-authoritative light-source entity used during the BOSS phase. Rather than
 * teleporting the Apprentice's light directly to the cursor position (as in the
 * platforming acts), the boss arena gives the light inertia: the Apprentice's
 * {@code LIGHT_TARGET|x|y} messages become a world-space target that the ball chases,
 * and per-tick the ball's velocity is blended toward a desired vector of magnitude
 * {@link #BALL_MAX_SPEED} using {@link #BALL_TURN_SPEED}.
 *
 * <p>The ball's position is clamped to the boss-arena rectangle so the lit region can
 * never leave the playable space. Position and velocity are stepped once per server
 * tick by {@link GameServer#runGameLoop()}; the resulting x/y are carried in every
 * {@link NetworkProtocol.ServerStatePacket} so both clients render the same light at
 * the same place without any further client-side prediction.</p>
 *
 * <p>Architecture role: {@code LightBall} is owned exclusively by {@link GameServer}.
 * It is the only entity in the project that is simulated on the server side rather than
 * on a client. This design ensures the two clients always agree on the light position
 * (eliminating the possibility of desync) at the cost of one extra field in every
 * server state broadcast. The ball is not a {@link GameElement} subclass because it
 * has no client-side collision or rendering role; each client reads the x/y from the
 * state packet and passes them to {@link LightRenderer} or {@link Camera} directly.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-17
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */
public class LightBall { // Plain simulation object; no GameElement hierarchy; server-only entity

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /**
     * Target speed magnitude, in world-space pixels per tick. The desired velocity
     * vector computed in {@link #step()} always has this magnitude when the ball is
     * not at its target. At 60 ticks per second, 4 px/tick = 240 px/s, which allows
     * the ball to cross the 3072 px arena width in roughly 12.8 seconds at full speed.
     */
    public static final float BALL_MAX_SPEED = 4.0f; // 4 px/tick = 240 px/s; controls how fast the ball can chase the Apprentice's cursor

    /**
     * Per-tick velocity blend factor. Each tick the current velocity is pulled
     * toward the desired velocity by this fraction. A value of 0.08 means the ball
     * closes 8% of the velocity gap per tick, giving a smooth "oil tanker" feel
     * with a significant lag that the Apprentice must anticipate.
     *
     * <p>At BALL_TURN_SPEED = 0.08f, the velocity converges to within 1% of the
     * desired value after approximately {@code log(0.01)/log(1-0.08) ≈ 56} ticks
     * (~0.93 s at 60 fps).</p>
     */
    public static final float BALL_TURN_SPEED = 0.08f; // 8% velocity blend per tick; larger = snappier tracking, smaller = more inertia lag

    /** Arena width in world-space pixels. Mirrors {@link GameServer#ARENA_W} (3072). */
    private static final int ARENA_W = GameServer.ARENA_W; // 3072 px; used as the upper clamp bound for x in clampX()

    /** Arena height in world-space pixels. Mirrors {@link GameServer#ARENA_H} (2304). */
    private static final int ARENA_H = GameServer.ARENA_H; // 2304 px; used as the upper clamp bound for y in clampY()

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Current world-space x of the ball; updated by {@link #step()} each tick. */
    private float x; // Authoritative ball x; carried in ServerStatePacket; read by clients to position the light mask

    /** Current world-space y of the ball; updated by {@link #step()} each tick. */
    private float y; // Authoritative ball y; carried in ServerStatePacket; read by clients to position the light mask

    /** Current per-tick velocity on the x axis; lerped toward desiredVx in {@link #step()}. */
    private float vx; // X velocity in px/tick; updated by BALL_TURN_SPEED blend toward desired direction each tick

    /** Current per-tick velocity on the y axis; lerped toward desiredVy in {@link #step()}. */
    private float vy; // Y velocity in px/tick; updated by BALL_TURN_SPEED blend toward desired direction each tick

    /** World-space x the ball is currently chasing. Set by {@link #setTarget(float, float)}. */
    private float targetX; // Desired ball destination x; converted from Apprentice canvas-space cursor via camera transform before being set

    /** World-space y the ball is currently chasing. Set by {@link #setTarget(float, float)}. */
    private float targetY; // Desired ball destination y; same conversion path as targetX

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code LightBall} positioned at the given world coordinates
     * with zero velocity. Both target components are initialised to the same
     * coordinates as the initial position so the ball holds still until its first
     * {@link #setTarget(float, float)} call.
     *
     * <p>Architecture role: Called once by {@link GameServer#enterBossArena()} when
     * the session transitions to the BOSS phase. The initial position is typically the
     * arena centre ({@code ARENA_W/2, ARENA_H/2}) so the Apprentice starts with the
     * light centred before their first cursor movement.</p>
     *
     * @param initialX starting world-space x; clamped to [0, ARENA_W] by
     *                 {@link #clampX(float)}
     * @param initialY starting world-space y; clamped to [0, ARENA_H] by
     *                 {@link #clampY(float)}
     */
    public LightBall(float initialX, float initialY) { // Two-argument constructor: initial position; velocity starts at zero; target matches position
        this.x = clampX(initialX);   // Clamp initial x to arena bounds in case caller passes out-of-range coordinates
        this.y = clampY(initialY);   // Clamp initial y to arena bounds symmetrically
        this.vx = 0f;                // Start with no horizontal velocity so the ball is stationary on the first tick
        this.vy = 0f;                // Start with no vertical velocity symmetrically
        this.targetX = this.x;       // Target matches initial position so step() produces no movement before setTarget() is called
        this.targetY = this.y;       // Target y matches initial position symmetrically
    }

    // -------------------------------------------------------------------------
    // Simulation
    // -------------------------------------------------------------------------

    /**
     * Updates the world-space target the ball is chasing. Subsequent {@link #step()}
     * calls will pull the velocity toward a vector of magnitude {@link #BALL_MAX_SPEED}
     * pointing at this target. The target coordinates are clamped to the arena bounds.
     *
     * <p>Architecture role: Called by {@link GameServer} each tick when the Apprentice
     * sends a {@code LIGHT_TARGET|x|y} message. The x/y in the message are canvas-space
     * coordinates that the server first converts to world space using the
     * {@link Camera#screenToWorldX(int)} / {@link Camera#screenToWorldY(int)} helpers
     * before passing them here.</p>
     *
     * @param tx world-space target x; clamped to [0, ARENA_W]
     * @param ty world-space target y; clamped to [0, ARENA_H]
     */
    public void setTarget(float tx, float ty) { // Called by GameServer on LIGHT_TARGET messages; updates the chase destination
        this.targetX = clampX(tx);              // Clamp target x to arena bounds so the ball cannot be directed outside the arena
        this.targetY = clampY(ty);              // Clamp target y to arena bounds symmetrically
    }

    /**
     * Advances the ball by one server tick. Computes the desired velocity toward the
     * current target (zero when already at the target), blends the current velocity
     * toward it by {@link #BALL_TURN_SPEED}, integrates position, and clamps the
     * result to the arena bounds.
     *
     * <p>Architecture role: Called once per server tick by {@link GameServer#runGameLoop()}.
     * The resulting x/y are then written into the {@link NetworkProtocol.ServerStatePacket}
     * broadcast to both clients so they can position the light mask correctly without
     * running their own physics.</p>
     *
     * <p>Step breakdown:
     * <ol>
     *   <li>Compute direction vector (dx, dy) from current position to target.</li>
     *   <li>Normalise and scale to {@link #BALL_MAX_SPEED} → desired velocity.</li>
     *   <li>If already at target (dist ≈ 0), desired velocity is zero (ball decelerates).</li>
     *   <li>Blend current velocity toward desired using {@link #BALL_TURN_SPEED}.</li>
     *   <li>Integrate: x += vx, y += vy.</li>
     *   <li>Clamp resulting position to arena bounds.</li>
     * </ol></p>
     */
    public void step() {                                                // Advance ball physics by one tick; called by GameServer.runGameLoop() each 16 ms
        float dx = targetX - x;                                         // Horizontal distance from current position to target
        float dy = targetY - y;                                         // Vertical distance from current position to target
        float dist = (float) Math.sqrt(dx * dx + dy * dy);             // Euclidean distance to target; used to normalise the direction vector

        float desiredVx;                                                // Desired x velocity: direction × BALL_MAX_SPEED, or 0 when at target
        float desiredVy;                                                // Desired y velocity: direction × BALL_MAX_SPEED, or 0 when at target
        if (dist > 0.0001f) {                                           // Threshold guard: only compute direction if meaningfully far from target
            float invDist = 1f / dist;                                  // Precompute reciprocal to avoid two division operations below
            desiredVx = dx * invDist * BALL_MAX_SPEED;                 // Normalise dx and scale to max speed: unit-x × BALL_MAX_SPEED
            desiredVy = dy * invDist * BALL_MAX_SPEED;                 // Normalise dy and scale to max speed: unit-y × BALL_MAX_SPEED
        } else {                                                        // Ball is at (or extremely close to) the target
            desiredVx = 0f;                                             // No desired velocity: ball should decelerate to a stop
            desiredVy = 0f;                                             // Symmetric: no desired y velocity
        }

        vx += (desiredVx - vx) * BALL_TURN_SPEED;                     // Blend current vx toward desired vx by 8% this tick (smooth steering)
        vy += (desiredVy - vy) * BALL_TURN_SPEED;                     // Blend current vy toward desired vy symmetrically

        x += vx;                                                        // Integrate position: advance x by current velocity
        y += vy;                                                        // Integrate position: advance y by current velocity

        x = clampX(x);                                                  // Clamp x to [0, ARENA_W] to prevent the ball from leaving the arena
        y = clampY(y);                                                  // Clamp y to [0, ARENA_H] to prevent the ball from leaving the arena
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the ball's current world-space x coordinate after the latest step. */
    public float getX() { return x; } // Read accessor; carried in ServerStatePacket; clients use this to position the light mask

    /** Returns the ball's current world-space y coordinate after the latest step. */
    public float getY() { return y; } // Read accessor; symmetric with getX()

    /** Returns the ball's current world-space target x (the position it is chasing). */
    public float getTargetX() { return targetX; } // Read accessor; may be used by server-side debug logging

    /** Returns the ball's current world-space target y (the position it is chasing). */
    public float getTargetY() { return targetY; } // Read accessor; symmetric with getTargetX()

    /** Returns the ball's current per-tick x velocity (px/tick). */
    public float getVelocityX() { return vx; } // Read accessor; useful for server-side debug or replays

    /** Returns the ball's current per-tick y velocity (px/tick). */
    public float getVelocityY() { return vy; } // Read accessor; symmetric with getVelocityX()

    /**
     * Instantly snaps the ball's position and target to the given world coordinates,
     * zeroing velocity. Used when the session first enters the BOSS phase so the ball
     * does not have to lerp in from a stale pre-boss position, which would cause the
     * light to pop or sweep visibly from the wrong location on the first frame.
     *
     * <p>Architecture role: Called by {@link GameServer#enterBossArena()} after
     * constructing the {@code LightBall}, or after a session reconnect by
     * {@link GameServer#applySnapshot(SessionSnapshot)} to restore the saved light
     * position.</p>
     *
     * @param newX world-space x to snap to; clamped to [0, ARENA_W]
     * @param newY world-space y to snap to; clamped to [0, ARENA_H]
     */
    public void snapTo(float newX, float newY) { // Instant teleport with velocity reset; used at boss-entry and snapshot restore to prevent visual pop
        this.x = clampX(newX);                   // Clamp and store new x position; affects next ServerStatePacket
        this.y = clampY(newY);                   // Clamp and store new y position symmetrically
        this.vx = 0f;                            // Zero horizontal velocity so step() starts from rest
        this.vy = 0f;                            // Zero vertical velocity symmetrically
        this.targetX = this.x;                   // Align target with snapped position so step() produces no movement on the first tick
        this.targetY = this.y;                   // Align target y symmetrically
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Clamps a world-space x value to the arena's horizontal bounds [0, ARENA_W].
     *
     * @param v the value to clamp
     * @return the clamped value; guaranteed to be in [0, 3072]
     */
    private static float clampX(float v) {     // Arena x boundary clamp; [0, ARENA_W]; called after every position update and in setTarget
        if (v < 0f) return 0f;                 // Clamp below left edge: cannot go west of the arena
        if (v > ARENA_W) return ARENA_W;       // Clamp above right edge: cannot go east of the arena
        return v;                              // Value already within bounds: return as-is
    }

    /**
     * Clamps a world-space y value to the arena's vertical bounds [0, ARENA_H].
     *
     * @param v the value to clamp
     * @return the clamped value; guaranteed to be in [0, 2304]
     */
    private static float clampY(float v) {     // Arena y boundary clamp; [0, ARENA_H]; symmetric with clampX
        if (v < 0f) return 0f;                 // Clamp below top edge: cannot go above the arena
        if (v > ARENA_H) return ARENA_H;       // Clamp above bottom edge: cannot go below the arena
        return v;                              // Value already within bounds: return as-is
    }
}
