/**
 * Spawns 8 falling brick platforms at random horizontal positions. Blocks fall at
 * 6 px/tick and become permanent obstacles on landing. Deals 1 damage to the
 * Wanderer on contact during the fall. Duration: 3000 ms.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockRain extends BossAttack {

    /** The number of falling blocks spawned by this attack. */
    private static final int BLOCK_COUNT = 8;

    /** The fall speed of each block in pixels per tick. */
    private static final float FALL_SPEED = 6f;

    /** The list of falling brick platforms. */
    private List<Platform> fallingBlocks;

    /** Maps each block to its current vertical velocity (0 = stopped). */
    private Map<Platform, Float> velocityMap;

    /** Reference to the Wanderer for contact damage. */
    private Player player;

    /** Reference to existing platforms for ground detection. */
    private List<Platform> groundPlatforms;

    /** The ground Y level for simple landing detection. */
    private int groundY;

    /**
     * Constructs a new {@code BlockRain} attack.
     *
     * @param player          the Wanderer entity for contact damage
     * @param groundPlatforms the existing level platforms for landing detection
     * @param groundY         the default ground Y-coordinate
     */
    public BlockRain(Player player, List<Platform> groundPlatforms, int groundY) {
        super(0, 0, 0, 0, 3000L);
        this.player = player;
        this.groundPlatforms = groundPlatforms;
        this.groundY = groundY;
        this.fallingBlocks = new ArrayList<>();
        this.velocityMap = new HashMap<>();

        // Spawn 8 blocks at random x positions above the screen
        for (int i = 0; i < BLOCK_COUNT; i++) {
            int blockX = (int) (Math.random() * 1024);
            Platform block = new Platform(Platform.PlatformType.BRICK, blockX, -50);
            fallingBlocks.add(block);
            velocityMap.put(block, FALL_SPEED);
        }
    }

    /**
     * Moves falling blocks downward, checks for landing and player contact.
     *
     * @param deltaMs the time elapsed since the last tick, in milliseconds
     */
    @Override
    public void update(long deltaMs) {
        super.update(deltaMs);

        for (Platform block : fallingBlocks) {
            Float vel = velocityMap.get(block);
            if (vel == null || vel == 0f) {
                continue;
            }

            // Move block down
            block.y += (int) vel.floatValue();

            // Check if block hit the ground level
            boolean landed = false;

            if (block.y + block.height >= groundY) {
                block.y = groundY - block.height;
                landed = true;
            }

            // Check if block landed on an existing platform
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

            // Contact damage to player
            if (vel > 0f && player != null && !player.isInvincible()) {
                if (block.getBounds().intersects(player.getBounds())) {
                    player.takeDamage(1);
                    velocityMap.put(block, 0f);
                }
            }
        }
    }

    /**
     * Renders all falling and landed blocks.
     *
     * @param g the {@link Graphics2D} context to draw on
     */
    @Override
    public void render(Graphics2D g) {
        for (Platform block : fallingBlocks) {
            block.render(g);
        }
    }

    /**
     * Returns the list of falling block platforms, which can be added to the level
     * as permanent obstacles after the attack ends.
     *
     * @return the list of block platforms
     */
    public List<Platform> getFallingBlocks() {
        return fallingBlocks;
    }
}
