/**
 * Moving platform whose motion is gated by the Apprentice's light. The block
 * follows a simple parametric path (linear oscillation along x or y, or
 * circular orbit around its origin), and the {@link LightBehavior} mode
 * decides whether light freezes or unfreezes the motion.
 *
 * <p>Architecture role: extends {@link Platform} with type {@code BRICK} so
 * the existing CollisionDetector path treats it as a standard solid surface.
 * The block's {@code update(deltaMs)} advances an internal phase counter
 * unless the light gate is active; pausing the phase counter (rather than
 * resetting it) means the platform resumes from where it stopped, which makes
 * the puzzle-pacing feel responsive rather than punishing.</p>
 *
 * <p>Modes:
 * <ul>
 *   <li>{@code FREEZE_ON_LIGHT} (default) — block moves on its pattern; when
 *       the Apprentice lights it, motion pauses. Useful for "stop the runaway
 *       elevator so the Wanderer can step off" puzzles.</li>
 *   <li>{@code MOVE_ON_LIGHT} — block is frozen; when lit, it begins moving.
 *       Useful for "shine on the gate to open the path" puzzles.</li>
 * </ul>
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
public class LightLockedMover extends Platform { // Light-gated moving platform

    // -------------------------------------------------------------------------
    // Mode enums
    // -------------------------------------------------------------------------

    /** The motion path traced by the platform when its motion is enabled. */
    public enum MovePattern {
        LINEAR_X,   // Sinusoidal oscillation along x; amplitude in pixels
        LINEAR_Y,   // Sinusoidal oscillation along y
        CIRCULAR    // Circular orbit around the construction origin; amplitude is the radius
    }

    /** Decides whether the light gates motion or unfreezes motion. */
    public enum LightBehavior {
        FREEZE_ON_LIGHT, // Default: moves until lit, then frozen
        MOVE_ON_LIGHT    // Inverse: frozen until lit, then moves
    }

    // -------------------------------------------------------------------------
    // Tuning constants
    // -------------------------------------------------------------------------

    private static final Color BLOCK_FILL    = new Color(0xC8, 0xA0, 0x40); // Warm gold to read as "engineered" rather than "natural rock"
    private static final Color BLOCK_OUTLINE = new Color(0x60, 0x40, 0x10); // Dark gold outline
    private static final Color FROZEN_TINT   = new Color(0xA0, 0xC0, 0xE0, 80); // Cool wash overlaid when motion is frozen by light

    // -------------------------------------------------------------------------
    // Per-instance state
    // -------------------------------------------------------------------------

    private final int originX;        // World-space x at construction; the centre of the motion path
    private final int originY;        // World-space y at construction
    private final MovePattern pattern;
    private final int amplitude;       // px (radius for CIRCULAR)
    private final int periodMs;        // One full cycle in ms

    private final LightBehavior behaviour;

    /**
     * Phase counter in ms. Advanced by {@code deltaMs} each tick when motion
     * is NOT gated. Modulated by {@code periodMs} to drive the parametric
     * position function. Persisting the phase across pauses means a freeze /
     * unfreeze cycle resumes from where it stopped.
     */
    private long phaseMs;

    private boolean motionEnabled;     // Cached gate state from the light system; written by setLit() / setLightIntensity()

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a light-locked moving platform.
     *
     * @param x          world-space left-edge x at the centre of the motion path
     * @param y          world-space top-edge y at the centre of the motion path
     * @param pattern    the motion pattern
     * @param amplitude  amplitude in pixels (radius for CIRCULAR)
     * @param periodMs   one full cycle in milliseconds
     * @param behaviour  whether light freezes or unfreezes the motion
     */
    public LightLockedMover(int x, int y, MovePattern pattern, int amplitude,
                            int periodMs, LightBehavior behaviour) {
        super(PlatformType.BRICK, x, y, 64, 16); // Default 64×16 — bigger than a tile so the Wanderer can stand on it comfortably
        this.originX = x;
        this.originY = y;
        this.pattern = pattern;
        this.amplitude = amplitude;
        this.periodMs = Math.max(200, periodMs); // Floor the period so we can never divide by zero
        this.behaviour = behaviour;
        this.phaseMs = 0L;
        // Initial motion state — depends on behaviour. FREEZE_ON_LIGHT starts moving
        // (no light yet, so not frozen); MOVE_ON_LIGHT starts frozen (no light = no motion).
        this.motionEnabled = (behaviour == LightBehavior.FREEZE_ON_LIGHT);
    }

    // -------------------------------------------------------------------------
    // Light gating — driven by Platform.setLit() (existing GameStarter loop)
    // -------------------------------------------------------------------------

    @Override
    public void setLit(boolean lit) {
        super.setLit(lit); // Preserve the Platform field for any downstream reads
        // Map (lit, behaviour) → motionEnabled. The truth table:
        //   FREEZE_ON_LIGHT, lit=true  → frozen (motionEnabled=false)
        //   FREEZE_ON_LIGHT, lit=false → moves  (motionEnabled=true)
        //   MOVE_ON_LIGHT,   lit=true  → moves  (motionEnabled=true)
        //   MOVE_ON_LIGHT,   lit=false → frozen (motionEnabled=false)
        switch (behaviour) {
            case FREEZE_ON_LIGHT: motionEnabled = !lit; break;
            case MOVE_ON_LIGHT:   motionEnabled = lit;  break;
        }
    }

    /** @return whether the platform is currently moving (false = frozen by light) */
    public boolean isMotionEnabled() { return motionEnabled; }

    // -------------------------------------------------------------------------
    // Per-tick simulation
    // -------------------------------------------------------------------------

    @Override
    public void update(long deltaMs) {
        if (motionEnabled) {
            phaseMs = (phaseMs + deltaMs) % periodMs; // Advance the phase, wrapping at the period
        }
        // Always recompute the position from the phase — even when frozen the
        // position must reflect where we paused (avoids the block jumping to
        // an old position on the first tick after un-freezing).
        double t = (double) phaseMs / (double) periodMs; // Normalised phase in [0, 1)
        double angle = t * 2 * Math.PI;                  // Convert to radians
        switch (pattern) {
            case LINEAR_X:
                this.x = originX + (int) Math.round(Math.sin(angle) * amplitude);
                this.y = originY;
                break;
            case LINEAR_Y:
                this.x = originX;
                this.y = originY + (int) Math.round(Math.sin(angle) * amplitude);
                break;
            case CIRCULAR:
                this.x = originX + (int) Math.round(Math.cos(angle) * amplitude);
                this.y = originY + (int) Math.round(Math.sin(angle) * amplitude);
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Render — frozen blocks get a cool wash
    // -------------------------------------------------------------------------

    @Override
    public void render(Graphics2D g) {
        if (!isActive()) return;
        BufferedImage img = SpriteLoader.getInstance().tryLoad("resources/sprites/hazards/mover.png");
        if (img != null) {
            g.drawImage(img, x, y, width, height, null);
            if (!motionEnabled) {           // Frozen-tint overlay: drawn on top of sprite to signal light-freeze state
                g.setColor(FROZEN_TINT);
                g.fillRect(x, y, width, height);
            }
        }
    }
}
