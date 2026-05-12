/**
 * Base class and shared utilities for generating level layouts.
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
public abstract class LevelGenerator {

    protected final List<GameElement> elements = new ArrayList<>();

    protected final LevelState state = new LevelState();

    private Altar lastAltar;

    private LoreFragment lastFragment;

    public abstract LevelRegistry.LoadResult build();

    protected void setPhase(LevelState.GamePhase phase) {
        state.currentPhase = phase;
    }

    protected void setBlockBudget(int blocks) {
        state.blockBudget = blocks;
    }

    protected void setSpawn(int x, int y) {
        this.spawnX = x;
        this.spawnY = y;
    }

    private int spawnX = -1;

    private int spawnY = -1;

    public int getSpawnX() { return spawnX; }

    public int getSpawnY() { return spawnY; }

    protected void platform(Platform.PlatformType type, int x, int y) {
        elements.add(new Platform(type, x, y));
    }

    protected void platform(Platform.PlatformType type, int x, int y, int w, int h) {
        elements.add(new Platform(type, x, y, w, h));
    }

    protected void brickRow(int startX, int y, int count) {
        brickRow(startX, y, count, 32);
    }

    protected void brickRow(int startX, int y, int count, int gap) {
        for (int i = 0; i < count; i++) {
            elements.add(new Platform(Platform.PlatformType.BRICK,
                                     startX + i * gap, y));
        }
    }

    protected void fragment(String id, int x, int y,
                            LoreFragment.AbilityUnlock unlock,
                            String body, String spritePath) {
        LoreFragment frag = new LoreFragment(id, body, unlock, x, y);
        if (spritePath != null) frag.setSpritePath(spritePath);
        elements.add(frag);
        this.lastFragment = frag;
    }

    protected LoreFragment lastFragment() { return lastFragment; }

    protected void altar(int id, int x, int y, String opt1, String opt2, String spritePath) {
        Altar a = new Altar(id, x, y, opt1, opt2);
        if (spritePath != null) a.setSpritePath(spritePath);
        elements.add(a);
        this.lastAltar = a;

        Map<String, Object> params = new HashMap<>();
        params.put("altarId", id);
        params.put("option1", opt1);
        params.put("option2", opt2);
        elements.add(new Trigger("ALTAR", x, y, params));
    }

    protected Altar lastAltar() { return lastAltar; }

    protected void portal(int x, int y) {
        elements.add(new Portal(x, y));
    }

    protected void portal(int x, int y, int w, int h, String spritePath) {
        Portal p = new Portal(x, y);
        if (spritePath != null) p.setSpritePath(spritePath);
        elements.add(p);
    }

    protected void spike(int x, int y) {
        elements.add(new CorruptedSpike(x, y));
    }

    protected void spike(int x, int y, int w, int h, String spritePath) {
        CorruptedSpike s = new CorruptedSpike(x, y, w, h);
        if (spritePath != null) s.setSpritePath(spritePath);
        elements.add(s);
    }

    protected void corruptedWall(int x, int y, int w, int h,
                                 int triggerW, int triggerH, String spritePath) {
        CorruptedWall cw = new CorruptedWall(x, y, w, h, triggerW, triggerH);
        if (spritePath != null) cw.setSpritePath(spritePath);
        elements.add(cw);
    }

    protected void phantomBlock(int x, int y, int w, int h, String spritePath) {
        PhantomBlock pb = new PhantomBlock(x, y, w, h);
        if (spritePath != null) pb.setSpritePath(spritePath);
        elements.add(pb);
    }

    protected void phantomBlock(int x, int y) {
        elements.add(new PhantomBlock(x, y, 32, 32));
    }

    protected void lightMover(int x, int y, LightLockedMover.MovePattern pattern,
                              int amplitude, int periodMs,
                              LightLockedMover.LightBehavior behaviour,
                              String spritePath) {
        LightLockedMover m = new LightLockedMover(x, y, pattern, amplitude, periodMs, behaviour);
        if (spritePath != null) m.setSpritePath(spritePath);
        elements.add(m);
    }

    protected void lightMover(int x, int y, LightLockedMover.MovePattern pattern) {
        elements.add(new LightLockedMover(x, y, pattern, 96, 3000,
                                          LightLockedMover.LightBehavior.FREEZE_ON_LIGHT));
    }

    protected void trigger(String type, int x, int y, Map<String, Object> params, String spritePath) {
        Trigger t = new Trigger(type, x, y, params != null ? params : new HashMap<>());
        if (spritePath != null) t.setSpritePath(spritePath);
        elements.add(t);
    }

    protected LevelRegistry.LoadResult finish() {
        return new LevelRegistry.LoadResult(elements, state);
    }
}
