/**
 * Defines the contract for any game entity that can be drawn to the screen using a
 * 2D graphics context. Implementing classes must provide both a rendering routine and
 * a bounding rectangle used for layout and collision detection purposes.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.awt.Graphics2D;
import java.awt.Rectangle;

public interface Renderable {

    /**
     * Draws this entity onto the provided 2D graphics context.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    void render(Graphics2D g);

    /**
     * Returns the axis-aligned bounding rectangle of this entity in world coordinates.
     *
     * @return a {@link java.awt.Rectangle} representing the bounds of this entity
     */
    Rectangle getBounds();
}
