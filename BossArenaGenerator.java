/**
 * Generates the final boss arena layout with cores, platforms, and spawn points.
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
public class BossArenaGenerator {

    public static final int ARENA_W = GameServer.ARENA_W;

    public static final int ARENA_H = GameServer.ARENA_H;

    public static final int CENTER_X = ARENA_W / 2;

    public static final int CENTER_Y = ARENA_H / 2;

    private static final int THRONE_WIDTH  = 128;

    private static final int THRONE_HEIGHT = 48;

    private static final int CORE_INSET = 512;

    private static final int PLATFORMS_PER_QUADRANT = 5;

    private static final int MIN_CORE_CLEARANCE = 200;

    private static final int MIN_THRONE_CLEARANCE = 260;

    private static final int WALL_THICKNESS = 24;

    public static final int BOSS_SPAWN_Y_OFFSET = 256;

    public BossArenaGenerator() {

    }

    public List<GameElement> generate(long seed) {
        List<GameElement> out = new ArrayList<>();

        out.add(new Platform(Platform.PlatformType.WALL,
                0, 0, ARENA_W, WALL_THICKNESS));

        out.add(new Platform(Platform.PlatformType.WALL,
                0, ARENA_H - WALL_THICKNESS, ARENA_W, WALL_THICKNESS));

        out.add(new Platform(Platform.PlatformType.WALL,
                0, 0, WALL_THICKNESS, ARENA_H));

        out.add(new Platform(Platform.PlatformType.WALL,
                ARENA_W - WALL_THICKNESS, 0, WALL_THICKNESS, ARENA_H));

        int[][] coreCorners = new int[][]{
                { CORE_INSET,           CORE_INSET            },
                { ARENA_W - CORE_INSET, CORE_INSET            },
                { CORE_INSET,           ARENA_H - CORE_INSET  },
                { ARENA_W - CORE_INSET, ARENA_H - CORE_INSET  }
        };
        for (int i = 0; i < 4; i++) {
            out.add(new Core(i, coreCorners[i][0], coreCorners[i][1]));
        }

        final int spawnSideCoreX = CENTER_X + 192;
        final int spawnSideCoreY = ARENA_H - BOSS_SPAWN_Y_OFFSET - 32;
        out.add(new Core(4, spawnSideCoreX, spawnSideCoreY));

        out.add(new Platform(Platform.PlatformType.WALL,
                CENTER_X - THRONE_WIDTH / 2,
                CENTER_Y - THRONE_HEIGHT / 2,
                THRONE_WIDTH,
                THRONE_HEIGHT));

        Random rng = new Random(seed);
        int attempts  = 0;
        int generated = 0;
        while (generated < PLATFORMS_PER_QUADRANT && attempts < 64) {
            attempts++;

            int qx = 320 + rng.nextInt(Math.max(1, CENTER_X - 560));
            int qy = 320 + rng.nextInt(Math.max(1, CENTER_Y - 500));

            if (!clearOfKeyPoints(qx, qy, coreCorners)) continue;

            addMirroredPlatform(out, qx,              qy);
            addMirroredPlatform(out, ARENA_W - qx,    qy);
            addMirroredPlatform(out, qx,              ARENA_H - qy);
            addMirroredPlatform(out, ARENA_W - qx,    ARENA_H - qy);
            generated++;
        }

        return out;
    }

    private static void addMirroredPlatform(List<GameElement> out, int x, int y) {
        out.add(new Platform(Platform.PlatformType.BRICK, x, y));
    }

    private static boolean clearOfKeyPoints(int x, int y, int[][] cores) {
        for (int[] c : cores) {
            int dx = c[0] - x, dy = c[1] - y;
            if (dx * dx + dy * dy < MIN_CORE_CLEARANCE * MIN_CORE_CLEARANCE) {
                return false;
            }
        }
        int dxc = CENTER_X - x, dyc = CENTER_Y - y;
        if (dxc * dxc + dyc * dyc < MIN_THRONE_CLEARANCE * MIN_THRONE_CLEARANCE) {
            return false;
        }
        return true;
    }
}
