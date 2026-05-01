/**
 Contract for any entity drawable to screen. Implementing classes provide rendering
 routine and bounding rectangle for layout and collision detection.
 */

import java.awt.Graphics2D;
import java.awt.Rectangle;

public interface Renderable {

    void render(Graphics2D g); // Draws entity to graphics context

    Rectangle getBounds(); // Returns AABB in world coordinates
}
