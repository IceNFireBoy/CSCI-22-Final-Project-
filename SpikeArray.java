/**
 * Spawns 5 ground spikes centred on the Wanderer's position with +/-40 px spread.
 * Spikes rise over 400 ms, remain active for 1600 ms dealing 1 damage on contact,
 * then retract over 400 ms. Total duration: 2400 ms.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

public class SpikeArray extends BossAttack {

    /** X-coordinates for each of the 5 spikes. */
    private int[] spikeX;

    /** The Y-coordinate of the ground from which spikes emerge. */
    private int groundY;

    /** The full height of each spike when fully extended. */
    private static final int SPIKE_HEIGHT = 48;

    /** The width of each spike. */
    private static final int SPIKE_WIDTH = 12;

    /** Reference to the Wanderer for contact damage. */
    private Player player;

    /**
     * Constructs a new {@code SpikeArray} centred on the given player x-position.
     *
     * @param playerX the Wanderer's current x-coordinate (centre)
     * @param groundY the y-coordinate of the ground surface
     * @param player  the Wanderer entity for contact damage
     */
    public SpikeArray(int playerX, int groundY, Player player) {
        super(playerX - 80, groundY - SPIKE_HEIGHT, 160, SPIKE_HEIGHT, 2400L);
        this.groundY = groundY;
        this.player = player;

        // 5 spikes centred on playerX with +/-40px spread
        this.spikeX = new int[5];
        for (int i = 0; i < 5; i++) {
            spikeX[i] = playerX - 80 + (i * 40);
        }
    }

    /**
     * Updates the spike state and checks for player contact during the active phase.
     *
     * @param deltaMs the time elapsed since the last tick, in milliseconds
     */
    @Override
    public void update(long deltaMs) {
        super.update(deltaMs);
        if (!active) {
            return;
        }

        // Active damage phase: 400ms to 2000ms
        if (elapsed >= 400 && elapsed <= 2000) {
            if (player != null && !player.isInvincible()) {
                int currentHeight = SPIKE_HEIGHT;
                for (int sx : spikeX) {
                    Rectangle spikeBounds = new Rectangle(
                            sx, groundY - currentHeight, SPIKE_WIDTH, currentHeight);
                    if (spikeBounds.intersects(player.getBounds())) {
                        player.takeDamage(1);
                        return;  // Only one hit per tick
                    }
                }
            }
        }
    }

    /**
     * Renders spikes at their current rise/retract state based on elapsed time.
     *
     * @param g the {@link Graphics2D} context to draw on
     */
    @Override
    public void render(Graphics2D g) {
        if (!active) {
            return;
        }

        // Calculate current spike height based on phase
        int currentHeight;
        if (elapsed < 400) {
            // Rising phase: 0 to SPIKE_HEIGHT over 400ms
            float progress = (float) elapsed / 400f;
            currentHeight = (int) (SPIKE_HEIGHT * progress);
        } else if (elapsed <= 2000) {
            // Full height
            currentHeight = SPIKE_HEIGHT;
        } else {
            // Retracting phase: SPIKE_HEIGHT to 0 over 400ms (2000-2400)
            float progress = (float) (elapsed - 2000) / 400f;
            currentHeight = (int) (SPIKE_HEIGHT * (1f - progress));
        }

        if (currentHeight <= 0) {
            return;
        }

        // Draw each spike as a triangle-ish shape
        g.setColor(new Color(0x4a, 0x30, 0x50));  // dark purple
        for (int sx : spikeX) {
            int spikeTop = groundY - currentHeight;
            // Draw as filled polygon (triangle spike)
            int[] xPoints = {sx, sx + SPIKE_WIDTH / 2, sx + SPIKE_WIDTH};
            int[] yPoints = {groundY, spikeTop, groundY};
            g.fillPolygon(xPoints, yPoints, 3);
        }
    }
}
