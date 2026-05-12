










































import java.awt.*;
public class SpikeArray extends BossAttack {











    private int[] spikeX;






    private int groundY;


    private static final int SPIKE_HEIGHT = 48;


    private static final int SPIKE_WIDTH = 12;






    private Player player;





















    public SpikeArray(int playerX, int groundY, Player player) {
        super(playerX - 80, groundY - SPIKE_HEIGHT, 160, SPIKE_HEIGHT, 2400L);
        this.groundY = groundY;
        this.player = player;


        this.spikeX = new int[5];
        for (int i = 0; i < 5; i++) {
            spikeX[i] = playerX - 80 + (i * 40);
        }
    }




















    @Override
    public void update(long deltaMs) {
        super.update(deltaMs);
        if (!active) {
            return;
        }


        if (elapsed >= 400 && elapsed <= 2000) {
            if (player != null && !player.isInvincible()) {
                int currentHeight = SPIKE_HEIGHT;
                for (int sx : spikeX) {
                    Rectangle spikeBounds = new Rectangle(
                            sx, groundY - currentHeight, SPIKE_WIDTH, currentHeight);
                    if (spikeBounds.intersects(player.getBounds())) {
                        player.takeDamage(1);
                        return;
                    }
                }
            }
        }
    }














    @Override
    public void render(Graphics2D g) {
        if (!active) {
            return;
        }


        int currentHeight;
        if (elapsed < 400) {
            float progress = (float) elapsed / 400f;
            currentHeight = (int) (SPIKE_HEIGHT * progress);
        } else if (elapsed <= 2000) {
            currentHeight = SPIKE_HEIGHT;
        } else {
            float progress = (float) (elapsed - 2000) / 400f;
            currentHeight = (int) (SPIKE_HEIGHT * (1f - progress));
        }

        if (currentHeight <= 0) {
            return;
        }


        g.setColor(new Color(0x4a, 0x30, 0x50));
        for (int sx : spikeX) {
            int spikeTop = groundY - currentHeight;

            int[] xPoints = {sx, sx + SPIKE_WIDTH / 2, sx + SPIKE_WIDTH};
            int[] yPoints = {groundY, spikeTop, groundY};
            g.fillPolygon(xPoints, yPoints, 3);
        }
    }
}
