/**
 * Holds the live state of the current level including phase and active elements.
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
public class LevelState {

    public enum GamePhase {

        MENU,
        LOBBY,
        ACT1,
        ACT2,
        ACT3,
        BOSS,
        FINAL_CORRIDOR,
        CUTSCENE,
        PAUSED_WAITING,
        END_SCREEN
    }

    public int currentLevel;

    public int blockBudget;

    public GamePhase currentPhase;

    public int[] coreHealth = {3, 3, 3, 3};

    public float remoteWandererX = -1;

    public float remoteWandererY = -1;

    public String remoteWandererState = "idle";

    public float remoteWandererHealth = 5;

    public int remoteLightX = 512;

    public int remoteLightY = 384;

    public int remoteLightRadius = 180;

    private boolean lightActive = true;

    public LevelState() {
        this.currentLevel = 1;
        this.blockBudget = 20;
        this.currentPhase = GamePhase.MENU;
    }

    public void advancePhase() { }

    public void setWandererPosition(float x, float y) {
        this.remoteWandererX = x;
        this.remoteWandererY = y;
    }

    public void setWandererState(String state) {
        this.remoteWandererState = (state != null) ? state : "idle";
    }

    public void setLightPosition(int x, int y) {
        this.remoteLightX = x;
        this.remoteLightY = y;
    }

    public void setLightRadius(int r) {
        this.remoteLightRadius = r;
    }

    public int getLightX() { return remoteLightX; }

    public int getLightY() { return remoteLightY; }

    public int getLightRadius() { return remoteLightRadius; }

    public void setLightActive(boolean b) { lightActive = b; }

    public boolean getLightActive() { return lightActive; }

    public void setWandererHealth(int h) { remoteWandererHealth = h; }

}
