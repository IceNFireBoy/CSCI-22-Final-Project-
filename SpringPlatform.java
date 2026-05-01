/**
 Platform that launches Wanderer upward when landed on. Platform subtype (with SLIDE,
 WALL). CollisionDetector reads BOUNCE_FORCE and applies upward velocity on top-face
 collision. Apprentice places via block-budget system. Bounce constant here so designers
 can retune all springs in the game by changing one value.
 */
public class SpringPlatform extends Platform { // SPRING-type platform; launches Wanderer upward on contact

    public static final float BOUNCE_FORCE = 18f; // 18 px/tick upward impulse; read by CollisionDetector for top-face bounce

    /**
     * Constructs a {@code SpringPlatform} at the given tile-space position using the
     * default platform dimensions defined by {@link Platform}.
     *
     * <p>Architecture role: Primary constructor used by the Apprentice's block-placement
     * system ({@link GameSession#placeBlock} → {@link GameStarter#spawnBlock}) when the
     * selected block type is SPRING and the player clicks a valid grid cell. The new
     * platform is immediately added to {@link GameStarter#elements} so that
     * {@link PhysicsEngine} processes it from the very next tick.</p>
     *
     * <p>Interaction: {@link GameStarter#spawnBlock} calls this constructor via the
     * {@code case "SPRING":} branch. The resulting object is iterated by
     * {@link CollisionDetector} on every physics tick; the SPRING type triggers the
     * bounce-force application path inside {@link CollisionDetector}.</p>
     *
     * @param x the x-coordinate (world space) of the platform's top-left corner
     * @param y the y-coordinate (world space) of the platform's top-left corner
     */
    public SpringPlatform(int x, int y) {                           // Two-argument constructor for default-sized spring tiles placed by the Apprentice at runtime
        super(Platform.PlatformType.SPRING, x, y);                  // Delegate to Platform(type, x, y) — sets type=SPRING, positions AABB at (x,y) with default width/height
    }

    /**
     * Constructs a {@code SpringPlatform} at the given position with explicit
     * width and height dimensions.
     *
     * <p>Architecture role: Used when level JSON hard-codes a non-default-sized spring
     * surface (e.g. a wide trampoline spanning several tiles). The width and height are
     * forwarded to {@link Platform} so that the AABB bounding box is sized correctly for
     * collision tests in {@link PhysicsEngine}. {@link LevelLoader} calls this
     * constructor when parsing a SPRING-type platform entry that carries explicit
     * dimension fields.</p>
     *
     * <p>Interaction: {@link LevelLoader#loadLevel(int)} invokes this constructor via
     * reflection or a switch statement when the platform-type field is {@code "SPRING"}
     * and the JSON entry specifies width and height. The platform is then registered
     * in {@link GameStarter#elements}.</p>
     *
     * @param x      the x-coordinate (world space) of the platform's top-left corner
     * @param y      the y-coordinate (world space) of the platform's top-left corner
     * @param width  the width of the platform in pixels — overrides the Platform default
     * @param height the height of the platform in pixels — overrides the Platform default
     */
    public SpringPlatform(int x, int y, int width, int height) {    // Four-argument constructor for explicitly-sized spring platforms loaded from level data
        super(Platform.PlatformType.SPRING, x, y, width, height);   // Delegate to Platform(type, x, y, w, h) — AABB set to (x,y,width,height), type=SPRING
    }
}
