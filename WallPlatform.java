/**
 Vertical or horizontal barrier blocking movement on all sides (fully solid). Platform
 subtype (with SlidePlatform, SpringPlatform). Unlike BRICK (top-only), WALL blocks from
 top, bottom, left, right. Used for level boundaries, corridors, hidden rooms, boss arena
 edges. Physics logic in PhysicsEngine/CollisionDetector; this class supplies WALL type tag.
 */
public class WallPlatform extends Platform { // WALL-type platform; fully solid on all sides

    public WallPlatform(int x, int y) { // Construct WALL platform at (x,y) with default dimensions
        super(Platform.PlatformType.WALL, x, y);
    }

    public WallPlatform(int x, int y, int width, int height) { // Construct WALL platform at (x,y) with explicit dimensions
        super(Platform.PlatformType.WALL, x, y, width, height);
    }
}
