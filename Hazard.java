/**
 Environmental danger dealing damage to Wanderer and receiving damage in return.
 Bridges GameElement hierarchy with Damageable contract; common ancestor for threat
 entities (CorruptedSpike, CorruptedWall, and other hazard subclasses introduced in
 P9.3'). Adds damage, health, maxHealth fields. Update/render/takeDamage methods are
 stubs; concrete subclasses override with their own behaviour and rendering.

 GameStarter.checkHazardContact() tests AABB overlap and reads getDamage() to decrement
 Player health. Player melee/projectile calls takeDamage() via server-authoritative path.
 */

import java.awt.Graphics2D; // 2D rendering context; required by the abstract render() contract inherited through GameElement

public class Hazard extends GameElement implements Damageable { // Extends GameElement for entity-list participation; implements Damageable for takeDamage/health contract

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    protected int damage;    // Contact damage per hit; read by GameStarter
    protected int health;    // Current HP; starts at maxHealth
    protected int maxHealth; // Constant maximum HP

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public Hazard(int x, int y, int width, int height, int damage, int maxHealth) { // Constructor; delegates to GameElement, initializes health/damage
        super(x, y, width, height);     // Delegate to GameElement: set position (x,y), AABB (width×height), active=true
        this.damage = damage;           // Store contact-damage value; read by GameStarter.checkHazardContact() on Wanderer overlap
        this.maxHealth = maxHealth;     // Store the maximum health; used by getMaxHealth() and proportional health-bar rendering
        this.health = maxHealth;        // Start at full health; decremented by authoritative takeDamage() path
    }

    // -------------------------------------------------------------------------
    // GameElement overrides
    // -------------------------------------------------------------------------

    @Override
    public void update(long deltaMs) { // Stub; concrete subclasses override with their own per-tick behaviour
        // Stub — logic implemented per-subclass (e.g. CorruptedWall's IDLE → WARNING → FALLING FSM).
    }

    @Override
    public void render(Graphics2D g) { // Stub; concrete subclasses override with sprite/shape rendering
        // Stub — rendering implemented per-subclass; most use SpriteOverridable.tryDrawSprite first.
    }

    // -------------------------------------------------------------------------
    // Damageable implementation
    // -------------------------------------------------------------------------

    @Override
    public void takeDamage(int amount) { // Stub; concrete subclasses override with server-authoritative or local damage logic
        // Stub — damage application logic to be implemented in a later phase.
        // Future implementation: health -= amount; if (health <= 0) { health = 0; setActive(false); }
    }

    @Override
    public int getHealth() { // Read accessor; returns current HP
        return health;       // Return the stored health; updated by takeDamage() or directly by the state-packet sync path
    }

    @Override
    public int getMaxHealth() { // Read accessor; returns maximum HP
        return maxHealth;       // Return the stored maximum; constant after construction
    }

    @Override
    public boolean isAlive() { // Predicate; true if health > 0
        return health > 0;     // Direct comparison; false means health == 0, hazard is destroyed
    }

    // -------------------------------------------------------------------------
    // Accessor
    // -------------------------------------------------------------------------

    public int getDamage() { // Read accessor; returns contact damage per hit
        return damage;       // Return the stored damage amount set by the subclass constructor via super()
    }
}
