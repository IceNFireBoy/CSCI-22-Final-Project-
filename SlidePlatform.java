/**
 Platform that deflects Wanderer velocity along surface rather than stopping horizontal
 movement, simulating slide effect. Platform subtype (with SpringPlatform, WallPlatform).
 Apprentice places via block-placement system. Slide physics (velocity deflection, friction)
 in PhysicsEngine/CollisionDetector; this class carries SLIDE type tag and delegates to Platform.
 */
public class SlidePlatform extends Platform { // SLIDE-type platform; deflects velocity along surface

    public SlidePlatform(int x, int y) { // Construct SLIDE platform at (x,y) with default dimensions
        super(Platform.PlatformType.SLIDE, x, y);
    }

    public SlidePlatform(int x, int y, int width, int height) { // Construct SLIDE platform at (x,y) with explicit dimensions
        super(Platform.PlatformType.SLIDE, x, y, width, height);
    }
}
