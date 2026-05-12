/**
 * Implements a spike hazard element that deals damage on contact.
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
public class CorruptedSpike extends Hazard implements SpriteOverridable {

    public static final int DEFAULT_WIDTH  = 32;
    public static final int DEFAULT_HEIGHT = 16;

    private static final int CONTACT_DAMAGE_COOLDOWN_MS = 800;

    private static final Color SPIKE_FILL    = new Color(0x60, 0x18, 0x28);
    private static final Color SPIKE_OUTLINE = new Color(0x20, 0x08, 0x10);
    private static final Color BASE_COLOUR   = new Color(0x2C, 0x10, 0x18);

    private long nextDamageMs;

    private String spritePath;

    public CorruptedSpike(int x, int y) {
        this(x, y, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public CorruptedSpike(int x, int y, int w, int h) {
        super(x, y, w, h,  1,  1);
        this.nextDamageMs = 0L;
        this.spritePath = null;
    }

    public boolean tryDamage(Player player) {
        long now = System.currentTimeMillis();
        if (now < nextDamageMs) return false;
        nextDamageMs = now + CONTACT_DAMAGE_COOLDOWN_MS;
        player.takeDamage(damage);
        return true;
    }

    @Override
    public void update(long deltaMs) {

    }

    @Override
    public void render(Graphics2D g) {
        if (!active) return;
        if (SpriteOverridable.tryDrawSprite(g, this, x, y, width, height)) return;

        if (spritePath == null) {
            java.awt.image.BufferedImage img = SpriteLoader.getInstance()
                .tryLoad("resources/sprites/hazards/spike.png");
            if (img != null) {
                g.drawImage(img, x, y, width, height, null);
                return;
            }
        }

        g.setColor(BASE_COLOUR);
        g.fillRect(x, y + height * 2 / 3, width, height / 3);

        int toothCount = Math.max(1, width / 16);
        int toothW = width / toothCount;
        for (int i = 0; i < toothCount; i++) {
            int leftX = x + i * toothW;
            Polygon tooth = new Polygon(
                new int[]{ leftX, leftX + toothW / 2, leftX + toothW },
                new int[]{ y + height, y, y + height },
                3
            );
            g.setColor(SPIKE_FILL);
            g.fillPolygon(tooth);
            g.setColor(SPIKE_OUTLINE);
            g.drawPolygon(tooth);
        }
    }

    @Override public void setSpritePath(String path) { this.spritePath = path; }
    @Override public String getSpritePath() { return spritePath; }
}
