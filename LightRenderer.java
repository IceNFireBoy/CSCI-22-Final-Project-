/**
 * Renders the dynamic lighting and shadow overlay for the Apprentice role.
 *
 * @author Marxus Antonio L. Magisa (253602) & Antonio Sebastian B. Pasia (254505)
 * @version May 12, 2026
 *
 * I have not discussed the Java language code in my program
 * with anyone other than my instructor or the teaching assistants
 * assigned to this course.
 *
 * I have not used Java language code obtained from another student,
 * or any other unauthorized source, either modified or unmodified.
 * If any Java language code or documentation used in my program
 * was obtained from another source, such as a textbook or website,
 * that has been clearly noted with a proper citation in the comments
 * of my program.
 */
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.*;
import java.util.List;
public class LightRenderer {

    private static final int CANVAS_W = 1024;
    private static final int CANVAS_H = 768;

    private static final int CAM_X = 717;
    private static final int CAM_Y = 0;
    private static final int CAM_W = CANVAS_W - CAM_X;
    private static final int CAM_H = CANVAS_H;

    public LightRenderer() {

    }

    public void renderLightMask(Graphics2D g, Point lightSource, int radius,
                                float velocityFactor) {

        float clamped         = Math.max(0.0f, Math.min(1.0f, velocityFactor));
        float scale           = 1.0f - (0.4f * clamped);
        int   effectiveRadius = Math.round(radius * scale);

        BufferedImage overlay = new BufferedImage(CANVAS_W, CANVAS_H,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D og = overlay.createGraphics();
        og.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        Rectangle2D fullCanvas = new Rectangle2D.Double(0, 0, CANVAS_W, CANVAS_H);
        double cx = lightSource.x;
        double cy = lightSource.y;

        drawDarknessLayer(og, fullCanvas, cx, cy, effectiveRadius + 45, 0.18f);

        drawDarknessLayer(og, fullCanvas, cx, cy, effectiveRadius + 20, 0.55f);

        drawDarknessLayer(og, fullCanvas, cx, cy, effectiveRadius, 0.92f);

        og.dispose();

        g.drawImage(overlay, 0, 0, null);
    }

    public void renderLightMask(Graphics2D g, Point lightSource, int radius,
                                float velocityFactor, boolean fullArenaReveal) {
        if (fullArenaReveal) {

            return;
        }
        renderLightMask(g, lightSource, radius, velocityFactor);
    }

    public void renderLightMask(Graphics2D g, Point lightSource, int radius) {
        BufferedImage darkness = new BufferedImage(CANVAS_W, CANVAS_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D dg = darkness.createGraphics();
        dg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        dg.setColor(new Color(0, 0, 0, 235));
        dg.fillRect(0, 0, CANVAS_W, CANVAS_H);

        dg.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OUT));
        dg.setColor(new Color(0, 0, 0, 255));
        dg.fillOval(lightSource.x - radius, lightSource.y - radius,
                    radius * 2, radius * 2);

        dg.setColor(new Color(0, 0, 0, 140));
        int r2 = radius + 25;
        dg.fillOval(lightSource.x - r2, lightSource.y - r2, r2 * 2, r2 * 2);

        dg.setColor(new Color(0, 0, 0, 60));
        int r3 = radius + 50;
        dg.fillOval(lightSource.x - r3, lightSource.y - r3, r3 * 2, r3 * 2);

        dg.dispose();
        g.drawImage(darkness, 0, 0, null);

        g.setColor(new Color(255, 255, 255, 120));
        g.fillOval(lightSource.x - 4, lightSource.y - 4, 8, 8);
    }

