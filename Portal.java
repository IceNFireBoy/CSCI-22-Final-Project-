/**
 * Represents the level-exit portal in Lumen Architect. The portal renders as a
 * pulsing gold archway and triggers level completion when the Wanderer enters
 * its inner opening.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-22
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Arc2D;
import java.awt.geom.RoundRectangle2D;

public class Portal extends GameElement {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Width of the portal archway in pixels. */
    private static final int PORTAL_WIDTH = 48;

    /** Height of the portal archway in pixels. */
    private static final int PORTAL_HEIGHT = 80;

    /** Base color of the portal frame (gold). */
    private static final Color FRAME_COLOR = new Color(0xC9, 0xA8, 0x4C);

    /** Inner glow color (bright gold). */
    private static final Color GLOW_COLOR = new Color(0xF0, 0xCC, 0x7A);

    /** Inner opening color (warm white). */
    private static final Color OPENING_COLOR = new Color(0xFF, 0xF8, 0xE0);

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Timer for the pulsing animation effect, in milliseconds. */
    private long pulseTimer;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code Portal} at the given world-space position.
     *
     * @param x the x-coordinate of the portal's left edge
     * @param y the y-coordinate of the portal's top edge
     */
    public Portal(int x, int y) {
        super(x, y, PORTAL_WIDTH, PORTAL_HEIGHT);
        this.pulseTimer = 0L;
    }

    // -------------------------------------------------------------------------
    // GameElement overrides
    // -------------------------------------------------------------------------

    @Override
    public void update(long deltaMs) {
        pulseTimer += deltaMs;
    }

    @Override
    public void render(Graphics2D g) {
        if (!active) return;

        float pulse = (float)(Math.sin(pulseTimer * 0.003) * 0.3 + 0.7);

        // Outer glow
        int glowSize = (int)(pulse * 6);
        g.setColor(new Color(0xF0, 0xCC, 0x7A, (int)(40 * pulse)));
        g.fillRoundRect(x - glowSize, y - glowSize,
                width + glowSize * 2, height + glowSize * 2, 12, 12);

        // Portal frame (gold archway)
        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(3.0f));
        g.setColor(FRAME_COLOR);
        // Left pillar
        g.fillRect(x, y + 20, 8, height - 20);
        // Right pillar
        g.fillRect(x + width - 8, y + 20, 8, height - 20);
        // Arch top
        g.fill(new Arc2D.Double(x, y, width, 40, 0, 180, Arc2D.PIE));

        // Inner opening (bright warm fill)
        int innerPad = 8;
        int openAlpha = (int)(180 * pulse + 40);
        g.setColor(new Color(0xFF, 0xF8, 0xE0, Math.min(255, openAlpha)));
        g.fillRect(x + innerPad, y + 20, width - innerPad * 2, height - 20);

        // Highlight edge
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(GLOW_COLOR);
        g.drawRect(x + innerPad, y + 20, width - innerPad * 2, height - 20);
        g.drawArc(x + 2, y + 2, width - 4, 36, 0, 180);

        g.setStroke(oldStroke);
    }

    /**
     * Returns the inner opening bounds used for collision detection with the
     * Wanderer. The opening is narrower than the full portal frame.
     *
     * @return a {@link Rectangle} representing the inner opening
     */
    @Override
    public Rectangle getBounds() {
        return new Rectangle(x + 8, y + 20, width - 16, height - 20);
    }
}
