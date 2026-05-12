













































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
