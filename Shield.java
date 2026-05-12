/**
 * Implements the Architect's temporary protective shield ability.
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
