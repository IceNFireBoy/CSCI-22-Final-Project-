/**
 Abstract base for all boss-phase attack patterns (SearingBeam, BlockRain, SpikeArray,
 CrusherBlock, Shield). Manages common lifetime countdown, active flag, and default hitbox.
 Each concrete subclass defines duration, rendering, and hit-detection logic.

 BossAttack instances are created by GameStarter when Apprentice selects attack action.
 Game loop calls update() on each active element every 16 ms; when elapsed exceeds durationMs,
 the attack expires and GameCanvas stops rendering it. Subclasses must call super.update()
 to ensure lifetime countdown logic always runs first.
 */

// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 1c "Abstract Classes" - declared abstract; cannot be instantiated
//                                directly. Holds shared state (durationMs,
//                                elapsed, expired) and a default update()
//                                implementation that all five concrete
//                                subclasses (SearingBeam, BlockRain,
//                                CrusherBlock, SpikeArray, Shield) inherit
//                                via super.update(). Exact pattern from the
//                                abstract-classes module.
// Module 3c "Collision"        - getHitbox() returns a Rectangle for AABB
//                                tests against player.getBounds(),
//                                following the bounding-box collision
//                                pattern from 3c.
// Module 1a "Modifiers"        - protected fields shared with subclasses;
//                                public accessors expose read-only state
//                                to GameStarter and GameCanvas.
// =========================================================================

import java.awt.Graphics2D; // 2D rendering context; required by the abstract render() inherited from GameElement
import java.awt.Rectangle;  // AWT rectangle returned by getHitbox(); used in collision tests with the Wanderer's bounds

