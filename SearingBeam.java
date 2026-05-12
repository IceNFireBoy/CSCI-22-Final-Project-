/**
 * A continuous, directed energy beam that the Apprentice aims at the Wanderer's
 * position during the boss fight. The beam originates from a fixed point on the boss
 * panel and extends to a world-space target that the Apprentice updates via gesture.
 * Deals 1 damage to the Wanderer every 30 ticks when the beam endpoint is within
 * 25 pixels of the player's centre. The beam persists until the attack is explicitly
 * deactivated; its duration field is set to {@link Long#MAX_VALUE} so the inherited
 * lifetime countdown never fires.
 *
 * <p>Architecture role: {@code SearingBeam} is one of the five {@link BossAttack}
 * subtypes. Unlike the other attacks, it has an indefinite lifetime: the Apprentice
 * holds the beam by keeping the gesture active, and {@link GameStarter} deactivates
 * it by calling {@link #setActive(boolean)} when the gesture is released. The
 * 30-tick damage throttle (approximately 2 hits per second at 60 fps) prevents the
 * beam from being an instant-kill weapon while still providing meaningful pressure.</p>
 *
 * <p>Rendering: Drawn as a two-layer line from {@link #BEAM_ORIGIN_X}/{@link #BEAM_ORIGIN_Y}
 * to {@link #beamTarget}: a wide (8 px) semi-transparent glow layer at alpha 0.2
 * beneath a narrower (4 px) fully-opaque core beam, both in warm cream gold
 * {@code #f8e4a0}.</p>
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
public class SearingBeam extends BossAttack { // Extends BossAttack but uses Long.MAX_VALUE duration; deactivated manually via setActive(false)

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * The world-space coordinates the beam is currently aimed at. Updated by
     * {@link GameStarter} each tick from the Apprentice's gesture input via
     * {@link #setBeamTarget(Point)}, allowing the beam to track the cursor
     * in real time.
     */
    private Point beamTarget; // Live beam endpoint; updated by setBeamTarget() from Apprentice gesture messages

    /**
     * Whether the beam is currently in damage-dealing mode. Normally {@code true};
     * can be set to {@code false} by {@link #setDamageActive(boolean)} to temporarily
     * disable damage without deactivating the visual (e.g. during a brief invulnerability
     * cutscene window).
     */
    private boolean damageActive; // Damage flag; normally true; set false during brief invulnerability windows

    /**
     * Reference to the Wanderer for proximity-damage checks each tick. Checked
     * against the beam endpoint position to determine whether the beam is
     * "on target" for the tick's damage calculation.
     */
    private Player player; // Wanderer reference for distance-based damage checks in update()

    /**
     * Per-tick counter used to throttle damage to once every 30 ticks (approximately
     * twice per second at 60 fps). Incremented every tick; damage is applied when
     * {@code damageTick % 30 == 0}.
     */
    private int damageTick; // Monotonically increasing tick counter; modulo 30 gate limits damage to ~2 hits/second

     
    private static final int BEAM_ORIGIN_X = 717; // Fixed x at the boss panel centre-left; matches the Shield position

     
    private static final int BEAM_ORIGIN_Y = 384; // Fixed y at the boss panel vertical centre

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code SearingBeam} aimed at the specified initial target.
     * The duration is set to {@link Long#MAX_VALUE} so the inherited lifetime
     * countdown never fires; the beam persists until manually deactivated via
     * {@link #setActive(boolean)}.
     *
     * <p>Architecture role: Called by the boss-attack factory in {@link GameStarter}
     * when the Apprentice initiates a Searing Beam gesture. The {@code beamTarget}
     * is updated each tick from the Apprentice's cursor position while the gesture
     * is held.</p>
     *
     * @param beamTarget   the initial world-space target coordinates; updated via
     *                     {@link #setBeamTarget(Point)} each subsequent tick
     * @param player       the Wanderer entity for proximity-damage checks; may be
     *                     {@code null} (null-guarded in update())
     */
    public SearingBeam(Point beamTarget, Player player) {                              // Two-argument constructor: target, player; Long.MAX_VALUE = indefinite duration
        super(BEAM_ORIGIN_X, BEAM_ORIGIN_Y, 0, 0, Long.MAX_VALUE);                  // Delegate: origin at (717,384); zero AABB; effectively infinite duration
        this.beamTarget = beamTarget;                                                  // Store initial target; updated by Apprentice gesture each tick
        this.damageActive = true;                                                      // Damage enabled by default; toggled off during brief invulnerability windows
        this.player = player;                                                          // Store Wanderer reference for per-tick proximity distance calculation
        this.damageTick = 0;                                                           // Start counter at 0; first potential damage hit at tick 30 (0.5 s after spawn)
    }

    // -------------------------------------------------------------------------
    // BossAttack override
    // -------------------------------------------------------------------------

    /**
     * Increments the damage tick counter and applies 1 point of damage to the
     * Wanderer every 30 ticks when the beam endpoint is within 25 pixels of the
     * player's centre.
     *
     * <p>Architecture role: Called every 16 ms by the game loop in
     * {@link GameStarter}. Deliberately does <em>not</em> call {@code super.update()}
     * because the lifetime is {@link Long#MAX_VALUE} — letting the base class run
     * would trigger immediate expiry on overflow rather than waiting for manual
     * deactivation. The {@code !active} check at the top handles the case where
     * the beam was manually deactivated mid-tick.</p>
     *
     * <p>Distance check: The beam is considered "on target" when the Euclidean
     * distance from {@link #beamTarget} to the Wanderer's centre is less than 25 px.
     * The player centre is estimated as {@code (x+12, y+16)} (half of the approximate
     * sprite width/height), not the exact hitbox centre, which is intentional to
     * match the visual impression of the beam endpoint.</p>
     *
     * @param deltaMs the time elapsed since the last tick; accepted for API
     *                symmetry but not used (the beam is always "on" while active)
     */
    @Override
    public void update(long deltaMs) {                                              // Per-tick beam logic; does NOT call super.update() — duration is indefinite
        // Do NOT call super.update() — duration is Long.MAX_VALUE (continuous)
        if (!active) {                                                               // Beam was manually deactivated (gesture released); no further processing
            return;                                                                   // Early exit: inactive beam neither damages nor counts ticks
        }

        damageTick++;                                                                // Increment tick counter; used by modulo 30 gate below

        if (damageActive && player != null && !player.isInvincible()) {             // All three conditions must hold: damage enabled, player exists, no i-frames
            double dx = beamTarget.x - (player.getX() + 12);                       // Horizontal distance from beam target to estimated player centre (x + 12 px)
            double dy = beamTarget.y - (player.getY() + 16);                       // Vertical distance from beam target to estimated player centre (y + 16 px)
            double dist = Math.sqrt(dx * dx + dy * dy);                            // Euclidean distance between beam endpoint and player centre

            if (dist < 25 && damageTick % 30 == 0) {                              // Beam is "on target" (within 25 px) AND 30-tick damage window has elapsed
                player.takeDamage(1);                                               // Deal 1 damage to the Wanderer; ~2 damage/second at 60 fps when sustained
            }
        }
    }

    /**
     * Renders the beam as a two-layer glowing line from the fixed origin to the
     * current target. The wider semi-transparent layer creates a soft glow halo;
     * the narrower fully-opaque layer forms the sharp visible core.
     *
     * <p>Architecture role: Called every frame by {@link GameCanvas#renderElements}
     * while the beam is active. The saved/restored composite and stroke state ensures
     * no visual bleed into adjacent render calls.</p>
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    @Override
    public void render(Graphics2D g) {                                             // Draw the two-layer beam line; skip if inactive
        if (!active) {                                                              // Do not render an inactive (released) beam
            return;
        }

        Composite originalComposite = g.getComposite(); // Save caller's composite so alpha settings don't bleed into sibling renders
        Stroke originalStroke = g.getStroke();           // Save caller's stroke so our 8f and 4f settings don't affect subsequent draws

        // Glow layer (wider, semi-transparent)
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f)); // 20% opacity: soft ambient glow around the beam core
        g.setColor(new Color(0xf8, 0xe4, 0xa0));                                   // Warm cream gold #f8e4a0: lighter than the portal gold for an energy-beam feel
        g.setStroke(new BasicStroke(8f));                                           // 8 px wide stroke: creates a broad halo effect around the beam
        g.drawLine(BEAM_ORIGIN_X, BEAM_ORIGIN_Y, beamTarget.x, beamTarget.y);     // Draw the wide glow line from origin to target

        // Core beam
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)); // Fully opaque: the beam core is sharply visible
        g.setColor(new Color(0xf8, 0xe4, 0xa0));                                   // Same warm cream gold as the glow layer for visual consistency
        g.setStroke(new BasicStroke(4f));                                           // 4 px stroke: narrower than the glow, forms the bright centre line
        g.drawLine(BEAM_ORIGIN_X, BEAM_ORIGIN_Y, beamTarget.x, beamTarget.y);     // Draw the narrow core line over the glow layer

        g.setComposite(originalComposite); // Restore caller's composite mode to prevent alpha leakage
        g.setStroke(originalStroke);       // Restore caller's stroke width to prevent thickness leakage
    }

    // -------------------------------------------------------------------------
    // Mutators
    // -------------------------------------------------------------------------

    /**
     * Updates the world-space coordinates the beam is aimed at. Called by
     * {@link GameStarter} each tick from the Apprentice's gesture position data
     * so the beam tracks the cursor in real time.
     *
     * <p>Interaction: The new target is used immediately on the next
     * {@link #update(long)} call for the damage-proximity check and on the next
     * {@link #render(Graphics2D)} call to draw the line endpoint.</p>
     *
     * @param target the new world-space target point; must not be {@code null}
     */
    public void setBeamTarget(Point target) { // Called by GameStarter each tick from Apprentice gesture data; updates aim point in real time
        this.beamTarget = target;              // Replace the stored target reference; used by the next update/render pair
    }

    /**
     * Sets whether the beam can currently deal damage. Setting to {@code false}
     * keeps the visual active but suppresses the damage check in
     * {@link #update(long)}.
     *
     * <p>Architecture role: May be called by {@link GameStarter} during brief
     * invulnerability windows (e.g. after the Wanderer triggers a dodge roll)
     * to prevent the beam from dealing damage for a short window without
     * interrupting the gesture.</p>
     *
     * @param damageActive {@code true} to re-enable damage; {@code false} to
     *                     temporarily suppress it
     */
    public void setDamageActive(boolean damageActive) { // Toggles damage-dealing without deactivating the visual; used for invulnerability windows
        this.damageActive = damageActive;                // Overwrite the flag; checked in update() before the proximity damage test
    }
}
