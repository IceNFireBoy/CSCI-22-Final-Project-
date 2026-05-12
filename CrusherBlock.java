















































import java.awt.*;
public class CrusherBlock extends BossAttack {











    private Point currentPos;







    private Point targetPos;






    private Player player;






    private static final float LERP_FACTOR = 0.04f;



















    public CrusherBlock(Point gesturePos, Player player) {
        super(512, 0, 80, 80, 8000L);
        this.currentPos = new Point(512, 0);
        this.targetPos  = new Point(gesturePos);
        this.player = player;
    }
























    @Override
    public void update(long deltaMs) {
        super.update(deltaMs);
        if (!active) {
            return;
        }


        currentPos.x = (int) (currentPos.x + (targetPos.x - currentPos.x) * LERP_FACTOR);
        currentPos.y = (int) (currentPos.y + (targetPos.y - currentPos.y) * LERP_FACTOR);


        x = currentPos.x - 40;
        y = currentPos.y - 40;


        if (player != null && !player.isInvincible()) {
            double dx = currentPos.x - (player.getX() + 12);
            double dy = currentPos.y - (player.getY() + 16);
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < 30) {
                player.takeDamage(2);
            }
        }
    }











    @Override
    public void render(Graphics2D g) {
        if (!active) {
            return;
        }

        Stroke originalStroke = g.getStroke();


        g.setColor(new Color(0x1a, 0x10, 0x20));
        g.fillRect(x, y, 80, 80);


        g.setColor(new Color(0x8b, 0x20, 0x70));
        g.setStroke(new BasicStroke(2f));
        g.drawRect(x, y, 80, 80);

        g.setStroke(originalStroke);
    }
















    public void setTargetPos(Point target) {
        this.targetPos = new Point(target);
    }
}
