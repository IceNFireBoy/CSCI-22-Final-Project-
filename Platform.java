/**
 * Represents a solid platform tile that players can stand and walk on.
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
import java.awt.image.*;
import java.util.*;
public class Platform extends GameElement {

    public enum PlatformType {
        BRICK,
        SLIDE,
        SPRING,
        WALL,

        CRUMBLE,

        INVISIBLE,

        MIMIC
    }

    public static final int TILE_SIZE = 32;

    public static final float SPRING_BOUNCE_FORCE = 18f;

    private static final Color FALLBACK_FILL   = new Color(0x2a, 0x2a, 0x35);

    private static final Color SPRING_FILL     = new Color(0x3a, 0x4a, 0x5a);

    private static final Color FALLBACK_BORDER = new Color(0x4a, 0x4a, 0x58);

    private PlatformType type;

    private int budgetCost;

    private boolean lit;

    private long crumbleTimer = 0;
    private boolean crumbleStarted = false;
    private float shakeOffsetX = 0;
    private float shakeOffsetY = 0;
    private float fallVelocity = 0;
    private boolean falling = false;
    public boolean fullyGone = false;
    private static final int CRUMBLE_SHAKE_DURATION = 2000;
    private static final int CRACK_STAGE_1 = 700;
    private static final int CRACK_STAGE_2 = 1400;
    private Random rand = new Random();

    private boolean mimicTriggered;

    private long mimicStartTime;

    private static final long MIMIC_DELAY_MS = 800L;

    private boolean solid;

    public Platform(PlatformType type, int x, int y) {
        super(x, y, defaultWidth(type), defaultHeight(type));
        this.type             = type;
        this.budgetCost       = resolveBudgetCost(type);
        this.lit              = false;
        this.solid            = true;
        this.mimicTriggered   = false;
        this.mimicStartTime   = -1L;
    }

    private static int defaultWidth(PlatformType type) {
        switch (type) {
            case SLIDE:   return 64;
            case SPRING:  return 24;
            case WALL:    return 16;
            default:      return 32;
        }
    }

    private static int defaultHeight(PlatformType type) {
        switch (type) {
            case SPRING:  return 24;
            case WALL:    return 64;
            default:      return 16;
        }
    }

    public Platform(PlatformType type, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.type             = type;
        this.budgetCost       = resolveBudgetCost(type);
        this.lit              = false;
        this.solid            = true;
        this.mimicTriggered   = false;
        this.mimicStartTime   = -1L;
    }

    private int resolveBudgetCost(PlatformType type) {
        switch (type) {
            case BRICK:     return 1;
            case SLIDE:     return 2;
            case SPRING:    return 3;
            case WALL:      return 2;
            case CRUMBLE:   return 2;
            case INVISIBLE: return 4;
            case MIMIC:     return 5;
            default:        return 1;
        }
    }

    @Override
    public void update(long deltaMs) {

        if (type == PlatformType.CRUMBLE && crumbleStarted && !fullyGone) {
            crumbleTimer += deltaMs;
            if (!falling) {
                float intensity = (float) crumbleTimer / CRUMBLE_SHAKE_DURATION;
                float maxShake = 2.0f + intensity * 3.0f;
                shakeOffsetX = (rand.nextFloat() - 0.5f) * maxShake * 2;
                shakeOffsetY = (rand.nextFloat() - 0.5f) * maxShake;
                if (crumbleTimer >= CRUMBLE_SHAKE_DURATION) {
                    falling = true;
                    fallVelocity = 0;
                    solid = false;
                    setActive(false);
                }
            } else {
                fallVelocity += 0.8f;
                y += (int) fallVelocity;
                shakeOffsetX = (rand.nextFloat() - 0.5f) * 6;
                if (y > 900) {
                    fullyGone = true;
                }
            }
            return;
        }

        if (!solid) return;

        long now = System.currentTimeMillis();

        if (type == PlatformType.MIMIC && mimicTriggered) {
            if ((now - mimicStartTime) >= MIMIC_DELAY_MS) {
                solid = false;
            }
        }
    }

    public void startCrumbling() {
        if (type == PlatformType.CRUMBLE && !crumbleStarted) {
            crumbleStarted = true;
        }
    }

    @Override
    public void render(Graphics2D g) {
        switch (type) {
            case CRUMBLE: {
                if (fullyGone) return;
                int drawX = (int)(x + shakeOffsetX);
                int drawY = (int)(y + shakeOffsetY);
                BufferedImage crumbleSprite = SpriteLoader.getInstance().load("resources/sprites/tiles/tile_crumble.png");
                if (crumbleSprite != null) {
                    g.drawImage(crumbleSprite, drawX, drawY, width, height, null);
                } else {
                    float darken = crumbleStarted ? Math.min(1.0f, (float)crumbleTimer / CRUMBLE_SHAKE_DURATION) : 0;
                    int baseR = (int)(0x2a + (0x1a - 0x2a) * darken);
                    int baseG = (int)(0x2a + (0x10 - 0x2a) * darken);
                    int baseB = (int)(0x35 + (0x10 - 0x35) * darken);
                    g.setColor(new Color(baseR, baseG, baseB));
                    g.fillRoundRect(drawX, drawY, width, height, 4, 4);
                    int strokeR = (int)(0x4a + (0x8b - 0x4a) * darken);
                    g.setColor(new Color(strokeR, 0x2a, 0x2a));
                    g.setStroke(new BasicStroke(0.5f));
                    g.drawRoundRect(drawX, drawY, width, height, 4, 4);
                }

                if (crumbleStarted && crumbleTimer >= CRACK_STAGE_1) {
                    g.setColor(new Color(0x8b, 0x2a, 0x2a, 180));
                    g.setStroke(new BasicStroke(0.8f));
                    g.drawLine(drawX + 6, drawY + 2, drawX + 14, drawY + 12);
                }

                if (crumbleStarted && crumbleTimer >= CRACK_STAGE_2) {
                    g.setColor(new Color(0x8b, 0x2a, 0x2a, 220));
                    g.setStroke(new BasicStroke(0.8f));
                    g.drawLine(drawX + 22, drawY + 1, drawX + 16, drawY + 14);
                    g.setColor(new Color(0x1a, 0x0a, 0x0a, 200));
                    g.fillRect(drawX + 18, drawY, 5, 4);
                }

                if (falling) {
                    g.setColor(new Color(0x1a, 0x10, 0x10));
                    g.fillRect(drawX, drawY, width/2 - 1, height);
                    g.fillRect(drawX + width/2 + 1, drawY + 2, width/2 - 1, height);
                    g.setColor(new Color(0x8b, 0x20, 0x20, 150));
                    g.fillRect(drawX + width/2 - 1, drawY, 2, height);
                }
                return;
            }
            case BRICK:  renderSprite(g, "resources/sprites/tiles/tile_brick.png",  FALLBACK_FILL); return;
            case SLIDE:  renderSprite(g, "resources/sprites/tiles/tile_slide.png",  FALLBACK_FILL); return;
            case SPRING: renderSprite(g, "resources/sprites/tiles/tile_spring.png", SPRING_FILL);  return;
            case WALL:   renderSprite(g, "resources/sprites/tiles/tile_wall.png",   FALLBACK_FILL); return;
            case MIMIC:  renderSprite(g, "resources/sprites/tiles/tile_mimic.png",  FALLBACK_FILL); return;
            case INVISIBLE:
                if (!lit) {
                    g.setColor(new Color(0x2a, 0x2a, 0x55, 40));
                    g.fillRoundRect(x, y, width, height, 4, 4);
                    return;
                }

                g.setColor(new Color(0x3a, 0x3a, 0x88));
                g.fillRoundRect(x, y, width, height, 4, 4);
                g.setColor(new Color(0x4a, 0x4a, 0x58));
                g.setStroke(new BasicStroke(0.5f));
                g.drawRoundRect(x, y, width, height, 4, 4);
                return;
            default:
                g.setColor(new Color(0x2a, 0x2a, 0x35));
                g.fillRoundRect(x, y, width, height, 4, 4);
                g.setColor(new Color(0x4a, 0x4a, 0x58));
                g.setStroke(new BasicStroke(0.5f));
                g.drawRoundRect(x, y, width, height, 4, 4);
        }
    }

    private void renderSprite(Graphics2D g, String spritePath, Color fallbackFill) {
        BufferedImage sprite = SpriteLoader.getInstance().load(spritePath);
        if (sprite != null) {
            g.drawImage(sprite, x, y, width, height, null);
        } else {
            g.setColor(fallbackFill);
            g.fillRoundRect(x, y, width, height, 4, 4);
            g.setColor(FALLBACK_BORDER);
            g.setStroke(new BasicStroke(0.5f));
            g.drawRoundRect(x, y, width, height, 4, 4);
        }
    }

    public PlatformType getType() {
        return type;
    }

    public int getBudgetCost() {
        return budgetCost;
    }

    public void setBudgetCost(int budgetCost) {
        this.budgetCost = budgetCost;
    }

    public boolean isLit() {
        return lit;
    }

    public void setLit(boolean lit) {
        this.lit = lit;
    }

    public boolean isSolid() {
        return solid;
    }

    public void setSolid(boolean solid) {
        this.solid = solid;
    }

    public boolean isMimicTriggered() {
        return mimicTriggered;
    }

    public void setMimicTriggered(boolean mimicTriggered) {
        this.mimicTriggered = mimicTriggered;
    }

    public long getMimicStartTime() {
        return mimicStartTime;
    }

    public void setMimicStartTime(long mimicStartTime) {
        this.mimicStartTime = mimicStartTime;
    }
}
