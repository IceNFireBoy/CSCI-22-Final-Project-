/**
 * Abstract base class for environmental hazards that deal damage to players.
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
public class Hazard extends GameElement implements Damageable {

    protected int damage;
    protected int health;
    protected int maxHealth;

    public Hazard(int x, int y, int width, int height, int damage, int maxHealth) {
        super(x, y, width, height);
        this.damage = damage;
        this.maxHealth = maxHealth;
        this.health = maxHealth;
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
        return maxHealth;
    }

    @Override
    public boolean isAlive() {
        return health > 0;
    }

    public int getDamage() {
        return damage;
    }
}
