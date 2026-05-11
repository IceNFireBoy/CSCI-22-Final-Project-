/**
 Contract for any entity that can receive damage and track health. Implementing
 classes manage health state in response to damage events.
 */

// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 1b "Interfaces"  - canonical interface example: four abstract
//                           method signatures and no fields. Implemented
//                           by Player, Hazard, and any future class that
//                           needs to participate in the damage / health
//                           system. Demonstrates the interfaces-module
//                           teaching that interfaces decouple "what" from
//                           "how".
// =========================================================================

public interface Damageable {

    void takeDamage(int amount); // Applies damage; reduces health

    int getHealth(); // Returns current health

    int getMaxHealth(); // Returns maximum health

    boolean isAlive(); // Returns true if health > 0
}
