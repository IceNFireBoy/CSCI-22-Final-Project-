/**
 * Represents a static or semi-static surface within the Lumen Architect game world
 * that the Wanderer can stand, slide, or bounce on. Each platform carries a type that
 * governs its behaviour, a budget cost relevant to the Apprentice's placement economy,
 * and a lighting flag indicating whether it is currently illuminated.
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
import java.util.Random;

public class Platform extends GameElement {

    // =========================================================================
    // Enum — PlatformType
    // =========================================================================

    /**
     * Enumerates the distinct varieties of platform that can appear in the game world.
     * Each constant represents a unique surface material or behaviour archetype that
     * influences how the Wanderer interacts with it.
     */
    public enum PlatformType {

        /** A solid, ordinary brick surface with no special effect. */
        BRICK,

        /** A sloped surface that causes the Wanderer to slide when standing on it. */
        SLIDE,

        /** A surface that launches the Wanderer upward on contact. */
        SPRING,

        /** A vertical or horizontal barrier that blocks movement entirely. */
        WALL,

        /** A surface that deteriorates and disappears after the Wanderer lands on it. */
        CRUMBLE,

        /** A surface that is not rendered but still participates in collision. */
        INVISIBLE,

        /**
         * A surface that disguises itself as another platform type, revealed only under
         * certain conditions.
         */
        MIMIC
    }

    // -------------------------------------------------------------------------
    // Core fields
    // -------------------------------------------------------------------------

    /** The behavioural type of this platform. */
    private PlatformType type;

    /**
     * The budget cost the Apprentice must pay to place this platform during the
     * preparation phase; higher-cost platforms offer stronger or rarer effects.
     */
    private int budgetCost;

    /**
     * Whether this platform is currently illuminated by a light source. Lit platforms
     * may affect DarkCrawler behaviour or reveal hidden platforms.
     */
    private boolean lit;

    // -------------------------------------------------------------------------
    // Crumble state fields
    // -------------------------------------------------------------------------

    /**
     * The absolute system-clock time in milliseconds when the Wanderer first contacted
     * this CRUMBLE platform. A value of {@code -1} means no contact has occurred yet.
     * After {@link #CRUMBLE_DELAY_MS} the platform is marked non-solid.
     */
    private long crumbleStartTime;

    /** Duration in milliseconds before a CRUMBLE platform loses solidity. */
    private static final long CRUMBLE_DELAY_MS = 3000L;

    // Crumble animation fields
    private long crumbleTimer = 0;
    private boolean crumbleStarted = false;
    private float shakeOffsetX = 0;
    private float shakeOffsetY = 0;
    private float fallVelocity = 0;
    private boolean falling = false;
    public boolean fullyGone = false;
    private static final int CRUMBLE_SHAKE_DURATION = 2000;
    private static final int CRACK_STAGE_1 = 700;
    private static final int CRACK_STAGE_2 = 1400;
    private Random rand = new Random();

    // -------------------------------------------------------------------------
    // Mimic state fields
    // -------------------------------------------------------------------------

    /**
     * Whether the Wanderer has already made first contact with this MIMIC platform.
     * Once triggered the collapse timer starts.
     */
    private boolean mimicTriggered;

    /**
     * The absolute system-clock time in milliseconds when this MIMIC platform was
     * first contacted. A value of {@code -1} means not yet triggered.
     * After {@link #MIMIC_DELAY_MS} the platform is marked non-solid.
     */
    private long mimicStartTime;

    /** Duration in milliseconds before a MIMIC platform collapses after first contact. */
    private static final long MIMIC_DELAY_MS = 800L;

    // -------------------------------------------------------------------------
    // Solidity flag
    // -------------------------------------------------------------------------

    /**
     * Whether this platform currently participates in collision resolution.
     * Starts {@code true}; set to {@code false} by the physics system after a CRUMBLE
     * or MIMIC platform reaches the end of its delay.
     */
    private boolean solid;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code Platform} of the specified type at the given world
     * position. Width and height default to 64×16 pixels, the standard platform tile
     * size.
     *
     * @param type the {@link PlatformType} that determines this platform's behaviour
     * @param x    the x-coordinate of the platform's left edge in world space
     * @param y    the y-coordinate of the platform's top edge in world space
     */
    public Platform(PlatformType type, int x, int y) {
        super(x, y, defaultWidth(type), defaultHeight(type));
        this.type             = type;
        this.budgetCost       = resolveBudgetCost(type);
        this.lit              = false;
        this.solid            = true;
        this.crumbleStartTime = -1L;
        this.mimicTriggered   = false;
        this.mimicStartTime   = -1L;
    }

    private static int defaultWidth(PlatformType type) {
        switch (type) {
            case SLIDE:   return 64;
            case SPRING:  return 24;
            case WALL:    return 16;
            default:      return 32; // BRICK, CRUMBLE, MIMIC, INVISIBLE
        }
    }

    private static int defaultHeight(PlatformType type) {
        switch (type) {
            case SPRING:  return 24;
            case WALL:    return 64;
            default:      return 16; // BRICK, CRUMBLE, MIMIC, INVISIBLE, SLIDE
        }
    }

    /**
     * Constructs a new {@code Platform} of the specified type at the given world
     * position with custom dimensions, used for non-standard tiles such as walls.
     *
     * @param type   the {@link PlatformType} that determines this platform's behaviour
     * @param x      the x-coordinate of the platform's left edge in world space
     * @param y      the y-coordinate of the platform's top edge in world space
     * @param width  the width of this platform in pixels
     * @param height the height of this platform in pixels
     */
    public Platform(PlatformType type, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.type             = type;
        this.budgetCost       = resolveBudgetCost(type);
        this.lit              = false;
        this.solid            = true;
        this.crumbleStartTime = -1L;
        this.mimicTriggered   = false;
        this.mimicStartTime   = -1L;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Derives a default budget cost from the given platform type. Rarer or more
     * impactful platform types carry a higher cost to balance the Apprentice's economy.
     *
     * @param type the {@link PlatformType} to evaluate
     * @return an integer budget cost associated with the type
     */
    private int resolveBudgetCost(PlatformType type) {
        switch (type) {
            case BRICK:     return 1;
            case SLIDE:     return 2;
            case SPRING:    return 3;
            case WALL:      return 2;
            case CRUMBLE:   return 2;
            case INVISIBLE: return 4;
            case MIMIC:     return 5;
            default:        return 1;
        }
    }

    // -------------------------------------------------------------------------
    // GameElement overrides
    // -------------------------------------------------------------------------

    /**
     * Updates the platform's timer-based state for the current game tick.
     * Handles crumble and mimic collapse by checking elapsed time against their
     * respective delay constants and marking the platform non-solid when the
     * delay has passed.
     *
     * @param deltaMs the time elapsed since the last update, in milliseconds
     */
    @Override
    public void update(long deltaMs) {
        // Crumble animation runs even after solid is false
        if (type == PlatformType.CRUMBLE && crumbleStarted && !fullyGone) {
            crumbleTimer += deltaMs;
            if (!falling) {
                float intensity = (float) crumbleTimer / CRUMBLE_SHAKE_DURATION;
                float maxShake = 2.0f + intensity * 3.0f;
                shakeOffsetX = (rand.nextFloat() - 0.5f) * maxShake * 2;
                shakeOffsetY = (rand.nextFloat() - 0.5f) * maxShake;
                if (crumbleTimer >= CRUMBLE_SHAKE_DURATION) {
                    falling = true;
                    fallVelocity = 0;
                    solid = false;
                    setActive(false);
                }
            } else {
                fallVelocity += 0.8f;
                y += (int) fallVelocity;
                shakeOffsetX = (rand.nextFloat() - 0.5f) * 6;
                if (y > 900) {
                    fullyGone = true;
                }
            }
            return;
        }

        if (!solid) return;

        long now = System.currentTimeMillis();

        if (type == PlatformType.CRUMBLE && crumbleStartTime >= 0) {
            if ((now - crumbleStartTime) >= CRUMBLE_DELAY_MS) {
                solid = false;
            }
        }

        if (type == PlatformType.MIMIC && mimicTriggered) {
            if ((now - mimicStartTime) >= MIMIC_DELAY_MS) {
                solid = false;
            }
        }
    }

    public void startCrumbling() {
        if (type == PlatformType.CRUMBLE && !crumbleStarted) {
            crumbleStarted = true;
        }
    }

    /**
     * Renders this platform onto the provided graphics context.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    @Override
    public void render(Graphics2D g) {
        Color fillColor;
        Color strokeColor = new Color(0x4a, 0x4a, 0x58);
        switch (type) {
            case CRUMBLE:
                if (fullyGone) return;
                int drawX = (int)(x + shakeOffsetX);
                int drawY = (int)(y + shakeOffsetY);
                float darken = crumbleStarted ? Math.min(1.0f, (float)crumbleTimer / CRUMBLE_SHAKE_DURATION) : 0;
                int baseR = (int)(0x2a + (0x1a - 0x2a) * darken);
                int baseG = (int)(0x2a + (0x10 - 0x2a) * darken);
                int baseB = (int)(0x35 + (0x10 - 0x35) * darken);
                g.setColor(new Color(baseR, baseG, baseB));
                g.fillRoundRect(drawX, drawY, width, height, 4, 4);
                int strokeR = (int)(0x4a + (0x8b - 0x4a) * darken);
                g.setColor(new Color(strokeR, 0x2a, 0x2a));
                g.setStroke(new BasicStroke(0.5f));
                g.drawRoundRect(drawX, drawY, width, height, 4, 4);
                if (crumbleStarted && crumbleTimer >= CRACK_STAGE_1) {
                    g.setColor(new Color(0x8b, 0x2a, 0x2a, 180));
                    g.setStroke(new BasicStroke(0.8f));
                    g.drawLine(drawX + 6, drawY + 2, drawX + 14, drawY + 12);
                }
                if (crumbleStarted && crumbleTimer >= CRACK_STAGE_2) {
                    g.setColor(new Color(0x8b, 0x2a, 0x2a, 220));
                    g.setStroke(new BasicStroke(0.8f));
                    g.drawLine(drawX + 22, drawY + 1, drawX + 16, drawY + 14);
                    g.setColor(new Color(0x1a, 0x0a, 0x0a, 200));
                    g.fillRect(drawX + 18, drawY, 5, 4);
                }
                if (falling) {
                    g.setColor(new Color(0x1a, 0x10, 0x10));
                    g.fillRect(drawX, drawY, width/2 - 1, height);
                    g.fillRect(drawX + width/2 + 1, drawY + 2, width/2 - 1, height);
                    g.setColor(new Color(0x8b, 0x20, 0x20, 150));
                    g.fillRect(drawX + width/2 - 1, drawY, 2, height);
                }
                return;
            case BRICK:
            case MIMIC:
                fillColor = new Color(0x2a, 0x2a, 0x35);
                break;
            case SLIDE:
                fillColor = new Color(0x2a, 0x2a, 0x35);
                break;
            case SPRING:
                fillColor = new Color(0x3a, 0x4a, 0x5a);
                break;
            case WALL:
                fillColor = new Color(0x2a, 0x2a, 0x35);
                break;
            case INVISIBLE:
                if (!lit) {
                    // Faint placeholder outline when unlit (not visible during Act 3 gameplay)
                    g.setColor(new Color(0x2a, 0x2a, 0x55, 40));
                    g.fillRoundRect(x, y, width, height, 4, 4);
                    return;
                }
                fillColor = new Color(0x3a, 0x3a, 0x88);
                break;
            default:
                fillColor = new Color(0x2a, 0x2a, 0x35);
        }
        g.setColor(fillColor);
        g.fillRoundRect(x, y, width, height, 4, 4);
        g.setColor(strokeColor);
        g.setStroke(new BasicStroke(0.5f));
        g.drawRoundRect(x, y, width, height, 4, 4);
    }

    // -------------------------------------------------------------------------
    // Core accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the behavioural type of this platform.
     *
     * @return the {@link PlatformType} of this platform
     */
    public PlatformType getType() {
        return type;
    }

    /**
     * Returns the budget cost the Apprentice must spend to place this platform.
     *
     * @return the budget cost as a positive integer
     */
    public int getBudgetCost() {
        return budgetCost;
    }

    /**
     * Sets the budget cost for this platform, allowing runtime balancing adjustments.
     *
     * @param budgetCost the new budget cost; should be a positive integer
     */
    public void setBudgetCost(int budgetCost) {
        this.budgetCost = budgetCost;
    }

    /**
     * Returns whether this platform is currently illuminated.
     *
     * @return {@code true} if this platform is lit; {@code false} otherwise
     */
    public boolean isLit() {
        return lit;
    }

    /**
     * Sets the illumination state of this platform.
     *
     * @param lit {@code true} to mark this platform as illuminated; {@code false} to
     *            extinguish it
     */
    public void setLit(boolean lit) {
        this.lit = lit;
    }

    // -------------------------------------------------------------------------
    // Solidity accessors
    // -------------------------------------------------------------------------

    /**
     * Returns whether this platform is currently solid and participates in collision.
     *
     * @return {@code true} if solid; {@code false} if the platform has crumbled or
     *         collapsed
     */
    public boolean isSolid() {
        return solid;
    }

    /**
     * Sets the solidity of this platform. Set to {@code false} by the physics system
     * after a crumble or mimic collapse delay has expired.
     *
     * @param solid {@code false} to make the platform passable
     */
    public void setSolid(boolean solid) {
        this.solid = solid;
    }

    // -------------------------------------------------------------------------
    // Crumble-state accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the system-clock time at which this CRUMBLE platform was first contacted.
     *
     * @return absolute timestamp in milliseconds, or {@code -1} if not yet contacted
     */
    public long getCrumbleStartTime() {
        return crumbleStartTime;
    }

    /**
     * Records the moment of first contact for a CRUMBLE platform. Should be called
     * exactly once by the collision detector on the first overlapping tick.
     *
     * @param crumbleStartTime absolute timestamp from {@link System#currentTimeMillis()}
     */
    public void setCrumbleStartTime(long crumbleStartTime) {
        this.crumbleStartTime = crumbleStartTime;
    }

    // -------------------------------------------------------------------------
    // Mimic-state accessors
    // -------------------------------------------------------------------------

    /**
     * Returns whether this MIMIC platform has been touched by the Wanderer.
     *
     * @return {@code true} once the collapse timer is running
     */
    public boolean isMimicTriggered() {
        return mimicTriggered;
    }

    /**
     * Marks this MIMIC platform as having been contacted, starting the collapse timer.
     *
     * @param mimicTriggered {@code true} to begin the collapse sequence
     */
    public void setMimicTriggered(boolean mimicTriggered) {
        this.mimicTriggered = mimicTriggered;
    }

    /**
     * Returns the system-clock time at which this MIMIC platform was first contacted.
     *
     * @return absolute timestamp in milliseconds, or {@code -1} if not yet triggered
     */
    public long getMimicStartTime() {
        return mimicStartTime;
    }

    /**
     * Records the moment of first contact for a MIMIC platform.
     *
     * @param mimicStartTime absolute timestamp from {@link System#currentTimeMillis()}
     */
    public void setMimicStartTime(long mimicStartTime) {
        this.mimicStartTime = mimicStartTime;
    }
}
