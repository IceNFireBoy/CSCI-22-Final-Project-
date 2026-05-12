/**
 * Spawns 5 ground spikes centred on the Wanderer's current x-position with a
 * ±80 px spread (spikes placed at 40 px intervals). Spikes rise over 400 ms,
 * remain fully extended for 1600 ms dealing 1 damage to the Wanderer on contact,
 * then retract over 400 ms. Total duration: 2400 ms.
 *
 * <p>Architecture role: {@code SpikeArray} is one of the five {@link BossAttack}
 * subtypes. It is instantiated by the boss-attack factory in {@link GameStarter}
 * when the Apprentice activates the Spike Array option on the boss panel. Spike
 * positions are calculated at construction time from the Wanderer's current
 * world-space x, creating a telegraphed threat that the player can dodge by moving
 * to a different position before the spikes fully emerge.</p>
 *
 * <p>Visual: Each spike is drawn as a filled triangle (dark purple {@code #4a3050})
 * whose height grows linearly during the rise phase and shrinks during the retract
 * phase. This provides a clear visual warning window before full damage is possible.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */


// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 1c "Abstract Classes" - extends BossAttack (abstract base) and
//                                 customises render and hitbox for one of
//                                 the five attack patterns. Inherits the
//                                 shared lifetime-countdown logic via
//                                 super.update(). Direct application of 1c.
// Module 3a "Graphics"          - render() uses Graphics2D primitives
//                                 (fillRect, drawLine, drawOval) plus
//                                 RenderingHints anti-aliasing per 3a.
// Module 3b "More Graphics"     - some attacks use AffineTransform-style
//                                 rotation (the rotating-beam sweep) and
//                                 AlphaComposite for translucent overlays.
// Module 3c "Collision"         - getHitbox() returns a Rectangle for
//                                 checkBossAttackHits() AABB tests, the
//                                 collision-module pattern.
// =========================================================================
import java.awt.*;
public class SpikeArray extends BossAttack { // Extends BossAttack for the shared 2400 ms lifetime countdown

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * World-space x-coordinates of the left edge of each of the 5 spikes. Computed
     * at construction time from the Wanderer's x-position: spikes are placed at 40 px
     * intervals starting 80 px to the left of the player. The first spike at
     * {@code playerX - 80}, then {@code -40}, {@code 0}, {@code +40}, {@code +80}.
     */
    private int[] spikeX; // 5-element array; each element is the left-edge x of one spike; set at construction

    /**
     * The y-coordinate of the ground surface from which spikes emerge. Spikes grow
     * upward from this y level. Their visible height transitions from 0 to
     * {@link #SPIKE_HEIGHT} during the rise phase.
     */
    private int groundY; // Ground level y; spike base is always at this y; spikes grow upward (decreasing y)

    /** The full height of each spike in pixels when fully extended during the active phase. */
    private static final int SPIKE_HEIGHT = 48; // 48 px = 1.5 standard tile heights; visually imposing but jumpable

    /** The width of each spike in pixels at its base. Used for the triangle polygon and AABB. */
    private static final int SPIKE_WIDTH = 12; // 12 px base width; narrow enough to make spacing between spikes visible

