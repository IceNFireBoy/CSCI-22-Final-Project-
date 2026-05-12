/**
 Abstract base for all drawable, updatable entities (platforms, portals, fragments,
 hazards, cores, triggers). Concrete subclasses implement update() and
 render() logic while inheriting positional and active-state fields.

 GameStarter maintains single List<GameElement> for all active world objects. Game
 loop iterates once per tick calling update() and render() on each active element.
 Active/inactive mechanism skips deactivated entities without list removal, avoiding
 ConcurrentModificationException.
 */

// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 1c "Abstract Classes"   - this file is the canonical abstract-
//                                  class example: declared with the
//                                  abstract keyword, holds non-static
//                                  state (x, y, width, height, active),
//                                  and declares two abstract methods
//                                  (update, render) that subclasses MUST
//                                  implement. Concrete methods (getBounds,
//                                  isActive, setActive) are inherited
//                                  unchanged.
// Module 1b "Interfaces"         - implements Renderable to fulfil the
//                                  drawable / bounding-box contract.
// Module 1d "Inner Classes"      - CollisionBox is a public static nested
//                                  class - exactly the static-nested-class
//                                  pattern described in the inner-classes
//                                  module (no implicit reference to the
//                                  outer instance).
// Module 3c "Collision"          - CollisionBox.intersects() implements the
//                                  textbook AABB overlap test from the
//                                  collision module: four boundary
//                                  comparisons (left/right/top/bottom).
//                                  All four conditions true means overlap.
// Module 1a "Modifiers"          - protected fields shared with subclasses
//                                  and abstract methods illustrate the
//                                  modifier hierarchy taught in 1a.
// =========================================================================

import java.awt.*;
public abstract class GameElement implements Renderable { // Abstract — cannot be instantiated directly; implements Renderable for getBounds() and render() contract

    // -------------------------------------------------------------------------
    // Protected fields
    // -------------------------------------------------------------------------

    protected int x;      // World-space left edge (px)
    protected int y;      // World-space top edge (px); Y increases downward
    protected int width;  // Width in pixels
    protected int height; // Height in pixels
    protected boolean active; // true = active in update/render; false = skipped but remains in list

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public GameElement(int x, int y, int width, int height) { // Constructor; every subclass delegates via super()
        this.x = x;          // Store the left-edge world-space x coordinate
        this.y = y;          // Store the top-edge world-space y coordinate
        this.width = width;  // Store the pixel width for AABB collision and rendering
        this.height = height; // Store the pixel height for AABB collision and rendering
        this.active = true;  // New elements are active by default; deactivate via setActive(false) when collected/destroyed
    }

    // -------------------------------------------------------------------------
    // Abstract methods
    // -------------------------------------------------------------------------

    /**
     * Updates the internal state of this element based on the time elapsed since the
     * last update tick. Called once per game tick by the game loop in
     * {@link GameStarter} for every active element.
     *
     * <p>Architecture role: Subclasses implement their own time-driven behaviour here —
     * e.g. {@link Platform#update(long)} drives the crumble animation timer;
     * {@link CorruptedWall#update(long)} advances the wall-fall FSM;
     * {@link LoreFragment#update(long)} advances the pulse glow animation.
     * The {@code deltaMs} parameter allows time-based (rather than frame-based)
     * calculations, making behaviour independent of actual frame rate within a
     * fixed-timestep loop.</p>
     *
     * @param deltaMs the time elapsed since the last update, in milliseconds;
     *                always {@link GameStarter#TICK_MS} (16 ms) in the current
     *                fixed-timestep loop
     */
    public abstract void update(long deltaMs); // Subclasses implement time-driven state changes; called every 16 ms by GameStarter's game loop

    /**
     * Draws this element onto the provided 2D graphics context. Called once per
     * frame for every active element by {@link GameCanvas}.
     *
     * <p>Architecture role: Each subclass paints itself — e.g. {@link Platform#render}
     * delegates to {@link SpriteLoader} for tile sprites; {@link LoreFragment#render}
     * draws a pulsing 6-point polygon; {@link Core#render} will eventually draw a
     * glowing orb. Keeping render logic inside each entity rather than in a monolithic
     * renderer makes it easy to add new entity types without touching existing drawing
     * code.</p>
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null};
     *          its transform may be shifted by {@link Camera} during boss rendering
     */
    @Override
    public abstract void render(Graphics2D g); // Subclasses implement their own visual representation; called by GameCanvas.renderElements()

    // -------------------------------------------------------------------------
    // Concrete methods
    // -------------------------------------------------------------------------

    /**
     * Returns an axis-aligned bounding {@link Rectangle} for this element, derived
     * from its current position and dimensions. Used by {@link CollisionDetector} and
     * {@link Physics} for AABB overlap tests.
     *
     * <p>Architecture role: Provides a uniform hitbox API across all entity types.
     * {@link Player#getBounds()} calls this on every platform each tick to resolve
     * collisions; {@link GameStarter#checkFragmentCollection()} calls it on every
     * {@link LoreFragment} to test overlap with the Wanderer. Subclasses may override
     * this to return a sub-region hitbox (e.g. {@link Portal#getBounds()} returns a
     * narrower inner opening).</p>
     *
     * @return a new {@link Rectangle} representing this element's world-space AABB;
     *         never {@code null}; a fresh object each call (not cached)
     */
    @Override
    public Rectangle getBounds() {            // Build a new Rectangle each call from current x/y/width/height
        return new Rectangle(x, y, width, height); // Rectangle(x, y, w, h) — top-left at (x,y), extends right/down by width/height
    }

