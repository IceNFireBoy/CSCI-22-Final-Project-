/**
 Procedurally generates platform/hazard layout for boss arena from deterministic seed,
 ensuring both clients receive identical layout while allowing each session to feel
 distinct. Generated list (Cores, throne, 20 platforms, boundary walls) passed to
 entity manager without modifying pre-authored level files. Deterministic seed ensures
 no element-level serialisation needed; both clients run generator with same seed.
 */

import java.util.ArrayList; // ArrayList implementation for the output entity list
import java.util.List;      // List interface for the returned GameElement collection
import java.util.Random;    // Seeded PRNG for deterministic quadrant-jitter platform placement

public class BossArenaGenerator { // Stateless procedural generator; all per-run randomness derives from the seed passed to generate()

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    public static final int ARENA_W = GameServer.ARENA_W; // 3072 px; matches GameServer.ARENA_W

    public static final int ARENA_H = GameServer.ARENA_H; // 2304 px; matches GameServer.ARENA_H

    public static final int CENTER_X = ARENA_W / 2; // 1536 px; arena horizontal center

    public static final int CENTER_Y = ARENA_H / 2; // 1152 px; arena vertical center

    private static final int THRONE_WIDTH  = 128; // 128 px wide; 4 tiles

    private static final int THRONE_HEIGHT = 48; // 48 px tall; 1.5 tiles

    private static final int CORE_INSET = 512; // 512 px from each edge

    private static final int PLATFORMS_PER_QUADRANT = 5; // 5 per quadrant × 4 mirrors = 20 total

    private static final int MIN_CORE_CLEARANCE = 200; // 200 px minimum distance from Core center

    private static final int MIN_THRONE_CLEARANCE = 260; // 260 px minimum distance from throne center

    private static final int WALL_THICKNESS = 24; // 24 px boundary wall thickness

    /** Vertical offset from the arena bottom used to place the Wanderer's boss spawn. */
    public static final int BOSS_SPAWN_Y_OFFSET = 256; // Positions spawn 256 px above the bottom wall — clear of hazards, facing the throne

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public BossArenaGenerator() { // Construct stateless generator; all per-generation randomness from seed
        // Stateless; no fields to initialize
    }

    // -------------------------------------------------------------------------
    // Generation
    // -------------------------------------------------------------------------

