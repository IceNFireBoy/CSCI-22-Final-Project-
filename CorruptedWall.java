/**
 * Background wall hazard with a three-phase fall-on-Wanderer attack. Sits in
 * the level as a passive backdrop until the Wanderer enters its trigger zone;
 * shakes for 1 second (dodge window); then falls toward the Wanderer's last
 * recorded ground level, dealing heavy contact damage on impact.
 *
 * <p>Architecture role: extends {@link Hazard} so the existing GameStarter
 * hazard-contact path (AABB → damage → Player.takeDamage) handles the
 * landing impact. The trigger detection happens via
 * {@link #checkTrigger(Player)} called from GameStarter each tick — no Player
 * back-reference is stored on the hazard, mirroring the pattern of the other
 * P9.3' hazards.</p>
 *
 * <p>FSM:
 * <pre>
 *   IDLE      → background, harmless, no trigger overlap yet
 *   WARNING   → 1000 ms shake + dodge window; Wanderer can leave the trigger
 *               zone to abort, but only via setReturnsAfterDodge(false) — by
 *               default the wall stays committed to falling once warning starts
 *   FALLING   → drops toward the ground at FALL_SPEED px/tick; AABB becomes
 *               active for damage; cooldown after impact prevents re-trigger
 *   LANDED    → static debris pile on the floor; can be set to RESET after a
 *               configurable interval if the level wants reusable traps
 * </pre>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-29
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.awt.Color;        // Wall + warning palette
import java.awt.Graphics2D;    // 2D rendering context
import java.awt.Rectangle;      // Trigger zone math

public class CorruptedWall extends Hazard implements SpriteOverridable { // Falling hazard with FSM

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    public static final long WARNING_DURATION_MS = 1000L; // 1-second dodge window
    public static final int  FALL_SPEED          = 9;     // px per tick (fast enough to feel committed)
    public static final int  IMPACT_DAMAGE       = 3;     // Heavy hit on landing

    private static final int SHAKE_AMPLITUDE = 4; // Max pixel offset during the warning shake

    private static final Color WALL_FILL_IDLE    = new Color(0x30, 0x18, 0x20); // Dark corrupted stone
    private static final Color WALL_FILL_WARNING = new Color(0x70, 0x18, 0x20); // Reddened during warning
    private static final Color WALL_OUTLINE      = new Color(0x10, 0x06, 0x0C); // Almost black outline

    // -------------------------------------------------------------------------
    // FSM
    // -------------------------------------------------------------------------

    /** Three-phase FSM driving the wall's state through its life cycle. */
    public enum WallPhase { IDLE, WARNING, FALLING, LANDED }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final int triggerW;        // Trigger zone width — typically wider than the wall itself
    private final int triggerH;        // Trigger zone height — typically taller
    private final int initialY;         // Starting world-space y; needed for the procedural fall arc and post-fall debris position

    private WallPhase phase;            // Current FSM state
    private long warningStartMs;        // Wall-clock ms when WARNING began; used to time the shake → fall transition
    private int  shakeOffsetX;          // Per-frame jitter during WARNING; cleared once falling begins
    private int  shakeOffsetY;          // Same for vertical jitter
    private int  fallTargetY;           // World-space y at which the wall lands (computed from Wanderer position when WARNING started)

    private String spritePath;          // Optional PNG override

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a corrupted wall.
     *
     * @param x        world-space left-edge x of the visible wall in pixels
     * @param y        world-space top-edge y of the visible wall in pixels
     * @param w        visible wall width in pixels
     * @param h        visible wall height in pixels
     * @param triggerW trigger-zone width in pixels (centred on the wall)
     * @param triggerH trigger-zone height in pixels (centred on the wall)
     */
    public CorruptedWall(int x, int y, int w, int h, int triggerW, int triggerH) {
        super(x, y, w, h, IMPACT_DAMAGE, 1);
        this.triggerW    = Math.max(triggerW, w);  // Never smaller than the wall itself — that would be unintuitive
        this.triggerH    = Math.max(triggerH, h);
        this.initialY    = y;
        this.phase       = WallPhase.IDLE;
        this.warningStartMs = 0L;
        this.shakeOffsetX = 0;
        this.shakeOffsetY = 0;
        this.fallTargetY  = y;
        this.spritePath   = null;
    }

    // -------------------------------------------------------------------------
    // Trigger detection
    // -------------------------------------------------------------------------

    /**
     * Returns the trigger zone — the rectangle whose overlap with the Wanderer
     * promotes the wall from IDLE to WARNING. Centred on the visible wall.
     *
     * @return a freshly-allocated {@link Rectangle}; never {@code null}
     */
    public Rectangle getTriggerZone() {
        int tx = x + width / 2 - triggerW / 2;          // Centre the trigger zone horizontally on the wall
        int ty = initialY + height / 2 - triggerH / 2;  // Centre vertically on the wall's *initial* y (so falling does not change the zone)
        return new Rectangle(tx, ty, triggerW, triggerH);
    }

    /**
     * Called by {@code GameStarter} each tick. Promotes IDLE → WARNING when
     * the Wanderer enters the trigger zone; transitions WARNING → FALLING
     * after {@link #WARNING_DURATION_MS}. Once falling, no further trigger
     * detection is needed — the AABB damage path handles impact.
     *
     * @param player the Wanderer; never {@code null}
     */
    public void checkTrigger(Player player) {
        if (phase == WallPhase.LANDED) return; // Permanent debris — no further state changes by default

        long now = System.currentTimeMillis();
        if (phase == WallPhase.IDLE) {
            if (player.getBounds().intersects(getTriggerZone())) {
                phase = WallPhase.WARNING;
                warningStartMs = now;
                // Lock in the fall target — drop to the Wanderer's current foot line.
                fallTargetY = player.getY() + player.getBounds().height - height;
            }
            return;
        }
        if (phase == WallPhase.WARNING) {
            if (now - warningStartMs >= WARNING_DURATION_MS) {
                phase = WallPhase.FALLING;
                shakeOffsetX = 0;
                shakeOffsetY = 0;
            }
            return;
        }
        // FALLING is handled in update(deltaMs) — purely time-driven, no Player query needed.
    }

    // -------------------------------------------------------------------------
    // Per-tick simulation
    // -------------------------------------------------------------------------

    @Override
    public void update(long deltaMs) {
        if (!active) return;

        switch (phase) {
            case WARNING:
                // Random jitter for the visual shake. New offset each tick keeps it lively
                // without ever moving the AABB itself — only the renderer reads these.
                shakeOffsetX = (int) ((Math.random() - 0.5) * 2 * SHAKE_AMPLITUDE);
                shakeOffsetY = (int) ((Math.random() - 0.5) * 2 * SHAKE_AMPLITUDE);
                break;
            case FALLING:
                // Drop toward the locked fall target. Once we reach (or pass) it, settle.
                if (y < fallTargetY) {
                    y += FALL_SPEED;
                    if (y >= fallTargetY) {
                        y = fallTargetY;
                        phase = WallPhase.LANDED;
                    }
                } else {
                    phase = WallPhase.LANDED;
                }
                break;
            case IDLE:
            case LANDED:
            default:
                // No per-tick work in these phases.
                break;
        }
    }

    /**
     * Returns whether the wall is currently dangerous (FALLING or freshly
     * LANDED). Read by {@code GameStarter} to gate the contact-damage path —
     * an IDLE or WARNING wall is just a backdrop and should not drain HP.
     *
     * @return {@code true} if Wanderer overlap should deal damage
     */
    public boolean isDangerous() {
        return phase == WallPhase.FALLING || phase == WallPhase.LANDED;
    }

    /** @return the current FSM phase; useful for sprite swaps and SFX cues */
    public WallPhase getPhase() { return phase; }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    @Override
    public void render(Graphics2D g) {
        if (!active) return;

        // Sprite override wins; the override is rendered with the same shake
        // offset during WARNING so the player still sees the danger cue.
        int rx = x + (phase == WallPhase.WARNING ? shakeOffsetX : 0);
        int ry = y + (phase == WallPhase.WARNING ? shakeOffsetY : 0);
        if (spritePath != null
            && SpriteOverridable.tryDrawSprite(g, this, rx, ry, width, height)) return;

        // P9.6' convention path — try resources/sprites/hazards/wall.png when
        // no explicit override is set.
        if (spritePath == null) {
            java.awt.image.BufferedImage img = SpriteLoader.getInstance()
                .tryLoad("resources/sprites/hazards/wall.png");
            if (img != null) {
                g.drawImage(img, rx, ry, width, height, null);
                return;
            }
        }

        // Procedural fallback. Colour shifts during WARNING and FALLING to red.
        Color fill;
        switch (phase) {
            case WARNING:
            case FALLING:
                fill = WALL_FILL_WARNING;
                break;
            case LANDED:
                fill = new Color(0x40, 0x10, 0x18); // Slightly darker post-impact
                break;
            case IDLE:
            default:
                fill = WALL_FILL_IDLE;
        }
        g.setColor(fill);
        g.fillRect(rx, ry, width, height);
        g.setColor(WALL_OUTLINE);
        g.drawRect(rx, ry, width, height);

        // Brick-pattern detail to break up the silhouette.
        g.setColor(WALL_OUTLINE);
        for (int row = 16; row < height; row += 16) {
            g.drawLine(rx, ry + row, rx + width, ry + row);
        }
        for (int col = 16; col < width; col += 32) {
            int offset = ((col / 32) % 2 == 0) ? 0 : 16; // Stagger every other row
            for (int row = 0; row < height; row += 16) {
                if ((row / 16) % 2 == 0) {
                    g.drawLine(rx + col, ry + row, rx + col, ry + row + 16);
                } else if (col + offset < width) {
                    g.drawLine(rx + col + offset, ry + row, rx + col + offset, ry + row + 16);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // SpriteOverridable
    // -------------------------------------------------------------------------

    @Override public void setSpritePath(String path) { this.spritePath = path; }
    @Override public String getSpritePath() { return spritePath; }
}
