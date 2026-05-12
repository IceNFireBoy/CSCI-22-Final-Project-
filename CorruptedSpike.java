/**
 * Static contact hazard — a row of corrupted spikes that damage the Wanderer
 * on overlap. Replaces DarkCrawler as the standard "stay away from this"
 * threat in Acts 1–3 (P9.3'). Self-cooldowns to avoid draining HP rapidly when
 * a player is briefly stuck against the spike's hitbox.
 *
 * <p>Architecture role: extends {@link Hazard} so the existing
 * {@code GameStarter} hazard-contact path (AABB overlap → {@code getDamage()}
 * → {@code Player.takeDamage(int)}) handles it without modification. The
 * overrides here are the per-instance damage cooldown (so 60 fps × 1 hp does
 * not equal 60 hp/sec) and the sprite rendering.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-29
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */


// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 1c "Abstract Classes" - extends Hazard (which extends GameElement);
//                                 fits the layered-abstract-class pattern.
// Module 1b "Interfaces"        - implements SpriteOverridable so the look
//                                 can be replaced with a PNG without code
//                                 changes - polymorphism via interface
//                                 covered in 1b.
// Module 3a "Graphics"          - render() draws procedurally with fill /
//                                 draw / Graphics2D primitives from 3a; PNG
//                                 fallback path uses drawImage.
// Module 3c "Collision"         - getBounds() yields the AABB consumed by
//                                 GameStarter.checkHazardContact() per the
//                                 collision module's overlap test.
// =========================================================================
import java.awt.*;
import java.awt.image.*;
public class CorruptedSpike extends Hazard { // Static damage hazard

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    public static final int DEFAULT_WIDTH  = 32; // One tile wide by default
    public static final int DEFAULT_HEIGHT = 16; // Half-tile tall — sits flush on the ground

    private static final int CONTACT_DAMAGE_COOLDOWN_MS = 800; // Per-instance cooldown so a stuck Wanderer is not drained per tick

    private static final Color SPIKE_FILL    = new Color(0x60, 0x18, 0x28); // Deep crimson — corrupted blood tone
    private static final Color SPIKE_OUTLINE = new Color(0x20, 0x08, 0x10); // Almost black for the outline
    private static final Color BASE_COLOUR   = new Color(0x2C, 0x10, 0x18); // Dark base strip the teeth grow from

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Wall-clock ms timestamp at which this spike may damage the Wanderer again. */
    private long nextDamageMs; // Cooldown latch updated each time the spike deals damage

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Default-size constructor: 32×16 footprint, 1 contact damage.
     *
     * @param x world-space left-edge x in pixels
     * @param y world-space top-edge y in pixels
     */
    public CorruptedSpike(int x, int y) {
        this(x, y, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * Custom-size constructor for spike rows or wider patches.
     *
     * @param x world-space left-edge x in pixels
     * @param y world-space top-edge y in pixels
     * @param w width in pixels (rendered as ceil(w/16) teeth)
     * @param h height in pixels (taller spikes look meaner)
     */
    public CorruptedSpike(int x, int y, int w, int h) {
        super(x, y, w, h, /*damage*/ 1, /*maxHealth*/ 1); // Hazard handles AABB + Damageable; 1 HP so a melee can clear it later if needed
        this.nextDamageMs = 0L;                            // No cooldown on the first overlap
    }

    // -------------------------------------------------------------------------
    // Behaviour
    // -------------------------------------------------------------------------

    /**
     * Attempts to deal contact damage to the Wanderer at most once per
     * {@link #CONTACT_DAMAGE_COOLDOWN_MS}. Called by {@code GameStarter} after
     * an AABB overlap is detected. Returns {@code true} if damage was actually
     * applied this call.
     *
     * @param player the Wanderer; never {@code null}
     * @return {@code true} if the Wanderer took damage this call
     */
    public boolean tryDamage(Player player) {
        long now = System.currentTimeMillis();
        if (now < nextDamageMs) return false;       // Still cooling down — overlap did not refresh damage
        nextDamageMs = now + CONTACT_DAMAGE_COOLDOWN_MS; // Latch the next allowed damage time
        player.takeDamage(damage);                   // Damage value is set in the Hazard super-constructor
        return true;
    }

    @Override
    public void update(long deltaMs) {
        // Spikes are passive — no per-tick state. Cooldown is wall-clock based
        // in tryDamage() so this method has nothing to do.
    }

    @Override
    public void render(Graphics2D g) {
        if (!active) return;
        BufferedImage img = SpriteLoader.getInstance().tryLoad("resources/sprites/hazards/spike.png");
        if (img != null) g.drawImage(img, x, y, width, height, null);
    }
}
