/**
 Stateless AABB collision detector and resolver for all entity-pair scenarios.
 Platform-specific rules: INVISIBLE solid only when lit; CRUMBLE disappears after
 3000 ms; MIMIC collapses after 800 ms; SLIDE deflects velocity along slope.

 Created by PhysicsEngine and shared for session lifetime. All context passed via
 parameters. PhysicsEngine calls checkPlayerPlatform per platform per tick;
 GameStarter calls checkPlayerHazard and checkProjectileTarget in own passes.
 */

import java.awt.Rectangle; // AWT Rectangle used as the common AABB type returned by GameElement.getBounds()

public class CollisionDetector { // Stateless AABB resolver; all collision context is passed as parameters; no mutable fields

    // -------------------------------------------------------------------------
    // Delay constants (mirror the values in Platform for readability here)
    // -------------------------------------------------------------------------

    private static final long CRUMBLE_DELAY_MS = 3000L; // CRUMBLE disappear delay (ms)

    private static final long MIMIC_DELAY_MS = 800L; // MIMIC collapse delay (ms)

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public CollisionDetector() { } // Stateless; no initialization needed

    // -------------------------------------------------------------------------
    // Player ↔ Platform
    // -------------------------------------------------------------------------

    public boolean checkPlayerPlatform(Player p, Platform pl) { // Tests and resolves player-platform collision; called per-platform-per-tick

        // --- Solidity gates ---

        // INVISIBLE platforms are only solid when illuminated by the Apprentice's light.
        if (pl.getType() == Platform.PlatformType.INVISIBLE && !pl.isLit()) { // Invisible and currently unlit: treat as non-solid; player passes through
            return false;                                                       // No collision for unlit INVISIBLE platforms
        }

        // Platforms that have already crumbled or collapsed are no longer solid.
        if (!pl.isSolid()) {          // isSolid() is false once CRUMBLE or MIMIC has expired; skip all further tests
            return false;             // Non-solid platform: player falls through
        }

        // --- Two-hitbox broad-phase tests ---
        Rectangle pb  = p.getBounds();           // Narrow vertical hitbox (24 px wide): used for landing/ceiling tests
        Rectangle hb  = p.getHorizontalBounds(); // Wider horizontal hitbox (44 px wide): used for left/right wall tests
        Rectangle plb = pl.getBounds();          // Platform AABB from its world-space position and dimensions

        boolean verticalOverlap   = pb.intersects(plb);  // Narrow hitbox overlap: tests whether the player's torso is inside the platform vertically
        boolean horizontalOverlap = hb.intersects(plb);  // Wide hitbox overlap: tests whether the player's body is inside the platform horizontally

        if (!verticalOverlap && !horizontalOverlap) { // Neither hitbox overlaps: no collision possible this tick
            return false;                              // Early exit: AABB broad-phase failed for both hitboxes
        }

        // --- First-contact side effects ---
        long now = System.currentTimeMillis(); // Capture current time for MIMIC trigger and CRUMBLE timer comparisons

        if (pl.getType() == Platform.PlatformType.MIMIC
                && !pl.isMimicTriggered()) {              // MIMIC: first contact — trigger the collapse countdown
            pl.setMimicTriggered(true);                   // Latch the trigger flag so this block runs only once
            pl.setMimicStartTime(now);                    // Record trigger time; MIMIC_DELAY_MS (800 ms) later the platform becomes non-solid
        }

        // --- Timer expiry checks ---
        if (pl.getType() == Platform.PlatformType.MIMIC
                && pl.isMimicTriggered()
                && (now - pl.getMimicStartTime()) >= MIMIC_DELAY_MS) { // MIMIC has been triggered and 800 ms have elapsed: collapse it
            pl.setSolid(false);                                          // Mark non-solid: player will fall through on the next tick
            return false;                                               // Return false: no collision resolution for a collapsing platform
        }

        // --- Penetration depths using narrow hitbox for vertical ---
        int overlapTop    = (pb.y + pb.height) - plb.y;             // How far the player's bottom edge has penetrated the platform's top surface
        int overlapBottom = (plb.y + plb.height) - pb.y;            // How far the platform's bottom edge has penetrated the player's top (ceiling case)

        // --- Penetration depths using wide hitbox for horizontal ---
        int overlapLeft   = (hb.x + hb.width) - plb.x;             // How far the player's right edge has penetrated the platform's left face
        int overlapRight  = (plb.x + plb.width) - hb.x;            // How far the platform's right edge has penetrated the player's left face

        // Only consider axes where there is actual overlap
        int minVertical   = Math.min(overlapTop, overlapBottom);    // Smaller vertical penetration depth: used to select the least-intrusive resolution axis
        int minHorizontal = Math.min(overlapLeft, overlapRight);    // Smaller horizontal penetration depth: used for axis-selection comparison

        // --- SLIDE: velocity deflection along slope surface ---
        if (pl.getType() == Platform.PlatformType.SLIDE) {          // SLIDE platforms redirect velocity along the slope instead of zeroing it
            resolveSlide(p, pl, overlapTop);                         // Delegate to slope-deflection helper; sets grounded and adjusts velX/velY
            return true;                                             // Collision resolved via slope deflection
        }

        // Determine which axis to resolve based on which hitbox overlaps
        boolean resolveVertical;                                    // true = push player vertically; false = push player horizontally
        if (verticalOverlap && horizontalOverlap) {                 // Both hitboxes overlap: choose the axis with less penetration (minimum displacement resolution)
            resolveVertical = minVertical <= minHorizontal;         // Resolve vertically if vertical penetration is smaller; avoids "sucking" player sideways on landings
        } else {                                                    // Only one hitbox overlaps: resolve along the overlapping axis
            resolveVertical = verticalOverlap;                      // If only the narrow hitbox overlaps, resolve vertically; horizontal-only = wall push
        }

        if (resolveVertical) {                                      // Resolve the overlap by pushing the player along the y axis
            if (overlapTop <= overlapBottom) {                      // Player fell onto the top surface (landing): overlapTop is the smaller penetration
                // Player fell onto the top surface of the platform.
                p.setY(plb.y - 88);                                // Push player above the platform: y = platformTop - playerHeight (88 px total hitbox)
                p.setGrounded(true);                               // Set grounded: gravity is suppressed and jump counter resets on landing

                if (pl instanceof SpringPlatform) {                // SpringPlatform special case: launch the player upward instead of stopping velY
                    // Spring: launch the player upward instead of stopping velY
                    p.setVelY(-SpringPlatform.BOUNCE_FORCE);       // Apply upward bounce force (constant from SpringPlatform); stronger than a normal jump
                    p.setGrounded(false);                          // Immediately unground: the spring launches the player airborne again
                } else {                                           // Normal platform landing: cancel downward velocity
                    p.setVelY(0f);                                 // Zero velY to prevent the player from accelerating through the platform
                }

                // Trigger crumble animation on top-collision with CRUMBLE platforms
                if (pl.getType() == Platform.PlatformType.CRUMBLE) { // CRUMBLE: first landing starts the 3-second disintegration timer
                    pl.startCrumbling();                              // Delegate to Platform.startCrumbling() to start the timer and animation
                }
            } else {                                               // Player jumped up into the underside of the platform (ceiling): overlapBottom is smaller
                // Player jumped up into the underside of the platform (ceiling).
                p.setY(plb.y + plb.height);                        // Push player below the platform bottom: y = platformBottom
                p.setVelY(0f);                                     // Zero upward velocity: the player stops and begins falling back down
            }
        } else {                                                   // Resolve horizontal: push the player left or right out of the platform
            // Only resolve horizontal collision against walls or platforms
            // whose top is above the horizontal hitbox bottom (torso region).
            // This prevents platforms far below the player from incorrectly pushing them sideways.
            boolean isWallType = pl.getType() == Platform.PlatformType.WALL;   // WALL platforms always block horizontal movement
            boolean platformTopAboveHitboxBottom = plb.y < (p.getY() + 48);   // Check: platform top is within the upper 48 px of the player's total height
            if (isWallType || platformTopAboveHitboxBottom) {                   // Only block if it's a wall-type OR the platform reaches the torso region
                if (overlapLeft <= overlapRight) {                              // Player moved right into the platform's left face: overlapLeft is smaller
                    // Player moved right into the left face of the platform.
                    p.setX(plb.x - 54);                                        // Push player left: x = platformLeft - playerWidth (54 px hitbox width)
                    p.setVelX(0f);                                             // Stop horizontal movement: player cannot walk through the wall
                    p.setWallTouching(true);                                   // Set wallTouching: enables wall-cling and wall-jump logic in InputRouter
                } else {                                                        // Player moved left into the platform's right face: overlapRight is smaller
                    // Player moved left into the right face of the platform.
                    p.setX(plb.x + plb.width - 10);                           // Push player right: x = platformRight - 10 (hitbox left-edge offset)
                    p.setVelX(0f);                                             // Stop horizontal movement: player is blocked by the platform's right face
                    p.setWallTouching(true);                                   // Set wallTouching: enables wall-cling and wall-jump logic
                }
            }
        }

        return true; // A solid collision was detected and resolved; caller uses this to track grounding this tick
    }

