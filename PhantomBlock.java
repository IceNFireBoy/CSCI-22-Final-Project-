/**
 * A temporary block placed by the Apprentice that fades after a fixed duration.
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
public class PhantomBlock extends Platform implements SpriteOverridable {

    public static final int DEFAULT_MAX_CHARGE = 1500;

    public static final int DEFAULT_CHARGE_RATE = 18;

    public static final int DEFAULT_DECAY_RATE = 5;

    private static final Color BLOCK_FILL    = new Color(0x88, 0xB0, 0xD8);
    private static final Color BLOCK_OUTLINE = new Color(0x44, 0x60, 0x80);
    private static final Color BLOCK_HIGHLIGHT = new Color(0xE0, 0xEC, 0xF6);

    private int charge;
    private final int maxCharge;
    private final int chargeRate;
    private final int decayRate;
    private float lightIntensity;

    private String spritePath;

    public PhantomBlock(int x, int y, int w, int h) {
        this(x, y, w, h, DEFAULT_MAX_CHARGE, DEFAULT_CHARGE_RATE, DEFAULT_DECAY_RATE);
    }

    public PhantomBlock(int x, int y, int w, int h,
                        int maxCharge, int chargeRate, int decayRate) {
        super(PlatformType.BRICK, x, y, w, h);
        this.charge = 0;
        this.maxCharge = maxCharge;
        this.chargeRate = chargeRate;
        this.decayRate = decayRate;
        this.lightIntensity = 0f;
        this.spritePath = null;
        setSolid(false);
    }

    public void setLightIntensity(float intensity) {
        this.lightIntensity = Math.max(0f, Math.min(1f, intensity));
    }

    public float getLightIntensity() { return lightIntensity; }

    public int getCharge() { return charge; }

    @Override
    public void update(long deltaMs) {

        int ticks = Math.max(1, (int) (deltaMs / 16));
        if (lightIntensity > 0f) {

            int gain = Math.round(chargeRate * lightIntensity) * ticks;
            charge = Math.min(maxCharge, charge + gain);
        } else {

            charge = Math.max(0, charge - decayRate * ticks);
        }

        setSolid(charge > 0);
    }

    @Override
    public void render(Graphics2D g) {
        if (!isActive()) return;
        if (charge <= 0) return;

        float alpha = Math.min(1f, (float) charge / (float) maxCharge);
        Composite oldComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        boolean drewSprite = false;
        if (spritePath != null) {
            drewSprite = SpriteOverridable.tryDrawSprite(g, this, x, y, width, height);
        } else {

            java.awt.image.BufferedImage img = SpriteLoader.getInstance()
                .tryLoad("resources/sprites/hazards/phantom.png");
            if (img != null) {
                g.drawImage(img, x, y, width, height, null);
                drewSprite = true;
            }
        }
        if (!drewSprite) {

            g.setColor(BLOCK_FILL);
            g.fillRect(x, y, width, height);
            g.setColor(BLOCK_OUTLINE);
            g.drawRect(x, y, width, height);

            if (alpha > 0.7f) {
                g.setColor(BLOCK_HIGHLIGHT);
                g.fillRect(x + 2, y + 2, width - 4, 3);
            }
        }

        g.setComposite(oldComposite);
    }

    @Override public void setSpritePath(String path) { this.spritePath = path; }
    @Override public String getSpritePath() { return spritePath; }
}
