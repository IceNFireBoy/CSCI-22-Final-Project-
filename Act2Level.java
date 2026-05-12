






























public class Act2Level extends LevelGenerator {

    @Override
    public LevelRegistry.LoadResult build() {


        setPhase(LevelState.GamePhase.ACT2);
        setBlockBudget(16);
        setSpawn(60, 652);


         brickRow(0, 740, 32);


        platform(Platform.PlatformType.BRICK, 200, 600, 96, 16);
        platform(Platform.PlatformType.BRICK, 400, 520, 128, 16);
        platform(Platform.PlatformType.SLIDE, 600, 480);
        platform(Platform.PlatformType.SPRING, 760, 600);



        fragment("A2-WALL_CLING", 800, 700,
                 LoreFragment.AbilityUnlock.WALL_CLING,
                 "Entry 014. The Apprentice has learned to brace. The walls remember the climb.",
                 "resources/sprites/fragments/wall_cling.png");
         fragment("A2-MELEE", 210, 580,
                 LoreFragment.AbilityUnlock.MELEE,
                 "Entry 069. Warriors strike true. A broken shell reveals what lies underneath.",
                 "resources/sprites/fragments/melee.png");



        altar(2, 15, 95, "POWER_SURGE", "SIGHT_RESTRICTION", "resources/sprites/altars/stone.png");
        lastAltar().setConcealment(BreakableWallConcealment.of());
        platform(Platform.PlatformType.BRICK, 7, 145, 96, 16);
        platform(Platform.PlatformType.BRICK, 137, 345, 30, 16);



        spike(340, 724, 84, 24, "resources/sprites/hazards/corrupted_spike.png");
        spike(434, 724, 84, 24, "resources/sprites/hazards/corrupted_spike.png");
        spike(508, 724, 84, 24, "resources/sprites/hazards/corrupted_spike.png");
        spike(582, 724, 84, 24, "resources/sprites/hazards/corrupted_spike.png");
        spike(656, 724, 84, 24, "resources/sprites/hazards/corrupted_spike.png");
        spike(730, 7, 84, 24, "resources/sprites/hazards/corrupted_spike.png");
        spike(804, 724, 84, 24, "resources/sprites/hazards/corrupted_spike.png");



        corruptedWall(150, 580, 64, 128, 256, 256, "resources/sprites/hazards/wall.png");


        lightMover(560, 560, LightLockedMover.MovePattern.LINEAR_X);


        portal(920, 400);

        return finish();
    }
}
