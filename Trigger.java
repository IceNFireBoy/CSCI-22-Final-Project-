/**
 * An invisible trigger zone that fires a callback when the player enters it.
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
import java.util.*;
public class Trigger extends GameElement implements SpriteOverridable {

    private String type;

    private Map<String, Object> params;

    private boolean fired;

    private String spritePath;

    public Trigger(String type, int x, int y, Map<String, Object> params) {
        super(x, y, 32, 32);
        this.type = type;
        this.params = (params != null) ? params : new HashMap<>();
        this.fired = false;
        this.spritePath = null;
    }

    @Override
    public void update(long deltaMs) {

    }

    @Override
    public void render(Graphics2D g) {

        SpriteOverridable.tryDrawSprite(g, this, x, y, width, height);
    }

    public void fire() {
        this.fired = true;
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public boolean isFired() {
        return fired;
    }

    @Override
    public void setSpritePath(String path) {
        this.spritePath = path;
    }

    @Override
    public String getSpritePath() {
        return spritePath;
    }
}
