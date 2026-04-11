/**
 * Represents one of the four destructible Cores that serve as the primary win
 * condition objectives in Lumen Architect. Each Core has a fixed index identifying
 * its position in the level, a small health pool of three hit points, and a destroyed
 * flag that signals to the game loop when it should be removed from play.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */


import java.awt.Graphics2D;

public class Core extends GameElement implements Damageable {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** The maximum number of hits a Core can absorb before being destroyed. */
    private static final int MAX_HEALTH = 3;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * The zero-based index of this Core in the level layout. Valid values are 0
     * through 3, corresponding to the four Core positions defined by the game design.
     */
    private int coreIndex;

    /**
     * The current health of this Core. Starts at {@value #MAX_HEALTH} and decrements
     * with each successful Wanderer attack until it reaches zero.
     */
    private int health;

    /**
     * Whether this Core has been fully destroyed. A destroyed Core no longer
     * participates in collision detection or damage calculations and is removed from
     * the active entity list by the game loop.
     */
    private boolean destroyed;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code Core} at the given world position with the specified
     * index. The Core is initialised to full health and is not yet destroyed.
     *
     * @param coreIndex the zero-based index identifying this Core's role in the level;
     *                  expected to be in the range [0, 3]
     * @param x         the x-coordinate of the Core's left edge in world space
     * @param y         the y-coordinate of the Core's top edge in world space
     */
    public Core(int coreIndex, int x, int y) {
        super(x, y, 32, 32);
        this.coreIndex = coreIndex;
        this.health = MAX_HEALTH;
        this.destroyed = false;
    }

    // -------------------------------------------------------------------------
    // GameElement overrides
    // -------------------------------------------------------------------------

    /**
     * Updates this Core's state for the current game tick. This stub will later drive
     * idle pulse animations and respond to the destroyed flag.
     *
     * @param deltaMs the time elapsed since the last update, in milliseconds
     */
    @Override
    public void update(long deltaMs) {
        // Stub — logic to be implemented in a later phase.
    }

    /**
     * Renders this Core onto the provided graphics context. This stub will later draw
     * a glowing orb whose intensity reflects the remaining health.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    @Override
    public void render(Graphics2D g) {
        // Stub — rendering to be implemented in a later phase.
    }

    // -------------------------------------------------------------------------
    // Damageable implementation
    // -------------------------------------------------------------------------

    /**
     * Reduces this Core's health by the specified amount. When health reaches zero the
     * Core is marked as destroyed and deactivated.
     *
     * @param amount the amount of damage to apply; must be non-negative
     */
    @Override
    public void takeDamage(int amount) {
        // Health is server-authoritative; the client reports hits via
        // Player.pendingCoreHitIndex → NetworkIO → CoreHitPacket.
    }

    /**
     * Returns the current health of this Core.
     *
     * @return the current health value, in the range {@code [0, 3]}
     */
    @Override
    public int getHealth() {
        return health;
    }

    /**
     * Returns the maximum health this Core can have.
     *
     * @return {@value #MAX_HEALTH}
     */
    @Override
    public int getMaxHealth() {
        return MAX_HEALTH;
    }

    /**
     * Determines whether this Core is still intact.
     *
     * @return {@code true} if {@code health > 0}; {@code false} otherwise
     */
    @Override
    public boolean isAlive() {
        return health > 0;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the zero-based index identifying this Core's position in the level.
     *
     * @return the core index, in the range [0, 3]
     */
    public int getCoreIndex() {
        return coreIndex;
    }

    /**
     * Returns whether this Core has been destroyed.
     *
     * @return {@code true} if the Core is destroyed; {@code false} if it is intact
     */
    public boolean isDestroyed() {
        return destroyed;
    }
}
