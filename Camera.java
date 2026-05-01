/**
 * World-space camera for the boss arena. Tracks a target world position (usually the
 * Wanderer) and lerps {@link #worldX}/{@link #worldY} toward it each frame at a fixed
 * blend rate. Provides {@link #screenToWorldX(int)}/{@link #worldToScreenX(int)} helpers
 * so rendering, input handling, and the light-mask renderer can agree on a single
 * coordinate space.
 *
 * <p>The camera is a singleton so {@link GameCanvas}, {@link InputRouter}, and
 * {@link LightRenderer} can share the same view state without passing it through
 * constructors or method parameters. Coordinates stay in the canvas's native pixel
 * space — no zoom, no rotation, only pan.</p>
 *
 * <p>Architecture role: During the BOSS phase {@link GameCanvas#renderBoss(java.awt.Graphics2D)}
 * calls {@link #follow(Player)} to track the Wanderer, then {@link #update(float)} to
 * advance the lerp, and finally uses {@link #getRenderOffsetX()} / {@link #getRenderOffsetY()}
 * to compute the {@code Graphics2D.translate} value that shifts all world-space entities
 * into screen space. {@link InputRouter} calls {@link #screenToWorldX(int)} /
 * {@link #screenToWorldY(int)} to convert mouse click coordinates into world space
 * before routing them to boss-attack targeting. {@link LightRenderer} uses the same
 * helpers to position the darkness mask on the correct world position.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-17
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */
public class Camera { // Singleton 2D pan camera; no zoom/rotation; boss arena only

    /**
     * Per-frame linear blend factor for lerping toward the camera target. A value of
     * {@code 0.1f} means the camera closes 10% of the remaining gap to the target each
     * frame, producing a smooth "rubber band" follow feel. At 60 fps this converges to
     * within 1 pixel of the target in approximately 30 frames (0.5 s).
     */
    public static final float CAMERA_LERP = 0.1f; // 10% blend per frame = smooth follow; increase for snappier tracking, decrease for more lag

    /**
     * Arena width in world-space pixels. Mirror of the arena dimensions used by
     * {@link BossArenaGenerator} and {@link GameServer}. Used by {@link #clamp()} to
     * prevent the camera from showing world space beyond the arena boundary.
     */
    public static final int ARENA_W = 3072; // 3072 px = 96 tiles × 32 px/tile; matches BossArenaGenerator.ARENA_W

    /**
     * Arena height in world-space pixels. Mirror of the arena dimensions.
     * Symmetric with {@link #ARENA_W}.
     */
    public static final int ARENA_H = 2304; // 2304 px = 72 tiles × 32 px/tile; matches BossArenaGenerator.ARENA_H

    private static final Camera INSTANCE = new Camera(); // Eagerly created singleton — safe because constructor has no external dependencies

    /**
     * Returns the process-wide camera singleton.
     *
     * <p>Architecture role: Called by {@link GameCanvas}, {@link InputRouter},
     * {@link LightRenderer}, and {@link GameStarter} to obtain the shared camera.
     * Because the instance is eagerly created, this method never returns {@code null}
     * and never needs synchronisation.</p>
     *
     * @return the shared {@code Camera}; never {@code null}
     */
    public static Camera getInstance() { return INSTANCE; } // Static factory for the singleton; always returns the same object

    private float worldX;   // Current camera centre in world-space x; lerped toward targetX each frame
    private float worldY;   // Current camera centre in world-space y; lerped toward targetY each frame
    private float targetX;  // Desired camera centre in world-space x; set by follow() / followPoint()
    private float targetY;  // Desired camera centre in world-space y; set by follow() / followPoint()

    private Camera() {   // Private constructor — enforces singleton; initialises by calling reset()
        reset();         // Set worldX/Y and targetX/Y to the arena centre as a safe default
    }

    /**
     * Resets the camera to the arena centre with no in-flight lerp (position equals
     * target). Called in the constructor and by {@link GameStarter#enterBossArena(long)}
     * before {@link #snapTo(float, float)} is called for the exact spawn point, ensuring
     * there is no residual lerp state from a previous session.
     *
     * <p>Architecture role: Ensures that entering the boss arena always starts from a
     * known, neutral camera position rather than whatever world coordinates were in use
     * during Act 1–3 traversal.</p>
     */
    public void reset() {                   // Zero-lag reset to arena centre; call before entering the boss arena
        this.worldX = ARENA_W / 2f;        // Place the camera centre at the horizontal midpoint of the arena
        this.worldY = ARENA_H / 2f;        // Place the camera centre at the vertical midpoint of the arena
        this.targetX = worldX;             // Align target with position so no lerp is triggered on the first update()
        this.targetY = worldY;             // Symmetric with targetX
    }

