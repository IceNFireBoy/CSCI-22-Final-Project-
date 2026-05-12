







































import java.awt.*;
public abstract class GameElement implements Renderable {





    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean active;





    public GameElement(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.active = true;
    }






















    public abstract void update(long deltaMs);















    @Override
    public abstract void render(Graphics2D g);




















    @Override
    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }












    public boolean isActive() {
        return active;
    }














    public void setActive(boolean active) {
        this.active = active;
    }























    public static class CollisionBox {


        public int x;


        public int y;


        public int w;


        public int h;














        public CollisionBox(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }





















        public boolean intersects(CollisionBox other) {
            return this.x < other.x + other.w
                    && this.x + this.w > other.x
                    && this.y < other.y + other.h
                    && this.y + this.h > other.y;

        }
    }
}
