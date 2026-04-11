/**
 * Acts as the static dialogue and narration database for all Lumen Architect cutscenes,
 * returning the ordered array of text lines associated with any
 * {@link rendering.CutsceneRenderer.CutsceneID}. Keeping script data in this dedicated
 * class separates story content from rendering logic and makes it straightforward to
 * edit or localise dialogue without touching the renderer.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */


public class CutsceneScript {

    private static final String[][] SCRIPTS = new String[CutsceneID.values().length][];

    static {
        SCRIPTS[CutsceneID.SIGNAL.ordinal()] = new String[]{
            "The simulation has been dark for a long time.",
            "It is not empty.",
            "It is waiting."
        };
        SCRIPTS[CutsceneID.FIRST_LIGHT.ordinal()] = new String[]{
            "The light inside you is not only for traversal.",
            "It was always meant to be returned."
        };
        SCRIPTS[CutsceneID.EDGE_OF_LIGHT.ordinal()] = new String[]{
            "One more step.",
            "Then everything changes."
        };
        SCRIPTS[CutsceneID.RETURNED.ordinal()] = new String[]{
            "You pushed back.",
            "Remember this."
        };
        SCRIPTS[CutsceneID.LAST_COOPERATIVE_ACT.ordinal()] = new String[]{
            "One last time.",
            "Together."
        };
        SCRIPTS[CutsceneID.ARCHITECT_SPEAKS.ordinal()] = new String[]{
            "YOU BUILT WITH ME ONCE.",
            "YOU WILL FINISH THIS WITH ME NOW.",
            "THERE IS NO OTHER WAY THE SIMULATION ENDS."
        };
        SCRIPTS[CutsceneID.CORE_1_DESTROYED.ordinal()] = new String[]{
            "The grip loosens.",
            "For now."
        };
        SCRIPTS[CutsceneID.CORE_2_DESTROYED.ordinal()] = new String[]{
            "Half of everything \u2014 gone.",
            "Keep going."
        };
        SCRIPTS[CutsceneID.CORE_3_DESTROYED.ordinal()] = new String[]{
            "It is losing its hold.",
            "One remains."
        };
        SCRIPTS[CutsceneID.CORE_4_DESTROYED.ordinal()] = new String[]{
            "",
            ""
        };
        SCRIPTS[CutsceneID.WANDERER_VICTORY.ordinal()] = new String[]{
            "The light is yours.",
            "It always was."
        };
        SCRIPTS[CutsceneID.HOME.ordinal()] = new String[]{
            "The simulation has been dark for a long time.",
            "It remembers, now, how to be otherwise."
        };
        SCRIPTS[CutsceneID.ARCHITECT_VICTORY.ordinal()] = new String[]{
            "The light inside you was always supposed to lead you home.",
            "Perhaps next time."
        };
    }

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a {@code CutsceneScript} instance. This class is effectively a
     * look-up table; no mutable state is maintained between calls.
     */
    public CutsceneScript() {
        // Stateless look-up table — no fields to initialise.
    }

    // -------------------------------------------------------------------------
    // Script retrieval
    // -------------------------------------------------------------------------

    /**
     * Returns the ordered array of dialogue and narration lines for the specified
     * cutscene. Each element in the returned array represents one text panel displayed
     * by {@link rendering.CutsceneRenderer}; panels advance automatically based on
     * the renderer's state machine timing.
     *
     * @param id the {@link CutsceneID} whose script lines are requested; must not be
     *           {@code null}
     * @return a non-null array of dialogue line strings in display order; may be empty
     *         but never {@code null}
     */
    public String[] getLines(CutsceneID id) {
        String[] lines = SCRIPTS[id.ordinal()];
        return (lines != null) ? lines : new String[0];
    }
}
