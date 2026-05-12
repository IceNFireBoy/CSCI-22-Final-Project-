/**
 * Static factory that maps level numbers to programmatic level generators.
 * Single dispatch point for level construction; returns a {@link LoadResult}
 * bundle consumed by {@link GameStarter#loadLevel(int, boolean)}.
 *
 * <p>Mapping:
 * <ul>
 *   <li>1 → {@link Act1Level}</li>
 *   <li>2 → {@link Act2Level}</li>
 *   <li>3 → {@link Act3Level}</li>
 *   <li>4 → boss arena (wraps {@link BossArenaGenerator#generate(long)})</li>
 *   <li>any other → empty result (safe-degradation fallback)</li>
 * </ul>
 *
 * <p>The {@code seed} parameter is meaningful only for level 4 (the boss
 * arena's deterministic-symmetry RNG); levels 1-3 ignore it. Callers loading a
 * regular level may pass {@code 0L}; callers loading the boss must pass the
 * seed broadcast by the server via {@link Protocol#BOSS_ARENA}.</p>
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
// Module 1d "Inner Classes"  - LoadResult is a public static nested
//                               class - exact static-nested-class
//                               shape from the inner-classes module.
// Module 1a "Modifiers"      - public static factory method (load),
//                               private constructor, no instance state.
// =========================================================================
import java.util.*;
public class LevelRegistry { // Stateless factory; no instance methods

    // -------------------------------------------------------------------------
    // Constructor (private — prevents instantiation)
    // -------------------------------------------------------------------------

    private LevelRegistry() { /* Utility class — never instantiated */ }

    // -------------------------------------------------------------------------
    // Public factory
    // -------------------------------------------------------------------------

    /**
     * Loads the level identified by {@code levelNum}, returning a bundle of
     * elements and state ready for {@link GameStarter} to install in its
     * world.
     *
     * @param levelNum the 1-based level identifier; 1-3 are platforming acts,
     *                 4 is the boss arena
     * @param seed     deterministic seed for level 4's procedural arena;
     *                 ignored for levels 1-3
     * @return a {@link LoadResult} bundle; never {@code null};
     *         empty (no elements, default state) for unknown {@code levelNum}
     */
    public static LoadResult load(int levelNum, long seed) { // Single dispatch entry point
        switch (levelNum) {                                   // Map level number to the generator
            case 1: return new Act1Level().build();           // Act 1 — platforming intro
            case 2: return new Act2Level().build();           // Act 2 — mid-campaign
            case 3: return new Act3Level().build();           // Act 3 — final platforming before boss
            case 4: return buildBossArena(seed);              // Boss arena via existing BossArenaGenerator
            default:                                           // Unknown level: safe-degradation
                System.err.println("LevelRegistry: unknown level " + levelNum + "; returning empty result");
                return new LoadResult(new ArrayList<>(), new LevelState()); // Empty fallback
        }
    }

    // -------------------------------------------------------------------------
    // Boss arena wrapper
    // -------------------------------------------------------------------------

    /**
     * Wraps {@link BossArenaGenerator#generate(long)} into the unified
     * {@link LoadResult} contract. The boss arena's deterministic symmetry comes
     * from the seed; the same seed produces the same layout on both clients.
     *
     * @param seed the deterministic seed broadcast by the server
     * @return a LoadResult containing the arena entities and a LevelState set
     *         to {@link LevelState.GamePhase#BOSS}
     */
    private static LoadResult buildBossArena(long seed) {       // Glue between the registry and the existing arena generator
        BossArenaGenerator gen = new BossArenaGenerator();       // Stateless; same instance pattern as before
        List<GameElement> arena = gen.generate(seed);            // Reuse the existing procedural arena
        LevelState state = new LevelState();                     // Fresh state object
        state.currentLevel = 4;                                  // Player-facing level 4 (the boss)
        state.currentPhase = LevelState.GamePhase.BOSS;          // Renderer flips into the boss arena view
        return new LoadResult(arena, state);                     // Bundle and return
    }

    // -------------------------------------------------------------------------
    // Nested bundle type
    // -------------------------------------------------------------------------

    /**
     * Bundle pairing the list of instantiated {@link GameElement}s with the
     * pre-configured {@link LevelState} for a loaded level. Produced by
     * {@link #load(int, long)} and consumed by
     * {@code GameStarter.loadLevel(int, boolean)}.
     */
    public static class LoadResult {

         
        public final List<GameElement> elements;

         
        public final LevelState state;

        /**
         * Constructs a {@code LoadResult} bundling the given elements and state.
         *
         * @param elements the list of game entities; must not be {@code null}
         * @param state    the pre-configured level state; must not be {@code null}
         */
        public LoadResult(List<GameElement> elements, LevelState state) {
            this.elements = elements;
            this.state    = state;
        }
    }
}
