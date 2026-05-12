/**
 * Implements the Architect's searing-beam ranged attack ability.
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
public class SearingBeam extends BossAttack {

    private Point beamTarget;

    private boolean damageActive;

    private Player player;

    private int damageTick;

    private static final int BEAM_ORIGIN_X = 717;

    private static final int BEAM_ORIGIN_Y = 384;

    public SearingBeam(Point beamTarget, Player player) {
        super(BEAM_ORIGIN_X, BEAM_ORIGIN_Y, 0, 0, Long.MAX_VALUE);
        this.beamTarget = beamTarget;
        this.damageActive = true;
        this.player = player;
        this.damageTick = 0;
    }

    @Override
    public void update(long deltaMs) {

        if (!active) {
            return;
        }

        damageTick++;

        if (damageActive && player != null && !player.isInvincible()) {
            double dx = beamTarget.x - (player.getX() + 12);
            double dy = beamTarget.y - (player.getY() + 16);
            double dist = Math.sqrt(dx * dx + dy * dy);

            if (dist < 25 && damageTick % 30 == 0) {
                player.takeDamage(1);
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

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
        g.setColor(new Color(0xf8, 0xe4, 0xa0));
        g.setStroke(new BasicStroke(8f));
        g.drawLine(BEAM_ORIGIN_X, BEAM_ORIGIN_Y, beamTarget.x, beamTarget.y);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g.setColor(new Color(0xf8, 0xe4, 0xa0));
        g.setStroke(new BasicStroke(4f));
        g.drawLine(BEAM_ORIGIN_X, BEAM_ORIGIN_Y, beamTarget.x, beamTarget.y);

        g.setComposite(originalComposite);
        g.setStroke(originalStroke);
    }

    public void setBeamTarget(Point target) {
        this.beamTarget = target;
    }

    public void setDamageActive(boolean damageActive) {
        this.damageActive = damageActive;
    }
}
