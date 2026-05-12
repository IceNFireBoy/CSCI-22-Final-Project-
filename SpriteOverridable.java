/**
 * Interface for elements that support runtime sprite swapping.
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
public interface SpriteOverridable {

    void setSpritePath(String path);

    String getSpritePath();

    static boolean tryDrawSprite(Graphics2D g, SpriteOverridable e, int x, int y, int w, int h) {
        String path = e.getSpritePath();
        if (path == null) return false;
        BufferedImage img = SpriteLoader.getInstance().tryLoad(path);
        if (img == null) return false;
        g.drawImage(img, x, y, w, h, null);
        return true;
    }
}
