/**
 * Abstract base class for programmatic level generators. Subclasses override
 * {@link #build()} and use the protected helper methods to place platforms,
 * fragments, altars, portals, crawlers, and triggers in world-space pixel
 * coordinates.
 *
 * <p>This class replaces the JSON-driven {@link LevelLoader#loadLevel(int)}
 * pipeline. Where the JSON path was data-defined and parsed at runtime, the
 * programmatic path is code-defined and edited inline — change a coordinate,
 * recompile, restart, see the change. There is no schema, no parser, no
 * validation step; the Java compiler verifies every entity placement.</p>
 *
 * <p><b>Coordinate system</b>: origin is the top-left of the level. X
 * increases to the right; Y increases <i>downward</i> (standard 2D screen
 * coordinates, not Cartesian). All values are world-space pixels; the standard
 * tile is 32×32 px so a 32-tile floor row spans 32 × 32 = 1024 px, which fills
 * the standard canvas width.</p>
 *
 * <p><b>Sprite overrides</b>: every helper that takes a {@code spritePath}
 * argument honors it via {@link SpriteOverridable#tryDrawSprite}. Pass
 * {@code null} for the procedural Graphics2D fallback; pass a path like
 * {@code "resources/sprites/fragments/dodge.png"} to override the look. If the
 * file is missing, {@link SpriteLoader#tryLoad(String)} returns {@code null}
 * and the entity falls back to its procedural draw — no magenta placeholder.</p>
 *
 * <p>Subclasses produce a {@link LevelRegistry.LoadResult} from {@link #build()};
 * the result is consumed by {@link LevelRegistry#load(int, long)} which is in
 * turn invoked by {@link GameStarter#loadLevel(int)}.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-29
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 1c "Abstract Classes" - canonical abstract-base-with-helpers
//                                pattern: build() is abstract; protected
//                                helper methods (platform, brickRow,
//                                fragment, altar, portal, spike,
//                                trigger, finish) provide reusable
//                                building blocks that every level
//                                subclass calls. Exactly the
//                                "concrete-helpers-on-abstract-base"
//                                idiom shown in the abstract-classes
//                                module.
// Module 1a "Modifiers"        - protected helpers visible only to
//                                subclasses; private spawn fields with
//                                public accessors; default-method
//                                visibility for pure helpers.
// =========================================================================

import java.util.ArrayList;        // Backing list for accumulated elements
import java.util.HashMap;           // Default empty params map for triggers
import java.util.List;              // Interface type for the elements list
import java.util.Map;               // Interface type for trigger params

public abstract class LevelGenerator { // Abstract — every level subclass overrides build()

    // -------------------------------------------------------------------------
    // Protected state — accumulated by helper methods
    // -------------------------------------------------------------------------

    /**
     * The growing list of entities for this level. Subclasses do not write to
     * this directly; helper methods append on their behalf so each placement is
     * a single line in {@link #build()}.
     */
    protected final List<GameElement> elements = new ArrayList<>(); // ArrayList is fine: only read after build() finishes

    /**
     * The mutable {@link LevelState} for this level, populated by
     * {@link #setPhase(LevelState.GamePhase)}, {@link #setTimeLimit(int)},
     * {@link #setBlockBudget(int)}, and {@link #setSpawn(int, int)}.
     * Defaults match {@link LevelState}'s no-arg constructor.
     */
    protected final LevelState state = new LevelState(); // Pre-configured; helpers overwrite fields

    /** Tracks the most recently added {@link Altar} so subclasses can configure it post-hoc. */
    private Altar lastAltar; // Updated by {@link #altar(...)}; null until at least one altar is placed

    /** Tracks the most recently added {@link LoreFragment} so subclasses can configure it post-hoc. */
    private LoreFragment lastFragment; // Updated by {@link #fragment(...)}; null until at least one is placed

    // -------------------------------------------------------------------------
    // Required subclass entry point
    // -------------------------------------------------------------------------

    /**
     * Builds and returns the level. Subclasses populate {@link #state} and
     * {@link #elements} via the helper methods, then return {@link #finish()}.
     *
     * @return a {@link LevelRegistry.LoadResult} bundling the elements list and
     *         the configured state; never {@code null}
     */
    public abstract LevelRegistry.LoadResult build(); // Subclass entry point

    // -------------------------------------------------------------------------
    // State helpers
    // -------------------------------------------------------------------------

    /**
     * Sets the high-level game phase for this level. Drives renderer selection
     * and input routing in {@link GameCanvas} and {@link InputRouter}.
     *
     * @param phase the {@link LevelState.GamePhase} (typically {@code ACT1},
     *              {@code ACT2}, or {@code ACT3})
     */
    protected void setPhase(LevelState.GamePhase phase) { // Direct write to state.currentPhase
        state.currentPhase = phase;                        // Caller is responsible for consistency with the level number
    }

    /**
     * Sets the level countdown timer in seconds.
     *
     * @param seconds the per-level time budget; converted to milliseconds for
     *                {@link LevelState#timeRemainingMs}
     */
    protected void setTimeLimit(int seconds) { // Helper for the common per-level field
        state.timeRemainingMs = seconds * 1000L; // Convert to ms for the countdown loop
    }

    /**
     * Sets the Apprentice's per-level block budget.
     *
     * @param blocks the number of placement tokens; consumed by
     *               {@link GameSession#placeBlock} each time the Apprentice
     *               drops a block
     */
    protected void setBlockBudget(int blocks) { // Direct write to state.blockBudget
        state.blockBudget = blocks;              // The HUD reads this each frame to display the remaining budget
    }

    /**
     * Sets the Wanderer's spawn position for this level. Stored as a transient
     * field on this generator; {@link GameStarter#loadLevel(int)} reads it
     * after {@link #build()} returns.
     *
     * <p>NOTE: {@link LevelState} does not currently carry a spawn-point field;
     * the Wanderer is repositioned by hardcoded constants in
     * {@link GameStarter#loadLevel(int)} (currently 60, 652). To support
     * per-level spawns programmatically, expose {@link #getSpawnX()} and
     * {@link #getSpawnY()} for {@link LevelRegistry} to surface to the caller.
     * Until {@code LevelRegistry.LoadResult} grows a spawn field, this helper
     * stores the spawn locally and the caller can read it via the accessors.</p>
     *
     * @param x the world-space spawn x in pixels
     * @param y the world-space spawn y in pixels
     */
    protected void setSpawn(int x, int y) { // Setter for the per-level spawn override
        this.spawnX = x;                     // Stored locally; LevelRegistry surfaces this if needed
        this.spawnY = y;                     // Caller queries via getSpawnX()/getSpawnY()
    }

    /** World-space spawn x; defaults to {@code -1} meaning "use caller's default". */
    private int spawnX = -1;

    /** World-space spawn y; defaults to {@code -1} meaning "use caller's default". */
    private int spawnY = -1;

    /** @return the configured spawn x, or {@code -1} if {@link #setSpawn(int, int)} was not called */
    public int getSpawnX() { return spawnX; }

    /** @return the configured spawn y, or {@code -1} if {@link #setSpawn(int, int)} was not called */
    public int getSpawnY() { return spawnY; }

    // -------------------------------------------------------------------------
    // Platform helpers — the dominant placement type per level
    // -------------------------------------------------------------------------

    /**
     * Places a single platform with default dimensions for its type.
     *
     * @param type one of {@link Platform.PlatformType} (BRICK, SLIDE, SPRING,
     *             WALL, CRUMBLE, INVISIBLE, MIMIC)
     * @param x    world-space left-edge x in pixels (origin top-left, y down)
     * @param y    world-space top-edge y in pixels
     */
    protected void platform(Platform.PlatformType type, int x, int y) { // Default-size variant
        elements.add(new Platform(type, x, y));                          // Platform's two-arg constructor uses type defaults for w/h
    }

    /**
     * Places a single platform with explicit width and height.
     *
     * @param type one of {@link Platform.PlatformType}
     * @param x    world-space left-edge x in pixels
     * @param y    world-space top-edge y in pixels
     * @param w    width in pixels
     * @param h    height in pixels
     */
    protected void platform(Platform.PlatformType type, int x, int y, int w, int h) { // Explicit-size variant
        elements.add(new Platform(type, x, y, w, h));                                   // Platform's four-arg constructor
    }

    /**
     * Places {@code count} BRICK platforms in a row at y, starting at startX,
     * spaced 32 pixels apart (one tile). Convenience for the dominant pattern
     * in old level layouts (e.g. a 32-tile ground floor).
     *
     * @param startX world-space left-edge x of the first platform in pixels
     * @param y      world-space top-edge y in pixels
     * @param count  the number of BRICK platforms to place
     */
    protected void brickRow(int startX, int y, int count) { // Default 32-px gap (tile size)
        brickRow(startX, y, count, 32);                      // Delegate to the explicit-gap variant
    }

    /**
     * Places {@code count} BRICK platforms in a row at y, starting at startX,
     * with the given pixel gap between successive platforms.
     *
     * @param startX world-space left-edge x of the first platform in pixels
     * @param y      world-space top-edge y in pixels
     * @param count  the number of BRICK platforms to place
     * @param gap    pixel spacing between successive platform left edges
     */
    protected void brickRow(int startX, int y, int count, int gap) { // Custom-gap variant
        for (int i = 0; i < count; i++) {                              // One platform per index 0..count-1
            elements.add(new Platform(Platform.PlatformType.BRICK,    // BRICK is the standard solid surface
                                     startX + i * gap, y));            // Stride by gap pixels per step
        }
    }

    // -------------------------------------------------------------------------
    // Fragment helper
    // -------------------------------------------------------------------------

    /**
     * Places a {@link LoreFragment} at world coordinates with a unique ID,
     * an ability unlock (or {@link LoreFragment.AbilityUnlock#NONE} for
     * narrative-only), the body text shown in the lore log, and an optional
     * sprite path.
     *
     * @param id         unique fragment identifier (e.g. {@code "A1-DODGE"});
     *                   referenced by the network protocol on collection
     * @param x          world-space left-edge x in pixels
     * @param y          world-space top-edge y in pixels
     * @param unlock     the ability granted on collection; use
     *                   {@link LoreFragment.AbilityUnlock#NONE} for narrative-only
     * @param body       narrative text shown in the lore log; may include
     *                   line breaks
     * @param spritePath optional PNG path; {@code null} for the procedural
     *                   shard render
     */
    protected void fragment(String id, int x, int y, // Six-arg single-call placement
                            LoreFragment.AbilityUnlock unlock,
                            String body, String spritePath) {
        LoreFragment frag = new LoreFragment(id, body, unlock, x, y); // Build the entity at the given position
        if (spritePath != null) frag.setSpritePath(spritePath);        // Honour the optional sprite override (P10.2 wires this)
        elements.add(frag);                                            // Add to the level entity list
        this.lastFragment = frag;                                      // Track for post-hoc configuration via lastFragment()
    }

    /**
     * Returns the most recently added {@link LoreFragment} so subclasses can
     * configure rare options (e.g. {@link LoreFragment#setGated(boolean)}) that
     * are not in the main helper signature.
     *
     * @return the last fragment, or {@code null} if none has been added
     */
    protected LoreFragment lastFragment() { return lastFragment; } // Post-hoc accessor

    // -------------------------------------------------------------------------
    // Altar helper
    // -------------------------------------------------------------------------

    /**
     * Places an {@link Altar} at world coordinates with a binary choice and
     * an optional sprite path. Adds both the visible Altar entity (for
     * rendering and overlap detection) and a matching {@link Trigger}
     * {@code "ALTAR"} entry (for legacy P8.6 dispatch compatibility) so
     * {@link GameStarter#checkAltarTrigger()} fires on either path.
     *
     * @param id         unique per-level altar identifier; matches
     *                   {@code altarId} in {@link Protocol#ALTAR_CHOICE}
     * @param x          world-space left-edge x in pixels
     * @param y          world-space top-edge y in pixels
     * @param opt1       first choice label (e.g. {@code "POWER_SURGE"})
     * @param opt2       second choice label (e.g. {@code "SIGHT_RESTRICTION"})
     * @param spritePath optional PNG path; {@code null} for the procedural
     *                   pedestal render
     */
    protected void altar(int id, int x, int y, String opt1, String opt2, String spritePath) {
        Altar a = new Altar(id, x, y, opt1, opt2); // Visible interactable
        if (spritePath != null) a.setSpritePath(spritePath); // Honour the optional sprite override
        elements.add(a);                            // Add the visible entity
        this.lastAltar = a;                         // Track for post-hoc concealment configuration

        // Legacy dispatch: emit a matching invisible Trigger("ALTAR", ...) so
        // GameStarter.checkAltarTrigger() picks up the overlap without
        // requiring P10.3's altar-side dispatch to land first. When P10.3
        // adds Altar-direct overlap detection, this trigger becomes harmless
        // (the same altarActive latch prevents double-firing).
        Map<String, Object> params = new HashMap<>();
        params.put("altarId", id);                                    // P8.6's expected key
        params.put("option1", opt1);                                  // Surfaces both options to the overlay
        params.put("option2", opt2);
        elements.add(new Trigger("ALTAR", x, y, params));             // Invisible companion zone
    }

    /**
     * Returns the most recently added {@link Altar} so subclasses can attach a
     * {@link AltarConcealment} or apply other post-hoc configuration.
     *
     * @return the last altar, or {@code null} if none has been added
     */
    protected Altar lastAltar() { return lastAltar; } // Post-hoc accessor for concealment wiring

    // -------------------------------------------------------------------------
    // Portal helper
    // -------------------------------------------------------------------------

    /**
     * Places a Portal at the given world position with default 48×80 dimensions.
     *
     * @param x world-space left-edge x in pixels
     * @param y world-space top-edge y in pixels
     */
    protected void portal(int x, int y) { // Default-size, no-sprite variant
        elements.add(new Portal(x, y));    // Default Portal constructor uses fixed 48×80
    }

    /**
     * Places a Portal with explicit dimensions and an optional sprite path.
     * Note: Portal's current constructor is fixed-size; the {@code w} and
     * {@code h} arguments are accepted for forward compatibility with a
     * future custom-size constructor. They are currently ignored at the
     * collision level but are propagated to the sprite draw.
     *
     * @param x          world-space left-edge x in pixels
     * @param y          world-space top-edge y in pixels
     * @param w          custom width in pixels (currently ignored by Portal collision)
     * @param h          custom height in pixels (currently ignored by Portal collision)
     * @param spritePath optional PNG path; {@code null} for the procedural draw
     */
    protected void portal(int x, int y, int w, int h, String spritePath) { // Forward-compatible variant
        Portal p = new Portal(x, y);                  // Use the existing constructor (fixed size)
        if (spritePath != null) p.setSpritePath(spritePath); // Honour the optional sprite override
        elements.add(p);                              // w/h reserved for future Portal-side support
    }

    // -------------------------------------------------------------------------
    // Hazard helpers (P9.3' — replaces the deleted crawler helpers)
    // -------------------------------------------------------------------------

    /**
     * Places a {@link CorruptedSpike} at the given world position with default
     * 32×16 dimensions. Deals 1 contact damage with a per-instance 800 ms
     * cooldown so a moving Wanderer is not drained instantly on overlap.
     *
     * @param x world-space left-edge x in pixels
     * @param y world-space top-edge y in pixels
     */
    protected void spike(int x, int y) { // Default-size, no-sprite variant
        elements.add(new CorruptedSpike(x, y));
    }

    /**
     * Places a {@link CorruptedSpike} with explicit dimensions and an optional
     * sprite override.
     *
     * @param x          world-space left-edge x in pixels
     * @param y          world-space top-edge y in pixels
     * @param w          width in pixels
     * @param h          height in pixels
     * @param spritePath optional PNG path; {@code null} for the procedural draw
     */
    protected void spike(int x, int y, int w, int h, String spritePath) { // Custom-size variant
        CorruptedSpike s = new CorruptedSpike(x, y, w, h);
        if (spritePath != null) s.setSpritePath(spritePath);
        elements.add(s);
    }

    /**
     * Places a {@link CorruptedWall} hazard. The wall sits in the background
     * until the Wanderer enters its trigger zone, then shakes for 1 second
     * (dodge window) before falling and dealing heavy contact damage.
     *
     * @param x          world-space left-edge x of the visible wall in pixels
     * @param y          world-space top-edge y of the visible wall in pixels
     * @param w          visible wall width in pixels
     * @param h          visible wall height in pixels
     * @param triggerW   trigger zone width in pixels (typically wider than {@code w})
     * @param triggerH   trigger zone height in pixels (typically taller than {@code h})
     * @param spritePath optional PNG path; {@code null} for the procedural draw
     */
    protected void corruptedWall(int x, int y, int w, int h,
                                 int triggerW, int triggerH, String spritePath) {
        CorruptedWall cw = new CorruptedWall(x, y, w, h, triggerW, triggerH);
        if (spritePath != null) cw.setSpritePath(spritePath);
        elements.add(cw);
    }

    /**
     * Places a {@link PhantomBlock} (light-memory block). Solid + visible
     * while lit; fades and becomes non-solid after light leaves it. Persistence
     * scales with how long and how brightly it was previously lit.
     *
     * @param x          world-space left-edge x in pixels
     * @param y          world-space top-edge y in pixels
     * @param w          width in pixels
     * @param h          height in pixels
     * @param spritePath optional PNG path; {@code null} for the procedural draw
     */
    protected void phantomBlock(int x, int y, int w, int h, String spritePath) {
        PhantomBlock pb = new PhantomBlock(x, y, w, h);
        if (spritePath != null) pb.setSpritePath(spritePath);
        elements.add(pb);
    }

    /**
     * Places a {@link PhantomBlock} with the standard 32×32 tile dimensions.
     *
     * @param x world-space left-edge x in pixels
     * @param y world-space top-edge y in pixels
     */
    protected void phantomBlock(int x, int y) { // Default-size variant
        elements.add(new PhantomBlock(x, y, 32, 32));
    }

    /**
     * Places a {@link LightLockedMover} — a moving platform whose motion is
     * gated by light. {@code FREEZE_ON_LIGHT} (default behaviour) keeps the
     * platform moving until illuminated, then freezes it; {@code MOVE_ON_LIGHT}
     * inverts the rule.
     *
     * @param x          world-space left-edge x at the start of the motion path
     * @param y          world-space top-edge y at the start of the motion path
     * @param pattern    the motion pattern (LINEAR_X / LINEAR_Y / CIRCULAR)
     * @param amplitude  amplitude of the motion in pixels (radius for CIRCULAR)
     * @param periodMs   one full cycle in milliseconds
     * @param behaviour  light gating mode (FREEZE_ON_LIGHT or MOVE_ON_LIGHT)
     * @param spritePath optional PNG path; {@code null} for the procedural draw
     */
    protected void lightMover(int x, int y, LightLockedMover.MovePattern pattern,
                              int amplitude, int periodMs,
                              LightLockedMover.LightBehavior behaviour,
                              String spritePath) {
        LightLockedMover m = new LightLockedMover(x, y, pattern, amplitude, periodMs, behaviour);
        if (spritePath != null) m.setSpritePath(spritePath);
        elements.add(m);
    }

    /**
     * Places a {@link LightLockedMover} with default amplitude (96 px), period
     * (3000 ms), and {@code FREEZE_ON_LIGHT} behaviour.
     *
     * @param x       world-space left-edge x at the start of the motion path
     * @param y       world-space top-edge y at the start of the motion path
     * @param pattern the motion pattern (LINEAR_X / LINEAR_Y / CIRCULAR)
     */
    protected void lightMover(int x, int y, LightLockedMover.MovePattern pattern) {
        elements.add(new LightLockedMover(x, y, pattern, 96, 3000,
                                          LightLockedMover.LightBehavior.FREEZE_ON_LIGHT));
    }

    // -------------------------------------------------------------------------
    // Trigger helper — escape hatch for non-altar dispatch zones
    // -------------------------------------------------------------------------

    /**
     * Places an invisible {@link Trigger} dispatch zone. Used for
     * {@code "CUTSCENE"}, {@code "WALL_BREAK_HINT"}, and similar one-shot
     * events. For altar zones, prefer {@link #altar(int, int, int, String, String, String)}
     * which sets up both the visible entity and the trigger at once.
     *
     * @param type       trigger type string; matched by
     *                   {@link GameStarter}'s dispatch logic
     * @param x          world-space left-edge x in pixels
     * @param y          world-space top-edge y in pixels
     * @param params     type-specific parameters; pass {@code null} for an
     *                   empty map
     * @param spritePath optional PNG path to give this trigger a visible
     *                   glyph; {@code null} keeps it invisible (the default)
     */
    protected void trigger(String type, int x, int y, Map<String, Object> params, String spritePath) {
        Trigger t = new Trigger(type, x, y, params != null ? params : new HashMap<>()); // Construct with non-null params
        if (spritePath != null) t.setSpritePath(spritePath);                              // Honour the optional sprite override
        elements.add(t);                                                                  // Add to the level entity list
    }

    // -------------------------------------------------------------------------
    // Finish — terminal helper that subclasses return
    // -------------------------------------------------------------------------

    /**
     * Bundles the accumulated elements and state into a {@link LevelRegistry.LoadResult}.
     * Subclasses end {@link #build()} with {@code return finish();}.
     *
     * @return a fresh {@link LevelRegistry.LoadResult} carrying the elements list
     *         and configured state; never {@code null}
     */
    protected LevelRegistry.LoadResult finish() { // Terminal call for subclasses
        return new LevelRegistry.LoadResult(elements, state); // Reuse the existing bundle type
    }
}
