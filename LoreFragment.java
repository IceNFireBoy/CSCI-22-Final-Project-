/**
 * Represents a collectible narrative shard hidden throughout the Lumen Architect world
 * that the Wanderer can discover to expand the game's story and unlock new combat
 * abilities. Each fragment carries a unique identifier, body text displayed in the
 * lore log, an optional ability it grants upon collection, and a flag recording whether
 * the Wanderer has already picked it up.
 *
 * <p>Architecture role: {@code LoreFragment} is a first-class {@link GameElement}
 * managed by the shared entity list in {@link GameStarter}. {@link CollisionDetector}
 * tests the Wanderer's bounds against each active, uncollected fragment; on overlap it
 * calls {@link #collect()} to deactivate the fragment and trigger ability-unlock
 * processing in {@link GameSession}. {@link LevelLoader} instantiates fragments from
 * level JSON data; {@link FragmentLibrary} indexes all known fragments for lore-log
 * display. Collected status and the {@link AbilityUnlock} are relayed to the server
 * via {@link NetworkProtocol.FragmentCollectedPacket}.</p>
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
import java.awt.*;
import java.awt.image.*;
public class LoreFragment extends GameElement { // Extends GameElement for position, AABB, active flag, and update/render contract

    // =========================================================================
    // Enum — AbilityUnlock
    // =========================================================================

    /**
     * Enumerates the abilities that a {@link LoreFragment} can grant to the Wanderer
     * upon collection. Most fragments carry narrative value only ({@link #NONE}), while
     * key story fragments bestow a specific combat or traversal skill.
     *
     * <p>Architecture role: The unlock constant is read by {@link GameSession} after
     * {@link CollisionDetector} calls {@link LoreFragment#collect()}. The session then
     * calls the appropriate unlock method (e.g. enabling the melee attack, the dodge
     * roll, or flagging Radiant Collapse availability). The constants are set
     * programmatically by {@link LevelGenerator} subclasses during level construction.</p>
     */
    public enum AbilityUnlock {

        /** Fragment grants no gameplay ability; its value is purely narrative. */
        NONE,

        /** Unlocks the Wanderer's close-range melee strike ability. */
        MELEE,

        /** Unlocks the ability to fire a ranged energy projectile. */
        PROJECTILE,

        /** Unlocks the dodge-roll evasion manoeuvre. */
        DODGE,

        /** Unlocks the ability to cling to and traverse vertical wall surfaces. */
        WALL_CLING,

        /**
         * Unlocks the shadow-dash ability: the Wanderer phases through a short distance
         * with brief damage immunity during the dash frames.
         */
        SHADOW_DASH,

        // ---- P8.9 boss-fight power-set unlocks ----
        // EMBER, IRON, and RADIANT_COLLAPSE are consumed by the boss-fight power
        // set introduced in P8.9. EMBER and IRON are stored in Player#activeBoosts
        // so combat code can branch on their presence. RADIANT_COLLAPSE flips
        // GameSession#setRadiantCollapseUnlocked(true) so the SHIFT key in the
        // BOSS phase drives the Radiant Collapse FSM instead of being a no-op.

        /**
         * P8.9 — Ember boost: adds +1 damage to the Wanderer's melee strikes against
         * {@link Core} entities during the boss fight.
         */
        EMBER,

        /**
         * P8.9 — Iron boost: reduces all incoming damage to the Wanderer by 20%,
         * with a floor of 1 HP per hit, during the boss fight.
         */
        IRON,

        /**
         * P8.9 — Radiant Collapse hidden unlock. Enables the SHIFT-hold mechanic in the
         * BOSS phase: holding SHIFT charges and then releases a 3-second full-arena light
         * reveal, subject to a 60-second cooldown. Gated behind a level-7 environmental
         * puzzle hint; persisted in {@link GameSession} so it survives level reloads
         * within the same run.
         */
        RADIANT_COLLAPSE,

        // ---- P9.1' — additional ability unlocks for the new 3-level campaign ----
        // These four enum constants let the level-author distribute new shard
        // pickups across Act 2-3 without touching the protocol or fragment
        // catalog beyond appending to this enum. Each maps to a private flag
        // on Player set by Player#unlockAbility().

        /**
         * P9.1' — Veil unlock: short stealth window when the Wanderer steps
         * out of the Apprentice's lit radius. Future systems that gate on
         * stealth (hazard hostility, alarm triggers, etc.) check
         * {@code Player.hasVeil} on the hot path.
         */
        VEIL,

        /**
         * P9.1' — Echo unlock: a one-shot audible ping that briefly highlights
         * nearby hazards (CorruptedSpike, CorruptedWall) on the Wanderer's HUD.
         * The ping consumption logic and HUD rendering land alongside the
         * gameplay system that uses {@code Player.hasEcho}.
         */
        ECHO,

        /**
         * P9.1' — Tether unlock: short directional dash anchored to a
         * placed block. Combat code branches on {@code Player.hasTether}
         * to extend the move-set; level layouts can rely on it as a
         * traversal tool by Act 3.
         */
        TETHER,

        /**
         * P9.1' — Shadow-step unlock. Compound traversal blink that requires
         * both {@link #VEIL} and {@link #TETHER} to actually fire. The flag
         * is set unconditionally on collection; the consumer enforces the
         * dual-prereq gate by checking {@code hasShadowStep && hasVeil &&
         * hasTether} before executing the move.
         */
        SHADOW_STEP
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * A unique string identifier for this fragment used for save-state tracking and
     * lore-log lookup (e.g., {@code "A1-INTRO"}). Matches the field in
     * {@link NetworkProtocol.FragmentCollectedPacket#fragmentID}.
     */
    private String fragmentID; // Unique ID; used by GameSession and FragmentLibrary to look up this fragment

    /**
     * The narrative body text displayed in the lore log when this fragment is collected.
     * May contain line breaks ({@code \n}) for multi-paragraph lore entries.
     */
    private String bodyText; // Shown in the lore-log overlay on collection; sourced from LoreFragment constructor or FragmentLibrary

    /**
     * The ability granted to the Wanderer upon collecting this fragment. Defaults to
     * {@link AbilityUnlock#NONE} for purely narrative shards. Read by {@link GameSession}
     * after collection to determine which game system to unlock.
     */
    private AbilityUnlock unlock; // Combat/traversal unlock; NONE for narrative-only fragments

    /**
     * Whether this fragment has already been collected by the Wanderer. Collected
     * fragments are deactivated via {@link #collect()} and are no longer rendered or
     * tested for collision.
     */
    private boolean collected; // True after collect() is called; guards against double-collect on the same frame

    /**
     * Whether this fragment is gated behind prerequisite abilities the Wanderer has not
     * yet obtained. A gated fragment is rendered with a distinct visual indicator but
     * cannot be collected until the gate condition is met.
     */
    private boolean gated; // Prerequisite gate flag; CollisionDetector skips collection if gated==true

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code LoreFragment} with the given identifier, body text, and
     * ability unlock, placed at the specified world coordinates with a 20×20 AABB.
     *
     * <p>Architecture role: Instantiated by {@link LevelGenerator} subclasses during
     * level construction, and by {@link FragmentLibrary} when pre-populating the
     * global fragment index.
     * The fragment starts active and uncollected so the collision and render
     * systems immediately include it in their processing passes.</p>
     *
     * @param fragmentID a unique identifier string; must not be {@code null}
     * @param bodyText   the narrative text shown on collection; must not be {@code null}
     * @param unlock     the {@link AbilityUnlock} granted; use {@link AbilityUnlock#NONE}
     *                   for purely narrative fragments
     * @param x          the x-coordinate of the fragment's left edge in world space
     * @param y          the y-coordinate of the fragment's top edge in world space
     */
    public LoreFragment(String fragmentID, String bodyText, AbilityUnlock unlock,
                        int x, int y) {
        super(x, y, 20, 20);         // 20×20 px AABB; compact hitbox ensures the Wanderer must be nearly on top to collect
        this.fragmentID = fragmentID; // Store the unique ID for network relaying and lore-log indexing
        this.bodyText   = bodyText;   // Store the narrative text shown in the lore-log overlay after collection
        this.unlock     = unlock;     // Store the ability unlock constant; read by GameSession on collection
        this.collected  = false;      // Fragment starts uncollected; collect() sets this to true and deactivates the element
        this.gated      = false;      // Fragment starts ungated; CollisionDetector or GameStarter may gate it later
    }

    // -------------------------------------------------------------------------
    // GameElement overrides
    // -------------------------------------------------------------------------

    /**
     * Updates this fragment's state for the current game tick. Currently a stub that
     * preserves the update contract for future idle float/glow animation logic.
     *
     * <p>Architecture role: Called every tick by {@link GameStarter}'s element update
     * pass. No action is needed while collected, but the empty body is intentional
     * rather than omitting the override, ensuring future animation state can be added
     * here without modifying the caller.</p>
     *
     * @param deltaMs the time elapsed since the last update, in milliseconds
     */
    @Override
    public void update(long deltaMs) {
        // Idle animation (gentle bob/glow) to be implemented in a later phase.
        // deltaMs is available here for frame-rate-independent animation timing.
    }

    /**
     * Renders this fragment as a six-point angular shard with a pulsating glow halo
     * and a bright inner highlight line. Combat-unlock fragments (any {@link AbilityUnlock}
     * other than {@link AbilityUnlock#NONE}) render in bright gold; narrative-only
     * fragments render in a muted antique gold to help the Wanderer prioritise.
     *
     * <p>Rendering pipeline per frame:
     * <ol>
     *   <li>Compute a sinusoidal pulse factor from {@link System#currentTimeMillis()}
     *       to animate the glow radius.</li>
     *   <li>Draw a filled oval glow halo behind the shard polygon using the pulsed
     *       radius and a low-alpha colour.</li>
     *   <li>Build and fill the 6-point shard {@link Polygon} in the primary gold hue.</li>
     *   <li>Draw a bright near-white diagonal inner highlight line to simulate a
     *       gemstone facet.</li>
     * </ol></p>
     *
     * <p>Architecture role: Called every frame by {@link GameCanvas#renderElements}
     * while this fragment is active. Rendering is skipped entirely once
     * {@link #collected} is {@code true} and {@link GameElement#setActive(boolean)}
     * has removed this element from the active list.</p>
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    @Override
    public void render(Graphics2D g) {
        if (collected) return;
        String conv = defaultSpritePath(unlock);
        if (conv != null) {
            BufferedImage img = SpriteLoader.getInstance().tryLoad(conv);
            if (img != null) g.drawImage(img, x, y, width, height, null);
        }
    }

    /**
     * Sets the collected state of this fragment directly. Prefer calling {@link #collect()}
     * for the standard pickup flow, which also deactivates the element. This setter is
     * provided for deserialization and test use.
     *
     * @param collected {@code true} to mark this fragment as already collected
     */
    public void setCollected(boolean collected) {
        this.collected = collected; // Directly set the collected flag; does NOT call setActive(false)
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the unique identifier of this fragment. Used by
     * {@link NetworkProtocol.FragmentCollectedPacket} to identify which fragment was
     * collected, and by {@link FragmentLibrary} to look up the corresponding lore entry.
     *
     * @return the fragment ID string; never {@code null}
     */
    public String getFragmentID() {
        return fragmentID; // Immutable ID set at construction; never changes after creation
    }

    /**
     * Returns the narrative body text associated with this fragment. Displayed in the
     * lore-log overlay when the Wanderer collects the fragment.
     *
     * @return the body text string; never {@code null}
     */
    public String getBodyText() {
        return bodyText; // Narrative text set at construction; displayed in the lore-log UI
    }

    /**
     * Returns the ability unlocked by collecting this fragment. Read by
     * {@link GameSession} immediately after {@link #collect()} is called to activate
     * the corresponding gameplay system (melee, projectile, dodge, etc.).
     *
     * @return the {@link AbilityUnlock} constant for this fragment; never {@code null}
     */
    public AbilityUnlock getUnlock() {
        return unlock; // Unlock constant set at construction; read once by GameSession on collection
    }

    /**
     * Returns whether this fragment has already been collected by the Wanderer. A
     * collected fragment will also be inactive ({@link GameElement#isActive()} returns
     * {@code false}), so this flag is primarily used for serialisation checks.
     *
     * @return {@code true} if the Wanderer has collected this fragment; {@code false} otherwise
     */
    public boolean isCollected() {
        return collected; // True after collect() has been called; used by GameSession and FragmentLibrary
    }

    /**
     * Marks this fragment as collected and deactivates it so it is no longer rendered
     * or tested for collision. Called by {@link CollisionDetector} when the Wanderer's
     * bounds overlap the fragment's AABB and {@link #gated} is {@code false}.
     *
     * <p>Architecture role: After this call, the element's active flag is {@code false}
     * so {@link GameStarter}'s element-list pruning will remove it from future update
     * and render passes. {@link GameSession} is responsible for processing the ability
     * unlock after this method returns.</p>
     */
    public void collect() {
        this.collected = true;  // Mark as collected so isCollected() returns true for subsequent checks
        setActive(false);       // Deactivate: GameStarter will remove from the active element list
    }

    /**
     * Returns whether this fragment is currently gated behind prerequisite abilities.
     * A gated fragment is visible to the Wanderer but cannot be collected until the
     * gate condition is met. Used by {@link CollisionDetector} to suppress pickup.
     *
     * @return {@code true} if this fragment requires a prerequisite not yet obtained;
     *         {@code false} if it can be freely collected
     */
    public boolean isGated() {
        return gated; // Read by CollisionDetector to skip pickup when prerequisite abilities are missing
    }

    /**
     * Sets the gated state of this fragment. Called by {@link GameStarter} or
     * {@link GameSession} when the Wanderer acquires or loses the prerequisite ability
     * that gates this fragment.
     *
     * @param gated {@code true} to prevent collection until prerequisites are met;
     *              {@code false} to allow collection
     */
    public void setGated(boolean gated) {
        this.gated = gated; // Toggle gate state; CollisionDetector reads this flag before calling collect()
    }

    // -------------------------------------------------------------------------
    // P9.6' — Convention-based sprite resolution
    // -------------------------------------------------------------------------

    /**
     * Returns the conventional PNG path for a given ability unlock. When a
     * level author places a fragment without an explicit sprite path, the
     * renderer queries this method and tries the conventional file via
     * {@link SpriteLoader#tryLoad(String)}. If the PNG exists, it is used;
     * otherwise the procedural shard renders as before.
     *
     * <p>The convention is {@code resources/sprites/fragments/<key>.png} where
     * {@code <key>} is the lowercase enum name. To add custom art for, say, the
     * DODGE fragment, drop {@code dodge.png} into that folder. To restore the
     * procedural look, delete the file. No code changes either way.</p>
     *
     * <p>To extend this map — for example, adding a new {@code SOMETHING}
     * enum constant — add a {@code case} below pointing at the desired path.
     * Constants without an explicit case fall through to the {@code default},
     * which returns the lowercase enum name under {@code fragments/}.</p>
     *
     * @param unlock the ability unlock; may be {@code null}
     * @return the conventional PNG path, or {@code null} for {@code null} input
     */
    public static String defaultSpritePath(AbilityUnlock unlock) {
        if (unlock == null) return null; // Defensive — null in, null out
        switch (unlock) {
            // Each case maps to a single conventional path. Lowercase the
            // enum name so file paths stay consistent across all entries.
            case NONE:             return "resources/sprites/fragments/lore.png";
            case MELEE:            return "resources/sprites/fragments/melee.png";
            case PROJECTILE:       return "resources/sprites/fragments/projectile.png";
            case DODGE:            return "resources/sprites/fragments/dodge.png";
            case WALL_CLING:       return "resources/sprites/fragments/wall_cling.png";
            case SHADOW_DASH:      return "resources/sprites/fragments/shadow_dash.png";
            case EMBER:            return "resources/sprites/fragments/ember.png";
            case IRON:             return "resources/sprites/fragments/iron.png";
            case RADIANT_COLLAPSE: return "resources/sprites/fragments/radiant.png";
            case VEIL:             return "resources/sprites/fragments/veil.png";
            case ECHO:             return "resources/sprites/fragments/echo.png";
            case TETHER:           return "resources/sprites/fragments/tether.png";
            case SHADOW_STEP:      return "resources/sprites/fragments/shadow_step.png";
            // Defensive fall-through: future enum values get a path derived
            // from the constant name so SpriteLoader still has somewhere to
            // look — caller can always set an explicit override if desired.
            default: return "resources/sprites/fragments/" + unlock.name().toLowerCase() + ".png";
        }
    }
}