    /**
     * Returns whether this element is currently active.
     *
     * <p>Architecture role: The game loop in {@link GameStarter} checks this before
     * calling {@link #update(long)}; {@link GameCanvas#renderElements()} checks it
     * before calling {@link #render(Graphics2D)}. Inactive elements are effectively
     * invisible and non-interactive without being removed from the list.</p>
     *
     * @return {@code true} if this element should be updated and rendered;
     *         {@code false} if it has been logically removed from play
     */
    public boolean isActive() {    // Read accessor for the active flag; called on every element every tick by the game loop
        return active;             // Return the stored flag; true = participate, false = skip
    }

    /**
     * Sets the active state of this element. Setting to {@code false} effectively
     * removes the element from gameplay without touching the list structure, making
     * the operation safe during iteration of a
     * {@link java.util.concurrent.CopyOnWriteArrayList}.
     *
     * <p>Interaction: Called by {@link LoreFragment#collect()} when a fragment is
     * picked up; by {@link BossAttack#update(long)} when an attack expires; and by
     * {@link GameStarter} when cleaning up entities after level transitions.</p>
     *
     * @param active {@code true} to re-activate a dormant element; {@code false}
     *               to deactivate it
     */
    public void setActive(boolean active) { // Mutator for the active flag; deactivating skips update+render without list removal
        this.active = active;               // Overwrite the flag directly; no side-effects — subclasses may override to add cleanup logic
    }

    // =========================================================================
    // Static nested class — CollisionBox
    // =========================================================================

    /**
     * A simple axis-aligned collision box used for broad-phase collision detection
     * between game elements. Stores its own position and dimensions independently of
     * the owning element so it can represent sub-regions, offset hitboxes, or
     * temporary test boxes without mutating the parent's fields.
     *
     * <p>Architecture role: Used by {@link Physics} and {@link CollisionDetector}
     * to test narrow-phase AABB overlaps. For example, {@link CollisionDetector} builds
     * two CollisionBoxes for the Wanderer — a narrow vertical box and a wide horizontal
     * box — so that landing on a platform and walking through a doorway are resolved
     * with different widths, preventing the player from getting stuck on ledges.</p>
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-21
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class CollisionBox { // Static nested class — no reference to the outer GameElement; can be created standalone

         
        public int x; // World-space left edge; set by constructor

         
        public int y; // World-space top edge; set by constructor

         
        public int w; // Pixel width; used in intersects() to calculate the right edge as x+w

         
        public int h; // Pixel height; used in intersects() to calculate the bottom edge as y+h

        /**
         * Constructs a {@code CollisionBox} with the given position and size.
         *
         * <p>Interaction: Constructed by {@link CollisionDetector} each tick for each
         * entity pair being tested, or once at entity construction time for static
         * hitboxes. The box is cheap to allocate and is never pooled — the JVM's
         * short-lived allocation optimisations handle the cost at 60 fps.</p>
         *
         * @param x the x-coordinate of the left edge
         * @param y the y-coordinate of the top edge
         * @param w the width in pixels
         * @param h the height in pixels
         */
        public CollisionBox(int x, int y, int w, int h) { // Four-argument constructor storing all four AABB fields
            this.x = x; // Store left-edge x
            this.y = y; // Store top-edge y
            this.w = w; // Store width
            this.h = h; // Store height
        }

        /**
         * Tests whether this collision box overlaps with another using the standard
         * AABB (Axis-Aligned Bounding Box) separating-axis test. Two boxes overlap when
         * their projections onto both the x-axis and y-axis overlap simultaneously.
         *
         * <p>Architecture role: Core predicate used by {@link CollisionDetector} to
         * determine whether a platform and the player's hitbox are touching. The
         * four-condition formulation is the classic AABB overlap test: if any one
         * condition fails, the boxes are separated along that axis and cannot be
         * overlapping.</p>
         *
         * <p>Interaction: Called by {@link CollisionDetector} for every platform in the
         * active element list each physics tick. Also used by {@link BossAttack}
         * subclasses to test whether their hitbox reaches the Wanderer.</p>
         *
         * @param other the other {@link CollisionBox} to test against; must not be
         *              {@code null}
         * @return {@code true} if the two boxes share at least one pixel of area;
         *         {@code false} if they are fully separated along at least one axis
         */
        public boolean intersects(CollisionBox other) {               // Standard AABB overlap test; returns true only when boxes share area on both axes
            return this.x < other.x + other.w                        // This box's left edge is to the left of the other's right edge (not separated on X, left side)
                    && this.x + this.w > other.x                     // This box's right edge is to the right of the other's left edge (not separated on X, right side)
                    && this.y < other.y + other.h                    // This box's top edge is above the other's bottom edge (not separated on Y, top side)
                    && this.y + this.h > other.y;                    // This box's bottom edge is below the other's top edge (not separated on Y, bottom side)
            // All four conditions true → boxes overlap in 2D space
        }
    }
}