    public void renderShadowFans(Graphics2D g, LightBall lb, List<Platform> plats) {
        if (g == null || lb == null || plats == null || plats.isEmpty()) return;

        Camera cam = Camera.getInstance();

        final int halfW = CANVAS_W / 2;
        final int halfH = CANVAS_H / 2;
        BufferedImage buf = new BufferedImage(halfW, halfH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D bg = buf.createGraphics();

        bg.setColor(Color.BLACK);
        bg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        final float lbX = lb.getX();
        final float lbY = lb.getY();

        final float lightScreenX = cam.worldToScreenX(Math.round(lbX)) * 0.5f;
        final float lightScreenY = cam.worldToScreenY(Math.round(lbY)) * 0.5f;

        final float cullRadius   = 2f * SHADOW_CULL_RADIUS;
        final float cullRadiusSq = cullRadius * cullRadius;

        final float FAR = 2000f;

        for (Platform plat : plats) {
            if (plat == null || !plat.isSolid()) continue;

            if (plat.getType() == Platform.PlatformType.INVISIBLE && !plat.isLit()) continue;

            Rectangle bnds = plat.getBounds();

            if (lbX > bnds.x && lbX < bnds.x + bnds.width
                    && lbY > bnds.y && lbY < bnds.y + bnds.height) {
                continue;
            }

            float nearestX = Math.max(bnds.x, Math.min(lbX, bnds.x + bnds.width));
            float nearestY = Math.max(bnds.y, Math.min(lbY, bnds.y + bnds.height));
            float ndx      = nearestX - lbX;
            float ndy      = nearestY - lbY;
            if (ndx * ndx + ndy * ndy > cullRadiusSq) continue;

            float px = cam.worldToScreenX(bnds.x) * 0.5f;
            float py = cam.worldToScreenY(bnds.y) * 0.5f;
            float pw = bnds.width  * 0.5f;
            float ph = bnds.height * 0.5f;

            float[] cxs = { px,      px + pw, px + pw, px      };
            float[] cys = { py,      py,      py + ph, py + ph };

            float[] exs = new float[4];
            float[] eys = new float[4];
            for (int i = 0; i < 4; i++) {
                float vx  = cxs[i] - lightScreenX;
                float vy  = cys[i] - lightScreenY;
                float len = (float) Math.sqrt(vx * vx + vy * vy);
                if (len < 0.01f) {
                    exs[i] = cxs[i];
                    eys[i] = cys[i];
                } else {
                    float scale = FAR / len;
                    exs[i] = cxs[i] + vx * scale;
                    eys[i] = cys[i] + vy * scale;
                }
            }

            for (int i = 0; i < 4; i++) {
                int j = (i + 1) & 3;
                int[] xs = {
                        Math.round(cxs[i]), Math.round(cxs[j]),
                        Math.round(exs[j]), Math.round(exs[i])
                };
                int[] ys = {
                        Math.round(cys[i]), Math.round(cys[j]),
                        Math.round(eys[j]), Math.round(eys[i])
                };
                bg.fillPolygon(xs, ys, 4);
            }
        }

        bg.dispose();

        Composite prev = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.92f));
        g.drawImage(buf, 0, 0, CANVAS_W, CANVAS_H, null);
        g.setComposite(prev);
    }

    private static final int SHADOW_CULL_RADIUS = 360;

    private void drawDarknessLayer(Graphics2D og, Rectangle2D fullCanvas,
                                   double cx, double cy, int r, float alpha) {
        Area darkness = new Area(fullCanvas);
        Ellipse2D lightEllipse = new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
        darkness.subtract(new Area(lightEllipse));

        og.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        og.setColor(Color.BLACK);
        og.fill(darkness);
    }

    public void renderCorruption(Graphics2D g, boolean[] destroyedCores,
                                 BufferedImage cameraFeedImage) {

        if (destroyedCores[3]) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g.setColor(Color.BLACK);
            g.fill(new Rectangle2D.Double(CAM_X, CAM_Y, CAM_W, CAM_H));
            return;
        }

        if (destroyedCores[2] && cameraFeedImage != null) {
            RescaleOp darken = new RescaleOp(0.35f, 0, null);
            BufferedImage darkened = darken.filter(cameraFeedImage, null);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g.drawImage(darkened, CAM_X, CAM_Y, null);
        }

        if (destroyedCores[0]) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g.setColor(Color.BLACK);
            g.fill(new Rectangle2D.Double(CAM_X, 384, 153, 384));
        }

        if (destroyedCores[1]) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g.setColor(Color.BLACK);
            g.fill(new Rectangle2D.Double(870, 0, 154, 384));
        }
    }
}
