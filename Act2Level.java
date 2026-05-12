/**
 * Act 2 level generator. Mid-campaign — corrupted hazards introduced, faithful
 * meter unlocks, altars start appearing. Subclass of {@link LevelGenerator} —
 * placement is done with the inherited helper methods inside {@link #build()}.
 *
 * <p><b>This is a scaffold</b>: the placements below are minimal placeholders
 * so the level loads cleanly out of the box. Replace them with the actual Act
 * 2 layout by editing this file directly. See {@link Act1Level} for the full
 * helper reference.</p>
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
// Module 1c "Abstract Classes" - extends LevelGenerator, providing a
//                                 concrete build() method that calls the
//                                 inherited protected helpers (platform,
//                                 brickRow, fragment, altar, portal, etc.).
//                                 Canonical extend-abstract-and-implement
//                                 pattern from 1c.
// Module 1a "Modifiers"         - private helper methods, package-private
//                                 build() override, protected access via
//                                 inheritance.
// =========================================================================
public class Act2Level extends LevelGenerator { // Concrete Act 2 generator

    @Override
    public LevelRegistry.LoadResult build() { // Populate elements + state and return finish()

        // ---- State ----
        setPhase(LevelState.GamePhase.ACT2); // Act 2 phase: hazard suite + faithful meter
        setBlockBudget(16);                  // Slightly larger Apprentice budget for harder traversal
        setSpawn(60, 652);                   // Same spawn as Act 1 by default

        // ---- Default ground row ----
         brickRow(0, 740, 32); // 32 BRICK tiles → spans full canvas width

        // ---- Example platforms ----
        platform(Platform.PlatformType.BRICK, 200, 600, 96, 16);
        platform(Platform.PlatformType.BRICK, 400, 520, 128, 16);
        platform(Platform.PlatformType.SLIDE, 600, 480);
        platform(Platform.PlatformType.SPRING, 760, 600);

        // ---- Example fragment ----
        // Combat unlock (WALL_CLING); sprite path optional.
        fragment("A2-WALL_CLING", 800, 700, // id, x, y
                 LoreFragment.AbilityUnlock.WALL_CLING,
                 "Entry 014. The Apprentice has learned to brace. The walls remember the climb.",
                 "resources/sprites/fragments/wall_cling.png"); // null sprite → procedural shard
         fragment("A2-MELEE", 210, 580, // id, x, y
                 LoreFragment.AbilityUnlock.MELEE,
                 "Entry 069. Warriors strike true. A broken shell reveals what lies underneath.",
                 "resources/sprites/fragments/melee.png");

        // ---- Example altar ----
        // Choice altar with the P8.6 power-surge / sight-restriction options.
        altar(2, 15, 95, "POWER_SURGE", "SIGHT_RESTRICTION", "resources/sprites/altars/stone.png");
        lastAltar().setConcealment(BreakableWallConcealment.of());
        platform(Platform.PlatformType.BRICK, 7, 145, 96, 16);
        platform(Platform.PlatformType.BRICK, 137, 345, 30, 16);

        // ---- Example hazards (P9.3' replaces crawlers with light-driven hazards) ----
        // CorruptedSpike — straightforward contact-damage pickup deterrent.
        spike(340, 724, 84, 24, "resources/sprites/hazards/corrupted_spike.png");
        spike(434, 724, 84, 24, "resources/sprites/hazards/corrupted_spike.png");
        spike(508, 724, 84, 24, "resources/sprites/hazards/corrupted_spike.png");
        spike(582, 724, 84, 24, "resources/sprites/hazards/corrupted_spike.png");
        spike(656, 724, 84, 24, "resources/sprites/hazards/corrupted_spike.png");
        spike(730, 7, 84, 24, "resources/sprites/hazards/corrupted_spike.png");
        spike(804, 724, 84, 24, "resources/sprites/hazards/corrupted_spike.png");

        // CorruptedWall — background wall with a 1 s shake-warning before it falls.
        // The trigger zone is 4× the wall footprint so the player has room to react.
        corruptedWall(150, 580, 64, 128, 256, 256, "resources/sprites/hazards/wall.png");

        // LightLockedMover — moving platform that freezes when the Apprentice lights it.
        lightMover(560, 560, LightLockedMover.MovePattern.LINEAR_X);

        // ---- Portal ----
        portal(920, 400);

        return finish();
    }
}
