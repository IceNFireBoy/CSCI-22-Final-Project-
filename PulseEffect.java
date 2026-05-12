/**
 * A brief expanding ring of golden light emitted at the Wanderer's world position when
 * a pulse ability is activated. The ring grows outward from the origin while fading,
 * providing a visible area-of-effect cue for both players showing the reach of the
 * pulse before it disappears.
 *
 * <p>Architecture role: {@code PulseEffect} is a purely visual, transient
 * {@link GameElement} subclass created by {@link Player#executePulse()} (or the
 * equivalent ability handler in {@link GameStarter}) at the Wanderer's current
 * world-space centre. It is added to {@link GameStarter#elements} immediately and
 * deactivates itself once its alpha reaches zero (after approximately 20 ticks at
 * 60 fps). Because it is a {@code GameElement}, no special handling is needed: the
 * standard update/render loop in {@link GameStarter} and {@link GameCanvas} drives
 * it correctly, and the post-tick cleanup sweep removes it after deactivation.</p>
 *
 * <p>The ring expands at 8 pixels per tick and fades by 0.045 per tick, giving a
 * total expand distance of roughly 160 pixels (20 ticks × 8 px) and an appearance
 * duration of approximately 0.33 s at 60 fps. The maximum radius field (200 px) is
 * retained as a safety cap; in practice the alpha reaches zero first at ~160 px.</p>
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
import java.awt.geom.*;
public class PulseEffect extends GameElement { // Extends GameElement for automatic management by the entity update/render loop and cleanup sweep

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * The current outer radius of the expanding ring in pixels. Starts at 0 and
     * increases by 8 pixels every tick. The ring is drawn as a circle of this
     * radius centred on ({@link #x}, {@link #y}). Becomes the bounding box
     * half-dimension used in the {@link Ellipse2D.Double} constructor.
     */
    private int radius; // Current ring radius; incremented by 8 px per tick in update(); drives Ellipse2D draw dimensions

    /**
     * The maximum radius the ring can reach before the effect ends. In practice
     * the alpha reaches zero before this limit is hit; retained as a safety cap
     * to ensure the ring never exceeds arena-scale dimensions.
     */
    private int maxRadius; // Safety maximum; 200 px; not used in a conditional check currently but documents the intended upper bound

    /**
     * The current opacity of the ring, ranging from 0.9 (fully spawned) down to
     * 0.0 (fully faded). Decremented by 0.045 each tick. When it reaches 0.0, the
     * effect deactivates itself. Passed to {@link AlphaComposite#getInstance} to
     * set the draw opacity on each frame.
     */
    private float alpha; // Current opacity [0.0, 0.9]; decremented 0.045/tick; reaches 0 after ~20 ticks (≈0.33 s at 60 fps)

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code PulseEffect} centred at the given world coordinates.
     * The ring starts at radius 0 with alpha 0.9 and a maximum radius of 200 px.
     *
     * <p>Architecture role: Called by {@link Player#executePulse()} (or equivalent)
     * at the Wanderer's current world-space centre position. The zero width/height
     * passed to {@link GameElement} means the AABB has no extent — the ring is
     * visual-only and is never used for collision tests, so its inherited
     * {@link GameElement#getBounds()} result is a degenerate zero-size rectangle
     * that will never intersect anything.</p>
     *
     * @param centreX the world-space x coordinate of the pulse origin (ring centre)
     * @param centreY the world-space y coordinate of the pulse origin (ring centre)
     */
    public PulseEffect(int centreX, int centreY) { // Two-argument constructor: only the origin matters; visual dimensions are driven by the radius field
        super(centreX, centreY, 0, 0);             // Delegate to GameElement with 0×0 AABB: this effect has no collision footprint
        this.radius = 0;                            // Start at zero radius so the ring begins as a point at the origin on the first frame
        this.maxRadius = 200;                       // Safety cap: 200 px is well beyond the fade-out point (~160 px) but prevents runaway expansion
        this.alpha = 0.9f;                          // Start near-fully opaque (0.9) for a strong initial visual impact; fades each tick
    }

    // -------------------------------------------------------------------------
    // GameElement overrides
    // -------------------------------------------------------------------------

    /**
     * Expands the ring radius and fades the alpha each tick, then deactivates the
     * effect once the alpha reaches zero.
     *
     * <p>Architecture role: Called every 16 ms by the game loop in
     * {@link GameStarter}. The radius and alpha are both updated unconditionally
     * before the deactivation check, ensuring the final frame renders an almost-
     * invisible ring at the correct expanded size rather than freezing at the
     * previous frame's values.</p>
     *
     * <p>Interaction: When {@link #setActive(boolean) setActive(false)} is called
     * (alpha ≤ 0), the {@link GameCanvas#renderElements} pass skips this element
     * and {@link GameStarter}'s post-tick cleanup removes it from the elements list,
     * ending the effect with no further processing cost.</p>
     *
     * @param deltaMs the time elapsed since the last update, in milliseconds;
     *                used by the fixed-timestep loop but the expand/fade rates here
     *                are fixed per-tick rather than time-scaled, matching the
     *                60 fps loop cadence
     */
    @Override
    public void update(long deltaMs) {  // Advance ring expansion and fade each tick; called by GameStarter's game loop
        radius += 8;                    // Expand the ring by 8 px this tick; at 60 fps this gives 480 px/s expansion speed
        alpha -= 0.045f;               // Fade alpha by 0.045 this tick; starting at 0.9, reaches 0 after exactly 20 ticks (~0.33 s)
        if (alpha <= 0f) {             // Check if the ring has fully faded out
            alpha = 0f;               // Clamp to zero to prevent negative alpha values from being passed to AlphaComposite
            setActive(false);          // Deactivate: marks the element for removal by GameStarter's cleanup sweep
        }
    }

    /**
     * Renders the expanding golden ring onto the provided graphics context using the
     * current radius and alpha. The composite and stroke are saved and restored around
     * the draw call to prevent state leakage into subsequent render calls.
     *
     * <p>Architecture role: Called every frame by {@link GameCanvas#renderElements}
     * for every active element. The ring is drawn as an unfilled {@link Ellipse2D.Double}
     * outline (not a filled circle) with a 1.5 px gold stroke, matching the border
     * weight of other game HUD elements.</p>
     *
     * <p>The origin ({@link #x}, {@link #y}) is in world space. If this effect was
     * created during the boss phase, the camera transform applied by
     * {@link GameCanvas#renderBoss} has already shifted the graphics context, so the
     * ring appears at the correct screen position automatically.</p>
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null};
     *          may be translated into world space by the Camera offset
     */
    @Override
    public void render(Graphics2D g) {                                                              // Draw the ring at current radius and alpha; skip if inactive or fully faded
        if (!active || alpha <= 0f) {                                                               // Early exit: do not draw if deactivated or alpha already reached zero
            return;                                                                                  // No rendering needed; effect is complete
        }

        Composite originalComposite = g.getComposite();                                             // Save caller's composite before applying alpha; must restore to prevent bleed into sibling renders
        Stroke originalStroke = g.getStroke();                                                      // Save caller's stroke before setting 1.5 px; restore after to avoid affecting subsequent draw calls

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));                // Apply current alpha (0.0–0.9) via SRC_OVER compositing for smooth fade
        g.setColor(new Color(0xc9, 0xa8, 0x4c));                                                   // Warm gold colour #c9a84c: matches Portal frame, Shield, and HUD accent palette
        g.setStroke(new BasicStroke(1.5f));                                                         // 1.5 px thin stroke: fine enough to read as a ring without being visually heavy

        Ellipse2D.Double ring = new Ellipse2D.Double(                                               // Construct an ellipse centred on (x,y) with diameter = 2*radius on both axes
                x - radius, y - radius, radius * 2, radius * 2);                                   // Top-left corner at (x-radius, y-radius); width/height = 2*radius → circle centred at (x,y)
        g.draw(ring);                                                                               // Draw only the outline (not filled): produces the expanding ring visual

        g.setComposite(originalComposite);                                                          // Restore original composite mode so subsequent elements are unaffected by the alpha setting
        g.setStroke(originalStroke);                                                                // Restore original stroke so subsequent outlines use their own intended line widths
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the current radius of the pulse ring in pixels.
     *
     * <p>Interaction: May be read by {@link GameStarter}'s pulse-hit-detection
     * logic to determine which game entities fall within the ring's current reach.
     * For example, hazards within the ring radius could be affected when
     * the pulse reaches them.</p>
     *
     * @return the current ring radius; starts at 0 and increases by 8 px per tick
     */
    public int getRadius() { // Read accessor for the current ring radius; used by pulse hit-detection and debug tools
        return radius;       // Return the stored radius; incremented in update() each tick
    }

    /**
     * Returns the current opacity of the pulse ring.
     *
     * <p>Interaction: Used by any system that needs to know the visual intensity
     * of the pulse (e.g. a particle system that emits sparks proportional to alpha),
     * or by tests that verify the fade-out lifecycle.</p>
     *
     * @return the current alpha value in the range {@code [0.0, 0.9]};
     *         0.0 means fully faded and the element is about to be or has been
     *         deactivated
     */
    public float getAlpha() { // Read accessor for the current opacity; used by particle systems or lifecycle tests
        return alpha;         // Return the stored alpha; clamped to 0.0 by update() before setActive(false) is called
    }
}