    // -------------------------------------------------------------------------
    // Player ↔ Hazard
    // -------------------------------------------------------------------------

    public boolean checkPlayerHazard(Player p, Hazard h) { // Simple AABB overlap test; caller applies damage
        return p.getBounds().intersects(h.getBounds());      // Standard Rectangle.intersects: true if any pixel is shared between both AABBs
    }

    // -------------------------------------------------------------------------
    // Projectile ↔ target
    // -------------------------------------------------------------------------

    public boolean checkProjectileTarget(Projectile proj, GameElement target) { // Tests and resolves projectile-target hit; deactivates on success
        if (!proj.isActive() || !target.isActive()) {                           // Early exit: only test active projectile against active target
            return false;                                                         // One or both entities are inactive; no hit possible
        }

        if (!proj.getBounds().intersects(target.getBounds())) {                 // AABB broad-phase: projectile and target bounds must overlap for a hit
            return false;                                                         // No overlap: projectile misses the target this tick
        }

        // Apply damage if the target can receive it.
        if (target instanceof Damageable) {                                      // Only apply damage if the target implements the Damageable interface
            ((Damageable) target).takeDamage(proj.getDamage());                 // Cast and call takeDamage; amount comes from the projectile's damage field
        }

        // Deactivate the projectile so it cannot hit again.
        proj.setActive(false);                                                   // Mark projectile as spent: it will be skipped in subsequent update/render passes

        return true; // Hit registered: bounds overlapped, damage applied, projectile deactivated
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void resolveSlide(Player p, Platform pl, int overlapTop) { // SLIDE-specific resolution: deflects velocity along slope
        Rectangle plb = pl.getBounds();                                // Get platform AABB for slope direction calculation

        // Derive slope direction from tile dimensions (run = width, rise = height).
        float run  = plb.width;                                        // Horizontal component of the slope direction (positive = right)
        float rise = plb.height;                                       // Vertical component of the slope direction (positive = downward)
        float len  = (float) Math.sqrt(run * run + rise * rise);       // Magnitude of the (run, rise) vector for normalisation

        float sDirX = run  / len;   // Normalised slope x direction: unit vector along the slope surface
        float sDirY = rise / len;   // Normalised slope y direction: unit vector along the slope surface

        // Surface normal perpendicular to slope, pointing upward-left.
        float normX = -sDirY;       // Normal x = -sDirY (perpendicular, pointing away from slope surface)
        float normY =  sDirX;       // Normal y =  sDirX (perpendicular, completing the 90° rotation)

        // Project current velocity onto the normal.
        float dot = p.getVelX() * normX + p.getVelY() * normY;        // dot product of velocity and normal; positive = moving into the surface

        // Remove the normal component; what remains is motion along the slope.
        p.setVelX(p.getVelX() - dot * normX);                         // Subtract the normal component from velX: leaves only the along-slope x velocity
        p.setVelY(p.getVelY() - dot * normY);                         // Subtract the normal component from velY: leaves only the along-slope y velocity

        // Push the player above the surface if they have penetrated the top face.
        if (overlapTop > 0) {                                          // Player has penetrated the top surface: push them above it
            p.setY(p.getY() - overlapTop);                             // Translate player up by the penetration depth to sit on the slope surface
            p.setGrounded(true);                                       // Set grounded: gravity is suppressed while on the slope
        }
    }
}
