/**
 Static or semi-static collision surface that the Wanderer stands, slides, or bounces on.
 Each platform has a type (BRICK, SLIDE, SPRING, WALL, CRUMBLE) that governs physics
 behavior, a budget cost for Apprentice placement, and a lighting flag.

 Platform is the primary collision geometry class. CollisionDetector resolves player-platform
 overlaps each tick; PhysicsEngine reads isSolid() before axis resolution; LevelLoader
 instantiates platforms from JSON at level load time; BossArenaGenerator creates platforms
 procedurally. Apprentice places platforms via GameSession.placeBlock().
 */

import java.awt.BasicStroke;                      // Configures outline stroke widths for platform rendering
import java.awt.Color;                             // AWT colour definitions for platform fill and border hues
import java.awt.Graphics2D;                        // 2D rendering context passed to render() by GameCanvas
import java.awt.image.BufferedImage;               // Stores loaded tile sprites from SpriteLoader for each platform type
import java.util.Random;                           // PRNG used to generate random shake offsets during the crumble animation

public class Platform extends GameElement { // Extends GameElement for position, AABB, active flag, and the update/render contract

    // =========================================================================
    // Enum — PlatformType
    // =========================================================================

    /**
     Distinct varieties of platform that appear in the game world. Each type influences
     how the Wanderer interacts: CollisionDetector branches to type-specific resolution
     (SLIDE acceleration, SPRING impulse, CRUMBLE collapse sequence), BossArenaGenerator
     specifies generated tile types, and Apprentice budget system assigns placement costs.
     */
    public enum PlatformType {
        BRICK,  // Solid, ordinary brick surface with no special effect
        SLIDE,  // Sloped surface that imparts lateral momentum while standing
        SPRING, // Compressed spring that launches Wanderer upward on contact
        WALL,   // Vertical or horizontal barrier that fully blocks movement

        CRUMBLE,

        /**
         * A surface that has no visual presence in unlit areas but still provides solid
         * collision when the LightBall illuminates it; used in Act 3 darkness sections.
         */
        INVISIBLE,

        /**
         * A surface that masquerades as a normal platform but collapses a short delay
         * after the Wanderer first steps on it, catching unaware players off guard.
         */
        MIMIC
    }

    // -------------------------------------------------------------------------
    // Core fields
    // -------------------------------------------------------------------------

    /** The behavioural type of this platform; determines collision response and rendering. */
    private PlatformType type; // Set at construction; read by CollisionDetector and render()

    /**
     * The block-budget cost the Apprentice pays to place this platform. Higher-cost
     * types offer stronger gameplay effects; the budget system prevents the Apprentice
     * from flooding the arena with powerful platforms indefinitely.
     */
    private int budgetCost; // Set by resolveBudgetCost() at construction; read by GameSession

    /**
     * Whether this platform is currently illuminated by the LightBall. For INVISIBLE
     * platforms, being lit makes them solid and visible; for all others the flag is
     * used by the light-mask renderer.
     */
    private boolean lit; // Toggled by LightRenderer each frame; read by CollisionDetector for INVISIBLE gating

    // -------------------------------------------------------------------------
    // Crumble state fields
    // -------------------------------------------------------------------------

    // Crumble animation and physics fields — only active for PlatformType.CRUMBLE

    private long crumbleTimer = 0;                   // Accumulated ms since startCrumbling() was called; drives all phase transitions
    private boolean crumbleStarted = false;          // True once the Wanderer has triggered the crumble sequence; gates the update logic
    private float shakeOffsetX = 0;                  // Current horizontal shake displacement in pixels; applied in render() draw calls
    private float shakeOffsetY = 0;                  // Current vertical shake displacement in pixels; amplitude grows with crumbleTimer
    private float fallVelocity = 0;                  // Downward speed in px/tick once falling==true; accelerated by 0.8 each tick
    private boolean falling = false;                 // True after CRUMBLE_SHAKE_DURATION ms; platform is now falling and no longer solid
    public boolean fullyGone = false;                // True once the falling platform has dropped below y=900; render() skips it entirely
    private static final int CRUMBLE_SHAKE_DURATION = 2000; // ms of shaking before the platform starts to fall (2 s warning)
    private static final int CRACK_STAGE_1 = 700;   // ms elapsed at which the first crack overlay is drawn on the crumble sprite
    private static final int CRACK_STAGE_2 = 1400;  // ms elapsed at which a second, deeper crack overlay is drawn
    private Random rand = new Random();              // Per-instance PRNG for frame-to-frame shake randomness; no seed needed

