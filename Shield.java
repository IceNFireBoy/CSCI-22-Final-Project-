












































import java.awt.*;
import java.util.*;
import java.util.List;
public class Shield extends BossAttack {











    private Rectangle shieldRect;










    private List<Projectile> projectiles;



















    public Shield(List<Projectile> projectiles) {
        super(717, 364, 120, 40, 2000L);
        this.shieldRect = new Rectangle(717, 364, 120, 40);
        this.projectiles = projectiles;
    }





















    @Override
    public void update(long deltaMs) {
        super.update(deltaMs);
        if (!active) {
            return;
        }

        if (projectiles != null) {
            for (Projectile proj : projectiles) {
                if (proj.isActive() && proj.isFromPlayer()
                        && proj.getBounds().intersects(shieldRect)) {
                    proj.setActive(false);
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
        Stroke originalStroke = g.getStroke();


        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
        g.setColor(new Color(0xc9, 0xa8, 0x4c));
        g.fillRect(shieldRect.x, shieldRect.y, shieldRect.width, shieldRect.height);


        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g.setColor(new Color(0xf0, 0xcc, 0x7a));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(shieldRect.x, shieldRect.y, shieldRect.width, shieldRect.height);

        g.setComposite(originalComposite);
        g.setStroke(originalStroke);
    }
}
