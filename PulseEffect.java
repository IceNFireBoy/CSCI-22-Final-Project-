/**
 * A brief expanding ring of golden light emitted by the Wanderer's pulse ability.
 * The ring grows outward from the origin while fading, providing a visual cue for
 * both players. Extends {@link GameElement} so it can be managed by the standard
 * entity update/render loop.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;

public class PulseEffect extends GameElement {

    /** The current radius of the expanding ring in pixels. */
    private int radius;

    /** The maximum radius the ring can reach before it deactivates. */
    private int maxRadius;

    /** The current opacity of the ring, decreasing each tick. */
    private float alpha;

    /**
     * Constructs a new {@code PulseEffect} centred at the given world coordinates.
     *
     * @param centreX the x-coordinate of the pulse origin
     * @param centreY the y-coordinate of the pulse origin
     */
    public PulseEffect(int centreX, int centreY) {
        super(centreX, centreY, 0, 0);
        this.radius = 0;
        this.maxRadius = 200;
        this.alpha = 0.9f;
    }

    /**
     * Expands the ring and fades its opacity each tick. Deactivates the effect
     * when the alpha drops to zero or below.
     *
     * @param deltaMs the time elapsed since the last update, in milliseconds
     */
    @Override
    public void update(long deltaMs) {
        radius += 8;
        alpha -= 0.045f;
        if (alpha <= 0f) {
            alpha = 0f;
            setActive(false);
        }
    }

    /**
     * Renders the expanding golden ring with the current radius and opacity.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    @Override
    public void render(Graphics2D g) {
        if (!active || alpha <= 0f) {
            return;
        }

        Composite originalComposite = g.getComposite();
        Stroke originalStroke = g.getStroke();

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setColor(new Color(0xc9, 0xa8, 0x4c));  // gold #c9a84c
        g.setStroke(new BasicStroke(1.5f));

        Ellipse2D.Double ring = new Ellipse2D.Double(
                x - radius, y - radius, radius * 2, radius * 2);
        g.draw(ring);

        g.setComposite(originalComposite);
        g.setStroke(originalStroke);
    }

    /**
     * Returns the current radius of the pulse ring.
     *
     * @return the radius in pixels
     */
    public int getRadius() {
        return radius;
    }

    /**
     * Returns the current opacity of the pulse ring.
     *
     * @return the alpha value in the range [0.0, 0.9]
     */
    public float getAlpha() {
        return alpha;
    }
}
