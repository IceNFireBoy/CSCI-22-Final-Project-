













































import java.awt.*;
public class LightLockedMover extends Platform implements SpriteOverridable {






    public enum MovePattern {
        LINEAR_X,
        LINEAR_Y,
        CIRCULAR
    }


    public enum LightBehavior {
        FREEZE_ON_LIGHT,
        MOVE_ON_LIGHT
    }





    private static final Color BLOCK_FILL    = new Color(0xC8, 0xA0, 0x40);
    private static final Color BLOCK_OUTLINE = new Color(0x60, 0x40, 0x10);
    private static final Color FROZEN_TINT   = new Color(0xA0, 0xC0, 0xE0, 80);





    private final int originX;
    private final int originY;
    private final MovePattern pattern;
    private final int amplitude;
    private final int periodMs;

    private final LightBehavior behaviour;







    private long phaseMs;

    private boolean motionEnabled;

    private String spritePath;















    public LightLockedMover(int x, int y, MovePattern pattern, int amplitude,
                            int periodMs, LightBehavior behaviour) {
        super(PlatformType.BRICK, x, y, 64, 16);
        this.originX = x;
        this.originY = y;
        this.pattern = pattern;
        this.amplitude = amplitude;
        this.periodMs = Math.max(200, periodMs);
        this.behaviour = behaviour;
        this.phaseMs = 0L;


        this.motionEnabled = (behaviour == LightBehavior.FREEZE_ON_LIGHT);
        this.spritePath = null;
    }





    @Override
    public void setLit(boolean lit) {
        super.setLit(lit);





        switch (behaviour) {
            case FREEZE_ON_LIGHT: motionEnabled = !lit; break;
            case MOVE_ON_LIGHT:   motionEnabled = lit;  break;
        }
    }


    public boolean isMotionEnabled() { return motionEnabled; }





    @Override
    public void update(long deltaMs) {
        if (motionEnabled) {
            phaseMs = (phaseMs + deltaMs) % periodMs;
        }



        double t = (double) phaseMs / (double) periodMs;
        double angle = t * 2 * Math.PI;
        switch (pattern) {
            case LINEAR_X:
                this.x = originX + (int) Math.round(Math.sin(angle) * amplitude);
                this.y = originY;
                break;
            case LINEAR_Y:
                this.x = originX;
                this.y = originY + (int) Math.round(Math.sin(angle) * amplitude);
                break;
            case CIRCULAR:
                this.x = originX + (int) Math.round(Math.cos(angle) * amplitude);
                this.y = originY + (int) Math.round(Math.sin(angle) * amplitude);
                break;
        }
    }





    @Override
    public void render(Graphics2D g) {
        if (!isActive()) return;

        if (spritePath != null
            && SpriteOverridable.tryDrawSprite(g, this, x, y, width, height)) {

            if (!motionEnabled) {
                g.setColor(FROZEN_TINT);
                g.fillRect(x, y, width, height);
            }
            return;
        }



        if (spritePath == null) {
            java.awt.image.BufferedImage img = SpriteLoader.getInstance()
                .tryLoad("resources/sprites/hazards/mover.png");
            if (img != null) {
                g.drawImage(img, x, y, width, height, null);
                if (!motionEnabled) {
                    g.setColor(FROZEN_TINT);
                    g.fillRect(x, y, width, height);
                }
                return;
            }
        }


        g.setColor(BLOCK_FILL);
        g.fillRect(x, y, width, height);
        g.setColor(BLOCK_OUTLINE);
        g.drawRect(x, y, width, height);

        g.drawLine(x + 4, y + height / 2 - 2, x + width - 4, y + height / 2 - 2);
        g.drawLine(x + 4, y + height / 2 + 2, x + width - 4, y + height / 2 + 2);
        if (!motionEnabled) {
            g.setColor(FROZEN_TINT);
            g.fillRect(x, y, width, height);
        }
    }





    @Override public void setSpritePath(String path) { this.spritePath = path; }
    @Override public String getSpritePath() { return spritePath; }
}
