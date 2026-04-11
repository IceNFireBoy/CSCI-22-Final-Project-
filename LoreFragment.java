/**
 * Represents a collectible narrative shard hidden throughout the Lumen Architect world
 * that the Wanderer can discover to expand the game's story and unlock new combat
 * abilities. Each fragment carries a unique identifier, body text displayed in the
 * lore log, an optional ability it grants upon collection, and a flag recording whether
 * the Wanderer has already picked it up.
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
import java.awt.Polygon;

public class LoreFragment extends GameElement {

    // =========================================================================
    // Enum — AbilityUnlock
    // =========================================================================

    /**
     * Enumerates the abilities that a {@link LoreFragment} can grant to the Wanderer
     * upon collection. Most fragments carry narrative value only ({@link #NONE}), while
     * key story fragments bestow a specific combat or traversal skill.
     */
    public enum AbilityUnlock {

        /** This fragment grants no new ability; it is purely narrative. */
        NONE,

        /** Unlocks the Wanderer's close-range melee attack. */
        MELEE,

        /** Unlocks the ability to fire a ranged projectile. */
        PROJECTILE,

        /** Unlocks the dodge-roll evasion move. */
        DODGE,

        /** Unlocks the ability to cling to and climb vertical walls. */
        WALL_CLING,

        /**
         * Unlocks the shadow-dash ability, allowing the Wanderer to phase through a
         * short distance while temporarily immune to damage.
         */
        SHADOW_DASH
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * A unique string identifier for this fragment used for save-state tracking and
     * lore-log lookup (e.g., {@code "FRAG_001"}).
     */
    private String fragmentID;

    /**
     * The narrative body text displayed in the lore log when the Wanderer collects
     * this fragment.
     */
    private String bodyText;

    /**
     * The ability unlocked by collecting this fragment. Defaults to
     * {@link AbilityUnlock#NONE} for purely narrative fragments.
     */
    private AbilityUnlock unlock;

    /**
     * Whether this fragment has already been collected by the Wanderer. Collected
     * fragments are deactivated and no longer rendered or checked for collision.
     */
    private boolean collected;

    /**
     * Whether this fragment is gated behind prerequisite abilities the Wanderer has
     * not yet obtained. A gated fragment is visible but cannot be collected until the
     * gate condition is satisfied.
     */
    private boolean gated;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code LoreFragment} with the given identifier, body text, and
     * ability unlock, placed at the specified world coordinates. The fragment begins
     * uncollected.
     *
     * @param fragmentID a unique identifier string for this fragment; must not be
     *                   {@code null}
     * @param bodyText   the narrative text shown in the lore log on collection; must
     *                   not be {@code null}
     * @param unlock     the {@link AbilityUnlock} granted upon collection; use
     *                   {@link AbilityUnlock#NONE} for purely narrative fragments
     * @param x          the x-coordinate of the fragment's left edge in world space
     * @param y          the y-coordinate of the fragment's top edge in world space
     */
    public LoreFragment(String fragmentID, String bodyText, AbilityUnlock unlock,
                        int x, int y) {
        super(x, y, 20, 20);
        this.fragmentID = fragmentID;
        this.bodyText = bodyText;
        this.unlock = unlock;
        this.collected = false;
        this.gated = false;
    }

    // -------------------------------------------------------------------------
    // GameElement overrides
    // -------------------------------------------------------------------------

    /**
     * Updates this fragment's state for the current game tick. This stub will later
     * drive a gentle float or glow animation when the fragment is not yet collected.
     *
     * @param deltaMs the time elapsed since the last update, in milliseconds
     */
    @Override
    public void update(long deltaMs) {
        // Stub — idle animation logic to be implemented in a later phase.
    }

    /**
     * Renders this fragment onto the provided graphics context. This stub will later
     * draw a glowing shard sprite when the fragment is active and uncollected.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    @Override
    public void render(Graphics2D g) {
        if (collected) return;

        boolean isCombat = (unlock != AbilityUnlock.NONE);

        // Outer pulse glow
        long now = System.currentTimeMillis();
        float pulse = (float)(Math.sin(now * 0.004) * 0.3 + 0.7);
        int glowSize = (int)(pulse * 8);
        if (isCombat) {
            g.setColor(new Color(0xf0, 0xcc, 0x7a, 50));
        } else {
            g.setColor(new Color(0xc9, 0xa8, 0x4c, 40));
        }
        g.fillOval(x - glowSize, y - glowSize, width + glowSize * 2, height + glowSize * 2);

        // Draw angular shard shape as a polygon
        int cx = x + width / 2;
        int cy = y + height / 2;
        int r = width / 2;
        int[] xPoints = {cx, cx + r, cx + r / 2, cx, cx - r / 2, cx - r};
        int[] yPoints = {cy - r, cy - r / 3, cy + r / 2, cy + r, cy + r / 2, cy - r / 3};
        Polygon shard = new Polygon(xPoints, yPoints, 6);

        if (isCombat) {
            g.setColor(new Color(0xf0, 0xcc, 0x7a));
        } else {
            g.setColor(new Color(0xc9, 0xa8, 0x4c));
        }
        g.fill(shard);

        // Bright inner highlight
        g.setColor(new Color(0xff, 0xff, 0xf0, 180));
        g.setStroke(new BasicStroke(0.8f));
        g.drawLine(cx - r / 3, cy - r / 2, cx + r / 4, cy);
    }

    /**
     * Sets the collected state of this fragment.
     *
     * @param collected {@code true} to mark as collected
     */
    public void setCollected(boolean collected) {
        this.collected = collected;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the unique identifier of this fragment.
     *
     * @return the fragment ID string; never {@code null}
     */
    public String getFragmentID() {
        return fragmentID;
    }

    /**
     * Returns the narrative body text associated with this fragment.
     *
     * @return the body text string; never {@code null}
     */
    public String getBodyText() {
        return bodyText;
    }

    /**
     * Returns the ability unlocked by collecting this fragment.
     *
     * @return the {@link AbilityUnlock} constant for this fragment
     */
    public AbilityUnlock getUnlock() {
        return unlock;
    }

    /**
     * Returns whether this fragment has already been collected by the Wanderer.
     *
     * @return {@code true} if collected; {@code false} otherwise
     */
    public boolean isCollected() {
        return collected;
    }

    /**
     * Marks this fragment as collected and deactivates it so it is no longer rendered
     * or checked for collision.
     */
    public void collect() {
        this.collected = true;
        setActive(false);
    }

    /**
     * Returns whether this fragment is gated behind prerequisite abilities.
     *
     * @return {@code true} if gated and uncollectable; {@code false} otherwise
     */
    public boolean isGated() {
        return gated;
    }

    /**
     * Sets the gated state of this fragment. A gated fragment is rendered as
     * unreachable by the level renderer until its prerequisites are met.
     *
     * @param gated {@code true} to mark as gated; {@code false} to allow collection
     */
    public void setGated(boolean gated) {
        this.gated = gated;
    }
}
