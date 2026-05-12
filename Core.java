/**
 * Represents a damageable boss-arena core that tracks its own health.
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
public class Core extends GameElement implements Damageable {

    private static final int MAX_HEALTH = 3;

    private int coreIndex;

    private int health;

    private boolean destroyed;

    public Core(int coreIndex, int x, int y) {
        super(x, y, 32, 32);
        this.coreIndex = coreIndex;
        this.health = MAX_HEALTH;
        this.destroyed = false;
    }

    @Override
    public void update(long deltaMs) {

    }

    @Override
    public void render(Graphics2D g) {

    }

    @Override
    public void takeDamage(int amount) {

    }

    @Override
    public int getHealth() {
        return health;
    }

    @Override
    public int getMaxHealth() {
        return MAX_HEALTH;
    }

    @Override
    public boolean isAlive() {
        return health > 0;
    }

    public int getCoreIndex() {
        return coreIndex;
    }

    public boolean isDestroyed() {
        return destroyed;
    }
}
