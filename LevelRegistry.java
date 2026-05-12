



































import java.util.*;
public class LevelRegistry {





    private LevelRegistry() {  }

















    public static LoadResult load(int levelNum, long seed) {
        switch (levelNum) {
            case 1: return new Act1Level().build();
            case 2: return new Act2Level().build();
            case 3: return new Act3Level().build();
            case 4: return buildBossArena(seed);
            default:
                System.err.println("LevelRegistry: unknown level " + levelNum + "; returning empty result");
                return new LoadResult(new ArrayList<>(), new LevelState());
        }
    }














    private static LoadResult buildBossArena(long seed) {
        BossArenaGenerator gen = new BossArenaGenerator();
        List<GameElement> arena = gen.generate(seed);
        LevelState state = new LevelState();
        state.currentLevel = 4;
        state.currentPhase = LevelState.GamePhase.BOSS;
        return new LoadResult(arena, state);
    }











    public static class LoadResult {


        public final List<GameElement> elements;


        public final LevelState state;







        public LoadResult(List<GameElement> elements, LevelState state) {
            this.elements = elements;
            this.state    = state;
        }
    }
}
