/**
 * Provides AABB collision tests and resolution between the primary game entities in
 * Lumen Architect. Each public method handles one entity-pair scenario so call sites
 * remain readable without additional comments.
 *
 * <p>Platform-specific rules are applied inside {@link #checkPlayerPlatform}:
 * <ul>
 *   <li><b>INVISIBLE</b> — only solid when {@link Platform#isLit()} is {@code true}.</li>
 *   <li><b>CRUMBLE</b>   — starts a 3000 ms countdown on first contact; becomes
 *       non-solid after the delay.</li>
 *   <li><b>MIMIC</b>     — solid on first contact; collapses and becomes non-solid
 *       after 800 ms.</li>
 *   <li><b>SLIDE</b>     — deflects the player's velocity along the slope surface
 *       defined by the platform's tile dimensions instead of a hard stop.</li>
 * </ul>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */


import java.awt.Rectangle;

public class CollisionDetector {

    // -------------------------------------------------------------------------
    // Delay constants (mirror the values in Platform for readability here)
    // -------------------------------------------------------------------------

    /** Time in milliseconds before a CRUMBLE platform loses solidity after contact. */
    private static final long CRUMBLE_DELAY_MS = 3000L;

    /** Time in milliseconds before a MIMIC platform collapses after first contact. */
    private static final long MIMIC_DELAY_MS = 800L;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a {@code CollisionDetector}. The detector is fully stateless; all
     * relevant data is passed through method parameters.
     */
    public CollisionDetector() {
        // Stateless — no fields to initialise.
    }

    // -------------------------------------------------------------------------
    // Player ↔ Platform
    // -------------------------------------------------------------------------

