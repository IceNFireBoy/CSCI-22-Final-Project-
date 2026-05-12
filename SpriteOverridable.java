/**
 * Marker interface for game entities whose default procedural rendering can be
 * replaced at runtime by a PNG sprite loaded via {@link SpriteLoader#tryLoad(String)}.
 * Each implementing class stores an optional sprite path; when the path is
 * {@code null} or the file is missing, the entity falls back to its hand-drawn
 * Graphics2D rendering. When the file is present, the bitmap is drawn in place
 * of the procedural art.
 *
 * <p>Architecture role: Lets {@link LoreFragment}, {@link Portal}, {@link Trigger},
 * and {@link Altar} share one rendering short-circuit pattern. Each entity calls
 * {@link #tryDrawSprite(Graphics2D, SpriteOverridable, int, int, int, int)} at the
 * top of its {@code render(Graphics2D)} method; if the helper returns {@code true},
 * the entity returns immediately and skips its procedural draw. This keeps the
 * "PNG if present, procedural if not" logic in one place rather than copy-pasted
 * across four render methods.</p>
 *
 * <p>The interface intentionally does not extend {@link Renderable} — it is a
 * sprite-override contract, not a render contract — so any class can opt in
 * without changing its rendering signature.</p>
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
// Module 1b "Interfaces"      - interface as a "marker contract": two
//                               accessor methods plus a static helper
//                               method. Implementing classes (LoreFragment,
//                               Portal, Trigger, Altar) opt into the
//                               sprite-override behaviour without changing
//                               their render signature.
// Module 3a "Graphics"        - tryDrawSprite uses Graphics2D.drawImage()
//                               with destination width/height parameters,
//                               the bilinear-scaled draw form shown in
//                               the Graphics module.
// Module 1d "Inner / static"  - static interface method (Java 8+) keeps
//                               the shared draw helper attached to the
//                               contract, conceptually similar to a
//                               static utility on a class.
// =========================================================================

import java.awt.*;
import java.awt.image.*;
public interface SpriteOverridable { // Tiny interface — two accessors plus a static helper

    /**
     * Sets the optional sprite path for this entity. A non-{@code null} path tells
     * the renderer to attempt loading a PNG via
     * {@link SpriteLoader#tryLoad(String)} and draw it in place of the procedural
     * fallback; a {@code null} path forces the procedural path (the default).
     *
     * @param path filesystem-relative or classpath-relative PNG path, or
     *             {@code null} to clear the override; not validated here
     */
    void setSpritePath(String path); // Setter; entities store the path in a private field

    /**
     * Returns the currently configured sprite path, or {@code null} if no override
     * is set. Read by {@link #tryDrawSprite(Graphics2D, SpriteOverridable, int, int, int, int)}
     * each frame to decide whether to attempt a sprite draw.
     *
     * @return the configured sprite path, or {@code null} for procedural rendering
     */
    String getSpritePath(); // Read accessor; returns null when no override is configured

    /**
     * Attempts to draw the entity's PNG override at the given world-space rectangle.
     * Returns {@code true} if a non-null bitmap was drawn (caller should
     * {@code return} from its render method to skip the procedural fallback);
     * returns {@code false} if no path is set or the path failed to load
     * (caller should fall through to the procedural draw).
     *
     * <p>Implementation note: uses {@link SpriteLoader#tryLoad(String)} rather than
     * {@link SpriteLoader#load(String)} so missing assets do not produce magenta
     * placeholder squares — instead, the procedural fallback paints the entity.
     * This is the entire point of the "sprite override" mechanism: drop a PNG to
     * replace the art, remove it to restore the procedural look, all without any
     * code edits.</p>
     *
     * @param g    the {@link Graphics2D} target; must not be {@code null}
     * @param e    the entity to query for a sprite path; must not be {@code null}
     * @param x    destination x in the current Graphics2D coordinate space
     * @param y    destination y in the current Graphics2D coordinate space
     * @param w    destination width in pixels
     * @param h    destination height in pixels
     * @return {@code true} if the bitmap was drawn (caller should not draw
     *         procedural art on top); {@code false} if the caller should fall
     *         through to its procedural draw
     */
    static boolean tryDrawSprite(Graphics2D g, SpriteOverridable e, int x, int y, int w, int h) {
        String path = e.getSpritePath();                      // Read the override path; null fast-paths to procedural
        if (path == null) return false;                       // No override configured — caller paints procedurally
        BufferedImage img = SpriteLoader.getInstance().tryLoad(path); // Optional load; null on missing or unreadable
        if (img == null) return false;                        // Load failed — caller paints procedurally
        g.drawImage(img, x, y, w, h, null);                   // Bilinear-scale to the destination rect; observer is null because we don't track async loads
        return true;                                          // Bitmap drawn — caller should skip its procedural draw
    }
}
