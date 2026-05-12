/**
 * Defines the boss's attack patterns and projectile behaviors.
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
public abstract class BossAttack extends GameElement {

    protected long durationMs;

    protected long elapsed;

    protected boolean expired;

    public BossAttack(int x, int y, int width, int height, long durationMs) {
        super(x, y, width, height);
        this.durationMs = durationMs;
        this.elapsed = 0L;
        this.expired = false;
    }

    @Override
    public void update(long deltaMs) {
        elapsed += deltaMs;
        if (elapsed >= durationMs) {
            expired = true;
            setActive(false);
        }
    }

    public boolean isExpired() {
        return expired;
    }

    public long getElapsed() {
        return elapsed;
    }

    public Rectangle getHitbox() {
        return getBounds();
    }
}
