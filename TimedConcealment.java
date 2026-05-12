/**
 * A concealment effect that automatically reveals itself after a set duration.
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
public class TimedConcealment extends AltarConcealment {

    private final long durationMs;

    private long firstPolledAtMs = -1L;

    public TimedConcealment(long durationMs) {
        this.durationMs = Math.max(0L, durationMs);
    }

    public static AltarConcealment of(long durationMs) {
        return new TimedConcealment(durationMs);
    }

    @Override
    public boolean isRevealed(LevelState state, GameStarter context) {
        long now = System.currentTimeMillis();
        if (firstPolledAtMs < 0L) firstPolledAtMs = now;
        return (now - firstPolledAtMs) < durationMs;
    }
}