    /**
     * Instantly moves both the camera position and the target to the given world
     * coordinates, bypassing the lerp. Used at boss-arena entry to eliminate the
     * visual pop that would occur if the camera had to lerp from an arbitrary
     * pre-boss position.
     *
     * <p>Architecture role: Called by {@link GameStarter#enterBossArena(long)} after
     * the Wanderer is placed at the arena spawn ({@code BossArenaGenerator.CENTER_X},
     * {@code ARENA_H - 256}) so the first rendered frame shows the spawn point, not
     * the arena centre.</p>
     *
     * @param x the target world-space x to snap to
     * @param y the target world-space y to snap to
     */
    public void snapTo(float x, float y) { // Instant teleport — no lerp; use at level-entry to prevent camera pop
        this.worldX = x;                   // Move camera position directly to x
        this.worldY = y;                   // Move camera position directly to y
        this.targetX = x;                  // Align target so update() produces no movement on the first frame
        this.targetY = y;                  // Symmetric with targetX
        clamp();                           // Ensure the snapped position respects arena boundary constraints
    }

    /**
     * Sets the camera target to the given {@link Player}'s world-space position.
     * The actual camera position will lerp toward this target on the next
     * {@link #update(float)} call.
     *
     * <p>Architecture role: Called every frame by {@link GameCanvas#renderBoss} as the
     * first step in the boss-arena render pipeline, before {@link #update(float)}.
     * The player's position may have changed since the last frame (physics moved them),
     * so this call always reflects the latest position.</p>
     *
     * @param p the player to follow; no-op if {@code p} is {@code null}
     */
    public void follow(Player p) {        // Update the lerp target to track the given player's world position
        if (p == null) return;            // Null guard: if no player exists (e.g. before spawn), do nothing
        this.targetX = p.getX();          // Set horizontal target to the player's current world-space x
        this.targetY = p.getY();          // Set vertical target to the player's current world-space y
    }

    /**
     * Sets the camera target to an explicit world-space point rather than tracking a
     * {@link Player}. Used for scripted camera movements (e.g. panning to a Core
     * during a destruction cutscene).
     *
     * <p>Architecture role: A more flexible variant of {@link #follow(Player)} for
     * situations where the desired target is a fixed world point rather than a
     * moving entity.</p>
     *
     * @param x the world-space x coordinate to pan toward
     * @param y the world-space y coordinate to pan toward
     */
    public void followPoint(float x, float y) { // Set explicit world-space lerp target; used for scripted camera pans
        this.targetX = x;                       // Set horizontal target to the given world x
        this.targetY = y;                       // Set vertical target to the given world y
    }

    /**
     * Advances the camera position one step toward the current target using linear
     * interpolation at rate {@link #CAMERA_LERP}. The {@code dt} parameter is accepted
     * for API symmetry with a time-based update loop but the lerp uses a fixed
     * per-frame blend rate matched to the 60 fps client loop.
     *
     * <p>Architecture role: Called once per frame by {@link GameCanvas#renderBoss(java.awt.Graphics2D)}
     * after {@link #follow(Player)} updates the target. The resulting worldX/Y values
     * drive {@link #getRenderOffsetX()} / {@link #getRenderOffsetY()}, which are applied
     * as the {@code Graphics2D.translate} in {@code renderBoss}.</p>
     *
     * @param dt time since last frame in seconds (accepted but not used in the current
     *           fixed-rate lerp implementation)
     */
    public void update(float dt) {                                  // Advance the lerp by one step; call once per rendered frame
        worldX += (targetX - worldX) * CAMERA_LERP;                // Linear interpolate x toward target: move 10% of the remaining gap
        worldY += (targetY - worldY) * CAMERA_LERP;                // Linear interpolate y toward target: move 10% of the remaining gap
        clamp();                                                    // Keep the camera centre within arena bounds after lerp
    }

    /**
     * Clamps the camera's world position so that the visible viewport (canvas-sized
     * window centred on worldX/Y) never shows area outside the arena boundary.
     * Called by {@link #update(float)} and {@link #snapTo(float, float)}.
     *
     * <p>The clamp limits are half the canvas width/height from each arena edge,
     * which is the minimum worldX/Y that keeps the left/top edge of the canvas at
     * world coordinate 0.</p>
     */
    private void clamp() {                                          // Prevent the viewport from displaying world space outside the 3072×2304 arena
        float halfW = GameCanvas.CANVAS_WIDTH / 2f;                 // Half the canvas width (512 px) — minimum safe camera x from the left edge
        float halfH = GameCanvas.CANVAS_HEIGHT / 2f;                // Half the canvas height (384 px) — minimum safe camera y from the top edge
        if (worldX < halfW) worldX = halfW;                         // Clamp left: don't allow the camera to show space left of x=0
        if (worldY < halfH) worldY = halfH;                         // Clamp top: don't allow the camera to show space above y=0
        if (worldX > ARENA_W - halfW) worldX = ARENA_W - halfW;    // Clamp right: don't allow the camera to show space right of arena boundary
        if (worldY > ARENA_H - halfH) worldY = ARENA_H - halfH;    // Clamp bottom: don't allow the camera to show space below arena boundary
    }

