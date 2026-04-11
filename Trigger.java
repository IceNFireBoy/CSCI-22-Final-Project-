/**
 * Represents a spatial trigger zone in the Lumen Architect game world that fires a
 * one-shot event when the Wanderer enters its bounding area. Each trigger carries a
 * type string identifying its purpose (e.g. cutscene, tutorial hint) and an arbitrary
 * parameter map that downstream handlers can inspect to configure the triggered action.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.awt.Graphics2D;
import java.util.HashMap;
import java.util.Map;

public class Trigger extends GameElement {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** The type identifier for this trigger (e.g. "CUTSCENE", "WALL_BREAK_HINT"). */
    private String type;

    /** Arbitrary key-value parameters associated with this trigger. */
    private Map<String, Object> params;

    /** Whether this trigger has already fired. Once fired it will not activate again. */
    private boolean fired;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code Trigger} at the given world position with the specified
     * type and parameters. The trigger zone defaults to 32x32 pixels and starts unfired.
     *
     * @param type   the trigger type identifier; must not be {@code null}
     * @param x      the x-coordinate of the trigger's left edge in world space
     * @param y      the y-coordinate of the trigger's top edge in world space
     * @param params a map of additional parameters; if {@code null}, an empty map is used
     */
    public Trigger(String type, int x, int y, Map<String, Object> params) {
        super(x, y, 32, 32);
        this.type = type;
        this.params = (params != null) ? params : new HashMap<>();
        this.fired = false;
    }

    // -------------------------------------------------------------------------
    // GameElement overrides
    // -------------------------------------------------------------------------

    /**
     * No-op update; triggers are passive until checked by the collision system.
     *
     * @param deltaMs the time elapsed since the last update, in milliseconds
     */
    @Override
    public void update(long deltaMs) {
        // Triggers are event-driven, no per-tick logic needed.
    }

    /**
     * Triggers are invisible and not rendered. This method intentionally does nothing.
     *
     * @param g the {@link Graphics2D} context; ignored
     */
    @Override
    public void render(Graphics2D g) {
        // Triggers have no visual representation.
    }

    // -------------------------------------------------------------------------
    // Trigger logic
    // -------------------------------------------------------------------------

    /**
     * Marks this trigger as fired. Subsequent calls to {@link #isFired()} will return
     * {@code true}.
     */
    public void fire() {
        this.fired = true;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the type identifier of this trigger.
     *
     * @return the trigger type string; never {@code null}
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the parameter map associated with this trigger.
     *
     * @return a mutable map of parameters; never {@code null}
     */
    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * Returns whether this trigger has already fired.
     *
     * @return {@code true} if fired; {@code false} otherwise
     */
    public boolean isFired() {
        return fired;
    }
}
