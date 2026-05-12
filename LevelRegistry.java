/**
 * Maps level numbers to their corresponding generator implementations.
 *
 * @author Marxus Antonio L. Magisa (253602) & Antonio Sebastian B. Pasia (254505)
 * @version May 12, 2026
 *
 * I have not discussed the Java language code in my program
 * with anyone other than my instructor or the teaching assistants
 * assigned to this course.
 *
 * I have not used Java language code obtained from another student,
 * or any other unauthorized source, either modified or unmodified.
 * If any Java language code or documentation used in my program
 * was obtained from another source, such as a textbook or website,
 * that has been clearly noted with a proper citation in the comments
 * of my program.
 */
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
