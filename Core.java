






































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
