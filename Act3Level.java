/**
 * Act 3 level generator. The final platforming act before the boss arena —
 * INVISIBLE platforms (only solid when illuminated) and Pattern crawlers
 * become available. The portal at the end of this level fires
 * {@link Protocol#BOSS_ENTER}, transitioning the session into the boss arena.
 * Subclass of {@link LevelGenerator}.
 *
 * <p><b>This is a scaffold</b>: the placements below are minimal placeholders.
 * Replace them with the actual Act 3 layout by editing this file directly.
 * See {@link Act1Level} for the full helper reference.</p>
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
public class Act3Level extends LevelGenerator { // Concrete Act 3 generator

    @Override
    public LevelRegistry.LoadResult build() { // Populate elements + state and return finish()

        // ---- State ----
        setPhase(LevelState.GamePhase.ACT3); // Act 3 phase: INVISIBLE platforms + full hazard suite
        setTimeLimit(240);                   // 4-minute timer for the wider darkness sections
        setBlockBudget(20);                  // Largest Apprentice budget — Wanderer's hardest run
        setSpawn(60, 652);                   // Same spawn convention

        // ---- Default ground row ----
        brickRow(0, 740, 32);

        // ---- Example platforms ----
        platform(Platform.PlatformType.BRICK, 200, 620, 128, 16);

        // INVISIBLE platforms — only solid when within the LightBall's radius.
        // Useful for darkness puzzles where the Apprentice's light reveals the path.
        platform(Platform.PlatformType.INVISIBLE, 380, 540);
        platform(Platform.PlatformType.INVISIBLE, 460, 480);

        platform(Platform.PlatformType.BRICK, 560, 440, 64, 16);
        platform(Platform.PlatformType.MIMIC, 700, 440);    // Looks like BRICK; collapses on first contact
        platform(Platform.PlatformType.BRICK, 820, 400, 96, 16);

        // ---- Example fragments ----
        fragment("A3-SHADOW_DASH", 580, 416,
                 LoreFragment.AbilityUnlock.SHADOW_DASH,
                 "Entry 027. The veil thins. The Wanderer steps between strides.",
                 null);

        fragment("A3-IRON", 850, 372,
                 LoreFragment.AbilityUnlock.IRON,
                 "Entry 031. Iron settles into the breath. Hits land lighter.",
                 null);

        // ---- Example altars (two for Act 3) ----
        altar(3, 300, 692, "POWER_SURGE", "SIGHT_RESTRICTION", null);
        altar(4, 760, 692, "POWER_SURGE", "SIGHT_RESTRICTION", null);

        // ---- Example hazards (P9.3' — Act 3 mixes the full hazard suite) ----
        // PhantomBlock — solid only while the Apprentice paints it with light;
        // fades out and becomes traversable shortly after the light leaves.
        phantomBlock(420, 580, 64, 32, null);

        // LightLockedMover with circular pattern — orbits a centre until lit.
        lightMover(700, 540, LightLockedMover.MovePattern.CIRCULAR,
                   72, 4000, LightLockedMover.LightBehavior.FREEZE_ON_LIGHT, null);

        // CorruptedSpike row at the lower edge — punishes falls.
        spike(160, 724);
        spike(220, 724);
        spike(280, 724);
        spike(335, 724);
        spike(380, 724);
        spike(740, 724);
        spike(790, 724);

        // CorruptedWall guarding the portal approach — wider trigger so the
        // player gets the warning shake even from afar.
        corruptedWall(840, 320, 80, 160, 320, 320, null);

        // ---- Portal ----
        // Reaching this portal fires Protocol.BOSS_ENTER (handled by GameStarter
        // once the level cap is updated to >= 3 — see P10.4 GameStarter wiring).
        portal(920, 320);

        return finish();
    }
}
