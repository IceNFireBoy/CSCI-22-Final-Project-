/**
 * Represents a spatial trigger zone in the Lumen Architect game world that fires a
 * one-shot event when the Wanderer enters its bounding area. Each trigger carries a
 * type string identifying its purpose (e.g. cutscene, tutorial hint) and an arbitrary
 * parameter map that downstream handlers can inspect to configure the triggered action.
 *
 * <p>Architecture role: {@code Trigger} objects are invisible {@link GameElement}
 * subclasses placed by {@link LevelGenerator} subclasses via the {@code trigger()}
 * helper. They sit in {@link GameStarter#elements} alongside
 * visible entities but are never rendered. Each tick, {@link GameStarter} iterates
 * all active elements; when it finds a {@code Trigger} whose bounding box intersects
 * {@link Player#getBounds()}, it calls {@link #fire()} to latch the trigger and then
 * dispatches the event based on {@link #getType()} — for example routing to
 * {@link CutsceneRenderer} when the type is {@code "CUTSCENE"}, or to the tutorial
 * overlay when the type is {@code "WALL_BREAK_HINT"}.</p>
 *
 * <p>The one-shot guarantee is enforced by the {@link #fired} flag: once
 * {@link #fire()} is called, {@link #isFired()} returns {@code true} forever,
 * and the game loop skips already-fired triggers in its dispatch logic without
 * deactivating them (so their AABB remains queryable if needed for debugging).</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */


// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 1c "Abstract Classes" - extends GameElement, the abstract base
//                                 covered in the abstract-classes module.
// Module 3a "Graphics"          - render(Graphics2D) override uses 3a's
//                                 drawing primitives (fill, draw, drawImage)
//                                 plus 3a's RenderingHints anti-aliasing.
// Module 3c "Collision"         - getBounds() / getHitbox() returns a
//                                 Rectangle for AABB tests per 3c.
// Module 1a "Modifiers"         - private fields with public accessors;
//                                 where present, static final constants
//                                 follow the constants pattern from 1a.
// =========================================================================
import java.awt.Graphics2D;   // 2D rendering context required by the abstract render() signature; unused here since triggers are invisible
import java.util.HashMap;      // Default implementation for an empty parameter map when null is passed to the constructor
import java.util.Map;          // General Map interface for the params field; allows any Map implementation to be passed at construction

