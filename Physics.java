



























import java.util.*;

public class Physics {





    private static final int FIXED_TIMESTEP_MS = 16;

    private static final float GRAVITY = 0.5f;

    private static final float TERMINAL_VELOCITY = 18f;


    public static final int DEFAULT_FALL_DEATH_Y = 900;
    private int fallDeathY = DEFAULT_FALL_DEATH_Y;

    public void setFallDeathY(int y) { this.fallDeathY = y; }
    public int  getFallDeathY()      { return fallDeathY; }





    public static final float WALK_SPEED = 4f;

    public static final float JUMP_VELOCITY = -12f;

    public static final int MAX_CONSECUTIVE_JUMPS = 2;





    private static final float DODGE_BOOST = 10f;
    private static final long DODGE_DURATION_MS = 500L;
    private static final long DODGE_COOLDOWN_MS = 3000L;





    private static final float WALL_CLING_FALL_SPEED = 1f;





    private static final int SHADOW_DASH_DISTANCE = 80;
    private static final long SHADOW_DASH_COOLDOWN_MS = 5000L;





    private final CollisionDetector collisionDetector;





    public Physics() {
        this.collisionDetector = new CollisionDetector();
    }





    public void update(long deltaMs, Player player, List<GameElement> elements) {
        long now = System.currentTimeMillis();


        if (player.isDodging() && now >= player.getDodgeEndTime()) {
            player.setDodging(false);
            player.setInvincible(false);
        }


        if (!player.isGrounded()) {
            applyGravity(player);
        } else {
            player.setVelY(0f);
        }


        player.setX(player.getX() + Math.round(player.getVelX()));
        player.setY(player.getY() + Math.round(player.getVelY()));


        player.setWallTouching(false);
        boolean groundedThisTick = false;


        for (GameElement el : elements) {
            if (!el.isActive()) continue;
            if (el instanceof Platform) {
                if (collisionDetector.checkPlayerPlatform(player, (Platform) el)) {
                    if (player.isGrounded()) {
                        groundedThisTick = true;
                    }
                }
            }
        }


        if (!groundedThisTick) {
            player.setGrounded(false);
        }


        if (player.isGrounded()) {
            player.setConsecutiveJumps(0);
        }


        wallCling(player);


        if (player.getY() > fallDeathY) {
            player.loseLife();
        }
    }





    public void applyGravity(Player player) {
        if (!player.isGrounded()) {
            float newVelY = player.getVelY() + GRAVITY;
            player.setVelY(Math.min(newVelY, TERMINAL_VELOCITY));
        }
    }





    public void walk(Player player, int direction) {
        player.setFacingDirection(direction);
        player.setVelX(WALK_SPEED * direction);
    }

    public void stopWalking(Player player) {
        player.setVelX(0f);
    }

    public void jump(Player player) {
        if (player.isGrounded() || player.getConsecutiveJumps() < MAX_CONSECUTIVE_JUMPS) {
            player.setVelY(JUMP_VELOCITY);
            player.setGrounded(false);
            player.setConsecutiveJumps(player.getConsecutiveJumps() + 1);
        }
    }





    public void dodgeRoll(Player player) {
        if (!player.isHasDodge()) return;

        long now = System.currentTimeMillis();
        if (now < player.getDodgeCooldownEnd()) return;

        player.setVelX(DODGE_BOOST * player.getFacingDirection());
        player.setDodging(true);
        player.setInvincible(true);
        player.setDodgeEndTime(now + DODGE_DURATION_MS);
        player.setInvincibleTimer(now + DODGE_DURATION_MS);
        player.setDodgeCooldownEnd(now + DODGE_COOLDOWN_MS);
    }





    public void wallCling(Player player) {
        if (!player.isHasWallCling()) return;
        if (player.isWallTouching() && !player.isGrounded()) {
            if (player.getVelY() > WALL_CLING_FALL_SPEED) {
                player.setVelY(WALL_CLING_FALL_SPEED);
            }
        }
    }





    public void shadowDash(Player player) {
        if (!player.isHasShadowDash()) return;

        long now = System.currentTimeMillis();
        if (now < player.getShadowDashCooldownEnd()) return;

        player.setX(player.getX() + SHADOW_DASH_DISTANCE * player.getFacingDirection());
        player.setShadowDashCooldownEnd(now + SHADOW_DASH_COOLDOWN_MS);
    }
}
