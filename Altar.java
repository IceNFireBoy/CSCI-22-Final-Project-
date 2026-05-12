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
// Module 1c "Abstract Classes" - extends GameElement, the abstract base
//                                 covered in the abstract-classes module.
// Module 3a "Graphics"          - render(Graphics2D) override uses 3a's
//                                 drawing primitives (fill, draw, drawImage)
//                                 plus 3a's RenderingHints anti-aliasing.
// Module 3c "Collision"         - getBounds() / getHitbox() returns a
//                                 Rectangle for AABB tests per 3c.
// Module 1a "Modifiers"         - private fields with public accessors;
//                                 where present, static final constants
//                                 follow the constants pattern from 1a.
// =========================================================================
import java.awt.*;
import java.awt.image.*;
public class Altar extends GameElement { // Visible interactable

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
     * Renders the altar using the convention sprite, or nothing if the sprite is missing.
     * Concealed altars are hidden until the concealment reports revealed.
     *
     * @param g the {@link Graphics2D} context; never {@code null}
     */
    @Override
    public void render(Graphics2D g) {
        BufferedImage img = SpriteLoader.getInstance().tryLoad("resources/sprites/altars/stone.png");
        if (img != null) {
            if (concealment != null && !concealment.isRevealed(null, null)) return;
            g.drawImage(img, x, y, width, height, null);
        }
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