public class Trigger extends GameElement implements SpriteOverridable { // Extends GameElement to be stored in the unified elements list and participate in AABB intersection checks; implements SpriteOverridable so a level generator may give a trigger a visible glyph

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * The type identifier for this trigger (e.g. {@code "CUTSCENE"},
     * {@code "WALL_BREAK_HINT"}). Read by {@link GameStarter}'s trigger-dispatch
     * logic to decide which handler to invoke after {@link #fire()} is called.
     * Case-sensitive; must match the string constants expected by the dispatch switch.
     */
    private String type; // Type tag set at construction; dictates which game-event path fires when the Wanderer overlaps this trigger zone

    /**
     * Arbitrary key-value parameters associated with this trigger. Content varies by
     * type: a {@code "CUTSCENE"} trigger stores the {@link CutsceneID} constant under
     * key {@code "id"}; a {@code "WALL_BREAK_HINT"} trigger may store the target tile
     * coordinate. Read by the dispatch handler after {@link #fire()} so it can
     * configure the action (e.g. which cutscene to start, which hint to show).
     */
    private Map<String, Object> params; // Type-specific configuration map; never null (guaranteed by constructor null-guard)

    /**
     * Whether this trigger has already fired. Set to {@code true} by {@link #fire()}
     * and never reset. The game loop checks this flag before dispatching to ensure
     * each trigger activates at most once per level visit.
     */
    private boolean fired; // One-shot guard: true after fire() is called; prevents the same trigger from activating twice

    /**
     * Optional PNG path that gives this trigger a visible glyph when set.
     * {@code null} by default — preserves the original "invisible zone"
     * behaviour. Level generators set this through the
     * {@code trigger(..., spritePath)} helper in {@link LevelGenerator}.
     */
    private String spritePath; // SpriteOverridable backing field; null → invisible (the legacy default)

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code Trigger} at the given world position with the specified
     * type and parameters. The trigger zone is fixed at 32×32 pixels (one standard
     * tile) and starts unfired.
     *
     * <p>Architecture role: Called by {@link LevelLoader} when it encounters a
     * {@code "trigger"} entity in the level JSON. The type string and params map come
     * directly from the JSON fields. The resulting Trigger is added to
     * {@link GameStarter#elements} immediately and is checked for overlap with the
     * Wanderer every tick until it fires.</p>
     *
     * @param type   the trigger type identifier; must not be {@code null}; determines
     *               which game-event handler is invoked on activation
     * @param x      the x-coordinate of the trigger's left edge in world space (pixels)
     * @param y      the y-coordinate of the trigger's top edge in world space (pixels)
     * @param params a map of additional parameters configuring the trigger's action;
     *               if {@code null}, an empty {@link HashMap} is used so downstream
     *               code can always call {@link Map#get(Object)} without null-checking
     */
    public Trigger(String type, int x, int y, Map<String, Object> params) { // Four-argument constructor; position + type + params; size is a fixed 32×32 tile
        super(x, y, 32, 32);                                               // Delegate to GameElement: place AABB at (x,y) with standard 32×32 tile size
        this.type = type;                                                   // Store the type tag; used by GameStarter's dispatch logic to identify the event
        this.params = (params != null) ? params : new HashMap<>();          // Use provided map or create an empty one; prevents NullPointerException in dispatch handlers
        this.fired = false;                                                 // Start unfired; will be set to true the first time the Wanderer enters this zone
        this.spritePath = null;                                             // No sprite override by default; render() stays a no-op (invisible) unless explicitly given a path
    }

    // -------------------------------------------------------------------------
    // GameElement overrides
    // -------------------------------------------------------------------------

    /**
     * No-op update; triggers are entirely passive and have no time-driven behaviour.
     * All activation logic is driven externally by {@link GameStarter}'s per-tick
     * overlap check rather than by the trigger itself.
     *
     * <p>Architecture role: Required by the abstract {@link GameElement#update(long)}
     * contract. The game loop calls this on every active element each tick, but for
     * triggers this call produces no state change — the trigger waits silently until
     * {@link GameStarter} detects overlap and calls {@link #fire()} explicitly.</p>
     *
     * @param deltaMs the time elapsed since the last update, in milliseconds; ignored
     */
    @Override
    public void update(long deltaMs) { // Satisfies abstract contract; triggers are event-driven so no per-tick state update is needed
        // Triggers are event-driven, no per-tick logic needed.
        // GameStarter.checkTriggers() is responsible for detecting overlap and calling fire().
    }

    /**
     * Triggers are intentionally invisible and produce no rendered output. This method
     * does nothing because trigger zones exist only in world-space logic, not as visible
     * game objects.
     *
     * <p>Architecture role: Required by the {@link Renderable#render(Graphics2D)}
     * contract inherited through {@link GameElement}. {@link GameCanvas#renderElements}
     * calls this on every active element, but for triggers the call is a safe no-op.
     * During development, a debug mode can add a semi-transparent rectangle draw here
     * to visualise trigger zone placement without affecting release builds.</p>
     *
     * @param g the {@link Graphics2D} context; ignored; must not be {@code null} per
     *          the interface contract even though this implementation does not use it
     */
    @Override
    public void render(Graphics2D g) { // Satisfies abstract render() contract; trigger zones are invisible by default but honour the SpriteOverridable hook
        // Sprite override: if a level generator gave this trigger a visible glyph (non-null spritePath) and the PNG loads, draw it.
        // No procedural fallback exists — triggers are invisible by default, so missing sprites simply preserve the original behaviour.
        SpriteOverridable.tryDrawSprite(g, this, x, y, width, height);
    }

    // -------------------------------------------------------------------------
    // Trigger logic
    // -------------------------------------------------------------------------

    /**
     * Marks this trigger as fired, latching the one-shot state. Once fired, subsequent
     * calls to {@link #isFired()} will always return {@code true}, and the game loop
     * will skip this trigger in future overlap checks.
     *
     * <p>Architecture role: Called by {@link GameStarter#checkTriggers()} (or the
     * equivalent overlap-check method) immediately after it detects that the Wanderer's
     * bounding box intersects this trigger's bounding box. The caller is responsible for
     * reading {@link #getType()} and {@link #getParams()} before or after calling
     * {@code fire()} to dispatch the associated game event (cutscene, hint, etc.).</p>
     *
     * <p>Interaction: After {@code fire()} is called, the trigger remains in
     * {@link GameStarter#elements} in its current (active) state; it is simply bypassed
     * by the overlap check because {@link #isFired()} now returns {@code true}. It is
     * not deactivated via {@link #setActive(boolean)} because the game may need to query
     * its position or params later in the same level session.</p>
     */
    public void fire() {      // Latch the one-shot flag; called by GameStarter when Wanderer enters the trigger zone
        this.fired = true;    // Set fired to true; once set, the game loop will no longer dispatch events for this trigger
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the type identifier of this trigger. Used by {@link GameStarter}'s
     * trigger-dispatch logic to route the event to the correct handler.
     *
     * <p>Interaction: The returned string is compared against constants such as
     * {@code "CUTSCENE"}, {@code "ALTAR"}, or {@code "WALL_BREAK_HINT"} inside
     * the dispatch switch/if-chain in {@link GameStarter}.</p>
     *
     * @return the trigger type string; never {@code null}; set at construction time
     *         and immutable thereafter
     */
    public String getType() { // Read accessor; returns the type tag for dispatch routing in GameStarter
        return type;          // Return the stored type string assigned in the constructor
    }

    /**
     * Returns the parameter map associated with this trigger. The caller can inspect
     * entries to configure the triggered action (e.g. retrieve the {@link CutsceneID}
     * stored under key {@code "id"} for a cutscene trigger).
     *
     * <p>Interaction: The returned map is the same object stored in the field; it is
     * mutable. Callers should treat it as read-only to avoid corrupting trigger state
     * if the trigger's parameters are inspected more than once.</p>
     *
     * @return the mutable parameter map; never {@code null} (guaranteed by the
     *         constructor's null-guard which substitutes an empty {@link HashMap})
     */
    public Map<String, Object> getParams() { // Read accessor; returns the configuration map for the dispatch handler to read
        return params;                        // Return the stored map; never null due to constructor null-guard
    }

    /**
     * Returns whether this trigger has already fired. The game loop in
     * {@link GameStarter} checks this flag before dispatching to ensure each trigger
     * activates at most once per level visit.
     *
     * <p>Interaction: If {@code isFired()} returns {@code true}, {@link GameStarter}
     * skips the event-dispatch step but may still check the trigger's position or
     * params for informational purposes (e.g. progress indicators on a map screen).</p>
     *
     * @return {@code true} if {@link #fire()} has been called; {@code false} if the
     *         trigger is still waiting for the Wanderer to enter its zone
     */
    public boolean isFired() { // Read accessor for the one-shot guard; true once fire() has been called
        return fired;           // Return the stored flag; set permanently to true by fire()
    }

    // -------------------------------------------------------------------------
    // SpriteOverridable
    // -------------------------------------------------------------------------

    /**
     * Sets the optional PNG path that gives this trigger a visible glyph.
     * Pass {@code null} to clear the override and return to the default
     * invisible behaviour; pass a path like
     * {@code "resources/sprites/triggers/altar_hint.png"} to draw a bitmap
     * inside the trigger's 32×32 zone.
     *
     * @param path the PNG path, or {@code null} to remain invisible
     */
    @Override
    public void setSpritePath(String path) { // SpriteOverridable contract
        this.spritePath = path;               // Stored as-is; null is the default
    }

    /**
     * Returns the configured sprite path, or {@code null} if no override is set
     * (the default for trigger zones).
     *
     * @return the sprite path or {@code null}
     */
    @Override
    public String getSpritePath() { // SpriteOverridable contract
        return spritePath;          // Null when no override is configured
    }
}
