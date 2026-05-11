/**
 Contract for any entity drawable to screen. Implementing classes provide rendering
 routine and bounding rectangle for layout and collision detection.
 */

// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 1b "Interfaces"      - canonical interface declaration: two
//                               abstract method signatures, no fields, no
//                               bodies. Used by GameElement and Player as
//                               the universal contract for "drawable
//                               game object".
// Module 3a "Graphics"        - the render(Graphics2D) signature mirrors
//                               the graphics module's paintComponent
//                               override pattern, with Graphics2D as the
//                               drawing context.
// Module 3c "Collision"       - getBounds() returns a Rectangle for AABB
//                               collision testing as shown in the
//                               collision module's bounding-box approach.
// =========================================================================

import java.awt.Graphics2D;
import java.awt.Rectangle;

public interface Renderable {

    void render(Graphics2D g); // Draws entity to graphics context

    Rectangle getBounds(); // Returns AABB in world coordinates
}
