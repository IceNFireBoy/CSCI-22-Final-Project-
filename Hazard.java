































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
