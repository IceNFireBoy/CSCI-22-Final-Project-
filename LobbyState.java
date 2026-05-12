/**
 * Holds the current lobby state including role selections and ready flags.
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
public class LobbyState {

    public boolean wandererTaken     = false;

    public boolean apprenticeTaken   = false;

    public boolean wandererHovered   = false;

    public boolean apprenticeHovered = false;

    public boolean isReconnectSession = false;

    public String vacantRole = null;

    public void applyMessage(String msg) {
        String[] p = msg.split("\\|");
        if (p.length >= 5) {
            wandererTaken     = "1".equals(p[1]);
            apprenticeTaken   = "1".equals(p[2]);
            wandererHovered   = "1".equals(p[3]);
            apprenticeHovered = "1".equals(p[4]);
        }

    }
}
