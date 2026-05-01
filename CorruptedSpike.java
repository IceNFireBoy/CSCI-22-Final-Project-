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
 * not equal 60 hp/sec) and the procedural rendering with an optional sprite
 * override via {@link SpriteOverridable}.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-29
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.awt.Color;        // Spike palette colours
import java.awt.Graphics2D;    // 2D rendering context
import java.awt.Polygon;        // Triangle silhouettes for each spike tooth

public class CorruptedSpike extends Hazard implements SpriteOverridable { // Static damage hazard with sprite override

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

    /** Optional PNG path that replaces the procedural spike row when set. */
    private String spritePath; // Honoured by {@link SpriteOverridable#tryDrawSprite}

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
        this.spritePath = null;                            // Procedural fallback by default
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
        if (SpriteOverridable.tryDrawSprite(g, this, x, y, width, height)) return; // Sprite override wins

        // Procedural fallback — dark base strip with triangular teeth on top.
        g.setColor(BASE_COLOUR);
        g.fillRect(x, y + height * 2 / 3, width, height / 3);

        // Number of teeth scales with width; one tooth per 16 px (rounded up so even narrow spikes have ≥1 tooth).
        int toothCount = Math.max(1, width / 16);
        int toothW = width / toothCount;
        for (int i = 0; i < toothCount; i++) {
            int leftX = x + i * toothW;
            Polygon tooth = new Polygon(
                new int[]{ leftX, leftX + toothW / 2, leftX + toothW },
                new int[]{ y + height, y, y + height },
                3
            );
            g.setColor(SPIKE_FILL);
            g.fillPolygon(tooth);
            g.setColor(SPIKE_OUTLINE);
            g.drawPolygon(tooth);
        }
    }

    // -------------------------------------------------------------------------
    // SpriteOverridable
    // -------------------------------------------------------------------------

    @Override public void setSpritePath(String path) { this.spritePath = path; }
    @Override public String getSpritePath() { return spritePath; }
}
