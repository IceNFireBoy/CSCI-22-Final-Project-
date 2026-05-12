/**
 * Holds the dialogue lines and stage directions for every cutscene.
 *
 * @author Marxus Antonio L. Magisa (253602) & Antonio Sebastian B. Pasia (254505)
 * @version May 12, 2026
 *
 * I have not discussed the Java language code in my program
 * with anyone other than my instructor or the teaching assistants
 * assigned to this course.
 *
 * I have not used Java language code obtained from another student,
 * or any other unauthorized source, either modified or unmodified.
 * If any Java language code or documentation used in my program
 * was obtained from another source, such as a textbook or website,
 * that has been clearly noted with a proper citation in the comments
 * of my program.
 */
public class CutsceneScript {

    private static final String[][] SCRIPTS = new String[CutsceneID.values().length][];

    static {
        SCRIPTS[CutsceneID.SIGNAL.ordinal()] = new String[]{
            "He said, \"Let there be light\", and there was light... Only light.",
            "Be not afraid. For I am your friend, in this broken place."
        };
        SCRIPTS[CutsceneID.FIRST_LIGHT.ordinal()] = new String[]{
            "I lift the lantern. Old relic. First Light ignites.",
            "The world responds. He knows."
        };
        SCRIPTS[CutsceneID.EDGE_OF_LIGHT.ordinal()] = new String[]{
            "Beyond the lantern's reach, the dark crawls. Death can reach you there."
        };
        SCRIPTS[CutsceneID.RETURNED.ordinal()] = new String[]{
            "You are back in the light. You are fortunate to be alive"
        };
        SCRIPTS[CutsceneID.LAST_COOPERATIVE_ACT.ordinal()] = new String[]{
            "One last bridge, built together.",
            "After this, only one of them can be right."
        };
        SCRIPTS[CutsceneID.ARCHITECT_SPEAKS.ordinal()] = new String[]{
            "The Architect finally speaks:",
            "\"Why can't I make it perfect? Why do you all reject my creation?\""
        };
        SCRIPTS[CutsceneID.CORE_1_DESTROYED.ordinal()] = new String[]{
            "A Core shatters. He feels it."
        };
        SCRIPTS[CutsceneID.CORE_2_DESTROYED.ordinal()] = new String[]{
            "Another Core gone. The time is coming."
        };
        SCRIPTS[CutsceneID.CORE_3_DESTROYED.ordinal()] = new String[]{
            "Three down. The darkness screams louder."
        };
        SCRIPTS[CutsceneID.CORE_4_DESTROYED.ordinal()] = new String[]{
            "The last Core falls.",
            "..."
        };
        SCRIPTS[CutsceneID.WANDERER_VICTORY.ordinal()] = new String[]{
            "The darkness breaks through. Yet you will remain. To design is all I am. Yet my creations are hollow. You will see upon my death. And witness the horrors of the void."
        };
        SCRIPTS[CutsceneID.HOME.ordinal()] = new String[]{
            "The lattice is broken. A gate opens. To other slices of reality. It's time for new beginnings."
        };
        SCRIPTS[CutsceneID.ARCHITECT_VICTORY.ordinal()] = new String[]{
            "\"You were so close.\"",
            "\"Fear not, dear friend. I will remake you, as I will remake this dead world. I will make you perfect.\""
        };
    }

    public CutsceneScript() {}

    public String[] getLines(CutsceneID id) {
        if (id == null) return new String[0];
        String[] lines = SCRIPTS[id.ordinal()];
        return lines != null ? lines : new String[0];
    }
}
