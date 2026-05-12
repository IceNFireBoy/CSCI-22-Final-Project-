/**
 * Generates colored-rectangle placeholder sprites when real art is missing.
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
import java.io.*;
import javax.imageio.*;
public class PlaceholderSpriteGenerator {

    private static Color colourFor(LoreFragment.AbilityUnlock u) {
        switch (u) {
            case NONE:             return new Color(0xC9, 0xA8, 0x4C);
            case MELEE:            return new Color(0xC0, 0x40, 0x40);
            case PROJECTILE:       return new Color(0x40, 0xC0, 0x40);
            case DODGE:            return new Color(0x40, 0xB0, 0xE0);
            case WALL_CLING:       return new Color(0x80, 0x60, 0x40);
            case SHADOW_DASH:      return new Color(0x60, 0x30, 0xB0);
            case EMBER:            return new Color(0xE0, 0x60, 0x20);
            case IRON:             return new Color(0x80, 0x80, 0x90);
            case RADIANT_COLLAPSE: return new Color(0xFF, 0xF0, 0x80);
            case VEIL:             return new Color(0x40, 0x40, 0x80);
            case ECHO:             return new Color(0x80, 0xC0, 0xC0);
            case TETHER:           return new Color(0xA0, 0x60, 0x40);
            case SHADOW_STEP:      return new Color(0x20, 0x20, 0x40);
            default:               return new Color(0x80, 0x80, 0x80);
        }
    }

    public static void main(String[] args) throws IOException {

        new File("resources/sprites/fragments").mkdirs();
        new File("resources/sprites/altars").mkdirs();
        new File("resources/sprites/portals").mkdirs();
        new File("resources/sprites/hazards").mkdirs();

        for (LoreFragment.AbilityUnlock u : LoreFragment.AbilityUnlock.values()) {
            String filename = u.name().toLowerCase();

            if (u == LoreFragment.AbilityUnlock.NONE)              filename = "lore";
            if (u == LoreFragment.AbilityUnlock.RADIANT_COLLAPSE)  filename = "radiant";
            generateFragmentPng(
                "resources/sprites/fragments/" + filename + ".png",
                colourFor(u));
        }

        generateAltarPng("resources/sprites/altars/stone.png");

        generatePortalPng("resources/sprites/portals/portal.png");

        generateSpikePng("resources/sprites/hazards/spike.png");
        generateWallPng("resources/sprites/hazards/wall.png");
        generatePhantomPng("resources/sprites/hazards/phantom.png");
        generateMoverPng("resources/sprites/hazards/mover.png");

        System.out.println("PlaceholderSpriteGenerator: all PNGs written under resources/sprites/");
    }

    private static void generateFragmentPng(String path, Color c) throws IOException {
        BufferedImage img = newRgba(20, 20);
        Graphics2D g = setup(img);
        int cx = 10, cy = 10, r = 9;
        Polygon shard = new Polygon(
            new int[]{cx, cx + r, cx + r / 2, cx, cx - r / 2, cx - r},
            new int[]{cy - r, cy - r / 3, cy + r / 2, cy + r, cy + r / 2, cy - r / 3},
            6);
        g.setColor(c);
        g.fill(shard);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(0.8f));
        g.drawLine(cx - r / 3, cy - r / 2, cx + r / 4, cy);
        g.dispose();
        write(img, path);
    }

    private static void generateAltarPng(String path) throws IOException {
        BufferedImage img = newRgba(48, 64);
        Graphics2D g = setup(img);

        g.setColor(new Color(0x2A, 0x2A, 0x32));
        g.fillRect(0, 16, 48, 48);

        g.setColor(new Color(0x44, 0x44, 0x4E));
        g.fillRect(-2, 12, 52, 8);

        g.setColor(new Color(0xF0, 0xCC, 0x7A));
        g.setStroke(new BasicStroke(2.0f));
        g.drawOval(10, 4, 28, 28);
        g.drawLine(14, 18, 34, 18);
        g.drawLine(24, 8, 24, 28);

        g.dispose();
        write(img, path);
    }

    private static void generatePortalPng(String path) throws IOException {
        BufferedImage img = newRgba(48, 80);
        Graphics2D g = setup(img);

        g.setColor(new Color(0xFF, 0xF8, 0xE0, 200));
        g.fillRect(8, 20, 32, 60);

        g.setColor(new Color(0xC9, 0xA8, 0x4C));
        g.fillRect(0, 20, 8, 60);
        g.fillRect(40, 20, 8, 60);
        g.fill(new Arc2D.Double(0, 0, 48, 40, 0, 180, Arc2D.PIE));
        g.dispose();
        write(img, path);
    }

    private static void generateSpikePng(String path) throws IOException {
        BufferedImage img = newRgba(32, 16);
        Graphics2D g = setup(img);
        g.setColor(new Color(0x2C, 0x10, 0x18));
        g.fillRect(0, 11, 32, 5);
        g.setColor(new Color(0x60, 0x18, 0x28));
        g.fill(new Polygon(new int[]{0, 16, 32}, new int[]{16, 0, 16}, 3));
        g.setColor(new Color(0x20, 0x08, 0x10));
        g.drawPolygon(new Polygon(new int[]{0, 16, 32}, new int[]{16, 0, 16}, 3));
        g.dispose();
        write(img, path);
    }

    private static void generateWallPng(String path) throws IOException {
        BufferedImage img = newRgba(64, 128);
        Graphics2D g = setup(img);
        g.setColor(new Color(0x30, 0x18, 0x20));
        g.fillRect(0, 0, 64, 128);
        g.setColor(new Color(0x10, 0x06, 0x0C));
        g.drawRect(0, 0, 63, 127);

        for (int row = 16; row < 128; row += 16) g.drawLine(0, row, 64, row);
        for (int row = 0; row < 128; row += 32) {
            g.drawLine(32, row, 32, row + 16);
        }
        for (int row = 16; row < 128; row += 32) {
            g.drawLine(16, row, 16, row + 16);
            g.drawLine(48, row, 48, row + 16);
        }
        g.dispose();
        write(img, path);
    }

    private static void generatePhantomPng(String path) throws IOException {
        BufferedImage img = newRgba(32, 32);
        Graphics2D g = setup(img);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
        g.setColor(new Color(0x88, 0xB0, 0xD8));
        g.fillRect(0, 0, 32, 32);
        g.setColor(new Color(0x44, 0x60, 0x80));
        g.drawRect(0, 0, 31, 31);
        g.setColor(new Color(0xE0, 0xEC, 0xF6));
        g.fillRect(2, 2, 28, 3);
        g.dispose();
        write(img, path);
    }

    private static void generateMoverPng(String path) throws IOException {
        BufferedImage img = newRgba(64, 16);
        Graphics2D g = setup(img);
        g.setColor(new Color(0xC8, 0xA0, 0x40));
        g.fillRect(0, 0, 64, 16);
        g.setColor(new Color(0x60, 0x40, 0x10));
        g.drawRect(0, 0, 63, 15);
        g.drawLine(4, 6, 60, 6);
        g.drawLine(4, 10, 60, 10);
        g.dispose();
        write(img, path);
    }

    private static BufferedImage newRgba(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    private static Graphics2D setup(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        return g;
    }

    private static void write(BufferedImage img, String path) throws IOException {
        File f = new File(path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
        System.out.println("  wrote " + path);
    }
}
