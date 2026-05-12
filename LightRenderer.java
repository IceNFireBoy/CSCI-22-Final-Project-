/**
 * Handles all lighting and darkness-overlay rendering for Lumen Architect, drawing a
 * radial light mask around active light sources to simulate the limited visibility that
 * defines the game's atmosphere. It also paints a corruption overlay whose intensity
 * increases with each Core the Wanderer destroys, reflecting the deteriorating state of
 * the game world.
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
// Module 3b "More Graphics"   - AffineTransform-style usage of Graphics2D
//                               composite save/restore; AlphaComposite
//                               for layered transparency; geometric Area
//                               subtraction (full-canvas rect minus
//                               light ellipse) builds on the
//                               transformations and Path2D ideas from 3b.
// Module 3a "Graphics"        - BufferedImage off-screen buffers,
//                               Graphics2D drawing primitives (fill,
//                               draw, setColor, setComposite),
//                               RenderingHints.KEY_ANTIALIASING per the
//                               Graphics module's anti-alias pattern.
// Module 1a "Modifiers"       - private fields with public accessors;
//                               final constants for layer dimensions.
// (The DST_OUT cut-out technique, RadialGradientPaint, half-resolution
//  shadow-fan buffer, and corruption-overlay polygon fan all go beyond
//  the modules' graphics scope; flagged for external citation in the
//  Cowork research prompt.)
// =========================================================================

import java.awt.AlphaComposite;       // Compositing rules for darkness layers (SRC_OVER) and the DST_OUT cut-out technique
import java.awt.Color;                // AWT colour for darkness fill (black) and the centre-glow dot (semi-transparent white)
import java.awt.Composite;            // Used to save/restore the graphics context's composite before and after compositing the shadow buffer
import java.awt.Graphics2D;           // 2D rendering context passed by GameCanvas each frame
import java.awt.Point;                // Holds the (x, y) screen-space centre of the light source for mask placement
import java.awt.Rectangle;            // Platform AABB rectangle used in shadow-fan cull and corner calculations
import java.awt.RenderingHints;       // Anti-aliasing hints applied to the overlay and half-res buffer graphics contexts
import java.awt.geom.Area;            // Allows geometric subtraction of the light ellipse from the full-canvas rectangle
import java.awt.geom.Ellipse2D;       // Represents the circular light cut-out subtracted from the darkness area
import java.awt.geom.Rectangle2D;    // Full-canvas rectangle used as the starting area for darkness-layer geometry; also used for corruption fill regions
import java.awt.image.BufferedImage;  // Off-screen image used for the darkness overlay and the half-resolution shadow-fan buffer
import java.awt.image.RescaleOp;      // Pixel-level image operation that darkens the camera feed for Core 2 corruption
import java.util.List;                // Platform list iterated in renderShadowFans()

public class LightRenderer {

    private static final int CANVAS_W = 1024; // Width of the game canvas in pixels; all overlay images match this size
    private static final int CANVAS_H = 768;  // Height of the game canvas in pixels

    // Camera feed panel bounds (right 30% of canvas)
    private static final int CAM_X = 717;              // X pixel where the Apprentice's camera feed panel begins
    private static final int CAM_Y = 0;                // Y pixel where the camera feed panel begins (top of canvas)
    private static final int CAM_W = CANVAS_W - CAM_X; // 307 px width of the camera feed panel
    private static final int CAM_H = CANVAS_H;          // 768 px height of the camera feed panel (full height)

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a {@code LightRenderer} ready for use. No resources are loaded at
     * construction time; all rendering state is created per-frame.
     *
     * <p>Architecture role: Instantiated once by {@link GameCanvas} or
     * {@link GameStarter}. All rendering methods accept the graphics context as a
     * parameter rather than holding a reference, making the renderer stateless and
     * safe to call from any thread that holds the graphics context.</p>
     */
    public LightRenderer() {
        // No-arg constructor: this renderer holds no per-instance mutable state
    }

    // -------------------------------------------------------------------------
    // Rendering methods
    // -------------------------------------------------------------------------

    /**
     * Draws a full-canvas darkness mask with a transparent radial cut-out centred on
     * the given light source, simulating a sphere of visibility in an otherwise dark
     * level. Three concentric layers create a soft penumbra effect: a core bright
     * zone, a mid falloff ring, and a faint outer glow.
     *
     * <p>The velocity factor shrinks the effective radius when the light ball is
     * moving fast, simulating the Apprentice's reduced control at speed. The mask is
     * rendered into a separate ARGB off-screen image then composited onto {@code g}
     * in a single {@code drawImage} call to avoid overdraw artifacts.</p>
     *
     * <p>Architecture role: Called each frame by {@link GameCanvas} after the game
     * world is drawn but before the HUD overlay, so darkness covers platforms,
     * crawlers, and the Wanderer but not UI elements.</p>
     *
     * @param g              the {@link Graphics2D} context to draw the mask onto;
     *                       must not be {@code null}
     * @param lightSource    the canvas-space centre of the light source; must not be
     *                       {@code null}
     * @param radius         the pixel radius of the visible area around the light
     *                       source; must be positive
     * @param velocityFactor a value from 0.0 (still, full radius) to 1.0 (fast,
     *                       radius reduced by 40%) representing the Apprentice's
     *                       light velocity
     */
    public void renderLightMask(Graphics2D g, Point lightSource, int radius,
                                float velocityFactor) {
        // Clamp velocity factor and scale radius
        float clamped         = Math.max(0.0f, Math.min(1.0f, velocityFactor)); // Keep velocity factor in [0.0, 1.0]; handles out-of-range inputs
        float scale           = 1.0f - (0.4f * clamped);                        // At max velocity: scale=0.6 → 40% radius reduction; at rest: scale=1.0
        int   effectiveRadius = Math.round(radius * scale);                      // Actual radius applied to darkness layers this frame

        // Create darkness overlay image
        BufferedImage overlay = new BufferedImage(CANVAS_W, CANVAS_H,
                BufferedImage.TYPE_INT_ARGB);             // ARGB overlay: alpha channel allows blending darkness with the canvas below
        Graphics2D og = overlay.createGraphics();         // Graphics context for drawing darkness layers into the off-screen image
        og.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);       // Anti-alias the elliptical cut-outs for smooth light-circle edges

        Rectangle2D fullCanvas = new Rectangle2D.Double(0, 0, CANVAS_W, CANVAS_H); // Full-canvas rectangle used as the base for Area subtraction
        double cx = lightSource.x; // Light centre X in canvas space; used by all three darkness layers
        double cy = lightSource.y; // Light centre Y in canvas space

        // Layer 1 — outer faint glow (radius + 45px, alpha 0.18)
        drawDarknessLayer(og, fullCanvas, cx, cy, effectiveRadius + 45, 0.18f); // Faint outer glow: 18% black at extended radius creates a soft halo boundary

        // Layer 2 — mid falloff ring (radius + 20px, alpha 0.55)
        drawDarknessLayer(og, fullCanvas, cx, cy, effectiveRadius + 20, 0.55f); // Mid penumbra: 55% black at +20px creates the visible falloff transition

        // Layer 3 — core darkness (exact radius, alpha 0.92)
        drawDarknessLayer(og, fullCanvas, cx, cy, effectiveRadius, 0.92f); // Core darkness: 92% black at exact radius; nearly opaque outside the light circle

        og.dispose(); // Release the overlay graphics context; frees native resources

        // Composite the overlay onto the game canvas
        g.drawImage(overlay, 0, 0, null); // Stamp the completed darkness mask onto the game canvas in one operation
    }

    /**
     * P8.9 — BOSS-phase overload that accepts a {@code fullArenaReveal} override.
     * When {@code fullArenaReveal} is {@code true} (Radiant Collapse ACTIVE),
     * the entire darkness overlay is skipped so the full arena is visible for
     * the 3-second window regardless of the light's position or radius. When
     * {@code false} the method delegates to the existing four-argument
     * implementation and behaves exactly as before, so callers can swap to
     * this overload unconditionally without changing per-frame output while
     * Radiant is idle.
     *
     * <p>Architecture role: Called during the BOSS phase in place of the
     * four-argument overload. The boolean parameter is driven by
     * {@link Player#isRadiantCollapseActive()}, which the Radiant Collapse FSM
     * sets to {@code true} for 3 seconds after SHIFT is released.</p>
     *
     * @param g                the {@link Graphics2D} context; must not be {@code null}
     * @param lightSource      the canvas-space centre of the light source
     * @param radius           the pixel radius of the visible area
     * @param velocityFactor   velocity scaling factor; see
     *                         {@link #renderLightMask(Graphics2D, Point, int, float)}
     * @param fullArenaReveal  {@code true} to skip the darkness mask entirely for
     *                         the Radiant Collapse full-arena reveal
     */
    public void renderLightMask(Graphics2D g, Point lightSource, int radius,
                                float velocityFactor, boolean fullArenaReveal) {
        if (fullArenaReveal) {
            // Radiant Collapse ACTIVE — no darkness overlay, arena fully visible.
            // The caller is also expected to suppress renderShadowFans for the
            // same window so the boss silhouettes are not painted over the
            // otherwise unlit platforms that the reveal is supposed to expose.
            return; // Skip all darkness rendering; the canvas remains fully lit for the reveal window
        }
        renderLightMask(g, lightSource, radius, velocityFactor); // Radiant inactive: delegate to the standard four-argument implementation
    }

    /**
     * D4 — Convenience overload that creates a simple concentric-ring light mask
     * without velocity factor scaling. Used for Act 2/3 gesture-controlled light
     * where the light ball does not have physics-based velocity.
     *
     * <p>Architecture role: Called by {@link GameCanvas} during ACT2 and ACT3 where
     * {@link MouseApprentice} positions the light without inertia physics. The
     * DST_OUT technique used here differs from the Area-subtraction approach in the
     * four-argument overload but produces a visually similar result.</p>
     *
     * @param g           the {@link Graphics2D} context to draw the mask onto; must
     *                    not be {@code null}
     * @param lightSource the canvas-space centre of the light source
     * @param radius      the pixel radius of the bright zone
     */
    public void renderLightMask(Graphics2D g, Point lightSource, int radius) {
        BufferedImage darkness = new BufferedImage(CANVAS_W, CANVAS_H, BufferedImage.TYPE_INT_ARGB); // ARGB off-screen image for the concentric-ring darkness mask
        Graphics2D dg = darkness.createGraphics();                                                    // Graphics context for building the mask layers
        dg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);    // Smooth ellipse edges for the cut-out ovals

        dg.setColor(new Color(0, 0, 0, 235));                                    // Nearly-opaque black (alpha=235/255 ≈ 92%); fills the entire canvas with darkness
        dg.fillRect(0, 0, CANVAS_W, CANVAS_H);                                  // Base layer: dark background over the whole canvas

        dg.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OUT));    // DST_OUT: punches a transparent hole through the layers below wherever we fill
        dg.setColor(new Color(0, 0, 0, 255));                                    // Fully opaque DST_OUT fill: creates a fully transparent circle at the light centre
        dg.fillOval(lightSource.x - radius, lightSource.y - radius,
                    radius * 2, radius * 2);                                     // Core bright circle: completely clears the darkness within the base radius

        dg.setColor(new Color(0, 0, 0, 140));                                   // Semi-transparent DST_OUT: partial punch-through for the mid-glow ring
        int r2 = radius + 25;                                                    // Mid-glow radius: 25 px beyond the core bright zone
        dg.fillOval(lightSource.x - r2, lightSource.y - r2, r2 * 2, r2 * 2);  // Mid-glow ring: alpha=140 DST_OUT creates a partial transparency falloff

        dg.setColor(new Color(0, 0, 0, 60));                                    // Very light DST_OUT: barely clears the darkness for the faint outer halo
        int r3 = radius + 50;                                                    // Outer halo radius: 50 px beyond the core
        dg.fillOval(lightSource.x - r3, lightSource.y - r3, r3 * 2, r3 * 2);  // Outer halo: alpha=60 DST_OUT creates a barely-visible glow at the perimeter

        dg.dispose();                           // Release the darkness graphics context
        g.drawImage(darkness, 0, 0, null);      // Composite the completed concentric-ring mask onto the game canvas

        // Small white dot at light source center
        g.setColor(new Color(255, 255, 255, 120)); // Semi-transparent white: visible inner glow dot at the light ball centre
        g.fillOval(lightSource.x - 4, lightSource.y - 4, 8, 8); // 8×8 px white dot marks the exact light position for the Apprentice
    }

    /**
     * P8.4 — Casts hard-edged shadows from every opaque platform that falls within
     * {@code 2 * SHADOW_CULL_RADIUS} of the boss {@link LightBall}. For each such
     * platform the four world-space corners are converted to screen space, then
     * projected along the ray from the light source outward to a point well beyond
     * the visible canvas; the resulting four edge-extrusion quads are filled with
     * opaque black on a half-resolution off-screen buffer. The buffer is then
     * composited onto {@code g} with {@link AlphaComposite#SRC_OVER} at the same
     * {@code 0.92f} alpha used by the core darkness layer of
     * {@link #renderLightMask(Graphics2D, Point, int, float)} so shadow regions
     * inside the light circle fade back to the same "lit darkness" tone as the
     * rest of the arena.
     *
     * <p>Half-resolution (512 × 384) buffering plus a simple world-space cull keep
     * the worst case of ~20 arena platforms × 4 extrusion quads = ~80 filled
     * polygons per frame inside the 4 ms shadow budget called out in the Phase 8
     * plan. Platforms with the light ball inside their AABB are skipped to avoid
     * degenerate shadow geometry, and invisible (unlit {@code INVISIBLE})
     * platforms cast no shadow since they cast no visible silhouette.</p>
     *
     * <p>Architecture role: Called by {@link GameCanvas} each frame during the BOSS
     * phase after {@link #renderLightMask} so shadows overlay the darkness mask
     * correctly. Suppressed when Radiant Collapse is ACTIVE.</p>
     *
     * @param g     the destination graphics context (screen-space, full canvas);
     *              must not be {@code null}
     * @param lb    the server-authoritative light ball providing the world-space
     *              shadow-casting origin; must not be {@code null}
     * @param plats list of candidate platforms to cast shadows from; may be
     *              empty but must not be {@code null}
     */
    public void renderShadowFans(Graphics2D g, LightBall lb, java.util.List<Platform> plats) {
        if (g == null || lb == null || plats == null || plats.isEmpty()) return; // Null/empty guard: skip entirely if dependencies are missing

        Camera cam = Camera.getInstance(); // Get the singleton camera to transform world coordinates to screen coordinates

        // Half-resolution off-screen buffer (perf guard from plan risk #1).
        final int halfW = CANVAS_W / 2; // 512 px: half the canvas width for the off-screen shadow buffer
        final int halfH = CANVAS_H / 2; // 384 px: half the canvas height
        BufferedImage buf = new BufferedImage(halfW, halfH, BufferedImage.TYPE_INT_ARGB); // Half-res ARGB buffer for shadow geometry
        Graphics2D bg = buf.createGraphics();                                              // Graphics context for drawing filled shadow quads into the buffer
        // Intentionally leave antialiasing OFF so shadow edges stay hard.
        bg.setColor(Color.BLACK);                                                                         // Shadow fill colour: opaque black
        bg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));                     // Full opacity: shadows are completely opaque in the buffer

        final float lbX = lb.getX(); // Light ball world X; used as the ray origin for all shadow projections
        final float lbY = lb.getY(); // Light ball world Y

        // Light position in half-resolution screen space.
        final float lightScreenX = cam.worldToScreenX(Math.round(lbX)) * 0.5f; // Convert light world X to screen X then halve for the half-res buffer
        final float lightScreenY = cam.worldToScreenY(Math.round(lbY)) * 0.5f; // Convert light world Y to screen Y then halve for the half-res buffer

        // World-space cull radius: 2 × the light mask's effective boss radius.
        final float cullRadius   = 2f * SHADOW_CULL_RADIUS;         // 720 px world-space cull zone; platforms outside this are too far to cast visible shadows
        final float cullRadiusSq = cullRadius * cullRadius;          // Pre-compute squared radius to avoid sqrt in the cull check

        // Extension length in half-resolution pixels. The half-res buffer is
        // 512 × 384 (diagonal ≈ 640), so 2000 guarantees the far vertex exits
        // the buffer regardless of corner position.
        final float FAR = 2000f; // Extended projection length; guarantees the shadow quad extends completely off the visible area

        for (Platform plat : plats) {                                  // Iterate every platform in the level
            if (plat == null || !plat.isSolid()) continue;             // Skip non-solid platforms: they do not block light
            // Unlit INVISIBLE platforms are not visible, so they should not
            // cast a silhouette. Lit ones fall through and behave like BRICKs.
            if (plat.getType() == Platform.PlatformType.INVISIBLE && !plat.isLit()) continue; // Skip unlit INVISIBLE: invisible to the Wanderer means no shadow

            Rectangle bnds = plat.getBounds(); // Get the platform AABB in world space for corner and cull calculations

            // Skip when the light is inside the platform — degenerate geometry.
            if (lbX > bnds.x && lbX < bnds.x + bnds.width
                    && lbY > bnds.y && lbY < bnds.y + bnds.height) {
                continue; // Light ball is inside this platform AABB: shadow geometry would be degenerate (inverted quads); skip
            }

            // World-space distance from light to the nearest point on the AABB.
            float nearestX = Math.max(bnds.x, Math.min(lbX, bnds.x + bnds.width));  // Clamp light X to the platform's x-range
            float nearestY = Math.max(bnds.y, Math.min(lbY, bnds.y + bnds.height)); // Clamp light Y to the platform's y-range
            float ndx      = nearestX - lbX;                                          // X distance from light to nearest point
            float ndy      = nearestY - lbY;                                          // Y distance from light to nearest point
            if (ndx * ndx + ndy * ndy > cullRadiusSq) continue;                      // Platform's nearest point is outside cull radius: skip

            // Screen-space corners at half resolution.
            float px = cam.worldToScreenX(bnds.x) * 0.5f;      // Platform left edge in half-res screen space
            float py = cam.worldToScreenY(bnds.y) * 0.5f;      // Platform top edge in half-res screen space
            float pw = bnds.width  * 0.5f;                      // Platform width scaled to half-res
            float ph = bnds.height * 0.5f;                      // Platform height scaled to half-res

            float[] cxs = { px,      px + pw, px + pw, px      }; // X coords of the 4 platform corners in half-res screen space (TL, TR, BR, BL)
            float[] cys = { py,      py,      py + ph, py + ph }; // Y coords of the 4 platform corners in half-res screen space

            // Extend each corner along the ray from the light through it.
            float[] exs = new float[4]; // X coords of the 4 extended (far) points for shadow projection
            float[] eys = new float[4]; // Y coords of the 4 extended (far) points
            for (int i = 0; i < 4; i++) {
                float vx  = cxs[i] - lightScreenX;            // Vector from light to this corner (X component)
                float vy  = cys[i] - lightScreenY;            // Vector from light to this corner (Y component)
                float len = (float) Math.sqrt(vx * vx + vy * vy); // Distance from light to this corner
                if (len < 0.01f) {
                    exs[i] = cxs[i]; // Corner is essentially at the light: extended point is the same as the corner
                    eys[i] = cys[i]; // (degenerate case; practically unreachable given the inside-platform guard above)
                } else {
                    float scale = FAR / len;             // Scale factor to extend the vector to FAR pixels from the light
                    exs[i] = cxs[i] + vx * scale;       // Extended point X: corner + (normalized direction × FAR)
                    eys[i] = cys[i] + vy * scale;       // Extended point Y
                }
            }

            // Four edge-extrusion quads form the full shadow region. Back-facing
            // edges extrude outward into shadow; front-facing edges extrude
            // inward and are masked by the platform itself on the main render.
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) & 3; // Next corner index (wraps: 3→0); each pair of adjacent corners forms one quad edge
                int[] xs = {
                        Math.round(cxs[i]), Math.round(cxs[j]), // Near edge: platform corner i and corner j
                        Math.round(exs[j]), Math.round(exs[i])  // Far edge: corresponding extended shadow points
                };
                int[] ys = {
                        Math.round(cys[i]), Math.round(cys[j]),
                        Math.round(eys[j]), Math.round(eys[i])
                };
                bg.fillPolygon(xs, ys, 4); // Fill the trapezoidal shadow quad (platform edge + two projection rays + extended edge)
            }
        }

        bg.dispose(); // Release the half-res buffer graphics context; frees native GC resources

        // Composite the half-res buffer onto the full canvas at the core darkness
        // alpha, matching the base layer of renderLightMask so shadow regions
        // inside the light circle tone-match the arena dark.
        Composite prev = g.getComposite();                                               // Save current composite so it can be restored after shadow draw
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.92f));    // Match the 0.92f alpha of the core darkness layer for visual consistency
        g.drawImage(buf, 0, 0, CANVAS_W, CANVAS_H, null);                              // Upscale the 512×384 shadow buffer to full 1024×768 canvas size
        g.setComposite(prev);                                                            // Restore the composite so subsequent draws are unaffected
    }

    /**
     * World-space cull radius used by {@link #renderShadowFans}. Platforms whose
     * AABB has no point within this distance of the {@link LightBall} are skipped,
     * keeping per-frame polygon count bounded even in dense arena layouts.
     */
    private static final int SHADOW_CULL_RADIUS = 360; // 360 px world-space; at BOSS scale this covers roughly 11 tile widths from the light

    /**
     * Draws a single darkness layer: fills the canvas area minus a circular cut-out
     * with black at the given alpha. Called three times per frame by the four-argument
     * {@link #renderLightMask} to build the soft penumbra effect.
     *
     * <p>Architecture role: Encapsulates the {@link Area} subtraction pattern so the
     * three-layer darkness construction in {@link #renderLightMask} is readable as
     * three concise calls with different radii and alphas.</p>
     *
     * @param og         the overlay graphics context (drawing into the ARGB off-screen image)
     * @param fullCanvas rectangle covering the full canvas; used as the base Area
     * @param cx         light source x in canvas space
     * @param cy         light source y in canvas space
     * @param r          radius of the light cut-out for this layer
     * @param alpha      opacity of the darkness fill (0.0 = transparent, 1.0 = opaque)
     */
    private void drawDarknessLayer(Graphics2D og, Rectangle2D fullCanvas,
                                   double cx, double cy, int r, float alpha) {
        Area darkness = new Area(fullCanvas);                                      // Start with the full-canvas rectangle as the dark region
        Ellipse2D lightEllipse = new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2); // Circular cut-out centred at (cx, cy) with radius r
        darkness.subtract(new Area(lightEllipse));                                 // Punch the light circle out of the darkness area

        og.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)); // Set the fill opacity for this layer
        og.setColor(Color.BLACK);                                                      // Black fill: the colour of darkness
        og.fill(darkness);                                                             // Fill the donut-shaped area (canvas minus light circle) with semi-transparent black
    }

    /**
     * Overlays corruption effects on the camera feed panel based on which Cores have
     * been destroyed. Each destroyed Core applies a distinct visual penalty to a
     * portion of the camera feed, progressively degrading the Apprentice's view.
     * <p>
     * The camera feed image is passed in so that Core 2's {@link RescaleOp} can
     * operate on actual pixel data. The caller should pass the current camera frame
     * (already drawn to the panel area) as {@code cameraFeedImage}.
     *
     * <p>Corruption severity by Core:
     * <ul>
     *   <li>Core 3 destroyed: total blackout of the entire camera panel (early return).</li>
     *   <li>Core 2 destroyed: 65% brightness reduction via {@link RescaleOp(0.35f)}.</li>
     *   <li>Core 0 destroyed: black rectangle over the bottom-left quadrant of the panel.</li>
     *   <li>Core 1 destroyed: black rectangle over the top-right quadrant of the panel.</li>
     * </ul></p>
     *
     * <p>Architecture role: Called by {@link GameCanvas} each frame during ACT2 and
     * BOSS phases after the camera feed is drawn to the right panel. Core destruction
     * state is read from {@link LevelState} and passed in as the boolean array.</p>
     *
     * @param g               the {@link Graphics2D} context to draw the corruption
     *                        overlay onto; must not be {@code null}
     * @param destroyedCores  a four-element boolean array where index {@code i} is
     *                        {@code true} if Core {@code i} has been destroyed; must
     *                        not be {@code null} and must have length 4
     * @param cameraFeedImage the current camera feed as a {@link BufferedImage},
     *                        used by Core 2 corruption for {@link RescaleOp}
     *                        darkening; may be {@code null} if Core 2 is intact
     */
    public void renderCorruption(Graphics2D g, boolean[] destroyedCores,
                                 BufferedImage cameraFeedImage) {
        // Core 3 destroyed: total blackout of camera panel
        if (destroyedCores[3]) {                                                           // Core 3 (final core) destroyed: most severe corruption
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));   // Fully opaque: total blackout leaves no visibility
            g.setColor(Color.BLACK);                                                        // Black fill for the complete camera panel blackout
            g.fill(new Rectangle2D.Double(CAM_X, CAM_Y, CAM_W, CAM_H));                  // Cover the entire 307×768 camera panel with black
            return; // Total blackout: no further corruption effects needed (they would be invisible anyway)
        }

        // Core 2 destroyed: apply RescaleOp to darken the entire camera panel
        if (destroyedCores[2] && cameraFeedImage != null) {             // Core 2 destroyed and camera feed available for processing
            RescaleOp darken = new RescaleOp(0.35f, 0, null);          // RescaleOp with scale=0.35: multiplies each RGB channel by 0.35 → 65% darker
            BufferedImage darkened = darken.filter(cameraFeedImage, null); // Apply the darkening op to produce a new image; null dst = allocate new buffer
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)); // Fully opaque: replace the camera panel with the darkened image
            g.drawImage(darkened, CAM_X, CAM_Y, null);                  // Stamp the darkened camera feed over the original panel area
        }

        // Core 0 destroyed: black rectangle over bottom-left of camera panel
        if (destroyedCores[0]) {                                                          // Core 0 destroyed: first quadrant corruption
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)); // Fully opaque black rectangle
            g.setColor(Color.BLACK);
            g.fill(new Rectangle2D.Double(CAM_X, 384, 153, 384));      // Bottom-left 153×384 px of the camera panel is blacked out
        }

        // Core 1 destroyed: black rectangle over top-right of camera panel
        if (destroyedCores[1]) {                                                          // Core 1 destroyed: second quadrant corruption
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)); // Fully opaque black rectangle
            g.setColor(Color.BLACK);
            g.fill(new Rectangle2D.Double(870, 0, 154, 384));           // Top-right 154×384 px of the camera panel is blacked out
        }
    }
}
