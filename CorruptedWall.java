/**
 * Implements a corrupted wall tile that blocks player movement.
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
public class CorruptedWall extends Hazard implements SpriteOverridable {

    public static final long WARNING_DURATION_MS = 1000L;
    public static final int  FALL_SPEED          = 9;
    public static final int  IMPACT_DAMAGE       = 3;

    private static final int SHAKE_AMPLITUDE = 4;

    private static final Color WALL_FILL_IDLE    = new Color(0x30, 0x18, 0x20);
    private static final Color WALL_FILL_WARNING = new Color(0x70, 0x18, 0x20);
    private static final Color WALL_OUTLINE      = new Color(0x10, 0x06, 0x0C);

    public enum WallPhase { IDLE, WARNING, FALLING, LANDED }

    private final int triggerW;
    private final int triggerH;
    private final int initialY;

    private WallPhase phase;
    private long warningStartMs;
    private int  shakeOffsetX;
    private int  shakeOffsetY;
    private int  fallTargetY;

    private String spritePath;

    public CorruptedWall(int x, int y, int w, int h, int triggerW, int triggerH) {
        super(x, y, w, h, IMPACT_DAMAGE, 1);
        this.triggerW    = Math.max(triggerW, w);
        this.triggerH    = Math.max(triggerH, h);
        this.initialY    = y;
        this.phase       = WallPhase.IDLE;
        this.warningStartMs = 0L;
        this.shakeOffsetX = 0;
        this.shakeOffsetY = 0;
        this.fallTargetY  = y;
        this.spritePath   = null;
    }

    public Rectangle getTriggerZone() {
        int tx = x + width / 2 - triggerW / 2;
        int ty = initialY + height / 2 - triggerH / 2;
        return new Rectangle(tx, ty, triggerW, triggerH);
    }

    public void checkTrigger(Player player) {
        if (phase == WallPhase.LANDED) return;

        long now = System.currentTimeMillis();
        if (phase == WallPhase.IDLE) {
            if (player.getBounds().intersects(getTriggerZone())) {
                phase = WallPhase.WARNING;
                warningStartMs = now;

                fallTargetY = player.getY() + player.getBounds().height - height;
            }
            return;
        }
        if (phase == WallPhase.WARNING) {
            if (now - warningStartMs >= WARNING_DURATION_MS) {
                phase = WallPhase.FALLING;
                shakeOffsetX = 0;
                shakeOffsetY = 0;
            }
            return;
        }

    }

    @Override
    public void update(long deltaMs) {
        if (!active) return;

        switch (phase) {
            case WARNING:

                shakeOffsetX = (int) ((Math.random() - 0.5) * 2 * SHAKE_AMPLITUDE);
                shakeOffsetY = (int) ((Math.random() - 0.5) * 2 * SHAKE_AMPLITUDE);
                break;
            case FALLING:

                if (y < fallTargetY) {
                    y += FALL_SPEED;
                    if (y >= fallTargetY) {
                        y = fallTargetY;
                        phase = WallPhase.LANDED;
                    }
                } else {
                    phase = WallPhase.LANDED;
                }
                break;
            case IDLE:
            case LANDED:
            default:

                break;
        }
    }

    public boolean isDangerous() {
        return phase == WallPhase.FALLING || phase == WallPhase.LANDED;
    }

    public WallPhase getPhase() { return phase; }

    @Override
    public void render(Graphics2D g) {
        if (!active) return;

        int rx = x + (phase == WallPhase.WARNING ? shakeOffsetX : 0);
        int ry = y + (phase == WallPhase.WARNING ? shakeOffsetY : 0);
        if (spritePath != null
            && SpriteOverridable.tryDrawSprite(g, this, rx, ry, width, height)) return;

        if (spritePath == null) {
            java.awt.image.BufferedImage img = SpriteLoader.getInstance()
                .tryLoad("resources/sprites/hazards/wall.png");
            if (img != null) {
                g.drawImage(img, rx, ry, width, height, null);
                return;
            }
        }

        Color fill;
        switch (phase) {
            case WARNING:
            case FALLING:
                fill = WALL_FILL_WARNING;
                break;
            case LANDED:
                fill = new Color(0x40, 0x10, 0x18);
                break;
            case IDLE:
            default:
                fill = WALL_FILL_IDLE;
        }
        g.setColor(fill);
        g.fillRect(rx, ry, width, height);
        g.setColor(WALL_OUTLINE);
        g.drawRect(rx, ry, width, height);

        g.setColor(WALL_OUTLINE);
        for (int row = 16; row < height; row += 16) {
            g.drawLine(rx, ry + row, rx + width, ry + row);
        }
        for (int col = 16; col < width; col += 32) {
            int offset = ((col / 32) % 2 == 0) ? 0 : 16;
            for (int row = 0; row < height; row += 16) {
                if ((row / 16) % 2 == 0) {
                    g.drawLine(rx + col, ry + row, rx + col, ry + row + 16);
                } else if (col + offset < width) {
                    g.drawLine(rx + col + offset, ry + row, rx + col + offset, ry + row + 16);
                }
            }
        }
    }

    @Override public void setSpritePath(String path) { this.spritePath = path; }
    @Override public String getSpritePath() { return spritePath; }
}
