














































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
