/**
 * Represents the level-exit portal in Lumen Architect. The portal renders as a
 * pulsing gold archway and triggers level completion when the Wanderer enters
 * its inner opening.
 *
 * <p>Architecture role: Each level JSON file (loaded by {@link LevelLoader}) contains
 * exactly one portal entity. Once the Wanderer's bounding rectangle overlaps the portal's
 * inner opening (returned by the overridden {@link #getBounds()}), the level-completion
 * path inside {@link GameStarter#checkPortalCollision()} fires: on level ≤ 9 it calls
 * {@link GameStarter#loadLevel(int)} to advance to the next level; on level 10 it sends
 * {@link Protocol#BOSS_ENTER} to the server to begin the boss sequence.</p>
 *
 * <p>The visual is intentionally wider/taller than the {@link #getBounds()} hitbox so the
 * player has to step inside the archway rather than merely touch its edge before the
 * transition fires, which feels more intentional and prevents accidental level-skips.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-22
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.awt.BasicStroke;          // Allows specifying stroke width for outline drawing
import java.awt.Color;                // AWT colour for frame, glow, and opening fills
import java.awt.Graphics2D;           // 2D rendering context passed to render()
import java.awt.Rectangle;            // AWT rectangle returned by getBounds() for collision tests
import java.awt.Stroke;               // Interface for stroke style; saved/restored around custom draws
import java.awt.geom.Arc2D;           // Provides an arc shape used to draw the semicircular archway top
import java.awt.geom.RoundRectangle2D; // Rounded rectangle used for the outer glow halo

public class Portal extends GameElement implements SpriteOverridable { // Extends GameElement to participate in the entity list and AABB collision checks; implements SpriteOverridable so a level generator can swap the procedural archway for a PNG

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Width of the portal archway in pixels. */
    private static final int PORTAL_WIDTH  = 48; // 48 px — just wider than the Wanderer (32 px) so entry feels natural

    /** Height of the portal archway in pixels. */
    private static final int PORTAL_HEIGHT = 80; // 80 px — tall enough to fit the Wanderer sprite with visual headroom

    /** Base colour of the portal frame (gold). */
    private static final Color FRAME_COLOR   = new Color(0xC9, 0xA8, 0x4C); // Warm gold matching the game's HUD and lore-fragment palette

    /** Inner glow edge colour (bright gold). */
    private static final Color GLOW_COLOR    = new Color(0xF0, 0xCC, 0x7A); // Lighter, more saturated gold for the highlight edge inside the arch

    /** Inner opening fill colour (warm white). */
    private static final Color OPENING_COLOR = new Color(0xFF, 0xF8, 0xE0); // Near-white warm colour representing the light beyond the portal

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * Accumulated time since the portal was created, used to drive the sin-wave pulse
     * animation. Incremented by {@code deltaMs} each tick in {@link #update(long)}.
     * A larger value produces a faster oscillation; the multiplier 0.003 in
     * {@link #render(Graphics2D)} keeps the pulse at roughly one cycle every 2 seconds.
     */
    private long pulseTimer; // Monotonically increasing ms counter; fed into sin() to produce a [0.4, 1.0] pulse factor

    /**
     * Optional PNG path that replaces the procedural archway draw when set.
     * {@code null} by default; level generators set this through the
     * {@code portal(...)} helper or via {@link #setSpritePath(String)}.
     */
    private String spritePath; // SpriteOverridable backing field; null → procedural fallback

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code Portal} at the given world-space position with fixed
     * dimensions ({@link #PORTAL_WIDTH} × {@link #PORTAL_HEIGHT}) and pulse timer
     * initialised to zero.
     *
     * <p>Architecture role: Called by {@link LevelLoader} when it encounters a
     * {@code "portal"} entity in the level JSON file. The portal is added to
     * {@link GameStarter#elements} immediately and becomes visible on the next frame.</p>
     *
     * @param x the x-coordinate (world space) of the portal's left edge
     * @param y the y-coordinate (world space) of the portal's top edge
     */
    public Portal(int x, int y) {                        // Two-argument constructor: position only; dimensions are fixed constants
        super(x, y, PORTAL_WIDTH, PORTAL_HEIGHT);        // Delegate to GameElement: set AABB position and fixed 48×80 pixel dimensions
        this.pulseTimer = 0L;                            // Start the pulse animation from the beginning of its sin cycle
        this.spritePath = null;                          // No sprite override by default; render() falls through to the procedural archway draw
    }

    // -------------------------------------------------------------------------
    // GameElement overrides
    // -------------------------------------------------------------------------

    /**
     * Advances the pulse animation timer by the elapsed tick time. The timer feeds
     * directly into {@link Math#sin(double)} inside {@link #render(Graphics2D)} to
     * produce the oscillating glow effect.
     *
     * <p>Architecture role: Called every 16 ms by {@link GameStarter}'s game loop.
     * The timer keeps accumulating for the entire time the portal is in the level;
     * no reset is needed because the sin function is periodic.</p>
     *
     * @param deltaMs the time elapsed since the last update, in milliseconds
     */
    @Override
    public void update(long deltaMs) {  // Advance the pulse timer each tick so render() produces a time-varying animation
        pulseTimer += deltaMs;          // Accumulate elapsed ms; at 60 fps this increases by 16 ms per frame
    }

    /**
     * Draws the portal as a pulsing gold archway. Render order: outer glow halo →
     * gold frame (two pillars + arched top) → warm inner opening fill → highlight edge.
     * The intensity of the glow and opening alpha modulates at ~0.5 Hz via
     * {@link Math#sin(double)}.
     *
     * <p>Architecture role: Called by {@link GameCanvas#renderElements(Graphics2D)} once
     * per frame. The camera transform applied by {@link GameCanvas#renderBoss} or the
     * act renderers automatically converts the world-space (x, y) to screen space before
     * this method is called, so all coordinates here are written in world space.</p>
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    @Override
    public void render(Graphics2D g) {
        if (!active) return; // Do not render if this portal has been deactivated (e.g. already used)

        // Sprite override: if a PNG path is set and loadable, draw it and return — skipping the procedural archway below.
        if (SpriteOverridable.tryDrawSprite(g, this, x, y, width, height)) return;

        // Compute pulse factor in [0.4, 1.0] for sinusoidal glow modulation
        float pulse = (float)(Math.sin(pulseTimer * 0.003) * 0.3 + 0.7); // sin oscillates in [-1,1]; scale to [0.4,1.0] so the portal never fully dims

        // --- Outer glow halo ---
        int glowSize = (int)(pulse * 6);                                  // Halo padding in pixels: 0 at minimum pulse, 6 at maximum — creates a breathing expansion effect
        g.setColor(new Color(0xF0, 0xCC, 0x7A, (int)(40 * pulse)));      // Semi-transparent warm gold; alpha varies 16–40 with pulse for a soft ambient glow
        g.fillRoundRect(x - glowSize, y - glowSize,                       // Draw rounded rectangle expanded by glowSize on each side beyond the portal frame
                width + glowSize * 2, height + glowSize * 2, 12, 12);    // 12 px corner radius keeps the halo rounded to match the arch aesthetic

        // --- Portal frame (gold archway): two pillars + semicircular arch top ---
        Stroke oldStroke = g.getStroke();                                  // Save current stroke so we can restore it after the thick frame draw
        g.setStroke(new BasicStroke(3.0f));                                // Use a 3 px stroke for the frame outline
        g.setColor(FRAME_COLOR);                                           // Apply the standard gold frame colour

        // Left pillar — a thin vertical bar on the left side below the arch
        g.fillRect(x, y + 20, 8, height - 20);                           // 8 px wide, starts below the 20 px arch region, extends to the bottom of the portal

        // Right pillar — mirror of left pillar on the right side
        g.fillRect(x + width - 8, y + 20, 8, height - 20);              // Same dimensions; offset right by (width - 8) to reach the right edge

        // Arch top — semicircular pie slice filling the upper 40 px of the portal
        g.fill(new Arc2D.Double(x, y, width, 40, 0, 180, Arc2D.PIE));    // Arc2D.PIE fills the closed shape; width×40 bounding box, 0°–180° = top semicircle

        // --- Inner opening ---
        int innerPad = 8;                                                  // 8 px inset from each side to leave the frame visible around the opening
        int openAlpha = (int)(180 * pulse + 40);                          // Alpha oscillates 40–220: never fully transparent, never fully opaque for a glowing effect
        g.setColor(new Color(0xFF, 0xF8, 0xE0, Math.min(255, openAlpha))); // Warm white fill; clamped to 255 to prevent integer overflow with max pulse
        g.fillRect(x + innerPad, y + 20, width - innerPad * 2, height - 20); // Fill the inner opening below the arch: padded 8 px on each side, starting below arch at y+20

        // --- Highlight edge around inner opening ---
        g.setStroke(new BasicStroke(1.5f));                               // Thin 1.5 px stroke for the highlight lines
        g.setColor(GLOW_COLOR);                                            // Brighter gold for the highlight, making the opening appear to emit light
        g.drawRect(x + innerPad, y + 20, width - innerPad * 2, height - 20); // Outline the inner opening rectangle
        g.drawArc(x + 2, y + 2, width - 4, 36, 0, 180);                 // Draw the arch's inner highlight arc, inset 2 px from the outer arch for depth

        g.setStroke(oldStroke); // Restore the original stroke so subsequent draw calls are not affected by the thick/thin strokes used above
    }

    /**
     * Returns the inner opening bounds used for collision detection with the Wanderer.
     * The hitbox is 16 px narrower and 20 px shorter than the full portal frame to
     * ensure the Wanderer steps visually inside the arch before the level-transition fires.
     *
     * <p>Architecture role: Called by {@link GameStarter#checkPortalCollision()} to test
     * whether {@code player.getBounds().intersects(portal.getBounds())} is true. By
     * returning the inner opening rather than the full frame AABB, the portal requires a
     * more deliberate entry motion, preventing accidental level advances.</p>
     *
     * @return a {@link Rectangle} representing the inner opening's world-space AABB;
     *         smaller than the full portal frame
     */
    @Override
    public Rectangle getBounds() {                                    // Override to return the narrower inner opening hitbox instead of the full 48×80 frame AABB
        return new Rectangle(x + 8, y + 20, width - 16, height - 20); // Inset 8 px on left/right, 20 px from top (arch region) — matches the rendered inner fill area
    }

    // -------------------------------------------------------------------------
    // SpriteOverridable
    // -------------------------------------------------------------------------

    /**
     * Sets the optional PNG path that replaces the procedural archway render.
     * Pass {@code null} to clear the override; pass a path like
     * {@code "resources/sprites/portals/act1.png"} to use a bitmap.
     *
     * @param path the PNG path, or {@code null} for procedural rendering
     */
    @Override
    public void setSpritePath(String path) { // SpriteOverridable contract
        this.spritePath = path;               // Stored as-is; null is valid
    }

    /**
     * Returns the configured sprite path, or {@code null} if no override is set.
     *
     * @return the sprite path or {@code null}
     */
    @Override
    public String getSpritePath() { // SpriteOverridable contract
        return spritePath;          // Null when no override is configured
    }
}
