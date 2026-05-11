/**
 * Script data for every cutscene keyed by {@link CutsceneID}. Each script is an
 * ordered array of narrative lines that the cutscene renderer will display one panel
 * at a time. Keeping the story data here decouples authoring from rendering — the
 * renderer simply calls {@link #getLines(CutsceneID)} and does not need to understand
 * the story structure, narrative sequence, or line count.
 *
 * <p>Architecture role: {@code CutsceneScript} is a pure data-provider class with
 * no mutable state. A single instance is held by {@link CutsceneRenderer} (or
 * equivalent), which calls {@link #getLines(CutsceneID)} when a cutscene begins to
 * obtain the ordered text panels. The renderer then advances through the array at its
 * own pace (click-to-continue, timed display, etc.) without referencing this class
 * again until the next cutscene starts.</p>
 *
 * <p>The script data lives in a static initialiser block rather than in a switch
 * statement or map literal to keep the authoring format compact: one line of code
 * per script line. Adding a new cutscene requires only a matching {@link CutsceneID}
 * constant and a new {@code SCRIPTS[CutsceneID.X.ordinal()] = ...} entry in the
 * static block — no changes to the renderer or any other class are needed.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-17
 * @certification I certify that this code is my own work and has not been copied
 *                from any other source, in whole or in part.
 */

// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 1d "Inner Classes"  - this file is the canonical enum example
//                               from the inner-classes module - a list
//                               of named constants used as keys for
//                               cutscene scripts.
// Module 1a "Modifiers"      - public enum constants are implicitly
//                               public static final, the constant
//                               convention from 1a.
// =========================================================================
public class CutsceneScript { // Data-only class; no mutable state; all script content is in the static SCRIPTS array

    // -------------------------------------------------------------------------
    // Static script table
    // -------------------------------------------------------------------------

    /**
     * Array of script-line arrays, one per {@link CutsceneID} ordinal. Indexed by
     * {@link CutsceneID#ordinal()} so the lookup in {@link #getLines(CutsceneID)} is
     * O(1) with no map overhead. The array is sized to hold one entry per
     * {@code CutsceneID} value; unregistered slots contain {@code null}, which
     * {@link #getLines(CutsceneID)} converts to an empty array.
     */
    private static final String[][] SCRIPTS = new String[CutsceneID.values().length][]; // Statically-sized String[][] indexed by CutsceneID.ordinal(); null entries become empty arrays

