/**
 * Holds the mutable runtime state of the current level in Lumen Architect, including
 * which act is active, how much block budget the Apprentice has remaining, the
 * countdown timer, and whether the timer is currently running. All game-loop and
 * server components share a single {@code LevelState} instance so that state changes
 * made in one subsystem are immediately visible to the others.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

public class LevelState {

    // =========================================================================
    // Enum — GamePhase
    // =========================================================================

    /**
     * Represents the high-level phase the game session is currently in. The phase
     * drives which renderer, update logic, and input bindings are active on any given
     * frame.
     */
    public enum GamePhase {

        /** The title and lobby screen displayed before a session begins. */
        MENU,

        /**
         * The first platforming act; introduces basic platforms, a single DarkCrawler,
         * and the first lore fragments.
         */
        ACT1,

        /**
         * The second platforming act; introduces hazard combinations and additional
         * lore fragments that unlock new Wanderer abilities.
         */
        ACT2,

        /**
         * The third platforming act; the most complex platform layout before the boss,
         * with all Core types present.
         */
        ACT3,

        /**
         * The boss arena encounter; the Wanderer must destroy all four Cores while the
         * Apprentice escalates defences.
         */
        BOSS,

        /**
         * A short linear gauntlet following the boss fight, leading to the final
         * victory or defeat determination.
         */
        FINAL_CORRIDOR,

        /**
         * A scripted narrative sequence; normal gameplay is suspended and
         * {@link rendering.CutsceneRenderer} takes over rendering.
         */
        CUTSCENE,

        /** Gesture calibration screen before the game begins. */
        CALIBRATION
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * The one-based index of the level currently loaded. Corresponds to the argument
     * passed to {@link LevelLoader#loadLevel(int)}.
     */
    public int currentLevel;

    /**
     * The number of block-placement tokens the Apprentice has remaining for this
     * phase. Decremented by {@link entities.Platform#getBudgetCost()} each time the
     * Apprentice places a platform.
     */
    public int blockBudget;

    /**
     * The number of milliseconds remaining on the current phase countdown timer.
     * Decremented each game tick while {@link #timerActive} is {@code true}. When
     * this reaches zero the server evaluates the victory condition.
     */
    public long timeRemainingMs;

    /**
     * Whether the countdown timer is currently running. Set to {@code false} during
     * cutscenes, the pause menu, and the pre-game lobby.
     */
    public boolean timerActive;

    /**
     * The active game phase, which determines which renderers and update pipelines
     * are engaged on each frame.
     */
    public GamePhase currentPhase;

    /**
     * Server-authoritative health snapshot for all four Cores. Updated each time the
     * client receives a {@link server.NetworkProtocol.ServerStatePacket} containing a
     * {@link server.NetworkProtocol.CoreStatePacket}. Index 0–3 match the ordering used
     * in {@link server.GameServer#getCoreHealth()}.
     */
    public int[] coreHealth = {3, 3, 3, 3};

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a {@code LevelState} initialised to the default pre-session values:
     * level 1, full block budget, a standard timer value, timer paused, and phase set
     * to {@link GamePhase#MENU}.
     */
    public LevelState() {
        this.currentLevel = 1;
        this.blockBudget = 20;
        this.timeRemainingMs = 300_000L; // 5 minutes default
        this.timerActive = false;
        this.currentPhase = GamePhase.MENU;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Advances the level state to the next logical phase in the campaign sequence.
     * Transitions follow the order: MENU → ACT1 → ACT2 → ACT3 → BOSS →
     * FINAL_CORRIDOR. Calling this when the phase is already {@link GamePhase#FINAL_CORRIDOR}
     * or a terminal state has no effect. This stub will be fully implemented in a
     * later phase when inter-act transition logic is complete.
     */
    public void advancePhase() {
        // Stub — phase transition logic to be implemented in a later phase.
    }
}
