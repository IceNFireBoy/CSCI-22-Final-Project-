/**
 * A defensive barrier that blocks player projectiles for 2 seconds. Positioned
 * at (717, 364) protecting the boss panel. Any player-fired projectile that
 * intersects the shield rectangle is deactivated.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.List;

public class Shield extends BossAttack {

    /** The bounding rectangle of the shield barrier. */
    private Rectangle shieldRect;

    /** Reference to the list of active projectiles to intercept. */
    private List<Projectile> projectiles;

    /**
     * Constructs a new {@code Shield} at the fixed boss-panel position.
     *
     * @param projectiles the list of active projectiles to check for interception
     */
    public Shield(List<Projectile> projectiles) {
        super(717, 364, 120, 40, 2000L);
        this.shieldRect = new Rectangle(717, 364, 120, 40);
        this.projectiles = projectiles;
    }

    /**
     * Checks all active player projectiles and deactivates any that intersect
     * the shield.
     *
     * @param deltaMs the time elapsed since the last tick, in milliseconds
     */
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

    /**
     * Renders the shield as a semi-transparent gold barrier with a golden stroke.
     *
     * @param g the {@link Graphics2D} context to draw on
     */
    @Override
    public void render(Graphics2D g) {
        if (!active) {
            return;
        }

        Composite originalComposite = g.getComposite();
        Stroke originalStroke = g.getStroke();

        // Fill — semi-transparent gold
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
        g.setColor(new Color(0xc9, 0xa8, 0x4c));  // #c9a84c
        g.fillRect(shieldRect.x, shieldRect.y, shieldRect.width, shieldRect.height);

        // Stroke — gold border
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g.setColor(new Color(0xf0, 0xcc, 0x7a));  // #f0cc7a
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(shieldRect.x, shieldRect.y, shieldRect.width, shieldRect.height);

        g.setComposite(originalComposite);
        g.setStroke(originalStroke);
    }
}