    /**
     * Returns the current camera centre world-space x coordinate (after lerp and clamp).
     *
     * @return world-space x of the camera centre in pixels
     */
    public float getWorldX() { return worldX; } // Read accessor; used by screenToWorldX/worldToScreenX coordinate transforms

    /**
     * Returns the current camera centre world-space y coordinate (after lerp and clamp).
     *
     * @return world-space y of the camera centre in pixels
     */
    public float getWorldY() { return worldY; } // Read accessor; used by screenToWorldY/worldToScreenY coordinate transforms

    /**
     * Returns the x translation offset that a {@code Graphics2D.translate} call should
     * apply to convert world-space entity coordinates to screen space. A negative worldX
     * shifts the world to the right; a positive offset shifts entities left relative to
     * the viewport.
     *
     * <p>Architecture role: Used by {@link GameCanvas#renderBoss(java.awt.Graphics2D)}:
     * {@code g.translate(cam.getRenderOffsetX(), cam.getRenderOffsetY())} is called
     * before rendering all world-space entities so they appear at the correct screen
     * position relative to the camera.</p>
     *
     * @return the Graphics2D x translation in pixels (may be negative)
     */
    public float getRenderOffsetX() {                                     // Translate offset for Graphics2D: shifts world coords into screen space
        return -worldX + GameCanvas.CANVAS_WIDTH / 2f;                   // -worldX moves the world left by worldX pixels; +CANVAS_WIDTH/2 re-centres on screen
    }

    /**
     * Returns the y translation offset for {@code Graphics2D.translate}. Symmetric with
     * {@link #getRenderOffsetX()}.
     *
     * @return the Graphics2D y translation in pixels (may be negative)
     */
    public float getRenderOffsetY() {                                     // Y component of the world-to-screen translate offset
        return -worldY + GameCanvas.CANVAS_HEIGHT / 2f;                  // -worldY + halfH: centres the camera's world position on the middle of the screen
    }

    /**
     * Converts a screen-space x coordinate to world-space. Accounts for the current
     * camera position. Used by {@link InputRouter} to transform mouse click x into
     * world space for boss-attack targeting.
     *
     * <p>Inverse of {@link #worldToScreenX(int)}.</p>
     *
     * @param sx the screen-space x coordinate (pixels from left edge of canvas)
     * @return the corresponding world-space x coordinate (pixels from left edge of arena)
     */
    public int screenToWorldX(int sx) {                                   // Inverse of worldToScreenX; used by InputRouter for click-to-world conversion
        return Math.round(sx + worldX - GameCanvas.CANVAS_WIDTH / 2f);   // Add sx offset, then correct for camera position and canvas centring
    }

    /**
     * Converts a screen-space y coordinate to world-space. Accounts for the current
     * camera position. Used by {@link InputRouter} for mouse y → world y conversion
     * during the BOSS phase.
     *
     * <p>Inverse of {@link #worldToScreenY(int)}.</p>
     *
     * @param sy the screen-space y coordinate (pixels from top edge of canvas)
     * @return the corresponding world-space y coordinate (pixels from top edge of arena)
     */
    public int screenToWorldY(int sy) {                                   // Inverse of worldToScreenY; symmetric with screenToWorldX
        return Math.round(sy + worldY - GameCanvas.CANVAS_HEIGHT / 2f);  // Add sy offset, correct for camera position and canvas centring
    }

    /**
     * Converts a world-space x coordinate to screen-space. Used by {@link GameCanvas}
     * to position the light mask on screen from the server-authoritative LightBall
     * world coordinates.
     *
     * <p>Inverse of {@link #screenToWorldX(int)}.</p>
     *
     * @param wx the world-space x coordinate (pixels from left edge of arena)
     * @return the corresponding screen-space x coordinate (pixels from left edge of canvas)
     */
    public int worldToScreenX(int wx) {                                   // World → screen conversion; used by GameCanvas to position the LightBall mask
        return Math.round(wx - worldX + GameCanvas.CANVAS_WIDTH / 2f);   // Subtract camera position, then add half-canvas to centre the world point
    }

    /**
     * Converts a world-space y coordinate to screen-space. Symmetric with
     * {@link #worldToScreenX(int)}.
     *
     * @param wy the world-space y coordinate (pixels from top edge of arena)
     * @return the corresponding screen-space y coordinate (pixels from top edge of canvas)
     */
    public int worldToScreenY(int wy) {                                   // World → screen y conversion; symmetric with worldToScreenX
        return Math.round(wy - worldY + GameCanvas.CANVAS_HEIGHT / 2f);  // Subtract camera y position, add half-canvas height to re-centre
    }
}