    static { // Static initialiser: populates SCRIPTS once when the class is loaded; runs before any constructor or getLines() call
        SCRIPTS[CutsceneID.SIGNAL.ordinal()] = new String[]{ // SIGNAL: game-opening cutscene; sets the mystery tone before any gameplay
            "He said, \"Let there be light\", and there was light... Only light.",   // First panel: establishes the isolation motif
            "Be not afraid. For I am your friend, in this broken place."                        // Second panel: hints at the cooperative relationship before the players know each other's roles
        };
        SCRIPTS[CutsceneID.FIRST_LIGHT.ordinal()] = new String[]{ // FIRST_LIGHT: plays when the Apprentice activates the lantern for the first time in Act 1
            "I lift the lantern. Old relic. First Light ignites.",  // First panel: role-confirmation moment for the Apprentice player
            "The world responds. He knows."                                     // Second panel: short, punchy line — the world reacts to light, establishing the game's core mechanic
        };
        SCRIPTS[CutsceneID.EDGE_OF_LIGHT.ordinal()] = new String[]{ // EDGE_OF_LIGHT: plays when the Wanderer first ventures to the boundary of the light radius
            "Beyond the lantern's reach, the dark crawls. Death can reach you there."           // Single panel: atmospheric warning that enemies exist in the dark
        };
        SCRIPTS[CutsceneID.RETURNED.ordinal()] = new String[]{ // RETURNED: plays when the Wanderer re-enters the light after surviving a dark zone
            "You are back in the light. You are fortunate to be alive" // Single panel: relief beat; acknowledges the cooperative success
        };
        SCRIPTS[CutsceneID.LAST_COOPERATIVE_ACT.ordinal()] = new String[]{ // LAST_COOPERATIVE_ACT: plays at end of Act 3, just before the boss — the last moment of pure cooperation
            "One last bridge, built together.",                       // First panel: references the Apprentice's block-placement role
            "After this, only one of them can be right."             // Second panel: foreshadows the boss mechanic where the two players have diverging victory conditions
        };
        SCRIPTS[CutsceneID.ARCHITECT_SPEAKS.ordinal()] = new String[]{ // ARCHITECT_SPEAKS: boss intro cutscene; the antagonist reveals their worldview
            "The Architect finally speaks:",                         // First panel: dramatic pause before the villain's dialogue
            "\"Why can't I make it perfect? Why do you all reject my creation?\""   // Second panel: the villain's line; in quotes to distinguish spoken dialogue from narration
        };
        SCRIPTS[CutsceneID.CORE_1_DESTROYED.ordinal()] = new String[]{ // CORE_1_DESTROYED: plays after the first boss Core is destroyed; mid-boss narrative beat
            "A Core shatters. He feels it."              // Single panel: brief; does not interrupt boss action for long
        };
        SCRIPTS[CutsceneID.CORE_2_DESTROYED.ordinal()] = new String[]{ // CORE_2_DESTROYED: plays after the second Core; escalating tension
            "Another Core gone. The time is coming."             // Single panel: "override" references the architectOverride mechanic that escalates difficulty
        };
        SCRIPTS[CutsceneID.CORE_3_DESTROYED.ordinal()] = new String[]{ // CORE_3_DESTROYED: plays after the third Core; near-victory mood
            "Three down. The darkness screams louder."                     // Single panel: "the dark is loud" — synesthetic language reflecting the boss's increasing attack intensity
        };
        SCRIPTS[CutsceneID.CORE_4_DESTROYED.ordinal()] = new String[]{ // CORE_4_DESTROYED: plays after the final Core; boss-defeated beat
            "The last Core falls.",                                  // First panel: simple statement of fact — the boss objective is complete
            "..."                       // Second panel: quiet aftermath; no triumphant fanfare, only silence
        };
        SCRIPTS[CutsceneID.WANDERER_VICTORY.ordinal()] = new String[]{ // WANDERER_VICTORY: shown on the Wanderer win ending; philosophical resolution
            "The darkness breaks through. Yet here you remain. I thought we could both live to see it. Goodbye." // Single panel: the victory condition is not defeating darkness but learning to coexist with it
        };
        SCRIPTS[CutsceneID.HOME.ordinal()] = new String[]{ // HOME: cooperative victory epilogue; both players win together
            "The lattice is broken. A gate opens. To other slices of reality. It's time for new beginnings."                                // Single panel: three short nouns — the simplest possible statement of arrival and safety
        };
        SCRIPTS[CutsceneID.ARCHITECT_VICTORY.ordinal()] = new String[]{ // ARCHITECT_VICTORY: Apprentice win ending; the Architect's perspective prevails
            "\"Your lantern is mine again.\"",                 // First panel: the Apprentice relinquishes the tool of their role
            "\"This world is broken, but I will make it anew. Again. I will make it, make you, perfect this time.\""           // Second panel: the Architect's victory condition — the world shaped by the Apprentice's design
        };
    }

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a {@code CutsceneScript} instance. This class has no instance-level
     * state; the constructor exists only so callers can instantiate it via
     * {@code new CutsceneScript()} rather than using static access, keeping the API
     * consistent with other service objects in the project.
     */
    public CutsceneScript() {} // No-arg constructor; class is data-only; all script content is in the static SCRIPTS array

    // -------------------------------------------------------------------------
    // Script lookup
    // -------------------------------------------------------------------------

    /**
     * Returns the ordered script lines for the given cutscene identifier. The renderer
     * advances panels based on its own timing logic; this method provides the data
     * and never returns {@code null} — unregistered or null IDs yield an empty array
     * so callers can safely iterate the result without null-checking.
     *
     * <p>Architecture role: Called by {@link CutsceneRenderer} (or the equivalent
     * cutscene-display subsystem in {@link GameCanvas}) at the moment a cutscene
     * begins. The returned array is treated as immutable by the renderer — it reads
     * entries sequentially and stores an index, never modifying the array itself.</p>
     *
     * <p>Interaction: The {@link CutsceneID} value that triggers this call is
     * determined by the cutscene event dispatcher in {@link GameStarter}: when a
     * {@link Protocol#CUTSCENE_START} message arrives (carrying the {@code CutsceneID}
     * name), {@link GameStarter} calls
     * {@code CutsceneRenderer.startCutscene(CutsceneID.valueOf(name))} which in turn
     * calls this method to load the script.</p>
     *
     * @param id the cutscene identifier; {@code null} is safe and returns an empty array
     * @return the ordered lines to display, in story sequence; never {@code null};
     *         an empty array if {@code id} is {@code null} or no script is registered
     *         for the given ID
     */
    public String[] getLines(CutsceneID id) {              // Primary API: returns script lines for a given cutscene; safe for null input
        if (id == null) return new String[0];              // Null guard: return empty array so callers do not need to handle null results
        String[] lines = SCRIPTS[id.ordinal()];            // Look up by ordinal index: O(1) array access into the static script table
        return lines != null ? lines : new String[0];      // Null-coalesce: unregistered CutsceneID slots in SCRIPTS are null; return empty array instead
    }
}
