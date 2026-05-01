/**
 * Visible interactable shrine offering the Wanderer a binary choice between two
 * named options (e.g. {@code "POWER_SURGE"} vs {@code "SIGHT_RESTRICTION"} from
 * the P8.6 pre-boss altar). When the Wanderer overlaps an active Altar's bounds,
 * {@link GameStarter#checkAltarTrigger()} opens the choice overlay on the canvas
 * and waits for input; the selected choice is sent to the server via
 * {@link Protocol#ALTAR_CHOICE} and the authoritative effect is applied on both
 * clients via {@link Protocol#ALTAR_RESULT}.
 *
 * <p>Architecture role: Altar is the visible, sprite-overridable counterpart to
 * the existing invisible {@link Trigger} {@code "ALTAR"} dispatch. Both paths
 * latch the same {@code altarActive} flag in {@link GameStarter} so a level may
 * place either (or both) without double-firing. Every Altar carries an optional
 * {@link AltarConcealment} so subclasses of the concealment can hide the altar
 * until a reveal condition is met (P9.2').</p>
 *
 * <p>Sprite override: Altar implements {@link SpriteOverridable}, so a level
 * generator may pass an optional PNG path to swap the procedural pedestal art
 * for a custom bitmap without touching this class.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-29
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.awt.BasicStroke;   // Outline stroke for the rune ring
import java.awt.Color;          // Stone, glow, and rune colours for the procedural fallback
import java.awt.Graphics2D;     // 2D rendering context passed by GameCanvas
import java.awt.RenderingHints; // Anti-aliasing hint for smooth rune curves

public class Altar extends GameElement implements SpriteOverridable { // Visible interactable; opts into sprite override

    // -------------------------------------------------------------------------
    // Default geometry
    // -------------------------------------------------------------------------

    public static final int DEFAULT_WIDTH  = 48; // Pedestal width in px; matches the visual proportions of a single Wanderer height
    public static final int DEFAULT_HEIGHT = 64; // Pedestal height in px; covers two stacked tiles

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * The numeric identifier matching the {@code altarId} param in the server's
     * {@link Protocol#ALTAR_CHOICE} wire format. Ties this entity to the choice
     * the player made when sending the result back to clients.
     */
    private final int altarId; // Stable per-level ID; passed to the network protocol

    /** First choice label shown in the overlay (e.g. {@code "POWER_SURGE"}). */
    private final String option1; // Server uses this string verbatim in ALTAR_RESULT

    /** Second choice label shown in the overlay (e.g. {@code "SIGHT_RESTRICTION"}). */
    private final String option2; // Server uses this string verbatim in ALTAR_RESULT

    /** Optional sprite override path; {@code null} → procedural pedestal draw. */
    private String spritePath; // Set by setSpritePath; honored by SpriteOverridable.tryDrawSprite

    /** Optional concealment behaviour; {@code null} → always visible/interactable. */
    private AltarConcealment concealment; // Set by setConcealment after construction by P9.2' helpers

    /**
     * True once the player has chosen an option at this altar. Prevents the
     * overlay from re-opening on subsequent overlaps within the same level.
     */
    private boolean activated; // Latch; set true after a choice is committed

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs an Altar at the given world position with the two named options.
     * Default 48×64 AABB; sprite path and concealment are {@code null} until
     * configured via setters.
     *
     * @param id    altar identifier; matches the {@code altarId} in the network
     *              protocol; should be unique per level
     * @param x     world-space left-edge x coordinate (px)
     * @param y     world-space top-edge y coordinate (px); y increases downward
     * @param opt1  first choice label; sent as the {@code choice} string when
     *              the player picks option 1
     * @param opt2  second choice label; sent as the {@code choice} string when
     *              the player picks option 2
     */
    public Altar(int id, int x, int y, String opt1, String opt2) { // Five-argument constructor; size is a fixed default
        super(x, y, DEFAULT_WIDTH, DEFAULT_HEIGHT); // Delegate to GameElement: 48×64 AABB anchored at (x,y)
        this.altarId = id;            // Store the ID for ALTAR_CHOICE / ALTAR_RESULT correlation
        this.option1 = opt1;          // Store option 1 label for the overlay and the wire message
        this.option2 = opt2;          // Store option 2 label for the overlay and the wire message
        this.spritePath = null;       // No sprite override by default; procedural pedestal renders below
        this.concealment = null;      // No concealment by default; altar is always visible
        this.activated = false;       // Fresh altar starts un-activated; first overlap opens the overlay
    }

    // -------------------------------------------------------------------------
    // GameElement overrides
    // -------------------------------------------------------------------------

    /**
     * No-op update; the altar is fully passive. Reveal logic lives in the
     * {@link AltarConcealment} (queried during render and overlap), and
     * activation logic lives in {@link GameStarter#checkAltarTrigger()}.
     *
     * @param deltaMs the time elapsed since the last update; ignored
     */
    @Override
    public void update(long deltaMs) {
        // Passive entity: nothing to tick.
    }

    /**
     * Renders the altar. Order: (1) sprite override if a PNG is set and loadable;
     * (2) hidden if a concealment exists and reports unrevealed; (3) procedural
     * stone-pedestal fallback otherwise.
     *
     * <p>The procedural fallback paints a dark stone block with a glowing gold
     * rune ring on top, matching the gold-on-dark visual language of the rest
     * of the game (Portal pillars, fragment shards). The rune pulses subtly
     * via a sinusoidal alpha so the altar is easier to spot from a distance.</p>
     *
     * @param g the {@link Graphics2D} context; never {@code null}
     */
    @Override
    public void render(Graphics2D g) {
        // (1) Sprite override wins when a path is set and the PNG loads.
        if (SpriteOverridable.tryDrawSprite(g, this, x, y, width, height)) return;

        // (2) Concealed altars paint nothing until revealed.
        // Note: when called from GameCanvas there is no LevelState/GameStarter
        // context here; concealments that need them are honoured by the
        // GameStarter-side overlap check (which has both). This render-time
        // check is best-effort and only honours stateless concealments via the
        // optional render-time hook; gameplay correctness is guaranteed by the
        // overlap-side check in GameStarter.checkAltarTrigger().
        if (concealment != null && !concealment.isRevealed(null, null)) {
            // Subclasses that need state will simply return true here (default
            // visible) and let the overlap path do the gating. Concealments
            // that can answer statelessly (e.g. an always-hidden type) hide the
            // altar visually as well.
            return;
        }

        // (3) Procedural fallback — stone pedestal + glowing rune.
        renderProceduralPedestal(g);
    }

    /**
     * Hand-drawn fallback when no sprite is set. Three layers: a back glow,
     * a dark stone block, and a pulsing gold rune ring at the top.
     *
     * @param g the rendering context; non-null
     */
    private void renderProceduralPedestal(Graphics2D g) {
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING); // Save AA hint to restore after the rune draw
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // AA on so the rune ring is smooth

        // Back glow — broad amber pool behind the pedestal so it pops against dark backgrounds
        g.setColor(new Color(0xD4, 0xAF, 0x37, 60)); // Translucent gold (alpha 60/255)
        g.fillOval(x - 8, y + height - 16, width + 16, 32); // Wide ellipse hugging the base

        // Stone block — main pedestal silhouette
        g.setColor(new Color(0x2A, 0x2A, 0x32)); // Cool dark grey to match the dungeon palette
        g.fillRect(x, y + height / 4, width, height * 3 / 4); // Lower 3/4 is the block
        g.setColor(new Color(0x44, 0x44, 0x4E)); // Lighter grey for the top capstone
        g.fillRect(x - 2, y + height / 4 - 4, width + 4, 8); // Slight overhang for the cap

        // Rune ring — gold circle with sinusoidal pulse
        long t = System.currentTimeMillis();          // Real-time clock drives the pulse
        float pulse = (float) (0.6 + 0.4 * Math.sin(t * 0.004)); // Range [0.2, 1.0]
        int alpha = (int) (160 * pulse) + 80;          // Range [80, 240]
        g.setStroke(new BasicStroke(2.0f));            // Thin ring outline
        g.setColor(new Color(0xF0, 0xCC, 0x7A, alpha)); // Bright gold with the pulsing alpha
        int runeSize = Math.min(width - 12, 28);       // Rune fits inside the pedestal width
        int runeX = x + (width - runeSize) / 2;        // Centred horizontally
        int runeY = y + 4;                             // Just below the top edge
        g.drawOval(runeX, runeY, runeSize, runeSize);  // Outer ring

        // Inner cross — simple two-line glyph so the rune reads clearly
        int cx = runeX + runeSize / 2;
        int cy = runeY + runeSize / 2;
        g.drawLine(cx - runeSize / 3, cy, cx + runeSize / 3, cy); // Horizontal stroke
        g.drawLine(cx, cy - runeSize / 3, cx, cy + runeSize / 3); // Vertical stroke

        if (oldAA != null) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA); // Restore AA to the previous value
    }

    // -------------------------------------------------------------------------
    // SpriteOverridable
    // -------------------------------------------------------------------------

    @Override
    public void setSpritePath(String path) { // Setter for the optional PNG override
        this.spritePath = path;               // Null clears the override; the next render falls back to procedural
    }

    @Override
    public String getSpritePath() { // Read accessor for the helper in SpriteOverridable.tryDrawSprite
        return spritePath;          // Returns null when no override is configured
    }

    // -------------------------------------------------------------------------
    // Concealment accessors
    // -------------------------------------------------------------------------

    /**
     * Sets the concealment behaviour for this altar. Pass {@code null} to clear.
     * Called by level generators after construction (e.g.
     * {@code lastAltar().setConcealment(GhostWallConcealment.of())}).
     *
     * @param c the concealment to install, or {@code null} to remove any existing
     */
    public void setConcealment(AltarConcealment c) { // Mutator; called by P9.2' helpers
        this.concealment = c;                         // Stored as-is; null is valid (always visible)
    }

    /**
     * Returns the installed concealment, or {@code null} if the altar is
     * unconditionally visible. Read by {@link GameStarter#checkAltarTrigger()}
     * during the overlap dispatch.
     *
     * @return the concealment, or {@code null}
     */
    public AltarConcealment getConcealment() { // Read accessor
        return concealment;                     // Null when no concealment is set
    }

    // -------------------------------------------------------------------------
    // Identity / state accessors
    // -------------------------------------------------------------------------

    /** @return the altar's per-level identifier; matches {@code altarId} on the wire */
    public int getAltarId() { return altarId; }

    /** @return the first choice label as configured at construction */
    public String getOption1() { return option1; }

    /** @return the second choice label as configured at construction */
    public String getOption2() { return option2; }

    /** @return {@code true} if the player has already committed a choice at this altar */
    public boolean isActivated() { return activated; }

    /**
     * Marks this altar as activated so subsequent overlaps do not re-open the
     * choice overlay. Called by {@link GameStarter} after the
     * {@link Protocol#ALTAR_CHOICE} message has been sent.
     */
    public void setActivated(boolean a) { // Latch setter; once true the overlay stays closed
        this.activated = a;                // Stored directly; no side-effects
    }
}