public abstract class BossAttack extends GameElement { // Abstract: cannot be instantiated; concrete attack types must extend this class

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * The total lifetime of this attack in milliseconds. Set at construction
     * time and never changed. When {@link #elapsed} reaches or exceeds this
     * value, {@link #update(long)} sets {@link #expired} and deactivates the
     * attack. Typical values: 2000 ms (Shield), 3000 ms (SpikeArray), up to
     * 5000 ms (SearingBeam).
     */
    protected long durationMs; // Fixed lifetime; read-only after construction; compared to elapsed each tick to detect expiry

    /**
     * Monotonically increasing accumulation of tick deltas since this attack was
     * constructed. Incremented by {@link #update(long)} each tick. Once this
     * reaches {@link #durationMs}, the attack expires.
     */
    protected long elapsed; // Running total of elapsed ms since spawn; fed into durationMs comparison each tick

    /**
     * Whether this attack has exceeded its lifetime. Set to {@code true} by
     * {@link #update(long)} at the moment {@link #elapsed} first reaches or
     * exceeds {@link #durationMs}. Distinguished from {@link GameElement#active}
     * so that callers can check specifically for timeout expiry (as opposed to
     * manual deactivation via {@link #setActive(boolean)}).
     */
    protected boolean expired; // True once the attack's duration is fully consumed; read by GameStarter to filter expired attacks from the list

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code BossAttack} at the given world-space position and
     * size with the specified total lifetime.
     *
     * <p>Architecture role: Called by every concrete subclass constructor via
     * {@code super(...)}. The position and dimensions define the AABB stored in
     * {@link GameElement} and also serve as the default return value of
     * {@link #getHitbox()}, which is sufficient for fixed-footprint attacks.
     * Elapsed is initialised to zero and expired is {@code false} so the attack
     * becomes live immediately after construction.</p>
     *
     * @param x          the x-coordinate of the attack's left edge in world space (pixels)
     * @param y          the y-coordinate of the attack's top edge in world space (pixels)
     * @param width      the width of the attack area in pixels
     * @param height     the height of the attack area in pixels
     * @param durationMs the total lifetime of this attack in milliseconds;
     *                   after this many ms, the attack expires and is deactivated
     */
    public BossAttack(int x, int y, int width, int height, long durationMs) { // Five-argument constructor; all subclasses delegate here via super()
        super(x, y, width, height);       // Delegate to GameElement: set position (x,y) and AABB size (width×height), active=true
        this.durationMs = durationMs;     // Store the total lifetime; used in update() to decide when to expire
        this.elapsed = 0L;                // Start elapsed timer at zero: attack begins its countdown immediately after construction
        this.expired = false;             // Start not-expired; set to true by update() once elapsed >= durationMs
    }

    // -------------------------------------------------------------------------
    // GameElement override
    // -------------------------------------------------------------------------

    /**
     * Advances the lifetime countdown by the elapsed tick delta and expires the
     * attack when its total duration is exceeded. Subclasses must call
     * {@code super.update(deltaMs)} at the start of their override to ensure
     * the lifetime management always runs; they should then check {@code !active}
     * and return early if the attack just expired.
     *
     * <p>Architecture role: Called every 16 ms by the game loop in
     * {@link GameStarter} for every active element. The expiry path sets
     * {@link #expired} and calls {@link #setActive(boolean) setActive(false)},
     * which causes {@link GameCanvas} to skip the render pass and signals
     * {@link GameStarter}'s cleanup sweep to remove this attack from
     * {@link GameStarter#elements}.</p>
     *
     * <p>Interaction: Concrete subclasses such as {@link Shield#update(long)} call
     * this first, then check {@code if (!active) return;} before doing their own
     * logic (e.g. projectile interception). This pattern ensures no work is done
     * after expiry on the same tick that expiry is detected.</p>
     *
     * @param deltaMs the time elapsed since the last tick, in milliseconds;
     *                always {@link GameStarter#TICK_MS} (16 ms) in the current
     *                fixed-timestep loop
     */
    @Override
    public void update(long deltaMs) {          // Advance lifetime timer; expire and deactivate once durationMs is reached
        elapsed += deltaMs;                     // Accumulate elapsed time; at 60 fps this increases by 16 ms per tick
        if (elapsed >= durationMs) {            // Check if the attack's total lifetime has been consumed
            expired = true;                     // Latch the expired flag; distinct from active so callers can distinguish timeout vs manual deactivation
            setActive(false);                   // Deactivate via GameElement.setActive(): skips update+render in subsequent ticks and triggers cleanup sweep
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns whether this attack has expired due to its lifetime being exhausted.
     *
     * <p>Interaction: Checked by {@link GameStarter}'s post-tick cleanup to identify
     * attacks that should be removed from {@link GameStarter#elements}. Using a
     * dedicated flag (rather than just {@code !isActive()}) allows the game loop to
     * distinguish attacks that expired naturally from those deactivated manually for
     * other reasons (e.g. early cancellation).</p>
     *
     * @return {@code true} if {@link #elapsed} has reached or exceeded
     *         {@link #durationMs}; {@code false} if the attack is still within
     *         its lifetime
     */
    public boolean isExpired() { // Read accessor for the expired flag; used by GameStarter cleanup to identify timeout-expired attacks
        return expired;          // Return the latched expired flag set by update() once durationMs is consumed
    }

    /**
     * Returns the elapsed time since this attack was created.
     *
     * <p>Interaction: Read by concrete subclasses that need to compute a
     * time-varying animation (e.g. {@link SearingBeam}'s sweep angle is a
     * function of elapsed time) or a progress fraction for UI feedback
     * ({@code elapsed / (float) durationMs} gives 0.0 → 1.0 over the attack
     * lifetime).</p>
     *
     * @return elapsed time in milliseconds; never negative; increases monotonically
     *         until the attack expires
     */
    public long getElapsed() { // Read accessor for the elapsed timer; used by subclasses for time-varying animation and progress calculation
        return elapsed;        // Return the running accumulated elapsed ms; increases by ~16 per tick at 60 fps
    }

    /**
     * Returns the current damage hitbox of this attack in world-space pixels.
     * The default implementation delegates to {@link GameElement#getBounds()},
     * returning the full AABB defined at construction time. This is correct for
     * fixed-footprint attacks like {@link CrusherBlock}, {@link SpikeArray}, and
     * {@link Shield} where the damage region equals the rendered region.
     *
     * <p>Attacks whose damage region differs from the rendered bounds — for
     * example a {@link SearingBeam} whose hitbox is a narrow disk around its
     * current sweep target rather than the full line, or a {@link BlockRain}
     * whose hitbox is the bounding box of the currently-falling brick — should
     * override this method to return the tighter rectangle. Subclasses that apply
     * damage directly inside {@link #update(long)} without a separate hitbox query
     * may leave this at the default.</p>
     *
     * <p>Interaction: Called by {@link GameStarter#checkBossAttackHits()} each
     * tick to test {@code player.getBounds().intersects(attack.getHitbox())}
     * for every active {@code BossAttack}.</p>
     *
     * @return a {@link Rectangle} covering the attack's current damage area in
     *         world space; never {@code null}; a fresh object per the contract
     *         of {@link GameElement#getBounds()}
     */
    public Rectangle getHitbox() { // Default hitbox = full GameElement AABB; override in subclasses with non-rectangular or offset damage regions
        return getBounds();        // Delegate to GameElement.getBounds() which builds a new Rectangle(x,y,width,height) each call
    }
}
