

















































public class Act1Level extends LevelGenerator {

    @Override
    public LevelRegistry.LoadResult build() {


        setPhase(LevelState.GamePhase.ACT1);
        setBlockBudget(12);
        setSpawn(60, 652);



        brickRow(0, 740, 32);



        platform(Platform.PlatformType.CRUMBLE, 480, 660);
        platform(Platform.PlatformType.CRUMBLE, 560, 660, 96, 16);
        platform(Platform.PlatformType.CRUMBLE, 580, 660);
        platform(Platform.PlatformType.CRUMBLE, 660, 560, 96, 16);





        platform(Platform.PlatformType.WALL, 410, 596, 16, 64);
        platform(Platform.PlatformType.WALL, 820, 450, 16, 84);
        corruptedWall(160, 570, 80, 90, 320, 320,  "resources/sprites/hazards/wall.png");


        platform(Platform.PlatformType.CRUMBLE, 880, 480);
        platform(Platform.PlatformType.CRUMBLE, 912, 480);



        fragment("A1-INTRO", 244, 716,
                 LoreFragment.AbilityUnlock.NONE,
                 "Entry 001. The first platform held. The apprentice is learning the grammar of light.",
                 null);



        portal(920, 400);

        return finish();
    }
}
