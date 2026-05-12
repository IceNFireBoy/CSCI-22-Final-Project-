/**
 * Serializable full-game-state snapshot used for network synchronization.
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
public class SessionSnapshot {

    public int currentLevel;

    public String currentAct;

    public float wandererX;

    public float wandererY;

    public int wandererHealth;

    public int wandererLives;

    public float lightBattery;

    public int lightX;

    public int lightY;

    public int lightRadius;

    public boolean lightActive;

    public int blockBudget;

    public String currentBlockType;

    public java.util.List<String> placedBlocks = new java.util.ArrayList<>();

    public int[] coreHealth;

    public long capturedAt;
}