    /**
     * Reference to the Wanderer for contact-damage checks during the active phase
     * (elapsed 400–2000 ms). Contact damage is checked against each fully-extended
     * spike's AABB.
     */
    private Player player; // Wanderer reference; used in update() for per-spike contact damage tests

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code SpikeArray} centred on the Wanderer's current x
     * position, with spikes placed at 40 px intervals across a 160 px span.
     *
     * <p>Architecture role: Called by the boss-attack factory in {@link GameStarter}
     * passing the Wanderer's current world-space x. The 2400 ms duration passed to
     * {@code super()} covers the full rise (400 ms) + active (1600 ms) + retract (400 ms)
     * cycle. The AABB passed to {@code super()} covers the full array span for
     * broad-phase hitbox queries.</p>
     *
     * @param playerX the Wanderer's current world-space x coordinate; used to centre
     *                the spike array
     * @param groundY the y-coordinate of the ground surface; spikes emerge from here
     * @param player  the Wanderer entity for contact-damage checks; may be {@code null}
     *                (null-guarded in update())
     */
    public SpikeArray(int playerX, int groundY, Player player) {          // Three-argument constructor: player x, ground y, Wanderer reference
        super(playerX - 80, groundY - SPIKE_HEIGHT, 160, SPIKE_HEIGHT, 2400L); // Delegate: AABB covers 160×48 px; 2400 ms duration
        this.groundY = groundY;                                             // Store ground y for spike-base positioning in render() and update()
        this.player = player;                                               // Store Wanderer reference for proximity damage checks in update()

        // 5 spikes centred on playerX with +/-40px spread (40 px intervals)
        this.spikeX = new int[5];                                          // Allocate 5-element array for spike x positions
        for (int i = 0; i < 5; i++) {                                      // Place spikes at i=0..4 corresponding to x offsets -80, -40, 0, +40, +80
            spikeX[i] = playerX - 80 + (i * 40);                          // Each spike is 40 px right of the previous, starting at playerX-80
        }
    }

    // -------------------------------------------------------------------------
    // BossAttack overrides
    // -------------------------------------------------------------------------

    /**
     * Advances the spike lifecycle and checks for player contact during the active
     * phase (400–2000 ms). Damage is dealt at most once per tick (the loop returns
     * immediately after the first hit to avoid multiple damage events in the same
     * tick).
     *
     * <p>Lifecycle phases (driven by the inherited {@link BossAttack#elapsed} counter):
     * <ul>
     *   <li>0–400 ms: Rising phase — spikes grow from 0 to {@link #SPIKE_HEIGHT}.</li>
     *   <li>400–2000 ms: Active phase — spikes fully extended; damage enabled.</li>
     *   <li>2000–2400 ms: Retracting phase — spikes shrink back to 0.</li>
     * </ul></p>
     *
     * @param deltaMs the time elapsed since the last tick, in milliseconds
     */
    @Override
    public void update(long deltaMs) {                                               // Advance lifecycle; check contact damage during active phase
        super.update(deltaMs);                                                        // Advance elapsed timer; sets active=false when 2400 ms elapses
        if (!active) {                                                                // Early exit: attack has expired; no damage possible after retract completes
            return;
        }

        // Active damage phase: 400ms to 2000ms
        if (elapsed >= 400 && elapsed <= 2000) {                                     // Spikes are fully extended during this window: enable damage checks
            if (player != null && !player.isInvincible()) {                          // Null guard for player; respect dodge invincibility frames
                int currentHeight = SPIKE_HEIGHT;                                     // During active phase spikes are always at full height
                for (int sx : spikeX) {                                              // Check each spike's AABB against the Wanderer's bounds
                    Rectangle spikeBounds = new Rectangle(
                            sx, groundY - currentHeight, SPIKE_WIDTH, currentHeight); // Build spike AABB: left=sx, top=groundY-height, width=SPIKE_WIDTH, height=currentHeight
                    if (spikeBounds.intersects(player.getBounds())) {                 // AABB overlap: player is standing in or touching this spike
                        player.takeDamage(1);                                         // Deal 1 damage; spikes deal consistent 1 damage regardless of position in array
                        return;  // Only one hit per tick                             // Return immediately: only one spike can hit the player per tick, preventing stacking
                    }
                }
            }
        }
    }

    /**
     * Renders all 5 spikes at their current height based on the elapsed time phase.
     * Each spike is drawn as a filled triangle pointing upward in dark purple.
     *
     * <p>Height computation:
     * <ul>
     *   <li>Rise (elapsed &lt; 400): {@code height = SPIKE_HEIGHT × (elapsed / 400)}</li>
     *   <li>Active (400 ≤ elapsed ≤ 2000): {@code height = SPIKE_HEIGHT}</li>
     *   <li>Retract (elapsed &gt; 2000): {@code height = SPIKE_HEIGHT × (1 - (elapsed-2000)/400)}</li>
     * </ul></p>
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    @Override
    public void render(Graphics2D g) {                                               // Draw each spike as a filled triangle at its current phase-driven height
        if (!active) {                                                                // Do not render expired spikes (retract animation has completed)
            return;
        }

        // Calculate current spike height based on phase
        int currentHeight;                                                            // Height of each spike this frame; varies by lifecycle phase
        if (elapsed < 400) {                                                          // Rising phase: 0 to SPIKE_HEIGHT over 400 ms
            float progress = (float) elapsed / 400f;                                 // Normalise elapsed to [0,1] over the 400 ms rise window
            currentHeight = (int) (SPIKE_HEIGHT * progress);                         // Linear interpolation from 0 px to SPIKE_HEIGHT (48 px)
        } else if (elapsed <= 2000) {                                                 // Active phase: spikes at full height
            currentHeight = SPIKE_HEIGHT;                                             // Full 48 px height; no interpolation needed
        } else {                                                                      // Retracting phase: SPIKE_HEIGHT to 0 over 400 ms (elapsed 2000–2400)
            float progress = (float) (elapsed - 2000) / 400f;                        // Normalise retract elapsed to [0,1] over the 400 ms retract window
            currentHeight = (int) (SPIKE_HEIGHT * (1f - progress));                  // Linear interpolation from SPIKE_HEIGHT to 0 px
        }

        if (currentHeight <= 0) {                                                     // Guard: don't draw degenerate zero-height spikes (at the very start or end)
            return;                                                                   // Skip rendering; no visible spike at 0 height
        }

        // Draw each spike as a triangle-ish shape
        g.setColor(new Color(0x4a, 0x30, 0x50));                                     // Dark purple #4a3050: ominous colour contrasting with the gold boss-panel palette
        for (int sx : spikeX) {                                                       // Draw one triangle per spike x-position
            int spikeTop = groundY - currentHeight;                                   // Top vertex y: ground level minus current height (spikes grow upward)
            // Draw as filled polygon (triangle spike)
            int[] xPoints = {sx, sx + SPIKE_WIDTH / 2, sx + SPIKE_WIDTH};           // x vertices: left base, tip centre, right base
            int[] yPoints = {groundY, spikeTop, groundY};                            // y vertices: both base corners at groundY; tip at spikeTop (higher up)
            g.fillPolygon(xPoints, yPoints, 3);                                      // Draw filled triangle with 3 vertices; creates the spike silhouette
        }
    }
}
