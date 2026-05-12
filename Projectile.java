/**
 Single ranged shot tracking velocity, damage, and cumulative distance. Deactivates
 when distance exceeds maxRange. Manages range/platform collisions; target collision
 against Hazard/Core handled by CollisionDetector.checkProjectileTarget(). Rendered
 as 8×8 golden ellipse with trailing light streak; trail direction from velX sign.
 */


// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 1c "Abstract Classes" - extends GameElement, the abstract base
//                                 covered in the abstract-classes module.
// Module 3a "Graphics"          - render(Graphics2D) override uses 3a's
//                                 drawing primitives (fill, draw, drawImage)
//                                 plus 3a's RenderingHints anti-aliasing.
// Module 3c "Collision"         - getBounds() / getHitbox() returns a
//                                 Rectangle for AABB tests per 3c.
// Module 1a "Modifiers"         - private fields with public accessors;
//                                 where present, static final constants
//                                 follow the constants pattern from 1a.
// =========================================================================
import java.awt.AlphaComposite; // Provides SRC_OVER compositing for the semi-transparent trail circles
import java.awt.Color;           // AWT colour for the gold projectile body and trail (#f0cc7a)
import java.awt.Composite;       // Interface type; saved before and restored after trail rendering
import java.awt.Graphics2D;      // 2D rendering context; used for fillOval on the body and trail circles
import java.awt.Rectangle;       // AWT rectangle for platform-collision bounds tests in update()
import java.util.List;           // List interface for the platforms reference; iterated in update() for wall-collision checks

public class Projectile extends GameElement { // Extends GameElement for entity-list participation; active flag managed by range/collision deactivation

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private float velX; // Horizontal velocity px/tick; +right/-left; used to advance x and compute trail direction

    private float velY; // Vertical velocity px/tick; +down/-up; 0 for flat shots

    private int damage; // Damage on contact; read by CollisionDetector.checkProjectileTarget()

    private int traveledDistance; // Cumulative horizontal pixels traveled; compared to maxRange each tick for auto-deactivation

    private int maxRange; // Maximum travel distance before auto-deactivation (typically 300-400 px)

    private boolean isFromPlayer; // Origin flag: true = player; false = boss-origin; checked by Shield.update() for interception

    private java.util.List<Platform> platforms; // Live platform list; iterated in update() for wall-hit detection; set via setPlatforms()

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public Projectile(int x, int y, float velX, float velY, int damage, int maxRange,
                      boolean isFromPlayer) {   // Construct projectile with explicit velocity, damage, range, and origin flag
        super(x, y, 8, 8);                     // Delegate to GameElement: 8×8 AABB at (x,y); active=true by default
        this.velX = velX;                       // Store horizontal velocity; applied each tick in update()
        this.velY = velY;                       // Store vertical velocity; applied each tick in update()
        this.damage = damage;                   // Store damage value; read by CollisionDetector on hit
        this.traveledDistance = 0;              // Start at zero traveled distance; incremented in update()
        this.maxRange = maxRange;               // Store maximum range; projectile deactivates when traveledDistance >= maxRange
        this.isFromPlayer = isFromPlayer;       // Store origin flag; checked by Shield.update() for interception logic
    }

    public Projectile(int x, int y, float velX, float velY, int damage, int maxRange) { // Player-fired projectile; defaults isFromPlayer=true
        this(x, y, velX, velY, damage, maxRange, true);                                  // Delegate: player-fired shot; Shield.update() will intercept based on isFromPlayer=true
    }

    // -------------------------------------------------------------------------
    // GameElement overrides
    // -------------------------------------------------------------------------

