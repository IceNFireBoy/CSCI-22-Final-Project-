/**
 Contract for any entity that can receive damage and track health. Implementing
 classes manage health state in response to damage events.
 */

public interface Damageable {

    void takeDamage(int amount); // Applies damage; reduces health

    int getHealth(); // Returns current health

    int getMaxHealth(); // Returns maximum health

    boolean isAlive(); // Returns true if health > 0
}
