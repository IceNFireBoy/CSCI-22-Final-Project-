/**
 * Defines the contract for any game entity that can receive damage and has a concept
 * of health. Implementing classes are responsible for tracking and managing their own
 * health state in response to damage events.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

public interface Damageable {

    /**
     * Applies the specified amount of damage to this entity, reducing its health
     * accordingly.
     *
     * @param amount the amount of damage to apply; must be a non-negative value
     */
    void takeDamage(int amount);

    /**
     * Returns the current health of this entity.
     *
     * @return the current health value
     */
    int getHealth();

    /**
     * Returns the maximum health this entity can have.
     *
     * @return the maximum health value
     */
    int getMaxHealth();

    /**
     * Determines whether this entity is still alive.
     *
     * @return {@code true} if the entity's health is above zero; {@code false} otherwise
     */
    boolean isAlive();
}
