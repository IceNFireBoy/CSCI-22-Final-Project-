/**
 * Implements the Architect's block-rain ability that drops blocks from above.
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
import java.util.*;
import java.util.List;
public class BlockRain extends BossAttack {

    private static final int BLOCK_COUNT = 8;

    private static final float FALL_SPEED = 6f;

    private List<Platform> fallingBlocks;

    private Map<Platform, Float> velocityMap;

    private Player player;

    private List<Platform> groundPlatforms;

    private int groundY;

    public BlockRain(Player player, List<Platform> groundPlatforms, int groundY) {
        super(0, 0, 0, 0, 3000L);
        this.player = player;
        this.groundPlatforms = groundPlatforms;
        this.groundY = groundY;
        this.fallingBlocks = new ArrayList<>();
        this.velocityMap = new HashMap<>();

        for (int i = 0; i < BLOCK_COUNT; i++) {
            int blockX = (int) (Math.random() * 1024);
            Platform block = new Platform(Platform.PlatformType.BRICK, blockX, -50);
            fallingBlocks.add(block);
            velocityMap.put(block, FALL_SPEED);
        }
    }

    @Override
    public void update(long deltaMs) {
        super.update(deltaMs);

        for (Platform block : fallingBlocks) {
            Float vel = velocityMap.get(block);
            if (vel == null || vel == 0f) {
                continue;
            }

            block.y += (int) vel.floatValue();

            boolean landed = false;

            if (block.y + block.height >= groundY) {
                block.y = groundY - block.height;
                landed = true;
            }

            if (!landed && groundPlatforms != null) {
                Rectangle blockBounds = block.getBounds();
                for (Platform gp : groundPlatforms) {
                    if (gp.isActive() && gp.isSolid()
                            && blockBounds.intersects(gp.getBounds())) {
                        block.y = gp.y - block.height;
                        landed = true;
                        break;
                    }
                }
            }

            if (landed) {
                velocityMap.put(block, 0f);
            }

            if (vel > 0f && player != null && !player.isInvincible()) {
                if (block.getBounds().intersects(player.getBounds())) {
                    player.takeDamage(1);
                    velocityMap.put(block, 0f);
                }
            }
        }
    }

    @Override
    public void render(Graphics2D g) {
        for (Platform block : fallingBlocks) {
            block.render(g);
        }
    }

    public List<Platform> getFallingBlocks() {
        return fallingBlocks;
    }
}