    public List<GameElement> generate(long seed) { // Generate complete arena (walls, cores, throne, 20 platforms) for given seed; same seed → same layout
        List<GameElement> out = new ArrayList<>();          // Output list; all generated entities are added here and returned to GameStarter

        // ---- Boundary walls so the arena is a closed rectangle ----
        // Top wall — a full-width slab sealing the top edge of the arena
        out.add(new Platform(Platform.PlatformType.WALL,
                0, 0, ARENA_W, WALL_THICKNESS));           // Top: x=0, y=0, spans full arena width, 24 px thick

        // Bottom wall — mirrors the top wall at the arena bottom
        out.add(new Platform(Platform.PlatformType.WALL,
                0, ARENA_H - WALL_THICKNESS, ARENA_W, WALL_THICKNESS)); // Bottom: y = ARENA_H - 24; same width as top

        // Left wall — a full-height slab sealing the left edge
        out.add(new Platform(Platform.PlatformType.WALL,
                0, 0, WALL_THICKNESS, ARENA_H));           // Left: x=0, spans full arena height, 24 px thick

        // Right wall — mirrors the left wall at the right edge
        out.add(new Platform(Platform.PlatformType.WALL,
                ARENA_W - WALL_THICKNESS, 0, WALL_THICKNESS, ARENA_H)); // Right: x = ARENA_W - 24; same height as left

        // ---- Four Cores at the corners ----
        // Core index ↔ corner mapping: 0=TL, 1=TR, 2=BL, 3=BR
        int[][] coreCorners = new int[][]{
                { CORE_INSET,           CORE_INSET            }, // 0 — top-left:     (512, 512)
                { ARENA_W - CORE_INSET, CORE_INSET            }, // 1 — top-right:    (2560, 512)
                { CORE_INSET,           ARENA_H - CORE_INSET  }, // 2 — bottom-left:  (512, 1792)
                { ARENA_W - CORE_INSET, ARENA_H - CORE_INSET  }  // 3 — bottom-right: (2560, 1792)
        };
        for (int i = 0; i < 4; i++) {                           // Create one Core per corner; index i maps to coreCorners[i]
            out.add(new Core(i, coreCorners[i][0], coreCorners[i][1])); // Core(coreIndex, x, y); 32×32 AABB at the corner position
        }

        // ---- Central throne marker ----
        // A wide WALL platform sitting dead-centre. Later phases swap this for a
        // proper Throne entity; for P8.2 the marker just needs to occupy the
        // centre and render visibly so QA can confirm the arena's orientation.
        out.add(new Platform(Platform.PlatformType.WALL,
                CENTER_X - THRONE_WIDTH / 2,          // Left edge: centre_x - 64 = 1472 px
                CENTER_Y - THRONE_HEIGHT / 2,         // Top edge:  centre_y - 24 = 1128 px
                THRONE_WIDTH,                          // Width: 128 px (4 tiles)
                THRONE_HEIGHT));                       // Height: 48 px (1.5 tiles)

        // ---- Twenty radially-symmetric platforms ----
        // Generate five jittered platforms in the top-left quadrant, then mirror
        // each one across both axes to produce 4× the count (20 total) with
        // perfect radial symmetry about (CENTER_X, CENTER_Y).
        Random rng = new Random(seed);                  // Seeded PRNG: same seed → same sequence → same platform positions on both clients
        int attempts  = 0;                              // Safety counter: abandon after 64 attempts to prevent infinite loops with tight clearance zones
        int generated = 0;                              // Count of valid quadrant candidates placed so far
        while (generated < PLATFORMS_PER_QUADRANT && attempts < 64) { // Loop until 5 valid platforms or 64 attempts exhausted
            attempts++;                                                  // Increment attempt counter; prevents infinite loop if clearance is impossible to satisfy
            // Quadrant-1 jitter bounds: stay 320 px from arena edges and clear
            // of Core hitboxes and the central throne exclusion zone.
            int qx = 320 + rng.nextInt(Math.max(1, CENTER_X - 560));   // Random x in [320, CENTER_X-240]: left quadrant, 320 px from left edge, 240 px from centre
            int qy = 320 + rng.nextInt(Math.max(1, CENTER_Y - 500));   // Random y in [320, CENTER_Y-180]: top quadrant, 320 px from top, 180 px from centre

            if (!clearOfKeyPoints(qx, qy, coreCorners)) continue;      // Reject if too close to a Core or the throne; try a new candidate

            // Mirror across horizontal axis, vertical axis, and both
            addMirroredPlatform(out, qx,              qy);              // Top-left quadrant: (qx, qy)
            addMirroredPlatform(out, ARENA_W - qx,    qy);              // Top-right quadrant: (ARENA_W-qx, qy)
            addMirroredPlatform(out, qx,              ARENA_H - qy);    // Bottom-left quadrant: (qx, ARENA_H-qy)
            addMirroredPlatform(out, ARENA_W - qx,    ARENA_H - qy);   // Bottom-right quadrant: (ARENA_W-qx, ARENA_H-qy)
            generated++;                                                  // Count this as a successfully placed quadrant candidate
        }

        return out; // Return the fully populated arena entity list; caller adds it to GameStarter#elements
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static void addMirroredPlatform(List<GameElement> out, int x, int y) { // Add single BRICK platform at (x,y); called 4x per valid candidate for symmetry
        out.add(new Platform(Platform.PlatformType.BRICK, x, y));                   // Create and add a standard BRICK platform at (x, y)
    }

    private static boolean clearOfKeyPoints(int x, int y, int[][] cores) { // Return true if (x,y) satisfies clearance constraints for all Cores and throne
        for (int[] c : cores) {                                              // Test each of the 4 Core corners
            int dx = c[0] - x, dy = c[1] - y;                              // Displacement from candidate to this Core centre
            if (dx * dx + dy * dy < MIN_CORE_CLEARANCE * MIN_CORE_CLEARANCE) { // Squared distance < squared clearance threshold (avoids sqrt)
                return false;                                                // Candidate is too close to this Core: reject it
            }
        }
        int dxc = CENTER_X - x, dyc = CENTER_Y - y;                        // Displacement from candidate to the arena centre (throne position)
        if (dxc * dxc + dyc * dyc < MIN_THRONE_CLEARANCE * MIN_THRONE_CLEARANCE) { // Squared distance < squared throne clearance threshold
            return false;                                                    // Candidate is inside the throne exclusion zone: reject it
        }
        return true; // Candidate passes all clearance constraints: valid spawn location
    }
}
