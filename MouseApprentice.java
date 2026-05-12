/**
 * Handles mouse-driven movement and light-orb control for the Apprentice.
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
public class MouseApprentice {

    private static MouseApprentice instance;

    private int mouseX, mouseY;
    private boolean leftPressed, rightPressed;

    private int lightRadius = 120;
    private boolean lightActive = true;
    private boolean forcedOff = false;

    private static final int MIN_RADIUS = 20;
    private static final int MAX_RADIUS = 180;

    public static synchronized MouseApprentice getInstance() {
        if (instance == null) instance = new MouseApprentice();
        return instance;
    }

    private MouseApprentice() {}

    public void updatePosition(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    public void setLeftPressed(boolean b) { leftPressed = b; }

    public void setRightPressed(boolean b) { rightPressed = b; }

    public void adjustRadius(int delta) {
        lightRadius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, lightRadius + delta));
    }

    public void toggleLight() {
        if (lightActive) {
            lightActive = false;
        } else {
            if (GameSession.getInstance().getLightBattery() > 5f) {
                lightActive = true;
                forcedOff = false;
            }
        }
    }

    public void setLightActive(boolean b) {
        lightActive = b;
        if (b) forcedOff = false;
    }

    public void setLightForcedOff(boolean b) { forcedOff = b; }

    public boolean isLightActive() { return lightActive && !forcedOff; }

    public int getX() { return mouseX; }

    public int getY() { return mouseY; }

    public boolean isLeftPressed() { return leftPressed; }

    public boolean isRightPressed() { return rightPressed; }

    public int getLightRadius() { return lightRadius; }

    public java.awt.Point getPosition() { return new java.awt.Point(mouseX, mouseY); }
}
