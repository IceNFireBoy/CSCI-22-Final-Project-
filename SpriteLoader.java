/**
 * Loads and caches sprite images from disk for use throughout the game.
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
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;
public class SpriteLoader {

    private static SpriteLoader instance;

    private HashMap<String, BufferedImage> cache = new HashMap<>();

    public static synchronized SpriteLoader getInstance() {
        if (instance == null) instance = new SpriteLoader();
        return instance;
    }

    private SpriteLoader() {}

    public BufferedImage load(String path) {
        if (cache.containsKey(path)) return cache.get(path);
        try {
            BufferedImage img = ImageIO.read(new File(path));
            if (img == null) throw new Exception("null result");
            cache.put(path, img);
            return img;
        } catch (Exception e) {
            System.out.println("SpriteLoader: missing sprite — " + path);
            BufferedImage placeholder = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = placeholder.createGraphics();
            g.setColor(new java.awt.Color(255, 0, 255));
            g.fillRect(0, 0, 64, 64);
            g.dispose();
            cache.put(path, placeholder);
            return placeholder;
        }
    }

    public BufferedImage tryLoad(String path) {
        String key = "try::" + path;
        if (cache.containsKey(key)) return cache.get(key);
        try {
            File f = new File(path);
            if (!f.isFile()) { cache.put(key, null); return null; }
            BufferedImage img = ImageIO.read(f);
            if (img == null) { cache.put(key, null); return null; }
            cache.put(key, img);
            return img;
        } catch (Exception e) {
            cache.put(key, null);
            return null;
        }
    }

    public BufferedImage loadScaled(String path, int w, int h) {
        BufferedImage src = load(path);
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return scaled;
    }
}
