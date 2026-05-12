/**
 * Represents an altar interactable that grants lore fragments to the Wanderer.
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
public class Altar extends GameElement implements SpriteOverridable {

    public static final int DEFAULT_WIDTH  = 48;
    public static final int DEFAULT_HEIGHT = 64;

    private final int altarId;

    private final String option1;

    private final String option2;

    private String spritePath;

    private AltarConcealment concealment;

    private boolean activated;

    public Altar(int id, int x, int y, String opt1, String opt2) {
        super(x, y, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        this.altarId = id;
        this.option1 = opt1;
        this.option2 = opt2;
        this.spritePath = null;
        this.concealment = null;
        this.activated = false;
    }

    @Override
    public void update(long deltaMs) {

    }

    @Override
    public void render(Graphics2D g) {

        if (SpriteOverridable.tryDrawSprite(g, this, x, y, width, height)) return;

        if (spritePath == null) {
            java.awt.image.BufferedImage img = SpriteLoader.getInstance()
                .tryLoad("resources/sprites/altars/stone.png");
            if (img != null) {
                g.drawImage(img, x, y, width, height, null);
                return;
            }
        }

        if (concealment != null && !concealment.isRevealed(null, null)) {

            return;
        }

        renderProceduralPedestal(g);
    }

    private void renderProceduralPedestal(Graphics2D g) {
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(0xD4, 0xAF, 0x37, 60));
        g.fillOval(x - 8, y + height - 16, width + 16, 32);

        g.setColor(new Color(0x2A, 0x2A, 0x32));
        g.fillRect(x, y + height / 4, width, height * 3 / 4);
        g.setColor(new Color(0x44, 0x44, 0x4E));
        g.fillRect(x - 2, y + height / 4 - 4, width + 4, 8);

        long t = System.currentTimeMillis();
        float pulse = (float) (0.6 + 0.4 * Math.sin(t * 0.004));
        int alpha = (int) (160 * pulse) + 80;
        g.setStroke(new BasicStroke(2.0f));
        g.setColor(new Color(0xF0, 0xCC, 0x7A, alpha));
        int runeSize = Math.min(width - 12, 28);
        int runeX = x + (width - runeSize) / 2;
        int runeY = y + 4;
        g.drawOval(runeX, runeY, runeSize, runeSize);

        int cx = runeX + runeSize / 2;
        int cy = runeY + runeSize / 2;
        g.drawLine(cx - runeSize / 3, cy, cx + runeSize / 3, cy);
        g.drawLine(cx, cy - runeSize / 3, cx, cy + runeSize / 3);

        if (oldAA != null) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
    }

    @Override
    public void setSpritePath(String path) {
        this.spritePath = path;
    }

    @Override
    public String getSpritePath() {
        return spritePath;
    }

    public void setConcealment(AltarConcealment c) {
        this.concealment = c;
    }

    public AltarConcealment getConcealment() {
        return concealment;
    }

    public int getAltarId() { return altarId; }

    public String getOption1() { return option1; }

    public String getOption2() { return option2; }

    public boolean isActivated() { return activated; }

    public void setActivated(boolean a) {
        this.activated = a;
    }
}
