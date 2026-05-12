/**
 * Handles pressure-plate activation and the concealment effects it controls.
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
import java.awt.*;
public class PressurePlateConcealment extends AltarConcealment {

    private final Rectangle plate;

    public PressurePlateConcealment(int plateX, int plateY, int plateW, int plateH) {
        this.plate = new Rectangle(plateX, plateY, plateW, plateH);
    }

    public static AltarConcealment of(int x, int y, int w, int h) {
        return new PressurePlateConcealment(x, y, w, h);
    }

    @Override
    public boolean isRevealed(LevelState state, GameStarter context) {
        if (context == null) return false;

        for (Platform pb : context.getPlacedBlocks()) {
            if (pb == null || !pb.isActive()) continue;
            if (pb.getBounds().intersects(plate)) return true;
        }
        return false;
    }
}