    /**
     * Tests whether the player overlaps the platform, applies platform-specific side
     * effects (crumble timer, mimic trigger), and — if the platform is solid and
     * overlapping — pushes the player out along the axis of least penetration.
     *
     * <p>Side effects on the player when a collision is resolved:
     * <ul>
     *   <li><b>Top face hit</b> — {@code grounded = true}, {@code velY = 0}.</li>
     *   <li><b>Bottom face hit</b> (ceiling) — {@code velY = 0}.</li>
     *   <li><b>Side face hit</b> — {@code velX = 0}, {@code wallTouching = true}.</li>
     *   <li><b>SLIDE platform</b> — velocity deflected along the slope normal instead
     *       of zeroed; {@code grounded = true} on the top face.</li>
     * </ul>
     *
     * @param p  the {@link Player} to test; must not be {@code null}
     * @param pl the {@link Platform} to test against; must not be {@code null}
     * @return {@code true} if a solid collision was detected and resolved;
     *         {@code false} if the platform is non-solid or bounds do not overlap
     */
    public boolean checkPlayerPlatform(Player p, Platform pl) {

        // --- Solidity gates ---

        // INVISIBLE platforms are only solid when illuminated.
        if (pl.getType() == Platform.PlatformType.INVISIBLE && !pl.isLit()) {
            return false;
        }

        // Platforms that have already crumbled or collapsed pass through.
        if (!pl.isSolid()) {
            return false;
        }

        // --- Two-hitbox broad-phase tests ---
        Rectangle pb  = p.getBounds();            // narrow 24px for vertical
        Rectangle hb  = p.getHorizontalBounds();  // wider 44px for horizontal
        Rectangle plb = pl.getBounds();

        boolean verticalOverlap = pb.intersects(plb);
        boolean horizontalOverlap = hb.intersects(plb);

        if (!verticalOverlap && !horizontalOverlap) {
            return false;
        }

        // --- First-contact side effects ---
        long now = System.currentTimeMillis();

        if (pl.getType() == Platform.PlatformType.MIMIC
                && !pl.isMimicTriggered()) {
            pl.setMimicTriggered(true);
            pl.setMimicStartTime(now);
        }

        // --- Timer expiry checks ---
        if (pl.getType() == Platform.PlatformType.MIMIC
                && pl.isMimicTriggered()
                && (now - pl.getMimicStartTime()) >= MIMIC_DELAY_MS) {
            pl.setSolid(false);
            return false;
        }

        // --- Penetration depths using narrow hitbox for vertical ---
        int overlapTop    = (pb.y + pb.height) - plb.y;
        int overlapBottom = (plb.y + plb.height) - pb.y;

        // --- Penetration depths using wide hitbox for horizontal ---
        int overlapLeft   = (hb.x + hb.width) - plb.x;
        int overlapRight  = (plb.x + plb.width) - hb.x;

        // Only consider axes where there is actual overlap
        int minVertical   = Math.min(overlapTop, overlapBottom);
        int minHorizontal = Math.min(overlapLeft, overlapRight);

        // --- SLIDE: velocity deflection along slope surface ---
        if (pl.getType() == Platform.PlatformType.SLIDE) {
            resolveSlide(p, pl, overlapTop);
            return true;
        }

        // Determine which axis to resolve based on which hitbox overlaps
        boolean resolveVertical;
        if (verticalOverlap && horizontalOverlap) {
            resolveVertical = minVertical <= minHorizontal;
        } else {
            resolveVertical = verticalOverlap;
        }

        if (resolveVertical) {
            if (overlapTop <= overlapBottom) {
                // Player fell onto the top surface of the platform.
                p.setY(plb.y - 88);
                p.setVelY(0f);
                p.setGrounded(true);

                // Trigger crumble animation on top-collision with CRUMBLE platforms
                if (pl.getType() == Platform.PlatformType.CRUMBLE) {
                    pl.startCrumbling();
                }
            } else {
                // Player jumped up into the underside of the platform (ceiling).
                p.setY(plb.y + plb.height);
                p.setVelY(0f);
            }
        } else {
            // Only resolve horizontal collision against walls or platforms
            // whose top is above the horizontal hitbox bottom (torso region)
            boolean isWallType = pl.getType() == Platform.PlatformType.WALL;
            boolean platformTopAboveHitboxBottom = plb.y < (p.getY() + 48);
            if (isWallType || platformTopAboveHitboxBottom) {
                if (overlapLeft <= overlapRight) {
                    // Player moved right into the left face of the platform.
                    p.setX(plb.x - 54);
                    p.setVelX(0f);
                    p.setWallTouching(true);
                } else {
                    // Player moved left into the right face of the platform.
                    p.setX(plb.x + plb.width - 10);
                    p.setVelX(0f);
                    p.setWallTouching(true);
                }
            }
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Player ↔ Hazard
    // -------------------------------------------------------------------------

    /**
     * Tests whether the player's bounding rectangle overlaps the hazard's bounding
     * rectangle. A {@code true} result should trigger a
     * {@link Damageable#takeDamage(int)} call on the player in the game loop.
     *
     * @param p the {@link Player} to test; must not be {@code null}
     * @param h the {@link Hazard} to test against; must not be {@code null}
     * @return {@code true} if the player's bounds intersect the hazard's bounds
     */
    public boolean checkPlayerHazard(Player p, Hazard h) {
        return p.getBounds().intersects(h.getBounds());
    }

    // -------------------------------------------------------------------------
    // Projectile ↔ target
    // -------------------------------------------------------------------------

    /**
     * Tests whether the active projectile overlaps the target's bounding rectangle.
     * On a hit the target receives damage (if it implements {@link Damageable}) and
     * the projectile is deactivated.
     *
     * @param proj   the {@link Projectile} to test; must not be {@code null}
     * @param target the {@link GameElement} to test against; must not be {@code null}
     * @return {@code true} if an overlap was detected and the hit was registered
     */
    public boolean checkProjectileTarget(Projectile proj, GameElement target) {
        if (!proj.isActive() || !target.isActive()) {
            return false;
        }

        if (!proj.getBounds().intersects(target.getBounds())) {
            return false;
        }

        // Apply damage if the target can receive it.
        if (target instanceof Damageable) {
            ((Damageable) target).takeDamage(proj.getDamage());
        }

        // Deactivate the projectile so it cannot hit again.
        proj.setActive(false);

        return true;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves a collision with a SLIDE platform by deflecting the player's velocity
     * along the slope surface rather than zeroing it, simulating a slide rather than
     * a hard stop.
     *
     * <p>The slope direction is derived from the platform's tile dimensions:
     * {@code slopeDir = normalize(width, height)}. The surface normal is the
     * perpendicular vector {@code (-height, width)} normalised. The component of
     * velocity along the normal is subtracted so only the along-slope component
     * remains.
     *
     * @param p          the {@link Player} whose velocity will be deflected
     * @param pl         the SLIDE {@link Platform}
     * @param overlapTop penetration depth of the player's bottom edge into the
     *                   platform's top face (pixels); used to push the player up
     */
    private void resolveSlide(Player p, Platform pl, int overlapTop) {
        Rectangle plb = pl.getBounds();

        // Derive slope direction from tile dimensions (run = width, rise = height).
        float run  = plb.width;
        float rise = plb.height;
        float len  = (float) Math.sqrt(run * run + rise * rise);

        float sDirX = run  / len;   // normalised slope direction (rightward)
        float sDirY = rise / len;

        // Surface normal perpendicular to slope, pointing upward.
        float normX = -sDirY;
        float normY =  sDirX;

        // Project current velocity onto the normal.
        float dot = p.getVelX() * normX + p.getVelY() * normY;

        // Remove the normal component; what remains is motion along the slope.
        p.setVelX(p.getVelX() - dot * normX);
        p.setVelY(p.getVelY() - dot * normY);

        // Push the player above the surface if they have penetrated the top face.
        if (overlapTop > 0) {
            p.setY(p.getY() - overlapTop);
            p.setGrounded(true);
        }
    }
}
