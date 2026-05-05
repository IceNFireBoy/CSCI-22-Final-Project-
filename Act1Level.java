/**
 * Act 1 level generator. The first platforming act of the Lumen Architect
 * campaign. Subclass of {@link LevelGenerator} — placement is done with the
 * inherited helper methods inside {@link #build()}.
 *
 * <p><b>This is a scaffold</b>: the placements below are minimal placeholders
 * so the level loads cleanly out of the box. Replace them with the actual Act
 * 1 layout by editing this file directly — coordinates are in world-space
 * pixels (origin top-left, y increases downward, tile size 32 px).</p>
 *
 * <p>Helper reference (inherited from {@link LevelGenerator}):
 * <pre>
 *   setPhase(GamePhase) / setTimeLimit(seconds) / setBlockBudget(blocks) / setSpawn(x, y)
 *   platform(BRICK|SLIDE|SPRING|WALL|CRUMBLE|INVISIBLE|MIMIC, x, y)
 *   platform(type, x, y, w, h)
 *   brickRow(startX, y, count)               // 32-px gap by default
 *   brickRow(startX, y, count, gap)
 *   fragment(id, x, y, unlock, body, spritePath)
 *   altar(id, x, y, opt1, opt2, spritePath)
 *   portal(x, y)
 *   spike(x, y) / spike(x, y, w, h, spritePath)
 *   corruptedWall(x, y, w, h, triggerW, triggerH, spritePath)
 *   phantomBlock(x, y) / phantomBlock(x, y, w, h, spritePath)
 *   lightMover(x, y, MovePattern) / lightMover(x, y, pattern, amp, periodMs, behaviour, spritePath)
 *   trigger(type, x, y, params, spritePath)
 *   lastFragment().setGated(true)            // post-hoc fragment config
 *   lastAltar().setConcealment(...)          // post-hoc altar config
 * </pre>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-29
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

public class Act1Level extends LevelGenerator { // Concrete Act 1 generator

    @Override
    public LevelRegistry.LoadResult build() { // Subclass entry point — populate elements + state and return finish()

        // ---- State ----
        setPhase(LevelState.GamePhase.ACT1); // Act 1 phase: full light, no special darkness mechanics
        setTimeLimit(90);                    // 90 second timer
        setBlockBudget(12);                  // Apprentice has 12 placement tokens
        setSpawn(60, 652);                   // Wanderer spawn near the bottom-left ground

        // ---- Default ground row ----
        // 32 BRICK tiles spaced 32 px apart → spans (0, 740) to (1024, 740) — full canvas width.
        brickRow(0, 740, 32);

        // ---- Example platforms ----
        // Mid-air ledge with a CRUMBLE trap on top.
        platform(Platform.PlatformType.BRICK, 560, 660, 96, 16);
        platform(Platform.PlatformType.CRUMBLE, 580, 660);

        // Vertical wall — uses the explicit-size variant.
        platform(Platform.PlatformType.WALL, 450, 596, 16, 64);

        // High ledge near the portal.
        platform(Platform.PlatformType.BRICK, 880, 480);
        platform(Platform.PlatformType.BRICK, 912, 480);

        // ---- Example fragment ----
        // Narrative-only (NONE unlock); sprite path null → procedural shard render.
        fragment("A1-INTRO", 244, 716, // id, x, y
                 LoreFragment.AbilityUnlock.NONE,
                 "Entry 001. The first platform held. The apprentice is learning the grammar of light.",
                 null);

        // ---- Example portal ----
        // Default 48×80; collision with the Wanderer triggers level 2 load.
        portal(920, 400);

        return finish(); // Bundle elements + state and return
    }
}
