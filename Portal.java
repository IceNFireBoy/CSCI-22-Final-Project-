/**
 * Represents the level-exit portal in Lumen Architect. The portal renders as a
 * pulsing gold archway and triggers level completion when the Wanderer enters
 * its inner opening.
 *
 * <p>Architecture role: Each level adds exactly one portal via
 * {@link LevelGenerator#portal(int, int)}. Once the Wanderer's bounding rectangle
 * overlaps the portal's inner opening (returned by the overridden {@link #getBounds()}),
 * the level-completion path inside {@link GameStarter#checkPortalCollision()} fires:
 * on level ≤ 3 it calls {@link GameStarter#loadLevel(int, boolean)} to advance to the
 * next level; on level 3 it sends {@link Protocol#BOSS_ENTER} to begin the boss sequence.</p>
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
public class Portal extends GameElement { // Extends GameElement to participate in the entity list and AABB collision checks

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Width of the portal archway in pixels. */
    private static final int PORTAL_WIDTH  = 48; // 48 px — just wider than the Wanderer (32 px) so entry feels natural

    /** Height of the portal archway in pixels. */
    private static final int PORTAL_HEIGHT = 80; // 80 px — tall enough to fit the Wanderer sprite with visual headroom

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
    public void update(long deltaMs) {
        // No per-tick state.
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
        if (!active) return;
        BufferedImage img = SpriteLoader.getInstance().tryLoad("resources/sprites/portals/portal.png");
        if (img != null) g.drawImage(img, x, y, width, height, null);
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
}
