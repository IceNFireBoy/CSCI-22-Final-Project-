/**
 * Spawns 8 falling brick platforms at random horizontal positions across the arena.
 * Each block falls at {@link #FALL_SPEED} pixels per tick and becomes a permanent
 * obstacle once it lands on the ground level or an existing platform. A block deals
 * 1 damage to the Wanderer on contact while it is still falling. Duration: 3000 ms.
 *
 * <p>Architecture role: {@code BlockRain} is one of the five {@link BossAttack}
 * subtypes. It is instantiated by the boss-attack factory in {@link GameStarter} when
 * the Apprentice activates the Block Rain option on the boss panel. The falling blocks
 * are separate {@link Platform} objects managed internally; after the attack expires,
 * {@link GameStarter} calls {@link #getFallingBlocks()} and adds the landed blocks to
 * the permanent platform list so the arena becomes progressively more cluttered over
 * repeated Block Rain activations.</p>
 *
 * <p>The random positions are generated with {@link Math#random()} at construction
 * time, so each Block Rain instance produces a unique layout. The x positions are
 * distributed across the 1024 px wide arena view; the blocks start at y=-50 (above
 * the top of the canvas) so they appear to fall in from above.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */


// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 1c "Abstract Classes" - extends BossAttack (abstract base) and
//                                 customises render and hitbox for one of
//                                 the five attack patterns. Inherits the
//                                 shared lifetime-countdown logic via
//                                 super.update(). Direct application of 1c.
// Module 3a "Graphics"          - render() uses Graphics2D primitives
//                                 (fillRect, drawLine, drawOval) plus
//                                 RenderingHints anti-aliasing per 3a.
// Module 3b "More Graphics"     - some attacks use AffineTransform-style
//                                 rotation (the rotating-beam sweep) and
//                                 AlphaComposite for translucent overlays.
// Module 3c "Collision"         - getHitbox() returns a Rectangle for
//                                 checkBossAttackHits() AABB tests, the
//                                 collision-module pattern.
// =========================================================================
import java.awt.*;
import java.util.*;
import java.util.List;
public class BlockRain extends BossAttack { // Extends BossAttack for the shared 3000 ms lifetime countdown

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

     
    private static final int BLOCK_COUNT = 8; // 8 blocks; enough to visibly fill the arena without being deterministic

     
    private static final float FALL_SPEED = 6f; // 6 px/tick = 360 px/s; visible but not so fast it's impossible to dodge

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * The list of {@link Platform} objects representing the falling bricks. Each
     * platform starts above the visible area ({@code y = -50}) and falls at
     * {@link #FALL_SPEED} until it lands or is stopped. After the attack ends,
     * landed blocks can be extracted via {@link #getFallingBlocks()} and added to
     * the arena as permanent obstacles.
     */
    private List<Platform> fallingBlocks; // 8 BRICK platforms; managed individually; exposed via getFallingBlocks() after expiry

    /**
     * Maps each falling block to its current vertical velocity. A velocity of 0
     * means the block has landed and is stationary; a velocity equal to
     * {@link #FALL_SPEED} means it is still falling. Using a map rather than an
     * array keeps the association between block identity and velocity robust even
     * if the list order changes.
     */
    private Map<Platform, Float> velocityMap; // Platform → current y velocity; 0f = landed; FALL_SPEED = still falling

    /**
     * Reference to the Wanderer player for contact-damage checks during the fall.
     * Only blocks that are still falling ({@code vel > 0}) deal damage; landed blocks
     * are harmless permanent obstacles.
     */
    private Player player; // Wanderer reference; used in update() to test contact while a block is falling

    /**
     * Reference to the existing arena platforms for landing-detection checks. Each
     * falling block is tested against these platforms to determine when it has landed
     * on an existing surface rather than the default ground level.
     */
    private List<Platform> groundPlatforms; // Existing arena platforms; used as a secondary landing surface check

    /**
     * The default ground Y coordinate. Blocks that reach this y value are considered
     * landed and stop moving. Acts as a fallback when the block has not hit an existing
     * platform first.
     */
    private int groundY; // Fallback landing y; blocks whose bottom edge reaches this value stop and become permanent

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code BlockRain} attack, immediately spawning
     * {@link #BLOCK_COUNT} falling brick platforms at random horizontal positions
     * above the visible canvas (y = -50) with initial velocity {@link #FALL_SPEED}.
     *
     * <p>Architecture role: Called by the boss-attack factory in {@link GameStarter}
     * when the Apprentice selects Block Rain on the boss panel. The 3000 ms
     * duration passed to {@code super()} is managed by the inherited
     * {@link BossAttack#update(long)} lifetime countdown.</p>
     *
     * @param player          the Wanderer entity for contact-damage checks during fall
     * @param groundPlatforms the existing arena platforms for landing detection;
     *                        may be {@code null} (falls back to {@code groundY} only)
     * @param groundY         the default ground y-coordinate; blocks land here if no
     *                        platform intercepts them first
     */
    public BlockRain(Player player, List<Platform> groundPlatforms, int groundY) { // Three-argument constructor: sets up player/platform refs and spawns 8 blocks
        super(0, 0, 0, 0, 3000L);                     // Delegate to BossAttack: no fixed position (0,0); zero AABB; 3000 ms lifetime
        this.player = player;                           // Store Wanderer reference for per-tick contact damage checks
        this.groundPlatforms = groundPlatforms;         // Store existing platform list for secondary landing detection
        this.groundY = groundY;                         // Store fallback landing y for blocks that miss all platforms
        this.fallingBlocks = new ArrayList<>();         // Initialise empty block list; populated in the spawn loop below
        this.velocityMap = new HashMap<>();             // Initialise empty velocity map; each block entry added in the spawn loop

        // Spawn 8 blocks at random x positions above the screen
        for (int i = 0; i < BLOCK_COUNT; i++) {        // Create BLOCK_COUNT (8) blocks at random horizontal positions
            int blockX = (int) (Math.random() * 1024); // Random x in [0, 1023]: spread across the 1024 px arena view width
            Platform block = new Platform(Platform.PlatformType.BRICK, blockX, -50); // Create BRICK platform at (blockX, -50): starts above the canvas top edge
            fallingBlocks.add(block);                   // Add to the managed block list for update/render passes
            velocityMap.put(block, FALL_SPEED);         // Initialise velocity to FALL_SPEED: block starts falling immediately
        }
    }

    // -------------------------------------------------------------------------
    // BossAttack overrides
    // -------------------------------------------------------------------------

    /**
     * Advances each falling block's position downward, checks for platform/ground
     * landing, and tests contact damage against the Wanderer for still-falling blocks.
     *
     * <p>Architecture role: Called every 16 ms by the game loop in
     * {@link GameStarter}. The {@code super.update(deltaMs)} call at the top manages
     * the 3000 ms lifetime; after expiry the method returns immediately via the
     * {@code !active} check.</p>
     *
     * <p>Landing detection uses two sequential checks: first the default ground level
     * ({@link #groundY}), then a pass over all existing arena platforms. The
     * first-hit wins: once a block lands on any surface, its velocity is set to 0 and
     * it remains in place permanently.</p>
     *
     * @param deltaMs the time elapsed since the last tick, in milliseconds
     */
    @Override
    public void update(long deltaMs) {                                          // Advance falling blocks; check landing and contact damage; call super for lifetime
        super.update(deltaMs);                                                   // Advance lifetime countdown; sets active=false when 3000 ms elapses

        for (Platform block : fallingBlocks) {                                   // Process each falling block independently
            Float vel = velocityMap.get(block);                                  // Look up this block's current velocity
            if (vel == null || vel == 0f) {                                      // Skip blocks with no velocity entry or zero velocity (already landed)
                continue;                                                         // No movement needed for landed blocks
            }

            // Move block down
            block.y += (int) vel.floatValue();                                   // Advance block y by its current fall velocity (downward)

            // Check if block hit the ground level
            boolean landed = false;                                              // Tracks whether this block landed this tick

            if (block.y + block.height >= groundY) {                            // Block bottom edge has reached or passed the default ground y
                block.y = groundY - block.height;                               // Snap block top to groundY - blockHeight so bottom edge aligns with the ground
                landed = true;                                                   // Mark as landed: velocity will be zeroed below
            }

            // Check if block landed on an existing platform
            if (!landed && groundPlatforms != null) {                           // Secondary landing check: only if not already landed and platform list is available
                Rectangle blockBounds = block.getBounds();                       // Get block AABB for intersection tests
                for (Platform gp : groundPlatforms) {                            // Test against every existing arena platform
                    if (gp.isActive() && gp.isSolid()                           // Only test active solid platforms; non-solid don't stop falling blocks
                            && blockBounds.intersects(gp.getBounds())) {         // AABB overlap: falling block has hit this platform's surface
                        block.y = gp.y - block.height;                          // Snap block onto the platform surface: top of platform minus block height
                        landed = true;                                           // Mark as landed
                        break;                                                   // Stop testing after the first landing surface is found
                    }
                }
            }

            if (landed) {                                                        // Block has landed on some surface this tick
                velocityMap.put(block, 0f);                                      // Zero the velocity: block is now a permanent stationary obstacle
            }

            // Contact damage to player
            if (vel > 0f && player != null && !player.isInvincible()) {         // Only deal damage from still-falling blocks; invincibility frames are respected
                if (block.getBounds().intersects(player.getBounds())) {          // AABB overlap: falling block has hit the Wanderer
                    player.takeDamage(1);                                        // Deal 1 damage to the Wanderer per contact
                    velocityMap.put(block, 0f);                                  // Stop the block on contact: it embeds at the Wanderer's position
                }
            }
        }
    }

    /**
     * Renders all falling and landed blocks by delegating to each block's own
     * {@link Platform#render(Graphics2D)} method.
     *
     * <p>Architecture role: Called every frame by {@link GameCanvas#renderElements}
     * while this attack is active. Landed blocks continue to be rendered as permanent
     * arena obstacles even after the attack expires.</p>
     *
     * @param g the {@link Graphics2D} context to draw on
     */
    @Override
    public void render(Graphics2D g) {            // Draw all 8 blocks (falling and landed) using their own Platform.render() methods
        for (Platform block : fallingBlocks) {    // Iterate every block regardless of velocity; landed blocks are still visible
            block.render(g);                      // Delegate rendering to Platform.render(): draws the appropriate BRICK tile sprite
        }
    }

    // -------------------------------------------------------------------------
    // Accessor
    // -------------------------------------------------------------------------

    /**
     * Returns the list of falling (and now potentially landed) block platforms.
     * After the attack expires, {@link GameStarter} calls this method to add landed
     * blocks to the arena's permanent platform list, making the terrain progressively
     * more cluttered with repeated Block Rain activations.
     *
     * @return the mutable list of {@link Platform} instances; never {@code null};
     *         may contain both falling and landed blocks
     */
    public List<Platform> getFallingBlocks() { // Called by GameStarter after expiry to promote landed blocks to permanent arena platforms
        return fallingBlocks;                   // Return the internal list; caller can add entries to the arena's platform list directly
    }
}
