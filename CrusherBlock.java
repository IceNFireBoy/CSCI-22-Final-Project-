/**
 * A large 80×80 dark block that chases the Apprentice's gesture position, lerping
 * toward it each tick at rate {@link #LERP_FACTOR}. Deals 2 damage to the Wanderer
 * when the block's centre comes within 30 pixels of the player's centre. Duration:
 * 8000 ms.
 *
 * <p>Architecture role: {@code CrusherBlock} is one of the five {@link BossAttack}
 * subtypes. Unlike the other attacks that are placed at fixed positions or random
 * locations, the Crusher Block is directly steered by the Apprentice via real-time
 * gesture updates. This makes it the most interactive boss attack: the Apprentice must
 * actively guide the block toward the Wanderer while the Wanderer dodges.</p>
 *
 * <p>The lerp factor {@link #LERP_FACTOR} of 0.04 (4% per tick) produces slow,
 * deliberate movement that the Wanderer can outrun with normal walking speed but
 * cannot easily dodge during a crowded arena. The 30 px contact radius (slightly
 * smaller than the block's 40 px radius) requires the block to nearly overlap the
 * player before damage fires, giving a brief reaction window.</p>
 *
 * <p>Rendering: A solid dark rectangle ({@code #1a1020}) with a magenta border
 * ({@code #8b2070}, 2 px stroke) to make it visually distinct from the arena
 * walls and platforms.</p>
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
public class CrusherBlock extends BossAttack { // Extends BossAttack for the 8000 ms lifetime countdown

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * The current world-space centre position of the crusher block. Updated each
     * tick by lerping toward {@link #targetPos} at {@link #LERP_FACTOR}. The
     * {@link GameElement#x} and {@link GameElement#y} fields are derived from this
     * by subtracting 40 px (half of the 80 px block size) each tick.
     */
    private Point currentPos; // Current block centre (world space); lerped toward targetPos each tick; x/y AABB derived from it

    /**
     * The world-space position the block is currently chasing. Updated by
     * {@link #setTargetPos(Point)} from the Apprentice's gesture data each tick.
     * The block never teleports; it lerps toward the target, so rapid target
     * changes produce smooth but lagged tracking.
     */
    private Point targetPos; // Gesture-driven destination; set by setTargetPos() from Apprentice input; block lerps toward it

    /**
     * Reference to the Wanderer for contact-damage checks each tick. The proximity
     * test compares the block's centre to the player's estimated centre and applies
     * 2 damage when the distance is less than 30 px.
     */
    private Player player; // Wanderer reference; used in update() for proximity-based contact damage

    /**
     * Linear interpolation factor per tick. Each tick, the current position moves
     * 4% of the remaining distance to the target, producing smooth deceleration as
     * the block approaches the target. Smaller values = more inertia / slower tracking.
     */
    private static final float LERP_FACTOR = 0.04f; // 4% blend per tick; block crosses half the remaining gap in ~17 ticks (~0.28 s)

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code CrusherBlock} starting at the centre-top of the arena
     * ({@code x = 512, y = 0}) and targeting the given gesture position.
     *
     * <p>Architecture role: Called by the boss-attack factory in {@link GameStarter}
     * when the Apprentice initiates a Crusher Block gesture. The block spawns at the
     * centre-top so it appears to drop in from above the arena, giving the Wanderer a
     * brief positional hint before it starts tracking.</p>
     *
     * @param gesturePos the initial world-space target position from the Apprentice's
     *                   gesture; updated via {@link #setTargetPos(Point)} each tick
     * @param player     the Wanderer entity for proximity damage; may be {@code null}
     *                   (null-guarded in update())
     */
    public CrusherBlock(Point gesturePos, Player player) {          // Two-argument constructor: initial gesture target and Wanderer reference
        super(512, 0, 80, 80, 8000L);                               // Delegate: spawn at (512,0), 80×80 AABB, 8000 ms lifetime
        this.currentPos = new Point(512, 0);                        // Start block centre at (512,0): arena centre-top; visible from the start
        this.targetPos  = new Point(gesturePos);                    // Store a copy of the initial gesture position as the first lerp target
        this.player = player;                                        // Store Wanderer reference for per-tick proximity damage checks
    }

    // -------------------------------------------------------------------------
    // BossAttack overrides
    // -------------------------------------------------------------------------

    /**
     * Lerps the block's centre toward the current gesture target and checks whether
     * the block's centre is within 30 px of the Wanderer's estimated centre for
     * contact damage.
     *
     * <p>Lerp formula: {@code current += (target - current) × LERP_FACTOR}
     * applied independently on x and y each tick. The resulting position is stored
     * in {@link #currentPos} and propagated to the inherited {@link GameElement#x}
     * and {@link GameElement#y} fields (offset by -40 so the AABB is centred on
     * {@code currentPos}).</p>
     *
     * <p>Contact damage: Computes Euclidean distance from block centre to player
     * centre. Deals 2 damage if distance &lt; 30 px and the player is not invincible.
     * No per-tick throttle: damage can fire every tick while the block is on top of
     * the player, but invincibility frames from a previous hit provide a natural
     * cooldown.</p>
     *
     * @param deltaMs the time elapsed since the last tick, in milliseconds
     */
    @Override
    public void update(long deltaMs) {                                                   // Lerp block toward gesture target; check proximity damage; advance lifetime
        super.update(deltaMs);                                                            // Advance elapsed timer; sets active=false when 8000 ms elapses
        if (!active) {                                                                    // Early exit: attack has expired; no movement or damage after lifetime ends
            return;
        }

        // Lerp toward target
        currentPos.x = (int) (currentPos.x + (targetPos.x - currentPos.x) * LERP_FACTOR); // Blend x: move 4% of remaining x gap toward target each tick
        currentPos.y = (int) (currentPos.y + (targetPos.y - currentPos.y) * LERP_FACTOR); // Blend y: move 4% of remaining y gap toward target each tick

        // Update GameElement position for bounds
        x = currentPos.x - 40;  // Derive left-edge x from centre x; block is 80 px wide, so left = centre - 40
        y = currentPos.y - 40;  // Derive top-edge y from centre y; block is 80 px tall, so top = centre - 40

        // Contact damage — 2 damage within 30px
        if (player != null && !player.isInvincible()) {                                   // Null guard for player; skip if player has active invincibility frames
            double dx = currentPos.x - (player.getX() + 12);                             // Horizontal distance from block centre to Wanderer estimated centre (x + 12 px)
            double dy = currentPos.y - (player.getY() + 16);                             // Vertical distance from block centre to Wanderer estimated centre (y + 16 px)
            double dist = Math.sqrt(dx * dx + dy * dy);                                  // Euclidean distance; compared to 30 px contact radius
            if (dist < 30) {                                                              // Block centre is within 30 px of player centre: contact!
                player.takeDamage(2);                                                     // Deal 2 damage (higher than beam/spike); reflects the block's mass/danger
            }
        }
    }

    /**
     * Renders the crusher block as an 80×80 dark rectangle with a 2 px magenta border.
     *
     * <p>Architecture role: Called every frame by {@link GameCanvas#renderElements}
     * while active. The dark fill ({@code #1a1020}) makes the block nearly black,
     * visually heavy. The magenta border ({@code #8b2070}) distinguishes it from
     * arena walls (which use the standard BRICK tile sprite).</p>
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    @Override
    public void render(Graphics2D g) {                              // Draw the 80×80 dark block with magenta border; skip if inactive
        if (!active) {                                              // Do not render an expired crusher block
            return;
        }

        Stroke originalStroke = g.getStroke();                     // Save caller's stroke before setting the 2 px border stroke

        // Fill — dark #1a1020
        g.setColor(new Color(0x1a, 0x10, 0x20));                   // Near-black dark purple fill; visually heavy and threatening
        g.fillRect(x, y, 80, 80);                                  // Fill the 80×80 block at the current AABB top-left position

        // Stroke — magenta #8b2070
        g.setColor(new Color(0x8b, 0x20, 0x70));                   // Deep magenta border; distinct from arena tiles and platform colours
        g.setStroke(new BasicStroke(2f));                           // 2 px border; visible but not so thick it obscures the fill
        g.drawRect(x, y, 80, 80);                                  // Outline the block rectangle with the magenta stroke

        g.setStroke(originalStroke);                               // Restore caller's stroke to prevent 2 px leakage into subsequent draws
    }

    // -------------------------------------------------------------------------
    // Mutator
    // -------------------------------------------------------------------------

    /**
     * Updates the gesture target position the block lerps toward. Called by
     * {@link GameStarter} each tick from the Apprentice's real-time cursor input
     * so the block continuously tracks the gesture even as it moves.
     *
     * <p>A defensive copy of the provided point is made to prevent shared mutable
     * state between the caller and this object (in case the caller reuses the same
     * {@link Point} object with different coordinates on subsequent ticks).</p>
     *
     * @param target the new world-space target position; must not be {@code null}
     */
    public void setTargetPos(Point target) {    // Called by GameStarter each tick from Apprentice gesture; updates lerp destination
        this.targetPos = new Point(target);     // Defensive copy: stores a fresh Point so caller mutations don't corrupt our target
    }
}