    // -------------------------------------------------------------------------
    // Mimic state fields
    // -------------------------------------------------------------------------

    /**
     * Whether the Wanderer has already made first contact with this MIMIC platform.
     * Once triggered, the collapse timer begins and {@link #update(long)} monitors
     * the elapsed time against {@link #MIMIC_DELAY_MS}.
     */
    private boolean mimicTriggered; // Set to true by CollisionDetector on first player contact; drives the mimic timer

    /**
     * The absolute system-clock time in milliseconds when this MIMIC platform was first
     * contacted. A value of {@code -1} indicates the platform has not yet been touched.
     * After {@link #MIMIC_DELAY_MS} milliseconds, {@link #update(long)} sets
     * {@link #solid} to {@code false}, causing the platform to vanish from collision.
     */
    private long mimicStartTime; // Stamped by CollisionDetector via setMimicStartTime(); compared in update()

    /** Delay in milliseconds between the Wanderer's first step and the MIMIC collapse. */
    private static final long MIMIC_DELAY_MS = 800L; // 0.8 s — long enough for the Wanderer to notice but short enough to be dangerous

    // -------------------------------------------------------------------------
    // Solidity flag
    // -------------------------------------------------------------------------

    /**
     * Whether this platform currently participates in collision resolution. Starts
     * {@code true} for all types; set to {@code false} by {@link #update(long)} when a
     * CRUMBLE platform's fall begins or a MIMIC platform's delay expires. Once false,
     * {@link CollisionDetector} skips this platform in its solid-only check.
     */
    private boolean solid; // Read by CollisionDetector.checkPlayerPlatform() before resolving any overlap

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code Platform} of the specified type at the given world
     * position using default tile dimensions computed by {@link #defaultWidth(PlatformType)}
     * and {@link #defaultHeight(PlatformType)}.
     *
     * <p>Architecture role: The primary constructor used by {@link LevelLoader} when
     * deserialising level JSON entries that do not specify explicit dimensions, and by
     * {@link BossArenaGenerator#addMirroredPlatform} for procedural BRICK tiles.
     * The budget cost is resolved immediately so the Apprentice's economy reflects the
     * correct cost at placement time.</p>
     *
     * @param type the {@link PlatformType} that determines behaviour and default dimensions
     * @param x    the x-coordinate of the platform's left edge in world space
     * @param y    the y-coordinate of the platform's top edge in world space
     */
    public Platform(PlatformType type, int x, int y) {
        super(x, y, defaultWidth(type), defaultHeight(type)); // Delegate to GameElement with type-specific default dimensions
        this.type             = type;                          // Store the type; used in update() branching and render() switch
        this.budgetCost       = resolveBudgetCost(type);       // Compute placement cost at construction so it never drifts
        this.lit              = false;                         // Platforms start unlit; LightRenderer will toggle as needed each frame
        this.solid            = true;                          // All platforms start solid; only crumble/mimic transitions turn this off
        this.mimicTriggered   = false;                         // MIMIC not yet touched; collapse sequence has not started
        this.mimicStartTime   = -1L;                          // Sentinel -1 means the MIMIC timestamp has not been recorded yet
    }

    /**
     * Returns the default tile width in pixels for the given platform type. Used by the
     * single-coordinate constructor to derive the AABB width when the caller does not
     * supply explicit dimensions.
     *
     * <p>Standard dimensions: SLIDE=64 (wide ramp), SPRING=24 (narrow pad),
     * WALL=16 (thin vertical slab), all others=32 (standard 1-tile width).</p>
     *
     * @param type the {@link PlatformType} whose default width to return
     * @return the default width in pixels for that type
     */
    private static int defaultWidth(PlatformType type) {
        switch (type) {
            case SLIDE:   return 64;   // Wide ramp: 2 tiles — long enough for the Wanderer to slide across
            case SPRING:  return 24;   // Narrow pad: 0.75 tile — small target that rewards precise positioning
            case WALL:    return 16;   // Thin slab: 0.5 tile — sized to act as a solid barrier rather than a floor
            default:      return 32;   // Standard tile: 1 tile — BRICK, CRUMBLE, MIMIC, INVISIBLE all share this
        }
    }

    /**
     * Returns the default tile height in pixels for the given platform type. Used by the
     * single-coordinate constructor to derive the AABB height when not explicitly given.
     *
     * <p>Standard dimensions: SPRING=24 (tall enough to be visually distinct), WALL=64
     * (full-height barrier spanning two tiles), all others=16 (standard thin platform).</p>
     *
     * @param type the {@link PlatformType} whose default height to return
     * @return the default height in pixels for that type
     */
    private static int defaultHeight(PlatformType type) {
        switch (type) {
            case SPRING:  return 24;   // Taller spring pad: visible coil representation, 0.75 tile
            case WALL:    return 64;   // Tall barrier: spans 2 tiles vertically to block traversal
            default:      return 16;   // Thin floor tile: 0.5 tile — the standard platform thickness
        }
    }

    /**
     * Constructs a new {@code Platform} of the specified type at the given world
     * position with custom dimensions. Used for non-standard tile sizes such as the
     * arena boundary walls, the boss throne marker, and level-designer specified tiles.
     *
     * <p>Architecture role: Called by {@link LevelLoader} when the JSON entry has
     * explicit {@code w}/{@code h} fields, and by {@link BossArenaGenerator} for the
     * boundary WALL tiles and the wide throne WALL platform.</p>
     *
     * @param type   the {@link PlatformType} that determines behaviour and rendering
     * @param x      the x-coordinate of the platform's left edge in world space
     * @param y      the y-coordinate of the platform's top edge in world space
     * @param width  the width of this platform in pixels
     * @param height the height of this platform in pixels
     */
    public Platform(PlatformType type, int x, int y, int width, int height) {
        super(x, y, width, height);                // Delegate to GameElement with caller-supplied dimensions
        this.type             = type;              // Store type for update() branching and render() switch
        this.budgetCost       = resolveBudgetCost(type); // Compute budget cost; caller may override later via setBudgetCost()
        this.lit              = false;             // Starts unlit; LightRenderer updates this each frame
        this.solid            = true;              // Starts solid; crumble/mimic transitions flip this to false later
        this.mimicTriggered   = false;             // MIMIC collapse sequence not yet started
        this.mimicStartTime   = -1L;              // -1 sentinel indicates no first-contact timestamp recorded
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Derives the default block-budget cost for the given platform type. Rarer or more
     * tactically powerful types carry a higher cost to balance the Apprentice's economy
     * and prevent spam-placement of dangerous platforms.
     *
     * <p>Cost table: BRICK=1 (common, cheap), SLIDE=2, WALL=2 (moderate utility),
     * CRUMBLE=2 (trap type), SPRING=3 (high mobility), INVISIBLE=4 (high power in Act 3),
     * MIMIC=5 (highest: most deceptive and dangerous trap).</p>
     *
     * <p>Architecture role: Called once per constructor invocation; the result is cached
     * in {@link #budgetCost}. {@link GameSession} reads the cost when the Apprentice
     * places a block to deduct from the current budget.</p>
     *
     * @param type the {@link PlatformType} to look up
     * @return the integer budget cost associated with that type
     */
    private int resolveBudgetCost(PlatformType type) {
        switch (type) {
            case BRICK:     return 1;  // Standard tile: lowest cost; primary placement option throughout the game
            case SLIDE:     return 2;  // Moderate utility: good for funnelling the Wanderer; costs one extra block
            case SPRING:    return 3;  // High-mobility tile: can launch the Wanderer to hard-to-reach areas; priced accordingly
            case WALL:      return 2;  // Barrier tile: equivalent cost to SLIDE; useful for blocking paths
            case CRUMBLE:   return 2;  // Trap tile: moderately expensive since it functions as a one-shot hazard
            case INVISIBLE: return 4;  // Act 3 power tile: near-invisible under normal light; significant tactical advantage
            case MIMIC:     return 5;  // Highest cost: the most deceptive trap type; punishes the Wanderer severely on contact
            default:        return 1;  // Fallback for any future types; treated as cheapest
        }
    }

    // -------------------------------------------------------------------------
    // GameElement overrides
    // -------------------------------------------------------------------------

    /**
     * Updates the platform's timer-based state for the current game tick. Handles
     * two independent state machines: the CRUMBLE shake-and-fall sequence for
     * {@link PlatformType#CRUMBLE} platforms, and the delayed collapse for
     * {@link PlatformType#MIMIC} platforms.
     *
     * <p>CRUMBLE lifecycle (driven by {@link #crumbleTimer}):
     * <ol>
     *   <li>0 – {@link #CRUMBLE_SHAKE_DURATION} ms: shake phase; {@link #shakeOffsetX}
     *       and {@link #shakeOffsetY} are randomised each tick with growing amplitude.</li>
     *   <li>At {@link #CRUMBLE_SHAKE_DURATION}: transition to falling; solid is set to
     *       {@code false} and {@link #setActive(boolean)} deactivates the platform so
     *       {@link CollisionDetector} skips it.</li>
     *   <li>Falling: {@link #fallVelocity} is incremented by 0.8 each tick (simulated
     *       gravity for the tile); once {@code y > 900} {@link #fullyGone} is set and
     *       render() returns immediately.</li>
     * </ol></p>
     *
     * <p>MIMIC collapse: once {@link #mimicTriggered} is true, checks
     * {@link System#currentTimeMillis()} against {@link #mimicStartTime}; after
     * {@link #MIMIC_DELAY_MS} ms sets {@link #solid} to {@code false}.</p>
     *
     * <p>Architecture role: Called every game-loop tick by {@link GameStarter}'s update
     * pass over the element list. The CRUMBLE early-return ensures the falling animation
     * continues even after solid is false, so the tile visually disappears correctly.</p>
     *
     * @param deltaMs the time elapsed since the last update, in milliseconds
     */
    @Override
    public void update(long deltaMs) {
        // CRUMBLE special-case: the animation runs independently of the solid flag.
        if (type == PlatformType.CRUMBLE && crumbleStarted && !fullyGone) { // Only run when this is a CRUMBLE that has been triggered and not yet vanished
            crumbleTimer += deltaMs;                                          // Accumulate elapsed time to drive phase transitions
            if (!falling) {                                                   // Shake phase: platform is still in place but shaking
                float intensity = (float) crumbleTimer / CRUMBLE_SHAKE_DURATION; // Normalise [0,1] across the shake window
                float maxShake = 2.0f + intensity * 3.0f;                    // Shake amplitude grows from 2 to 5 px as the timer progresses
                shakeOffsetX = (rand.nextFloat() - 0.5f) * maxShake * 2;    // Random x offset in [-maxShake, +maxShake]
                shakeOffsetY = (rand.nextFloat() - 0.5f) * maxShake;         // Random y offset in [-maxShake/2, +maxShake/2]; smaller than x for realism
                if (crumbleTimer >= CRUMBLE_SHAKE_DURATION) {                // Shake window has expired: transition to the falling phase
                    falling = true;                                            // Flag that the platform is now in freefall
                    fallVelocity = 0;                                         // Reset fall velocity; will be accelerated each tick below
                    solid = false;                                             // Remove from collision: the Wanderer can now pass through
                    setActive(false);                                          // Deactivate so CollisionDetector skips this element entirely
                }
            } else {                                                          // Falling phase: platform accelerates downward under simulated gravity
                fallVelocity += 0.8f;                                        // Gravity acceleration: 0.8 px/tick² matches a light debris feel
                y += (int) fallVelocity;                                     // Move the platform downward by its current velocity
                shakeOffsetX = (rand.nextFloat() - 0.5f) * 6;               // Maintain a wild x shake during the fall for a tumbling effect
                if (y > 900) {                                               // Platform has fallen well below the visible canvas (900 px threshold)
                    fullyGone = true;                                         // Mark as completely gone: render() will return immediately hereafter
                }
            }
            return; // CRUMBLE update is complete; skip the MIMIC check below
        }

        if (!solid) return; // If already non-solid (mimic already collapsed), nothing to do

        long now = System.currentTimeMillis(); // Get current wall-clock time for the MIMIC delay check

        if (type == PlatformType.MIMIC && mimicTriggered) {            // MIMIC collapse timer check: only runs after the Wanderer has stepped on it
            if ((now - mimicStartTime) >= MIMIC_DELAY_MS) {            // Check if MIMIC_DELAY_MS (800 ms) has elapsed since first contact
                solid = false;                                           // Collapse the platform: it will no longer block the Wanderer
            }
        }
    }

    /**
     * Triggers the crumble sequence for this CRUMBLE-type platform. Safe to call
     * multiple times; subsequent calls are no-ops once the sequence has already started.
     *
     * <p>Architecture role: Called by {@link CollisionDetector#checkPlayerPlatform}
     * the first time the Wanderer lands on a CRUMBLE tile. The actual shake-and-fall
     * logic is driven by {@link #update(long)} on subsequent ticks.</p>
     */
    public void startCrumbling() {
        if (type == PlatformType.CRUMBLE && !crumbleStarted) { // Guard: only start if this is a CRUMBLE that has not yet begun its sequence
            crumbleStarted = true;                              // Arm the crumble state machine; update() will pick it up on the next tick
        }
    }

    /**
     * Renders this platform onto the provided graphics context using the appropriate
     * tile sprite or fallback solid-colour representation for the platform's type.
     *
     * <p>Per-type rendering strategy:
     * <ul>
     *   <li>CRUMBLE: applies shake offsets to the draw position; overlays crack lines at
     *       {@link #CRACK_STAGE_1} and {@link #CRACK_STAGE_2}; draws split halves during
     *       the fall. Returns immediately if {@link #fullyGone}.</li>
     *   <li>BRICK, SLIDE, SPRING, WALL, MIMIC: attempt sprite load via
     *       {@link SpriteLoader#getInstance()}; fall back to a rounded-rect fill and
     *       outline if the sprite file is missing.</li>
     *   <li>INVISIBLE: draws a faint ghost rectangle when unlit (debug aid); draws a
     *       solid blue-tinted rectangle when lit (platform is now visible to the
     *       Wanderer).</li>
     * </ul></p>
     *
     * <p>Architecture role: Called every frame by {@link GameCanvas#renderElements}.
     * Stroke and colour state is modified locally; the caller's Graphics2D context is
     * not restored (GameCanvas resets it between elements).</p>
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    @Override
    public void render(Graphics2D g) {
        switch (type) {
            case CRUMBLE: {                                                    // CRUMBLE rendering: position-jittered sprite with crack overlays
                if (fullyGone) return;                                         // Platform has fallen off-screen: nothing to draw
                int drawX = (int)(x + shakeOffsetX);                          // Apply horizontal shake offset to the draw origin
                int drawY = (int)(y + shakeOffsetY);                          // Apply vertical shake offset to the draw origin
                BufferedImage crumbleSprite = SpriteLoader.getInstance().load("resources/sprites/tiles/tile_crumble.png"); // Try to load the crumble tile sprite
                if (crumbleSprite != null) {                                   // Sprite loaded: use it for the base tile image
                    g.drawImage(crumbleSprite, drawX, drawY, width, height, null); // Draw the sprite at the shake-adjusted position, scaled to the platform AABB
                } else {                                                        // Sprite missing: fall back to a solid-colour rounded rect
                    float darken = crumbleStarted ? Math.min(1.0f, (float)crumbleTimer / CRUMBLE_SHAKE_DURATION) : 0; // Darken factor 0→1 as the crumble progresses
                    int baseR = (int)(0x2a + (0x1a - 0x2a) * darken);         // Red channel interpolates from 0x2a to 0x1a as platform darkens
                    int baseG = (int)(0x2a + (0x10 - 0x2a) * darken);         // Green channel interpolates from 0x2a to 0x10
                    int baseB = (int)(0x35 + (0x10 - 0x35) * darken);         // Blue channel interpolates from 0x35 to 0x10
                    g.setColor(new Color(baseR, baseG, baseB));                // Set the animated fill colour
                    g.fillRoundRect(drawX, drawY, width, height, 4, 4);        // Draw the darkening rounded-rect fill
                    int strokeR = (int)(0x4a + (0x8b - 0x4a) * darken);       // Border red channel shifts toward #8b (danger red) as it crumbles
                    g.setColor(new Color(strokeR, 0x2a, 0x2a));                // Set the reddening border colour
                    g.setStroke(new BasicStroke(0.5f));                        // Thin 0.5 px border; heavier strokes would obscure the small tile
                    g.drawRoundRect(drawX, drawY, width, height, 4, 4);        // Draw the outline over the fill
                }
                // First crack overlay: appears after CRACK_STAGE_1 ms (700 ms)
                if (crumbleStarted && crumbleTimer >= CRACK_STAGE_1) {         // Stage 1 crack: platform is visibly damaged but still solid
                    g.setColor(new Color(0x8b, 0x2a, 0x2a, 180));             // Semi-transparent danger red: alpha=180 so the tile shows through
                    g.setStroke(new BasicStroke(0.8f));                        // Thin crack line: 0.8 px; realistic hairline crack
                    g.drawLine(drawX + 6, drawY + 2, drawX + 14, drawY + 12); // Diagonal crack from upper-left area toward lower-right
                }
                // Second crack overlay: appears after CRACK_STAGE_2 ms (1400 ms)
                if (crumbleStarted && crumbleTimer >= CRACK_STAGE_2) {         // Stage 2 crack: platform is severely damaged, about to fall
                    g.setColor(new Color(0x8b, 0x2a, 0x2a, 220));             // Brighter danger red: more opaque to signal imminent collapse
                    g.setStroke(new BasicStroke(0.8f));                        // Same thin crack weight as stage 1
                    g.drawLine(drawX + 22, drawY + 1, drawX + 16, drawY + 14); // Second diagonal crack on the right side
                    g.setColor(new Color(0x1a, 0x0a, 0x0a, 200));             // Near-black chip colour for the crumbled piece
                    g.fillRect(drawX + 18, drawY, 5, 4);                       // Small missing-chunk rectangle at the upper-right of the tile
                }
                // Falling animation: split the tile into two halves tumbling apart
                if (falling) {                                                 // Platform is in freefall; draw it as two separating halves
                    g.setColor(new Color(0x1a, 0x10, 0x10));                  // Very dark fill for the two debris halves
                    g.fillRect(drawX, drawY, width/2 - 1, height);            // Left half: starts at drawX, 1 px gap at centre
                    g.fillRect(drawX + width/2 + 1, drawY + 2, width/2 - 1, height); // Right half: offset 2 px down for an asymmetric tumble effect
                    g.setColor(new Color(0x8b, 0x20, 0x20, 150));             // Semi-transparent split-line colour
                    g.fillRect(drawX + width/2 - 1, drawY, 2, height);        // Thin red split line at the tile's centre to emphasise the break
                }
                return; // CRUMBLE rendering complete; skip the default switch fall-through
            }
            case BRICK: {                                                       // BRICK rendering: standard tile sprite or dark-blue rounded rect
                BufferedImage sprite = SpriteLoader.getInstance().load("resources/sprites/tiles/tile_brick.png"); // Load the brick tile sprite
                if (sprite != null) {                                           // Sprite found: draw it scaled to the platform AABB
                    g.drawImage(sprite, x, y, width, height, null);            // Draw sprite at the platform's world position
                } else {                                                        // No sprite: fallback rounded rect with BRICK colour scheme
                    g.setColor(new Color(0x2a, 0x2a, 0x35));                  // Dark blue-grey fill; matches the game's dark dungeon palette
                    g.fillRoundRect(x, y, width, height, 4, 4);               // Filled rounded rectangle to represent the tile body
                    g.setColor(new Color(0x4a, 0x4a, 0x58));                  // Slightly lighter border for contrast
                    g.setStroke(new BasicStroke(0.5f));                        // Thin 0.5 px border: subtle enough not to dominate
                    g.drawRoundRect(x, y, width, height, 4, 4);               // Outline the tile
                }
                return; // BRICK rendering complete
            }
            case SLIDE: {                                                       // SLIDE rendering: uses tile_slide.png for the visual ramp indicator
                BufferedImage sprite = SpriteLoader.getInstance().load("resources/sprites/tiles/tile_slide.png"); // Load the slide tile sprite
                if (sprite != null) {                                           // Sprite available: render it
                    g.drawImage(sprite, x, y, width, height, null);            // Draw the slide tile sprite at its position
                } else {                                                        // Fallback: same visual style as BRICK (to be distinguished by size)
                    g.setColor(new Color(0x2a, 0x2a, 0x35));                  // Same dark fill as BRICK; sprite differentiates the types visually
                    g.fillRoundRect(x, y, width, height, 4, 4);               // Draw the fallback rounded rect
                    g.setColor(new Color(0x4a, 0x4a, 0x58));                  // Consistent border colour across all fallback tiles
                    g.setStroke(new BasicStroke(0.5f));                        // Thin border to not overwhelm the tile body
                    g.drawRoundRect(x, y, width, height, 4, 4);               // Outline the fallback tile
                }
                return; // SLIDE rendering complete
            }
            case SPRING: {                                                      // SPRING rendering: uses tile_spring.png with a teal-blue colour scheme
                BufferedImage sprite = SpriteLoader.getInstance().load("resources/sprites/tiles/tile_spring.png"); // Load the spring tile sprite
                if (sprite != null) {                                           // Sprite available: draw it
                    g.drawImage(sprite, x, y, width, height, null);            // Draw the spring sprite at the platform position
                } else {                                                        // Fallback: teal-blue to visually distinguish springs from standard brick
                    g.setColor(new Color(0x3a, 0x4a, 0x5a));                  // Teal-blue fill; subtly different from the dark BRICK fill
                    g.fillRoundRect(x, y, width, height, 4, 4);               // Draw the teal rounded rect
                    g.setColor(new Color(0x4a, 0x4a, 0x58));                  // Standard border shared across all fallback platform types
                    g.setStroke(new BasicStroke(0.5f));                        // Thin consistent border
                    g.drawRoundRect(x, y, width, height, 4, 4);               // Outline
                }
                return; // SPRING rendering complete
            }
            case WALL: {                                                        // WALL rendering: uses tile_wall.png; same fallback style as BRICK
                BufferedImage sprite = SpriteLoader.getInstance().load("resources/sprites/tiles/tile_wall.png"); // Load the wall tile sprite
                if (sprite != null) {                                           // Sprite available: draw it
                    g.drawImage(sprite, x, y, width, height, null);            // Draw wall sprite at its position
                } else {                                                        // Fallback: identical to BRICK fallback; sprite sheet distinguishes them
                    g.setColor(new Color(0x2a, 0x2a, 0x35));                  // Dark fill shared with BRICK fallback
                    g.fillRoundRect(x, y, width, height, 4, 4);               // Rounded rect body
                    g.setColor(new Color(0x4a, 0x4a, 0x58));                  // Consistent border
                    g.setStroke(new BasicStroke(0.5f));                        // Thin stroke
                    g.drawRoundRect(x, y, width, height, 4, 4);               // Outline
                }
                return; // WALL rendering complete
            }
            case MIMIC: {                                                       // MIMIC rendering: uses tile_mimic.png; appears identical to normal platform
                BufferedImage sprite = SpriteLoader.getInstance().load("resources/sprites/tiles/tile_mimic.png"); // Load the mimic tile sprite
                if (sprite != null) {                                           // Sprite available; drawn identically to regular BRICK to deceive the Wanderer
                    g.drawImage(sprite, x, y, width, height, null);            // Draw the mimic tile — indistinguishable from normal tile in appearance
                } else {                                                        // Fallback: same dark fill to maintain the deceptive appearance
                    g.setColor(new Color(0x2a, 0x2a, 0x35));                  // Same dark fill as BRICK; mimic must look identical to deceive the player
                    g.fillRoundRect(x, y, width, height, 4, 4);               // Draw the deceptive rounded rect
                    g.setColor(new Color(0x4a, 0x4a, 0x58));                  // Same border colour as normal platforms; part of the deception
                    g.setStroke(new BasicStroke(0.5f));                        // Thin border matches normal tile
                    g.drawRoundRect(x, y, width, height, 4, 4);               // Outline matching normal tiles exactly
                }
                return; // MIMIC rendering complete
            }
            case INVISIBLE:                                                     // INVISIBLE rendering: only visible when lit; ghost outline when unlit
                if (!lit) {                                                     // Unlit INVISIBLE platform: draw a very faint ghost outline for debug/QA
                    g.setColor(new Color(0x2a, 0x2a, 0x55, 40));             // Near-transparent blue-purple: alpha=40, barely perceptible during play
                    g.fillRoundRect(x, y, width, height, 4, 4);               // Faint ghost fill; the Wanderer cannot see this during normal gameplay
                    return;                                                     // Return without drawing the visible form
                }
                // Lit INVISIBLE platform: draw it as a solid blue-glowing surface
                g.setColor(new Color(0x3a, 0x3a, 0x88));                      // Blue fill: signals the platform is illuminated and now solid/traversable
                g.fillRoundRect(x, y, width, height, 4, 4);                   // Draw the glowing blue fill
                g.setColor(new Color(0x4a, 0x4a, 0x58));                      // Standard border colour; same as all other platform types
                g.setStroke(new BasicStroke(0.5f));                            // Thin consistent border
                g.drawRoundRect(x, y, width, height, 4, 4);                   // Outline the lit platform
                return;                                                         // INVISIBLE rendering complete
            default:                                                            // Fallback for any unrecognised type: draw a generic dark tile
                g.setColor(new Color(0x2a, 0x2a, 0x35));                      // Default dark fill matching BRICK
                g.fillRoundRect(x, y, width, height, 4, 4);                   // Generic rounded rect body
                g.setColor(new Color(0x4a, 0x4a, 0x58));                      // Generic border
                g.setStroke(new BasicStroke(0.5f));                            // Thin stroke
                g.drawRoundRect(x, y, width, height, 4, 4);                   // Outline
        }
    }

    // -------------------------------------------------------------------------
    // Core accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the behavioural type of this platform. Used by {@link CollisionDetector}
     * to select the appropriate collision response (slide, spring, crumble, etc.) and
     * by the Apprentice block-placement UI to display the correct type label.
     *
     * @return the {@link PlatformType} of this platform; never {@code null}
     */
    public PlatformType getType() {
        return type; // Return the immutable type set at construction
    }

    /**
     * Returns the block-budget cost the Apprentice must spend to place this platform.
     * Used by {@link GameSession#placeBlock} to deduct from the Apprentice's remaining
     * budget before creating the platform entity.
     *
     * @return the positive integer budget cost for this platform type
     */
    public int getBudgetCost() {
        return budgetCost; // Return the cost computed by resolveBudgetCost() at construction
    }

    /**
     * Sets the budget cost for this platform, allowing runtime balancing adjustments
     * without reconstructing the platform. Primarily used by the test harness.
     *
     * @param budgetCost the new budget cost; should be a positive integer
     */
    public void setBudgetCost(int budgetCost) {
        this.budgetCost = budgetCost; // Overwrite the cost; takes effect on the next GameSession budget check
    }

    /**
     * Returns whether this platform is currently illuminated by the LightBall. Used by
     * {@link CollisionDetector} to gate INVISIBLE platform collision (only solid when lit)
     * and by the LightRenderer mask compositor.
     *
     * @return {@code true} if this platform is within the current light radius; {@code false} otherwise
     */
    public boolean isLit() {
        return lit; // Read by CollisionDetector to decide whether INVISIBLE platforms participate in collision
    }

    /**
     * Sets the illumination state of this platform. Called each frame by
     * {@link LightRenderer} after computing which platforms fall within the LightBall's
     * radius. For INVISIBLE platforms, toggling this to {@code true} makes them solid.
     *
     * @param lit {@code true} to mark as illuminated; {@code false} to extinguish
     */
    public void setLit(boolean lit) {
        this.lit = lit; // Overwrite; CollisionDetector reads this on the same tick for INVISIBLE gating
    }

    // -------------------------------------------------------------------------
    // Solidity accessors
    // -------------------------------------------------------------------------

    /**
     * Returns whether this platform is currently solid and participates in collision
     * resolution. Returns {@code false} once a CRUMBLE platform has begun to fall or a
     * MIMIC platform's delay has expired. {@link CollisionDetector} checks this before
     * performing any overlap test.
     *
     * @return {@code true} if solid and collidable; {@code false} if the platform has
     *         crumbled or collapsed
     */
    public boolean isSolid() {
        return solid; // Read by CollisionDetector.checkPlayerPlatform() at the top of each overlap test
    }

    /**
     * Sets the solidity of this platform. Typically called by {@link #update(long)}
     * internally when the crumble or mimic delay expires; may also be called by the
     * server-authoritative path to force a platform to become passable.
     *
     * @param solid {@code false} to make the platform passable; {@code true} to restore
     */
    public void setSolid(boolean solid) {
        this.solid = solid; // Overwrite; CollisionDetector sees the new value on the next tick
    }

    // -------------------------------------------------------------------------
    // Mimic-state accessors
    // -------------------------------------------------------------------------

    /**
     * Returns whether this MIMIC platform has been touched by the Wanderer and its
     * collapse timer is running. Used by the collision system to avoid re-triggering
     * an already-collapsing mimic.
     *
     * @return {@code true} once the Wanderer has landed on this MIMIC; {@code false} if untouched
     */
    public boolean isMimicTriggered() {
        return mimicTriggered; // Read by CollisionDetector to gate the first-contact logic
    }

    /**
     * Marks this MIMIC platform as having been contacted by the Wanderer, starting the
     * collapse countdown. Called by {@link CollisionDetector} on first contact.
     *
     * @param mimicTriggered {@code true} to begin the collapse sequence
     */
    public void setMimicTriggered(boolean mimicTriggered) {
        this.mimicTriggered = mimicTriggered; // Arm the timer; update() checks this flag every tick
    }

    /**
     * Returns the system-clock timestamp at which this MIMIC platform was first
     * contacted by the Wanderer. Used by {@link #update(long)} to compute elapsed time.
     *
     * @return absolute timestamp in milliseconds from {@link System#currentTimeMillis()},
     *         or {@code -1} if this MIMIC has not yet been triggered
     */
    public long getMimicStartTime() {
        return mimicStartTime; // Read by update() to compute (now - mimicStartTime) vs MIMIC_DELAY_MS
    }

    /**
     * Records the moment of first contact for a MIMIC platform. Called by
     * {@link CollisionDetector} immediately after setting {@link #mimicTriggered} so
     * the delay countdown has an accurate start reference.
     *
     * @param mimicStartTime absolute timestamp from {@link System#currentTimeMillis()}
     */
    public void setMimicStartTime(long mimicStartTime) {
        this.mimicStartTime = mimicStartTime; // Stamp the start time; update() uses it to collapse after MIMIC_DELAY_MS
    }
}
