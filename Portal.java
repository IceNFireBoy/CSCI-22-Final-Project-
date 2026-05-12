/**
 * A portal trigger that transitions the player to the next level or boss arena.
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
import java.awt.geom.*;
public class Portal extends GameElement implements SpriteOverridable {

    private static final int PORTAL_WIDTH  = 48;

    private static final int PORTAL_HEIGHT = 80;

    private static final Color FRAME_COLOR   = new Color(0xC9, 0xA8, 0x4C);

    private static final Color GLOW_COLOR    = new Color(0xF0, 0xCC, 0x7A);

    private static final Color OPENING_COLOR = new Color(0xFF, 0xF8, 0xE0);

    private long pulseTimer;

    private String spritePath;

    public Portal(int x, int y) {
        super(x, y, PORTAL_WIDTH, PORTAL_HEIGHT);
        this.pulseTimer = 0L;
        this.spritePath = null;
    }

    @Override
    public void update(long deltaMs) {
        pulseTimer += deltaMs;
    }

    @Override
    public void render(Graphics2D g) {
        if (!active) return;

        if (SpriteOverridable.tryDrawSprite(g, this, x, y, width, height)) return;

        if (spritePath == null) {
            java.awt.image.BufferedImage img = SpriteLoader.getInstance()
                .tryLoad("resources/sprites/portals/portal.png");
            if (img != null) {
                g.drawImage(img, x, y, width, height, null);
                return;
            }
        }

        float pulse = (float)(Math.sin(pulseTimer * 0.003) * 0.3 + 0.7);

        int glowSize = (int)(pulse * 6);
        g.setColor(new Color(0xF0, 0xCC, 0x7A, (int)(40 * pulse)));
        g.fillRoundRect(x - glowSize, y - glowSize,
                width + glowSize * 2, height + glowSize * 2, 12, 12);

        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(3.0f));
        g.setColor(FRAME_COLOR);

        g.fillRect(x, y + 20, 8, height - 20);

        g.fillRect(x + width - 8, y + 20, 8, height - 20);

        g.fill(new Arc2D.Double(x, y, width, 40, 0, 180, Arc2D.PIE));

        int innerPad = 8;
        int openAlpha = (int)(180 * pulse + 40);
        g.setColor(new Color(0xFF, 0xF8, 0xE0, Math.min(255, openAlpha)));
        g.fillRect(x + innerPad, y + 20, width - innerPad * 2, height - 20);

        g.setStroke(new BasicStroke(1.5f));
        g.setColor(GLOW_COLOR);
        g.drawRect(x + innerPad, y + 20, width - innerPad * 2, height - 20);
        g.drawArc(x + 2, y + 2, width - 4, 36, 0, 180);

        g.setStroke(oldStroke);
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(x + 8, y + 20, width - 16, height - 20);
    }

    @Override
    public void setSpritePath(String path) {
        this.spritePath = path;
    }

    @Override
    public String getSpritePath() {
        return spritePath;
    }
}
