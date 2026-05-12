





































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
            "The darkness breaks through. Yet here you remain. I thought we could both live to see it. Goodbye."
        };
        SCRIPTS[CutsceneID.HOME.ordinal()] = new String[]{
            "The lattice is broken. A gate opens. To other slices of reality. It's time for new beginnings."
        };
        SCRIPTS[CutsceneID.ARCHITECT_VICTORY.ordinal()] = new String[]{
            "\"Your lantern is mine again.\"",
            "\"This world is broken, but I will make it anew. Again. I will make it, make you, perfect this time.\""
        };
    }











    public CutsceneScript() {}




























    public String[] getLines(CutsceneID id) {
        if (id == null) return new String[0];
        String[] lines = SCRIPTS[id.ordinal()];
        return lines != null ? lines : new String[0];
    }
}
