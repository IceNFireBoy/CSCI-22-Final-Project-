/**
 * Represents a single ranged shot fired by the Wanderer in Lumen Architect, tracking
 * its own velocity, damage value, and the distance it has traveled so far. When the
 * cumulative traveled distance exceeds the configured maximum range the projectile
 * deactivates itself automatically, preventing it from persisting indefinitely across
 * the level.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;

public class Projectile extends GameElement {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** The horizontal velocity component of this projectile in pixels per millisecond. */
    private float velX;

    /** The vertical velocity component of this projectile in pixels per millisecond. */
    private float velY;

    /** The amount of damage this projectile deals to any entity it strikes. */
    private int damage;

    /**
     * The cumulative pixel distance this projectile has traveled since it was created.
     * Used in conjunction with {@link #maxRange} to determine when to deactivate.
     */
    private int traveledDistance;

    /**
     * The maximum pixel distance this projectile may travel before it is automatically
     * deactivated and removed from play.
     */
    private int maxRange;

    /**
     * Whether this projectile was fired by the player ({@code true}) or by a
     * future boss mechanic ({@code false}).
     */
    private boolean isFromPlayer;

    /** Reference to the platform list for tile collision. */
    private List<Platform> platforms;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code Projectile} at the given world position with the
     * specified velocity vector, damage value, maximum travel range, and origin flag.
     *
     * @param x            the initial x-coordinate of the projectile's left edge
     * @param y            the initial y-coordinate of the projectile's top edge
     * @param velX         the horizontal velocity in pixels per tick
     * @param velY         the vertical velocity in pixels per tick
     * @param damage       the damage dealt on contact with a valid target
     * @param maxRange     the maximum travel distance in pixels before auto-deactivation
     * @param isFromPlayer {@code true} if this projectile was fired by the Wanderer
     */
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

    /**
     * Constructs a player-fired {@code Projectile} (backward-compatible convenience
     * constructor that defaults {@code isFromPlayer} to {@code true}).
     *
     * @param x        the initial x-coordinate
     * @param y        the initial y-coordinate
     * @param velX     the horizontal velocity in pixels per tick
     * @param velY     the vertical velocity in pixels per tick
     * @param damage   the damage dealt on contact
     * @param maxRange the maximum travel distance before auto-deactivation
     */
    public Projectile(int x, int y, float velX, float velY, int damage, int maxRange) {
        this(x, y, velX, velY, damage, maxRange, true);
    }

    // -------------------------------------------------------------------------
    // GameElement overrides
    // -------------------------------------------------------------------------

    /**
     * Advances this projectile's position by its velocity, accumulates traveled
     * distance, checks for platform collision, and deactivates when range is
     * exceeded. Target collision is handled externally by {@link physics.CollisionDetector}.
     *
     * @param deltaMs the time elapsed since the last update, in milliseconds
     */
    @Override
    public void update(long deltaMs) {
        if (!active) {
            return;
        }

        // Move by velocity
        x += (int) velX;
        y += (int) velY;

        // Accumulate distance
        traveledDistance += (int) Math.abs(velX);

        // Range check
        if (traveledDistance >= maxRange) {
            setActive(false);
            return;
        }

        // Platform collision — deactivate on contact with solid tiles
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

    /**
     * Renders this projectile as a small golden ellipse with a trailing light
     * streak of three semi-transparent circles behind it.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    @Override
    public void render(Graphics2D g) {
        if (!active) {
            return;
        }

        Composite originalComposite = g.getComposite();

        // Main projectile body — filled 8x8 ellipse
        g.setColor(new Color(0xf0, 0xcc, 0x7a));  // #f0cc7a
        g.fillOval(x, y, width, height);

        // Trailing light streak — 3 circles behind in opposite direction of travel
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

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /**
     * Sets the platform list for tile collision detection.
     *
     * @param platforms the current level's platforms; must not be {@code null}
     */
    public void setPlatforms(List<Platform> platforms) {
        this.platforms = platforms;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the horizontal velocity of this projectile.
     *
     * @return horizontal velocity in pixels per millisecond
     */
    public float getVelX() {
        return velX;
    }

    /**
     * Returns the vertical velocity of this projectile.
     *
     * @return vertical velocity in pixels per millisecond
     */
    public float getVelY() {
        return velY;
    }

    /**
     * Returns the damage this projectile deals on contact.
     *
     * @return the damage value
     */
    public int getDamage() {
        return damage;
    }

    /**
     * Returns the cumulative distance this projectile has traveled since creation.
     *
     * @return distance in pixels
     */
    public int getTraveledDistance() {
        return traveledDistance;
    }

    /**
     * Returns the maximum range this projectile can travel before being deactivated.
     *
     * @return maximum range in pixels
     */
    public int getMaxRange() {
        return maxRange;
    }

    /**
     * Returns whether this projectile was fired by the player.
     *
     * @return {@code true} if player-fired; {@code false} for boss projectiles
     */
    public boolean isFromPlayer() {
        return isFromPlayer;
    }
}
