public class MouseApprentice {
    private static MouseApprentice instance;
    private int mouseX, mouseY;
    private boolean leftPressed, rightPressed;
    private int lightRadius = 120;
    private static final int MIN_RADIUS = 20;
    private static final int MAX_RADIUS = 180;

    public static synchronized MouseApprentice getInstance() {
        if (instance == null) instance = new MouseApprentice();
        return instance;
    }
    private MouseApprentice() {}

    public void updatePosition(int x, int y) { mouseX = x; mouseY = y; }
    public void setLeftPressed(boolean b) { leftPressed = b; }
    public void setRightPressed(boolean b) { rightPressed = b; }
    public void adjustRadius(int delta) {
        lightRadius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, lightRadius + delta));
    }

    public int getX() { return mouseX; }
    public int getY() { return mouseY; }
    public boolean isLeftPressed() { return leftPressed; }
    public boolean isRightPressed() { return rightPressed; }
    public int getLightRadius() { return lightRadius; }
    public java.awt.Point getPosition() { return new java.awt.Point(mouseX, mouseY); }
}
