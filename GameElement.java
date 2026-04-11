/**
 * Serves as the abstract base class for all drawable, updatable entities in the
 * Lumen Architect game world. Every concrete entity must implement its own update
 * and render logic while inheriting the shared positional and active-state fields
 * defined here.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */


import java.awt.Graphics2D;
import java.awt.Rectangle;

public abstract class GameElement implements Renderable {

    // -------------------------------------------------------------------------
    // Protected fields
    // -------------------------------------------------------------------------

    /** The x-coordinate of this element in world space (pixels, left edge). */
    protected int x;

    /** The y-coordinate of this element in world space (pixels, top edge). */
    protected int y;

    /** The width of this element in pixels. */
    protected int width;

    /** The height of this element in pixels. */
    protected int height;

    /**
     * Whether this element is currently active. Inactive elements are typically
     * skipped during update and render passes.
     */
    protected boolean active;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code GameElement} at the given position and size, set to
     * active by default.
     *
     * @param x      the initial x-coordinate in world space
     * @param y      the initial y-coordinate in world space
     * @param width  the width of this element in pixels
     * @param height the height of this element in pixels
     */
    public GameElement(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.active = true;
    }

    // -------------------------------------------------------------------------
    // Abstract methods
    // -------------------------------------------------------------------------

    /**
     * Updates the internal state of this element based on the time elapsed since the
     * last update tick.
     *
     * @param deltaMs the time elapsed since the last update, in milliseconds
     */
    public abstract void update(long deltaMs);

    /**
     * Draws this element onto the provided 2D graphics context.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    @Override
    public abstract void render(Graphics2D g);

    // -------------------------------------------------------------------------
    // Concrete methods
    // -------------------------------------------------------------------------

    /**
     * Returns an axis-aligned bounding {@link Rectangle} for this element, derived
     * from its current position and dimensions.
     *
     * @return a new {@link Rectangle} representing this element's bounds
     */
    @Override
    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    /**
     * Returns whether this element is currently active.
     *
     * @return {@code true} if active; {@code false} otherwise
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Sets the active state of this element.
     *
     * @param active {@code true} to mark this element as active; {@code false} to
     *               deactivate it
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    // =========================================================================
    // Static nested class — CollisionBox
    // =========================================================================

    /**
     * A simple axis-aligned collision box used for broad-phase collision detection
     * between game elements. This class stores its own position and dimensions
     * independently of the owning element so it can represent sub-regions or offset
     * hitboxes.
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-21
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class CollisionBox {

        /** The x-coordinate of the left edge of this collision box. */
        public int x;

        /** The y-coordinate of the top edge of this collision box. */
        public int y;

        /** The width of this collision box in pixels. */
        public int w;

        /** The height of this collision box in pixels. */
        public int h;

        /**
         * Constructs a {@code CollisionBox} with the given position and size.
         *
         * @param x the x-coordinate of the left edge
         * @param y the y-coordinate of the top edge
         * @param w the width in pixels
         * @param h the height in pixels
         */
        public CollisionBox(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        /**
         * Tests whether this collision box overlaps with another. Two boxes intersect
         * when they share at least one pixel of area along both axes simultaneously.
         *
         * @param other the other {@link CollisionBox} to test against; must not be
         *              {@code null}
         * @return {@code true} if the two boxes overlap; {@code false} otherwise
         */
        public boolean intersects(CollisionBox other) {
            return this.x < other.x + other.w
                    && this.x + this.w > other.x
                    && this.y < other.y + other.h
                    && this.y + this.h > other.y;
        }
    }
}
