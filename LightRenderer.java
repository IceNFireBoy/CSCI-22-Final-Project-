/**
 * Handles all lighting and darkness-overlay rendering for Lumen Architect, drawing a
 * radial light mask around active light sources to simulate the limited visibility that
 * defines the game's atmosphere. It also paints a corruption overlay whose intensity
 * increases with each Core the Wanderer destroys, reflecting the deteriorating state of
 * the game world.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;

public class LightRenderer {

    private static final int CANVAS_W = 1024;
    private static final int CANVAS_H = 768;

    // Camera feed panel bounds (right 30% of canvas)
    private static final int CAM_X = 717;
    private static final int CAM_Y = 0;
    private static final int CAM_W = CANVAS_W - CAM_X; // 307
    private static final int CAM_H = CANVAS_H;          // 768

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a {@code LightRenderer} ready for use. No resources are loaded at
     * construction time.
     */
    public LightRenderer() {
        // No-arg constructor.
    }

    // -------------------------------------------------------------------------
    // Rendering methods
    // -------------------------------------------------------------------------

    /**
     * Draws a full-canvas darkness mask with a transparent radial cut-out centred on
     * the given light source, simulating a sphere of visibility in an otherwise dark
     * level. Three concentric layers create a soft penumbra effect: a core bright
     * zone, a mid falloff ring, and a faint outer glow.
     *
     * @param g              the {@link Graphics2D} context to draw the mask onto;
     *                       must not be {@code null}
     * @param lightSource    the canvas-space centre of the light source; must not be
     *                       {@code null}
     * @param radius         the pixel radius of the visible area around the light
     *                       source; must be positive
     * @param velocityFactor a value from 0.0 (still, full radius) to 1.0 (fast,
     *                       radius reduced by 40%) representing the Apprentice's
     *                       light velocity
     */
    public void renderLightMask(Graphics2D g, Point lightSource, int radius,
                                float velocityFactor) {
        // Clamp velocity factor and scale radius
        float clamped = Math.max(0.0f, Math.min(1.0f, velocityFactor));
        float scale = 1.0f - (0.4f * clamped);
        int effectiveRadius = Math.round(radius * scale);

        // Create darkness overlay image
        BufferedImage overlay = new BufferedImage(CANVAS_W, CANVAS_H,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D og = overlay.createGraphics();
        og.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        Rectangle2D fullCanvas = new Rectangle2D.Double(0, 0, CANVAS_W, CANVAS_H);
        double cx = lightSource.x;
        double cy = lightSource.y;

        // Layer 1 — outer faint glow (radius + 45px, alpha 0.18)
        drawDarknessLayer(og, fullCanvas, cx, cy, effectiveRadius + 45, 0.18f);

        // Layer 2 — mid falloff ring (radius + 20px, alpha 0.55)
        drawDarknessLayer(og, fullCanvas, cx, cy, effectiveRadius + 20, 0.55f);

        // Layer 3 — core darkness (exact radius, alpha 0.92)
        drawDarknessLayer(og, fullCanvas, cx, cy, effectiveRadius, 0.92f);

        og.dispose();

        // Composite the overlay onto the game canvas
        g.drawImage(overlay, 0, 0, null);
    }

    /**
     * D4 — Convenience overload that creates a simple concentric-ring light mask
     * without velocity factor scaling. Used for Act 2/3 gesture-controlled light.
     */
    public void renderLightMask(Graphics2D g, Point lightSource, int radius) {
        BufferedImage darkness = new BufferedImage(CANVAS_W, CANVAS_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D dg = darkness.createGraphics();
        dg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        dg.setColor(new Color(0, 0, 0, 235));
        dg.fillRect(0, 0, CANVAS_W, CANVAS_H);
        dg.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OUT));
        dg.setColor(new Color(0, 0, 0, 255));
        dg.fillOval(lightSource.x - radius, lightSource.y - radius, radius * 2, radius * 2);
        dg.setColor(new Color(0, 0, 0, 140));
        int r2 = radius + 25;
        dg.fillOval(lightSource.x - r2, lightSource.y - r2, r2 * 2, r2 * 2);
        dg.setColor(new Color(0, 0, 0, 60));
        int r3 = radius + 50;
        dg.fillOval(lightSource.x - r3, lightSource.y - r3, r3 * 2, r3 * 2);
        dg.dispose();
        g.drawImage(darkness, 0, 0, null);
        // Small white dot at light source center
        g.setColor(new Color(255, 255, 255, 120));
        g.fillOval(lightSource.x - 4, lightSource.y - 4, 8, 8);
    }

    /**
     * Draws a single darkness layer: fills the canvas area minus a circular cut-out
     * with black at the given alpha.
     *
     * @param og         the overlay graphics context
     * @param fullCanvas rectangle covering the full canvas
     * @param cx         light source x
     * @param cy         light source y
     * @param r          radius of the light cut-out for this layer
     * @param alpha      opacity of the darkness fill
     */
    private void drawDarknessLayer(Graphics2D og, Rectangle2D fullCanvas,
                                   double cx, double cy, int r, float alpha) {
        Area darkness = new Area(fullCanvas);
        Ellipse2D lightEllipse = new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
        darkness.subtract(new Area(lightEllipse));

        og.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        og.setColor(Color.BLACK);
        og.fill(darkness);
    }

    /**
     * Overlays corruption effects on the camera feed panel based on which Cores have
     * been destroyed. Each destroyed Core applies a distinct visual penalty to a
     * portion of the camera feed, progressively degrading the Apprentice's view.
     * <p>
     * The camera feed image is passed in so that Core 2's {@link RescaleOp} can
     * operate on actual pixel data. The caller should pass the current camera frame
     * (already drawn to the panel area) as {@code cameraFeedImage}.
     *
     * @param g              the {@link Graphics2D} context to draw the corruption
     *                       overlay onto; must not be {@code null}
     * @param destroyedCores a four-element boolean array where index {@code i} is
     *                       {@code true} if Core {@code i} has been destroyed; must
     *                       not be {@code null} and must have length 4
     * @param cameraFeedImage the current camera feed as a {@link BufferedImage},
     *                        used by Core 2 corruption for {@link RescaleOp}
     *                        darkening; may be {@code null} if Core 2 is intact
     */
    public void renderCorruption(Graphics2D g, boolean[] destroyedCores,
                                 BufferedImage cameraFeedImage) {
        // Core 3 destroyed: total blackout of camera panel
        if (destroyedCores[3]) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g.setColor(Color.BLACK);
            g.fill(new Rectangle2D.Double(CAM_X, CAM_Y, CAM_W, CAM_H));
            return;
        }

        // Core 2 destroyed: apply RescaleOp to darken the entire camera panel
        if (destroyedCores[2] && cameraFeedImage != null) {
            RescaleOp darken = new RescaleOp(0.35f, 0, null);
            BufferedImage darkened = darken.filter(cameraFeedImage, null);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g.drawImage(darkened, CAM_X, CAM_Y, null);
        }

        // Core 0 destroyed: black rectangle over bottom-left of camera panel
        if (destroyedCores[0]) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g.setColor(Color.BLACK);
            g.fill(new Rectangle2D.Double(CAM_X, 384, 153, 384));
        }

        // Core 1 destroyed: black rectangle over top-right of camera panel
        if (destroyedCores[1]) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g.setColor(Color.BLACK);
            g.fill(new Rectangle2D.Double(870, 0, 154, 384));
        }
    }
}
