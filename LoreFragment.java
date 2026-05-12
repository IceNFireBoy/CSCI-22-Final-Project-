/**
 * Defines lore-fragment collectibles and the ability unlocks they grant.
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
public class LoreFragment extends GameElement implements SpriteOverridable {

    public enum AbilityUnlock {

        NONE,

        MELEE,

        PROJECTILE,

        DODGE,

        WALL_CLING,

        SHADOW_DASH,

        EMBER,

        IRON,

        RADIANT_COLLAPSE,

        VEIL,

        ECHO,

        TETHER,

        SHADOW_STEP
    }

    private String fragmentID;

    private String bodyText;

    private AbilityUnlock unlock;

    private boolean collected;

    private boolean gated;

    private String spritePath;

    public LoreFragment(String fragmentID, String bodyText, AbilityUnlock unlock,
                        int x, int y) {
        super(x, y, 20, 20);
        this.fragmentID = fragmentID;
        this.bodyText   = bodyText;
        this.unlock     = unlock;
        this.collected  = false;
        this.gated      = false;
        this.spritePath = null;
    }

    @Override
    public void update(long deltaMs) {

    }

    @Override
    public void render(Graphics2D g) {
        if (collected) return;

        if (SpriteOverridable.tryDrawSprite(g, this, x, y, width, height)) return;

        if (spritePath == null) {
            String conv = defaultSpritePath(unlock);
            if (conv != null) {
                java.awt.image.BufferedImage img = SpriteLoader.getInstance().tryLoad(conv);
                if (img != null) {
                    g.drawImage(img, x, y, width, height, null);
                    return;
                }
            }
        }

        boolean isCombat = (unlock != AbilityUnlock.NONE);

        long now = System.currentTimeMillis();
        float pulse = (float)(Math.sin(now * 0.004) * 0.3 + 0.7);
        int glowSize = (int)(pulse * 8);
        if (isCombat) {
            g.setColor(new Color(0xf0, 0xcc, 0x7a, 50));
        } else {
            g.setColor(new Color(0xc9, 0xa8, 0x4c, 40));
        }
        g.fillOval(x - glowSize, y - glowSize,
                   width + glowSize * 2, height + glowSize * 2);

        int cx = x + width / 2;
        int cy = y + height / 2;
        int r  = width / 2;

        int[] xPoints = {cx, cx + r, cx + r / 2, cx, cx - r / 2, cx - r};
        int[] yPoints = {cy - r, cy - r / 3, cy + r / 2, cy + r, cy + r / 2, cy - r / 3};
        Polygon shard = new Polygon(xPoints, yPoints, 6);

        if (isCombat) {
            g.setColor(new Color(0xf0, 0xcc, 0x7a));
        } else {
            g.setColor(new Color(0xc9, 0xa8, 0x4c));
        }
        g.fill(shard);

        g.setColor(new Color(0xff, 0xff, 0xf0, 180));
        g.setStroke(new BasicStroke(0.8f));
        g.drawLine(cx - r / 3, cy - r / 2,
                   cx + r / 4, cy);
    }

    public void setCollected(boolean collected) {
        this.collected = collected;
    }

    public String getFragmentID() {
        return fragmentID;
    }

    public String getBodyText() {
        return bodyText;
    }

    public AbilityUnlock getUnlock() {
        return unlock;
    }

    public boolean isCollected() {
        return collected;
    }

    public void collect() {
        this.collected = true;
        setActive(false);
    }

    public boolean isGated() {
        return gated;
    }

    public void setGated(boolean gated) {
        this.gated = gated;
    }

    @Override
    public void setSpritePath(String path) {
        this.spritePath = path;
    }

    @Override
    public String getSpritePath() {
        return spritePath;
    }

    public static String defaultSpritePath(AbilityUnlock unlock) {
        if (unlock == null) return null;
        switch (unlock) {

            case NONE:             return "resources/sprites/fragments/lore.png";
            case MELEE:            return "resources/sprites/fragments/melee.png";
            case PROJECTILE:       return "resources/sprites/fragments/projectile.png";
            case DODGE:            return "resources/sprites/fragments/dodge.png";
            case WALL_CLING:       return "resources/sprites/fragments/wall_cling.png";
            case SHADOW_DASH:      return "resources/sprites/fragments/shadow_dash.png";
            case EMBER:            return "resources/sprites/fragments/ember.png";
            case IRON:             return "resources/sprites/fragments/iron.png";
            case RADIANT_COLLAPSE: return "resources/sprites/fragments/radiant.png";
            case VEIL:             return "resources/sprites/fragments/veil.png";
            case ECHO:             return "resources/sprites/fragments/echo.png";
            case TETHER:           return "resources/sprites/fragments/tether.png";
            case SHADOW_STEP:      return "resources/sprites/fragments/shadow_step.png";

            default: return "resources/sprites/fragments/" + unlock.name().toLowerCase() + ".png";
        }
    }
}
