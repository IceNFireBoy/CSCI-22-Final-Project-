/**
 * Enumeration of every cutscene identifier the game can trigger. Each constant
 * is the lookup key both {@link CutsceneScript} (for dialogue/narration lines)
 * and {@link CutsceneRenderer} will use to resolve the correct story beat.
 *
 * <p>Architecture role: {@code CutsceneID} is the shared vocabulary between the
 * server and both clients for the lock-step cutscene system (P8.0). When the server
 * decides a story beat should play, it broadcasts a
 * {@link NetworkProtocol.CutscenePacket} whose {@code cutsceneId} field is the
 * {@link #name()} of one of these constants. Both clients parse that name with
 * {@link CutsceneID#valueOf(String)}, pass it to {@link CutsceneRenderer#play(CutsceneID)},
 * and send {@link Protocol#CUTSCENE_ACK} back to the server once they have advanced
 * past the final panel. The server waits for both ACKs before unpausing the game
 * loop, ensuring perfect synchronisation between players.</p>
 *
 * <p>The renderer itself is wired up in {@link CutsceneRenderer}; the dialogue lines
 * for each constant are stored in {@link CutsceneScript#SCRIPTS}.</p>
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
public enum CutsceneID { // Enum — each constant corresponds to one discrete story beat; ordinal used by CutsceneScript to index into SCRIPTS[][]

    /**
     * Pre-game title sequence: a signal flickers in the dark, establishing the
     * world's lore premise before either player has moved. Triggered at game start
     * by the server immediately after both players confirm readiness in the lobby.
     * Handled by {@link GameStarter#handleMessage} on the {@code CUT_TRIGGER} path
     * and by the F9 debug binding in {@link InputRouter#registerCutsceneBindings}.
     */
    SIGNAL, // Index 0 in CutsceneScript.SCRIPTS — the very first story beat the player sees

    /**
     * Act 1 intro: the Apprentice kindles the first light, teaching the player that
     * darkness = danger. Played automatically when Act 1 begins. Its script lines
     * introduce the cooperative premise: the Wanderer needs the Apprentice's light
     * to see and survive.
     */
    FIRST_LIGHT, // Index 1 — plays at the start of Act 1; introduces the light mechanic

    /**
     * Act 2 intro: the Wanderer reaches the edge of the light field, foreshadowing
     * that the Apprentice's battery will become a resource to manage. Script text
     * hints at the battery-drain mechanic introduced in Act 2.
     */
    EDGE_OF_LIGHT, // Index 2 — plays at the start of Act 2; signals the transition to resource management

    /**
     * Mid-game beat: the Wanderer returns from a dangerous dark section, signalling
     * that the cooperative bond is growing. Narrative reward for surviving Act 2's
     * battery-constrained traversal.
     */
    RETURNED, // Index 3 — mid-game narrative beat rewarding successful Act 2 completion

    /**
     * Late-game beat: the duo's final cooperative act before the boss. Sets up the
     * emotional stakes and signals to players that the boss arena is about to begin.
     * Triggered when the Wanderer reaches the portal at the end of Act 3 / Final Corridor.
     */
    LAST_COOPERATIVE_ACT, // Index 4 — pre-boss emotional beat; played just before the BOSS_ENTER signal

    /**
     * Boss-reveal: the Architect (the antagonist) finally speaks. Triggered by the
     * server immediately after both clients load the boss arena (P8.2) via the
     * {@link Protocol#BOSS_ARENA} broadcast. This is also the point where the server
     * activates the architect-override mode, turning the Apprentice into an adversary.
     */
    ARCHITECT_SPEAKS, // Index 5 — boss intro cutscene; plays right after enterBossArena() on both clients

    /**
     * First Core destroyed. The server triggers this as a narrative acknowledgement
     * when {@code coreHealth[0]} drops to zero. In addition to dialogue, this is the
     * cutscene that triggers the architect-override flag in {@link GameServer}, giving
     * the Apprentice access to offensive boss attacks.
     */
    CORE_1_DESTROYED, // Index 6 — narrative beat on first Core kill; server sets architectOverride=true after this

    /** Second Core destroyed. Narrative escalation — the Architect becomes desperate. */
    CORE_2_DESTROYED, // Index 7 — plays when coreHealth[1] reaches 0

    /** Third Core destroyed. The final confrontation is near. */
    CORE_3_DESTROYED, // Index 8 — plays when coreHealth[2] reaches 0

    /**
     * Fourth and final Core destroyed. This triggers the Wanderer victory condition;
     * the server sends a {@link NetworkProtocol.VictoryPacket} immediately after both
     * clients ACK this cutscene.
     */
    CORE_4_DESTROYED, // Index 9 — last core kill cutscene; victory evaluation follows its ACK

    /**
     * Wanderer-path ending. Played when all four Cores are destroyed and the Wanderer
     * wins. Both clients receive this via a {@link NetworkProtocol.CutscenePacket};
     * the screen transitions to {@link LevelState.GamePhase#END_SCREEN} after both ACK.
     */
    WANDERER_VICTORY, // Index 10 — shown to both players when the Wanderer destroys all Cores

    /**
     * Epilogue: home. Plays after the Wanderer victory as the final narrative beat
     * before the END_SCREEN phase, depicting a return to safety.
     */
    HOME, // Index 11 — epilogue beat following WANDERER_VICTORY; precedes END_SCREEN

    /**
     * Architect-path ending. Played when the Architect (Apprentice) wins — either by
     * running out the timer or by killing the Wanderer. Both clients receive this via a
     * {@link NetworkProtocol.CutscenePacket}; the screen transitions to
     * {@link LevelState.GamePhase#END_SCREEN} after both ACK.
     */
    ARCHITECT_VICTORY // Index 12 — shown to both players when the Architect wins; final constant in ordinal ordering
}
