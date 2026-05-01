/**
 * Light-memory block. Solid and visible while currently lit; after light leaves,
 * the block fades (and stays solid) for a window proportional to how long and
 * how brightly it was previously illuminated. Once the charge drains to zero,
 * the block becomes invisible and non-solid until light reaches it again.
 *
 * <p>Architecture role: extends {@link Platform} with type {@code BRICK} so the
 * existing CollisionDetector path treats it as a normal collision surface
 * (gated by the inherited {@link Platform#isSolid()} check). The block tracks a
 * {@code charge} counter:
 * <ul>
 *   <li>Each tick that the block is lit, {@code charge += chargeRate × intensity}
 *       up to {@code maxCharge}. Brighter / more central illumination charges
 *       faster (intensity factor scales from 0.4 at the edge of the lit radius
 *       to 1.0 at the centre).</li>
 *   <li>Each tick the block is unlit, {@code charge -= decayRate}.</li>
 *   <li>While {@code charge > 0}, {@link #isSolid()} returns {@code true} and
 *       the render method paints the block at an alpha proportional to
 *       {@code charge / maxCharge}.</li>
 *   <li>Once {@code charge} reaches zero, the block disappears and is no longer
 *       collidable — the Wanderer falls right through.</li>
 * </ul>
 *
 * <p>Designed for darkness sections in Acts 2-3 where the Apprentice must
 * "paint" a path with light for the Wanderer to traverse. The persistence
 * mechanic gives the Wanderer a brief window to cross even after the
 * Apprentice's cursor has moved on.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-29
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.awt.AlphaComposite; // Translucent fade based on charge level
import java.awt.Color;           // Block palette
import java.awt.Composite;       // Save/restore the graphics composite around the alpha draw
import java.awt.Graphics2D;       // 2D rendering context

public class PhantomBlock extends Platform implements SpriteOverridable { // Light-charging memory block

    // -------------------------------------------------------------------------
    // Tuning constants
    // -------------------------------------------------------------------------

    /** Maximum charge value; corresponds to ~{@code MAX_CHARGE / chargeRate} ticks of full-intensity light. */
    public static final int DEFAULT_MAX_CHARGE = 1500;

    /** Per-tick gain when fully illuminated; total time to top up = MAX_CHARGE / CHARGE_RATE ticks (≈ 9 s at default). */
    public static final int DEFAULT_CHARGE_RATE = 18;

    /** Per-tick drain when unlit; total persistence after a full charge ≈ MAX_CHARGE / DECAY_RATE ticks (≈ 5 s at default). */
    public static final int DEFAULT_DECAY_RATE = 5;

    private static final Color BLOCK_FILL    = new Color(0x88, 0xB0, 0xD8); // Cool blue tint to read as "ghostly light memory"
    private static final Color BLOCK_OUTLINE = new Color(0x44, 0x60, 0x80); // Slightly darker outline for definition
    private static final Color BLOCK_HIGHLIGHT = new Color(0xE0, 0xEC, 0xF6); // Soft top-edge highlight when fully charged

    // -------------------------------------------------------------------------
    // Per-instance state
    // -------------------------------------------------------------------------

    private int charge;            // Current charge (0..maxCharge); drives solidity + alpha
    private final int maxCharge;   // Configurable per instance via constructor (defaults to DEFAULT_MAX_CHARGE)
    private final int chargeRate;  // Per-tick gain when lit (scaled by intensity)
    private final int decayRate;   // Per-tick drain when unlit
    private float lightIntensity;  // Last-set intensity factor in [0, 1]; written by GameStarter each tick

    private String spritePath;     // Optional sprite override

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Constructs a phantom block with default tuning. Starts with zero charge —
     * the block is invisible / non-solid until first lit.
     *
     * @param x world-space left-edge x in pixels
     * @param y world-space top-edge y in pixels
     * @param w width in pixels
     * @param h height in pixels
     */
    public PhantomBlock(int x, int y, int w, int h) {
        this(x, y, w, h, DEFAULT_MAX_CHARGE, DEFAULT_CHARGE_RATE, DEFAULT_DECAY_RATE);
    }

    /**
     * Full constructor for designers who want to tune persistence per instance.
     *
     * @param x          world-space left-edge x in pixels
     * @param y          world-space top-edge y in pixels
     * @param w          width in pixels
     * @param h          height in pixels
     * @param maxCharge  maximum charge value (caps the persistence window)
     * @param chargeRate per-tick gain when fully lit (intensity = 1.0)
     * @param decayRate  per-tick drain when unlit
     */
    public PhantomBlock(int x, int y, int w, int h,
                        int maxCharge, int chargeRate, int decayRate) {
        super(PlatformType.BRICK, x, y, w, h); // Delegate to Platform — type BRICK so existing collision code treats us as a standard surface
        this.charge = 0;                        // Starts invisible / non-solid
        this.maxCharge = maxCharge;
        this.chargeRate = chargeRate;
        this.decayRate = decayRate;
        this.lightIntensity = 0f;
        this.spritePath = null;
        setSolid(false);                        // Begin non-solid; update() flips this on as charge accumulates
    }

    // -------------------------------------------------------------------------
    // Light-intensity input — written by GameStarter each tick
    // -------------------------------------------------------------------------

    /**
     * Sets the current light intensity at this block in [0, 1]. {@code 0}
     * means fully dark; {@code 1} means at the bright centre of the
     * Apprentice's light. Called once per tick by {@code GameStarter}.
     *
     * @param intensity clamped to [0, 1]
     */
    public void setLightIntensity(float intensity) {
        this.lightIntensity = Math.max(0f, Math.min(1f, intensity)); // Clamp defensively
    }

    /** @return the most recently received light intensity in [0, 1] */
    public float getLightIntensity() { return lightIntensity; }

    /** @return current charge value in [0, maxCharge] */
    public int getCharge() { return charge; }

    // -------------------------------------------------------------------------
    // Per-tick simulation
    // -------------------------------------------------------------------------

    @Override
    public void update(long deltaMs) {
        // Charge / decay model. Use deltaMs / 16 as the tick scaling so the
        // rates are consistent if the game-loop tick rate ever changes.
        // The current loop runs at 16 ms / tick so the multiplier is ~1.
        int ticks = Math.max(1, (int) (deltaMs / 16));
        if (lightIntensity > 0f) {
            // Charge — scaled by intensity so peripheral illumination charges slower.
            int gain = Math.round(chargeRate * lightIntensity) * ticks;
            charge = Math.min(maxCharge, charge + gain);
        } else {
            // Decay — constant rate regardless of how full the block was.
            charge = Math.max(0, charge - decayRate * ticks);
        }
        // Solid only while we have any charge left.
        setSolid(charge > 0);
    }

    // -------------------------------------------------------------------------
    // Render — alpha tracks charge fraction
    // -------------------------------------------------------------------------

    @Override
    public void render(Graphics2D g) {
        if (!isActive()) return;
        if (charge <= 0) return; // Fully drained — invisible and non-solid; rendering would just be a no-op anyway

        float alpha = Math.min(1f, (float) charge / (float) maxCharge);
        Composite oldComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // Sprite override wins; tryDrawSprite respects the alpha composite.
        if (spritePath == null
            || !SpriteOverridable.tryDrawSprite(g, this, x, y, width, height)) {
            // Procedural fallback — cool blue block with a top highlight.
            g.setColor(BLOCK_FILL);
            g.fillRect(x, y, width, height);
            g.setColor(BLOCK_OUTLINE);
            g.drawRect(x, y, width, height);
            // Top highlight stripe — only paints fully when nearly maxed out.
            if (alpha > 0.7f) {
                g.setColor(BLOCK_HIGHLIGHT);
                g.fillRect(x + 2, y + 2, width - 4, 3);
            }
        }

        g.setComposite(oldComposite);
    }

    // -------------------------------------------------------------------------
    // SpriteOverridable
    // -------------------------------------------------------------------------

    @Override public void setSpritePath(String path) { this.spritePath = path; }
    @Override public String getSpritePath() { return spritePath; }
}
