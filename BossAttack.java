






























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
