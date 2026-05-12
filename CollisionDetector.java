





























import java.awt.*;
public class CollisionDetector {





    private static final long CRUMBLE_DELAY_MS = 3000L;

    private static final long MIMIC_DELAY_MS = 800L;





    public CollisionDetector() { }





    public boolean checkPlayerPlatform(Player p, Platform pl) {




        if (pl.getType() == Platform.PlatformType.INVISIBLE && !pl.isLit()) {
            return false;
        }


        if (!pl.isSolid()) {
            return false;
        }


        Rectangle pb  = p.getBounds();
        Rectangle hb  = p.getHorizontalBounds();
        Rectangle plb = pl.getBounds();

        boolean verticalOverlap   = pb.intersects(plb);
        boolean horizontalOverlap = hb.intersects(plb);

        if (!verticalOverlap && !horizontalOverlap) {
            return false;
        }


        long now = System.currentTimeMillis();

        if (pl.getType() == Platform.PlatformType.MIMIC
                && !pl.isMimicTriggered()) {
            pl.setMimicTriggered(true);
            pl.setMimicStartTime(now);
        }


        if (pl.getType() == Platform.PlatformType.MIMIC
                && pl.isMimicTriggered()
                && (now - pl.getMimicStartTime()) >= MIMIC_DELAY_MS) {
            pl.setSolid(false);
            return false;
        }


        int overlapTop    = (pb.y + pb.height) - plb.y;
        int overlapBottom = (plb.y + plb.height) - pb.y;


        int overlapLeft   = (hb.x + hb.width) - plb.x;
        int overlapRight  = (plb.x + plb.width) - hb.x;


        int minVertical   = Math.min(overlapTop, overlapBottom);
        int minHorizontal = Math.min(overlapLeft, overlapRight);


        if (pl.getType() == Platform.PlatformType.SLIDE) {
            resolveSlide(p, pl, overlapTop);
            return true;
        }


        boolean resolveVertical;
        if (verticalOverlap && horizontalOverlap) {
            resolveVertical = minVertical <= minHorizontal;
        } else {
            resolveVertical = verticalOverlap;
        }

        if (resolveVertical) {
            if (overlapTop <= overlapBottom) {

                p.setY(plb.y - 88);
                p.setGrounded(true);

                if (pl.getType() == Platform.PlatformType.SPRING) {
                    p.setVelY(-Platform.SPRING_BOUNCE_FORCE);
                    p.setGrounded(false);
                } else {
                    p.setVelY(0f);
                }


                if (pl.getType() == Platform.PlatformType.CRUMBLE) {
                    pl.startCrumbling();
                }
            } else {

                p.setY(plb.y + plb.height);
                p.setVelY(0f);
            }
        } else {



            boolean isWallType = pl.getType() == Platform.PlatformType.WALL;
            boolean platformTopAboveHitboxBottom = plb.y < (p.getY() + 48);
            if (isWallType || platformTopAboveHitboxBottom) {
                if (overlapLeft <= overlapRight) {

                    p.setX(plb.x - 54);
                    p.setVelX(0f);
                    p.setWallTouching(true);
                } else {

                    p.setX(plb.x + plb.width - 10);
                    p.setVelX(0f);
                    p.setWallTouching(true);
                }
            }
        }

        return true;
    }





    public boolean checkPlayerHazard(Player p, Hazard h) {
        return p.getBounds().intersects(h.getBounds());
    }





    public boolean checkProjectileTarget(Projectile proj, GameElement target) {
        if (!proj.isActive() || !target.isActive()) {
            return false;
        }

        if (!proj.getBounds().intersects(target.getBounds())) {
            return false;
        }


        if (target instanceof Damageable) {
            ((Damageable) target).takeDamage(proj.getDamage());
        }


        proj.setActive(false);

        return true;
    }





    private void resolveSlide(Player p, Platform pl, int overlapTop) {
        Rectangle plb = pl.getBounds();


        float run  = plb.width;
        float rise = plb.height;
        float len  = (float) Math.sqrt(run * run + rise * rise);

        float sDirX = run  / len;
        float sDirY = rise / len;


        float normX = -sDirY;
        float normY =  sDirX;


        float dot = p.getVelX() * normX + p.getVelY() * normY;


        p.setVelX(p.getVelX() - dot * normX);
        p.setVelY(p.getVelY() - dot * normY);


        if (overlapTop > 0) {
            p.setY(p.getY() - overlapTop);
            p.setGrounded(true);
        }
    }
}
