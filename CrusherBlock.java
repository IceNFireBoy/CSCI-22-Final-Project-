/**
 * A large 80x80 dark block that follows the Apprentice's gesture position, lerping
 * toward it each tick. Deals 2 damage to the Wanderer on contact. Duration: 8000 ms.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;

public class CrusherBlock extends BossAttack {

    /** The current position of the crusher block. */
    private Point currentPos;

    /** The target position the block lerps toward (gesture position). */
    private Point targetPos;

    /** Reference to the Wanderer for contact damage. */
    private Player player;

    /** The interpolation factor per tick. */
    private static final float LERP_FACTOR = 0.04f;

    /**
     * Constructs a new {@code CrusherBlock} starting at the centre-top of the arena.
     *
     * @param gesturePos the initial gesture target position
     * @param player     the Wanderer entity for contact damage
     */
    public CrusherBlock(Point gesturePos, Player player) {
        super(512, 0, 80, 80, 8000L);
        this.currentPos = new Point(512, 0);
        this.targetPos = new Point(gesturePos);
        this.player = player;
    }

    /**
     * Lerps the block toward the gesture target and checks for player contact.
     *
     * @param deltaMs the time elapsed since the last tick, in milliseconds
     */
    @Override
    public void update(long deltaMs) {
        super.update(deltaMs);
        if (!active) {
            return;
        }

        // Lerp toward target
        currentPos.x = (int) (currentPos.x + (targetPos.x - currentPos.x) * LERP_FACTOR);
        currentPos.y = (int) (currentPos.y + (targetPos.y - currentPos.y) * LERP_FACTOR);

        // Update GameElement position for bounds
        x = currentPos.x - 40;
        y = currentPos.y - 40;

        // Contact damage — 2 damage within 30px
        if (player != null && !player.isInvincible()) {
            double dx = currentPos.x - (player.getX() + 12);
            double dy = currentPos.y - (player.getY() + 16);
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < 30) {
                player.takeDamage(2);
            }
        }
    }

    /**
     * Renders the crusher as an 80x80 dark rectangle with a magenta border.
     *
     * @param g the {@link Graphics2D} context to draw on
     */
    @Override
    public void render(Graphics2D g) {
        if (!active) {
            return;
        }

        Stroke originalStroke = g.getStroke();

        // Fill — dark #1a1020
        g.setColor(new Color(0x1a, 0x10, 0x20));
        g.fillRect(x, y, 80, 80);

        // Stroke — magenta #8b2070
        g.setColor(new Color(0x8b, 0x20, 0x70));
        g.setStroke(new BasicStroke(2f));
        g.drawRect(x, y, 80, 80);

        g.setStroke(originalStroke);
    }

    /**
     * Updates the gesture target position.
     *
     * @param target the new target point
     */
    public void setTargetPos(Point target) {
        this.targetPos = new Point(target);
    }
}
