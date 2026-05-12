































public class Act3Level extends LevelGenerator {

    @Override
    public LevelRegistry.LoadResult build() {


        setPhase(LevelState.GamePhase.ACT3);
        setBlockBudget(20);
        setSpawn(60, 652);


        brickRow(0, 740, 32);


        platform(Platform.PlatformType.BRICK, 200, 620, 128, 16);



        platform(Platform.PlatformType.INVISIBLE, 380, 540);
        platform(Platform.PlatformType.INVISIBLE, 460, 480);

        platform(Platform.PlatformType.MIMIC, 560, 440, 64, 16);
        platform(Platform.PlatformType.MIMIC, 700, 440);
        platform(Platform.PlatformType.BRICK, 820, 400, 96, 16);


        fragment("A3-SHADOW_DASH", 580, 416,
                 LoreFragment.AbilityUnlock.SHADOW_DASH,
                 "Entry 027. The veil thins. The Wanderer steps between strides.",
                 "resources/sprites/fragments/shadow_dash.png");

        fragment("A3-IRON", 850, 372,
                 LoreFragment.AbilityUnlock.IRON,
                 "Entry 031. Iron settles into the breath. Hits land lighter.",
                 "resources/sprites/fragments/iron.png");

        fragment("A3-DODGE", 850, 700,
                 LoreFragment.AbilityUnlock.DODGE,
                 "Entry 067. Agile is the survivor. Dodge the killing blow.",
                 "resources/sprites/fragments/dodge.png");



        altar(3, 300, 692, "POWER_SURGE", "SIGHT_RESTRICTION", null);
        lastAltar().setConcealment(BreakableWallConcealment.of());

        altar(4, 760, 692, "POWER_SURGE", "SIGHT_RESTRICTION", null);
        lastAltar().setConcealment(GhostWallConcealment.of());




        phantomBlock(420, 580, 64, 32, "resources/sprites/hazards/phantom_block.png");


        lightMover(700, 540, LightLockedMover.MovePattern.CIRCULAR,
                   72, 4000, LightLockedMover.LightBehavior.FREEZE_ON_LIGHT, "resources/sprites/hazards/light_mover.png");


        spike(160, 724, 84, 24, "resources/sprites/hazards/corrupted_spike.png");
        spike(220, 724, 84, 24, "resources/sprites/hazards/corrupted_spike.png");
        spike(280, 724, 84, 24, "resources/sprites/hazards/corrupted_spike.png");
        spike(335, 724, 84, 24, "resources/sprites/hazards/corrupted_spike.png");
        spike(380, 724, 84, 24, "resources/sprites/hazards/corrupted_spike.png");
        spike(740, 724, 84, 24, "resources/sprites/hazards/corrupted_spike.png");
        spike(790, 724, 84, 24, "resources/sprites/hazards/corrupted_spike.png");



        corruptedWall(840, 320, 80, 160, 320, 320, "resources/sprites/hazards/corrupted_wall.png");




        portal(920, 320);

        return finish();
    }
}
