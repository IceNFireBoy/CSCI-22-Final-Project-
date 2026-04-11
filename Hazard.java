/**
 * Represents an environmental danger in the Lumen Architect world that can both deal
 * damage to the Wanderer and receive damage in return. This class bridges the
 * {@link GameElement} hierarchy with the {@link interfaces.Damageable} contract,
 * serving as the common ancestor for all interactive threat entities such as
 * {@link DarkCrawler}.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */


import java.awt.Graphics2D;

public class Hazard extends GameElement implements Damageable {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** The amount of damage this hazard deals to the Wanderer on contact. */
    protected int damage;

    /** The current health of this hazard. Reaches zero when the hazard is destroyed. */
    protected int health;

    /** The maximum health this hazard can have; used to calculate health ratios. */
    protected int maxHealth;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code Hazard} at the given position with the specified
     * dimensions, damage output, and health pool.
     *
     * @param x         the x-coordinate of the hazard's left edge in world space
     * @param y         the y-coordinate of the hazard's top edge in world space
     * @param width     the width of this hazard in pixels
     * @param height    the height of this hazard in pixels
     * @param damage    the contact damage this hazard deals per hit
     * @param maxHealth the starting and maximum health of this hazard
     */
    public Hazard(int x, int y, int width, int height, int damage, int maxHealth) {
        super(x, y, width, height);
        this.damage = damage;
        this.maxHealth = maxHealth;
        this.health = maxHealth;
    }

    // -------------------------------------------------------------------------
    // GameElement overrides
    // -------------------------------------------------------------------------

    /**
     * Updates this hazard's state for the current game tick. Subclasses override this
     * method to implement patrol paths, attack patterns, and other dynamic behaviour.
     *
     * @param deltaMs the time elapsed since the last update, in milliseconds
     */
    @Override
    public void update(long deltaMs) {
        // Stub — logic to be implemented in subclasses or a later phase.
    }

    /**
     * Renders this hazard onto the provided graphics context. Subclasses override this
     * method to supply sprite-based or shape-based drawing routines.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    @Override
    public void render(Graphics2D g) {
        // Stub — rendering to be implemented in subclasses or a later phase.
    }

    // -------------------------------------------------------------------------
    // Damageable implementation
    // -------------------------------------------------------------------------

    /**
     * Reduces this hazard's health by the specified amount, clamping the result to a
     * minimum of zero. When health reaches zero the hazard becomes inactive.
     *
     * @param amount the amount of damage to apply; must be non-negative
     */
    @Override
    public void takeDamage(int amount) {
        // Stub — damage application logic to be implemented in a later phase.
    }

    /**
     * Returns the current health of this hazard.
     *
     * @return the current health value, in the range {@code [0, maxHealth]}
     */
    @Override
    public int getHealth() {
        return health;
    }

    /**
     * Returns the maximum health this hazard can have.
     *
     * @return the maximum health value
     */
    @Override
    public int getMaxHealth() {
        return maxHealth;
    }

    /**
     * Determines whether this hazard is still alive.
     *
     * @return {@code true} if {@code health > 0}; {@code false} otherwise
     */
    @Override
    public boolean isAlive() {
        return health > 0;
    }

    // -------------------------------------------------------------------------
    // Accessor
    // -------------------------------------------------------------------------

    /**
     * Returns the contact damage this hazard deals to the Wanderer per hit.
     *
     * @return the damage value
     */
    public int getDamage() {
        return damage;
    }
}