    @Override
    public void update(long deltaMs) {         // Advance by velocity; check range expiry and wall collision; target collision handled by CollisionDetector
        if (!active) {                          // Early exit: already deactivated by a previous hit or range check; no further movement
            return;
        }

        // Move by velocity
        x += (int) velX;                       // Advance x by the integer part of velX; sub-pixel remainder is truncated (acceptable for small projectiles)
        y += (int) velY;                       // Advance y by the integer part of velY; typically 0 for horizontal shots

        // Accumulate distance
        traveledDistance += (int) Math.abs(velX); // Accumulate horizontal pixels traveled; absolute value handles both left and right shots

        // Range check
        if (traveledDistance >= maxRange) {    // Projectile has reached or exceeded its maximum travel range
            setActive(false);                  // Deactivate: the shot fizzles out at the end of its range
            return;                            // Return immediately; no further platform checks needed for a deactivated projectile
        }

        // Platform collision — deactivate on contact with solid tiles
        if (platforms != null) {               // Only check if a platform list reference was provided via setPlatforms()
            Rectangle projBounds = getBounds(); // Get this projectile's 8×8 AABB for intersection testing
            for (Platform pl : platforms) {    // Iterate every platform in the current level
                if (pl.isActive() && pl.isSolid()                   // Only test active solid platforms; non-solid platforms let projectiles pass through
                        && projBounds.intersects(pl.getBounds())) { // AABB overlap: projectile has hit this platform tile
                    setActive(false);                                 // Deactivate: projectile embeds in the wall
                    return;                                           // Stop checking: the projectile is already gone after the first hit
                }
            }
        }
    }

    @Override
    public void render(Graphics2D g) {             // Draw golden ellipse and trailing light circles; trail direction from velX sign
        if (!active) {                              // Do not render inactive projectiles (already spent or range-expired)
            return;
        }

        Composite originalComposite = g.getComposite(); // Save caller's composite before applying per-trail alpha; restored after all drawing

        // Main projectile body — filled 8x8 ellipse
        g.setColor(new Color(0xf0, 0xcc, 0x7a));   // Bright gold colour #f0cc7a; matches glow highlight on Portal and Shield
        g.fillOval(x, y, width, height);             // Draw the filled 8×8 oval at the projectile's current position

        // Trailing light streak — 3 circles behind in opposite direction of travel
        int trailDir = (velX >= 0) ? -1 : 1;        // Trail direction: -1 (left) for right-moving shot; +1 (right) for left-moving shot

        for (int i = 1; i <= 3; i++) {               // Three trail circles: i=1 is closest to the body, i=3 is farthest
            float trailAlpha = 0.5f - (i * 0.15f);  // Alpha decreases with distance: 0.35 at i=1, 0.20 at i=2, 0.05 at i=3
            if (trailAlpha <= 0f) {                  // Clamp: if calculated alpha is zero or negative, use minimum visible value
                trailAlpha = 0.05f;                   // Minimum alpha so the farthest trail circle is just barely visible
            }
            int trailSize = Math.max(2, 8 - (i * 2)); // Trail size shrinks with distance: 6 at i=1, 4 at i=2, 2 at i=3; floor at 2
            int offsetX = trailDir * i * 4;           // Horizontal offset: 4 px per step in the trail direction (behind the projectile)

            g.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, trailAlpha));          // Apply per-trail alpha via SRC_OVER compositing
            g.setColor(new Color(0xf0, 0xcc, 0x7a));               // Same gold colour as body for the trail circles
            int centreX = x + width / 2 + offsetX - trailSize / 2; // Centre-align trail circle: projectile centre + offset - half trail size
            int centreY = y + height / 2 - trailSize / 2;          // Vertically centre trail on projectile centre
            g.fillOval(centreX, centreY, trailSize, trailSize);    // Draw filled trail circle at the computed position
        }

        g.setComposite(originalComposite); // Restore caller's composite mode; prevents alpha leak into subsequent render calls
    }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    public void setPlatforms(java.util.List<Platform> platforms) { // Set platform list for wall-hit detection; must be called before first update
        this.platforms = platforms;                       // Store reference; iterated in update() each tick while the projectile is active
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public float getVelX() { return velX; } // Read accessor; used by render() for trail direction

    public float getVelY() { return velY; } // Read accessor

    public int getDamage() { return damage; } // Read accessor; called by CollisionDetector.checkProjectileTarget()

    public int getTraveledDistance() { return traveledDistance; } // Read accessor

    public int getMaxRange() { return maxRange; } // Read accessor

    public boolean isFromPlayer() { return isFromPlayer; } // Read accessor; checked by Shield.update() for interception
}
