





















import java.awt.*;
import java.util.*;
import java.util.List;
public class Projectile extends GameElement {





    private float velX;

    private float velY;

    private int damage;

    private int traveledDistance;

    private int maxRange;

    private boolean isFromPlayer;

    private List<Platform> platforms;





    public Projectile(int x, int y, float velX, float velY, int damage, int maxRange,
                      boolean isFromPlayer) {
        super(x, y, 8, 8);
        this.velX = velX;
        this.velY = velY;
        this.damage = damage;
        this.traveledDistance = 0;
        this.maxRange = maxRange;
        this.isFromPlayer = isFromPlayer;
    }

    public Projectile(int x, int y, float velX, float velY, int damage, int maxRange) {
        this(x, y, velX, velY, damage, maxRange, true);
    }





    @Override
    public void update(long deltaMs) {
        if (!active) {
            return;
        }


        x += (int) velX;
        y += (int) velY;


        traveledDistance += (int) Math.abs(velX);


        if (traveledDistance >= maxRange) {
            setActive(false);
            return;
        }


        if (platforms != null) {
            Rectangle projBounds = getBounds();
            for (Platform pl : platforms) {
                if (pl.isActive() && pl.isSolid()
                        && projBounds.intersects(pl.getBounds())) {
                    setActive(false);
                    return;
                }
            }
        }
    }

    @Override
    public void render(Graphics2D g) {
        if (!active) {
            return;
        }

        Composite originalComposite = g.getComposite();


        g.setColor(new Color(0xf0, 0xcc, 0x7a));
        g.fillOval(x, y, width, height);


        int trailDir = (velX >= 0) ? -1 : 1;

        for (int i = 1; i <= 3; i++) {
            float trailAlpha = 0.5f - (i * 0.15f);
            if (trailAlpha <= 0f) {
                trailAlpha = 0.05f;
            }
            int trailSize = Math.max(2, 8 - (i * 2));
            int offsetX = trailDir * i * 4;

            g.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, trailAlpha));
            g.setColor(new Color(0xf0, 0xcc, 0x7a));
            int centreX = x + width / 2 + offsetX - trailSize / 2;
            int centreY = y + height / 2 - trailSize / 2;
            g.fillOval(centreX, centreY, trailSize, trailSize);
        }

        g.setComposite(originalComposite);
    }





    public void setPlatforms(List<Platform> platforms) {
        this.platforms = platforms;
    }





    public float getVelX() { return velX; }

    public float getVelY() { return velY; }

    public int getDamage() { return damage; }

    public int getTraveledDistance() { return traveledDistance; }

    public int getMaxRange() { return maxRange; }

    public boolean isFromPlayer() { return isFromPlayer; }
}
