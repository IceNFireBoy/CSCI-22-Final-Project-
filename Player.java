
































import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.*;
import java.util.List;
public class Player implements Damageable, Renderable {





    private int maxHealth;

    private int x;
    private int y;

    private int health;

    private float velX;
    private float velY;

    private boolean grounded;
    private boolean wallTouching;


    private boolean invincible;





    private long invincibleTimer;





    private int facingDirection;






    private int consecutiveJumps;






    private boolean dodging;


    private long dodgeEndTime;


    private long dodgeCooldownEnd;






    private long shadowDashCooldownEnd;









    private boolean hasMelee;





    private boolean hasProjectile;





    private boolean hasDodge;





    private boolean hasWallCling;





    private boolean hasShadowDash;









    private boolean hasVeil;


    private boolean hasEcho;


    private boolean hasTether;


    private boolean hasShadowStep;






    private boolean sightRestricted;






    public static final int FAITHFUL_MAX = 5;







    private int faithful = 0;
















    private final Set<LoreFragment.AbilityUnlock> activeBoosts =
            EnumSet.noneOf(LoreFragment.AbilityUnlock.class);













    public enum RadiantState { IDLE, CHARGING, ACTIVE, COOLDOWN }


    public static final long RADIANT_CHARGE_MS   = 2_000L;


    public static final long RADIANT_ACTIVE_MS   = 5000000_000L;


    public static final long RADIANT_COOLDOWN_MS = 60_000L;


    private RadiantState radiantState = RadiantState.IDLE;






    private long radiantStateStartMs = 0L;









    private volatile long radiantActiveUntilMs = 0L;






    private CoreHealthBar coreHealthBar;






    private String animState;


    private long meleeCooldown;








    public volatile int pendingCoreHitIndex = -1;


    private boolean chargeHeld;


    private long chargeStartTime;


    private boolean dodgeActive;


    private long dodgeCooldownRemaining;


    private long dashCooldownRemaining;


    private boolean healFlash;


    private long healFlashEnd;


    private boolean collectFlash;


    private long collectFlashStart;


    private List<GameElement> activeEntities;






    private int respawnX;


    private int respawnY;


    private boolean isDead;


    private long deathTimer;






    private boolean restartToAct1Pending = false;






    public static final int SPRITE_WIDTH  = 64;


    public static final int SPRITE_HEIGHT = 96;


    private static final int DEATH_ANIM_MAX_Y = 800;


    private static final long DEATH_ANIM_MS = 1200L;


    private static final int FRAME_DELAY = 6;


    private int frameIndex = 0;


    private int frameTick = 0;


    private String currentAnimState = "idle";


    private String lastAnimState = "";


    private int deathX;


    private int deathY;










    private boolean wasOnGround = false;


    private int coyoteFrames = 0;


    private static final int COYOTE_MAX = 4;


















    public Player(int x, int y) {
        this.x = x;
        this.y = y;


        this.velX = 0f;
        this.velY = 0f;


        this.grounded         = false;
        this.wallTouching     = false;
        this.invincible       = false;
        this.invincibleTimer  = 0L;
        this.facingDirection  = 1;
        this.consecutiveJumps = 0;


        this.dodging          = false;
        this.dodgeEndTime     = 0L;
        this.dodgeCooldownEnd = 0L;


        this.shadowDashCooldownEnd = 0L;


        this.hasMelee      = false;
        this.hasProjectile = false;
        this.hasDodge      = false;
        this.hasWallCling  = false;
        this.hasShadowDash = false;

        this.coreHealthBar = new CoreHealthBar();


        this.animState = "wanderer_idle";
        this.meleeCooldown = 0L;
        this.chargeHeld = false;
        this.chargeStartTime = 0L;
        this.dodgeActive = false;
        this.dodgeCooldownRemaining = 0L;
        this.dashCooldownRemaining = 0L;
        this.healFlash = false;
        this.healFlashEnd = 0L;
        this.collectFlash = false;
        this.collectFlashStart = 0L;
        this.activeEntities = new ArrayList<>();


        this.maxHealth = 100;
        this.health    = this.maxHealth;
        this.sightRestricted = false;
        this.faithful  = 0;
        this.respawnX  = x;
        this.respawnY  = y;
        this.isDead    = false;
        this.deathTimer = 0L;
        this.deathX    = x;
        this.deathY    = y;
    }


























    @Override
    public void render(Graphics2D g) {

        if (isDead) {
            float alpha = Math.max(0f, 1.0f - (deathTimer / 1200f));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            String deathPath = getSpritePathForState("death", Math.min(frameIndex, 4));
            BufferedImage deathSprite = SpriteLoader.getInstance().load(deathPath);
            if (deathSprite != null) {
                g.drawImage(deathSprite, deathX, deathY, SPRITE_WIDTH, SPRITE_HEIGHT, null);
            } else {
                g.setColor(new Color(200, 50, 50));
                g.fillRect(deathX + 20, deathY + 8, 24, 80);
            }
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            return;
        }

        String path = getSpritePathForState(currentAnimState, frameIndex);
        BufferedImage sprite = SpriteLoader.getInstance().load(path);


        if (invincible && (frameTick % 8 < 4)) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        }

        if (sprite != null) {
            if (facingDirection < 0) {

                g.drawImage(sprite, x + SPRITE_WIDTH, y, -SPRITE_WIDTH, SPRITE_HEIGHT, null);
            } else {
                g.drawImage(sprite, x, y, SPRITE_WIDTH, SPRITE_HEIGHT, null);
            }
        } else {

            g.setColor(new Color(0x55, 0x44, 0x88));
            g.fillRect(x + 20, y + 8, 24, 80);
        }

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }













    private String getSpritePathForState(String state, int frame) {
        switch (state) {
            case "run":    return "resources/sprites/wanderer/wanderer_run_f"    + (frame + 1) + ".png";
            case "death":  return "resources/sprites/wanderer/wanderer_death_f"  + (frame + 1) + ".png";
            case "melee":  return "resources/sprites/wanderer/wanderer_melee_f"  + (frame + 1) + ".png";
            case "dodge":  return "resources/sprites/wanderer/wanderer_dodge_f"  + (frame + 1) + ".png";
            case "charge": return "resources/sprites/wanderer/wanderer_charge_f" + (frame + 1) + ".png";
            case "jump":   return "resources/sprites/wanderer/wanderer_jump.png";
            case "fall":   return "resources/sprites/wanderer/wanderer_fall.png";
            default:       return "resources/sprites/wanderer/wanderer_idle.png";
        }
    }











    private int getFrameCount(String state) {
        switch (state) {
            case "run":    return 4;
            case "death":  return 5;
            case "melee":  return 2;
            case "dodge":  return 2;
            case "charge": return 3;
            default:       return 1;
        }
    }











    @Override
    public Rectangle getBounds() {
        return new Rectangle(x + 20, y + 8, 24, 80);
    }







    public Rectangle getFullBounds() {
        return new Rectangle(x, y, 64, 96);
    }







    public Rectangle getHorizontalBounds() {
        return new Rectangle(x + 10, y + 12, 44, 36);
    }


















    @Override
    public void takeDamage(int amount) {
        if (amount <= 0) return;
        if (invincible || isDead) return;



        if (hasBoost(LoreFragment.AbilityUnlock.IRON)) {
            int reduced = (int) Math.floor(amount * 0.8);
            amount = Math.max(1, reduced);
        }

        health = Math.max(0, health - amount);
        if (health <= 0) {
            loseLife();
        }
    }










    public void setHealth(int health) {
        this.health = Math.max(0, Math.min(maxHealth, health));
    }











    public void heal(int amount) {
        this.health = Math.min(maxHealth, this.health + amount);
        this.healFlash  = true;
        this.healFlashEnd = System.currentTimeMillis() + 300;
    }






    @Override
    public int getHealth() {
        return health;
    }






    @Override
    public int getMaxHealth() {
        return maxHealth;
    }






    @Override
    public boolean isAlive() {
        return health > 0;
    }










    public int getX() {
        return x;
    }







    public void setX(int x) {
        this.x = x;
    }






    public int getY() {
        return y;
    }







    public void setY(int y) {
        this.y = y;
    }












    public void setPosition(float x, float y) {
        this.x = (int) x;
        this.y = (int) y;
    }











    public void setAnimationState(String state) {
        if (state == null || state.isEmpty()) return;
        if (!state.equals(currentAnimState)) {
            currentAnimState = state;
            lastAnimState    = state;
            frameIndex       = 0;
            frameTick        = 0;
            animState        = "wanderer_" + state;
        }
    }










    public float getVelX() {
        return velX;
    }






    public void setVelX(float velX) {
        this.velX = velX;
    }






    public float getVelY() {
        return velY;
    }






    public void setVelY(float velY) {
        this.velY = velY;
    }










    public boolean isGrounded() {
        return grounded;
    }







    public void setGrounded(boolean grounded) {
        this.grounded = grounded;
    }






    public boolean isWallTouching() {
        return wallTouching;
    }







    public void setWallTouching(boolean wallTouching) {
        this.wallTouching = wallTouching;
    }






    public boolean isInvincible() {
        return invincible;
    }






    public void setInvincible(boolean invincible) {
        this.invincible = invincible;
    }







    public long getInvincibleTimer() {
        return invincibleTimer;
    }






    public void setInvincibleTimer(long invincibleTimer) {
        this.invincibleTimer = invincibleTimer;
    }






    public int getFacingDirection() {
        return facingDirection;
    }







    public void setFacingDirection(int facingDirection) {
        this.facingDirection = facingDirection;
    }






    public int getConsecutiveJumps() {
        return consecutiveJumps;
    }






    public void setConsecutiveJumps(int consecutiveJumps) {
        this.consecutiveJumps = consecutiveJumps;
    }










    public boolean isDodging() {
        return dodging;
    }






    public void setDodging(boolean dodging) {
        this.dodging = dodging;
    }






    public long getDodgeEndTime() {
        return dodgeEndTime;
    }






    public void setDodgeEndTime(long dodgeEndTime) {
        this.dodgeEndTime = dodgeEndTime;
    }






    public long getDodgeCooldownEnd() {
        return dodgeCooldownEnd;
    }






    public void setDodgeCooldownEnd(long dodgeCooldownEnd) {
        this.dodgeCooldownEnd = dodgeCooldownEnd;
    }










    public long getShadowDashCooldownEnd() {
        return shadowDashCooldownEnd;
    }






    public void setShadowDashCooldownEnd(long shadowDashCooldownEnd) {
        this.shadowDashCooldownEnd = shadowDashCooldownEnd;
    }










    public boolean isHasMelee() {
        return hasMelee;
    }






    public void setHasMelee(boolean hasMelee) {
        this.hasMelee = hasMelee;
    }













    public int consumePendingCoreHit() {
        int idx = pendingCoreHitIndex;
        pendingCoreHitIndex = -1;
        return idx;
    }






    public boolean isHasProjectile() {
        return hasProjectile;
    }






    public void setHasProjectile(boolean hasProjectile) {
        this.hasProjectile = hasProjectile;
    }






    public boolean isHasDodge() {
        return hasDodge;
    }






    public void setHasDodge(boolean hasDodge) {
        this.hasDodge = hasDodge;
    }






    public boolean isHasWallCling() {
        return hasWallCling;
    }






    public void setHasWallCling(boolean hasWallCling) {
        this.hasWallCling = hasWallCling;
    }






    public boolean isHasShadowDash() {
        return hasShadowDash;
    }






    public void setHasShadowDash(boolean hasShadowDash) {
        this.hasShadowDash = hasShadowDash;
    }











    public void unlockAbility(LoreFragment.AbilityUnlock unlock) {
        switch (unlock) {
            case MELEE:       hasMelee      = true; break;
            case PROJECTILE:  hasProjectile = true; break;
            case DODGE:       hasDodge      = true; break;
            case WALL_CLING:  hasWallCling  = true; break;
            case SHADOW_DASH: hasShadowDash = true; break;




            case EMBER:
            case IRON:
                activateBoost(unlock);
                break;



            case RADIANT_COLLAPSE:
                GameSession.getInstance().setRadiantCollapseUnlocked(true);
                break;






            case VEIL:        hasVeil       = true; break;
            case ECHO:        hasEcho       = true; break;
            case TETHER:      hasTether     = true; break;
            case SHADOW_STEP: hasShadowStep = true; break;

            default: break;
        }
        System.out.println("Ability unlocked: " + unlock.name());
    }













    public boolean hasUnlock(LoreFragment.AbilityUnlock unlock) {
        if (unlock == null) return false;
        switch (unlock) {
            case MELEE:        return hasMelee;
            case PROJECTILE:   return hasProjectile;
            case DODGE:        return hasDodge;
            case WALL_CLING:   return hasWallCling;
            case SHADOW_DASH:  return hasShadowDash;
            case EMBER:        return hasBoost(LoreFragment.AbilityUnlock.EMBER);
            case IRON:         return hasBoost(LoreFragment.AbilityUnlock.IRON);
            case RADIANT_COLLAPSE: return GameSession.getInstance().isRadiantCollapseUnlocked();
            case VEIL:         return hasVeil;
            case ECHO:         return hasEcho;
            case TETHER:       return hasTether;
            case SHADOW_STEP:  return hasShadowStep;
            case NONE:
            default:           return false;
        }
    }














    public void setActiveEntities(List<GameElement> entities) {
        this.activeEntities = entities;
    }











    public void executeMelee() {
        if (!hasMelee) {
            System.out.println("DEBUG: melee not yet unlocked");
            return;
        }
        if (meleeCooldown > 0) {
            return;
        }


        int hitboxX = (facingDirection == 1) ? (x + 24) : (x - 40);
        int hitboxY = y - 4;
        Rectangle hitbox = new Rectangle(hitboxX, hitboxY, 40, 40);



        boolean ember = hasBoost(LoreFragment.AbilityUnlock.EMBER);
        for (GameElement elem : activeEntities) {
            if (!elem.isActive()) {
                continue;
            }
            if (elem instanceof Damageable) {
                if (hitbox.intersects(elem.getBounds())) {
                    int dmg = 1;
                    if (ember && elem instanceof Core) {
                        dmg = 2;
                    }
                    ((Damageable) elem).takeDamage(dmg);

                    if (elem instanceof Core) {
                        pendingCoreHitIndex = ((Core) elem).getCoreIndex();
                    }
                }
            }
        }

        meleeCooldown = 400L;
        animState = "wanderer_melee";

    }









    public void startCharge() {
        if (!hasProjectile) {
            return;
        }
        chargeHeld      = true;
        chargeStartTime = System.nanoTime();
        animState       = "wanderer_charge";
    }













    public Projectile releaseProjectile() {
        if (!hasProjectile || !chargeHeld) {
            return null;
        }
        chargeHeld = false;

        long heldTime = (System.nanoTime() - chargeStartTime) / 1_000_000;
        if (heldTime < 1000) {
            animState = "wanderer_idle";
            return null;
        }


        int   projX    = x + 12 - 4;
        int   projY    = y + 16 - 4;
        float projVelX = facingDirection * 6f;
        Projectile proj = new Projectile(projX, projY, projVelX, 0f, 2, 600);

        animState = "wanderer_idle";
        return proj;
    }









    public void executeDodge() {
        if (!hasDodge || dodgeCooldownRemaining > 0) {
            return;
        }

        velX        = facingDirection * 10f;
        invincible  = true;
        dodgeActive = true;

        long now    = System.currentTimeMillis();
        dodgeEndTime   = now + 500;
        invincibleTimer = now + 500;

        dodgeCooldownRemaining = 3000L;
        animState = "wanderer_dodge";
    }












    public PulseEffect emitPulse() {
        PulseEffect pulse = new PulseEffect(x + 12, y + 16);

        return pulse;
    }









    public void executeShadowDash() {
        if (!hasShadowDash || dashCooldownRemaining > 0) {
            return;
        }

        x += facingDirection * 80;
        invincible = true;

        long now = System.currentTimeMillis();
        invincibleTimer = now + 200;

        dashCooldownRemaining = 5000L;
        animState = "wanderer_dodge";

    }











    public void updateCooldowns(long deltaMs) {
        if (meleeCooldown > 0) {
            meleeCooldown -= deltaMs;
        }
        if (dodgeCooldownRemaining > 0) {
            dodgeCooldownRemaining -= deltaMs;
        }
        if (dashCooldownRemaining > 0) {
            dashCooldownRemaining -= deltaMs;
        }


        long now = System.currentTimeMillis();
        if (dodgeActive && now >= dodgeEndTime) {
            dodgeActive = false;
            invincible  = false;
        }


        if (invincible && invincibleTimer > 0 && now >= invincibleTimer) {
            invincible = false;
        }


        if (healFlash && now >= healFlashEnd) {
            healFlash = false;
        }
    }






















    public void update(long deltaMs) {




        boolean animGrounded;
        if (grounded) {
            animGrounded = true;
            coyoteFrames = 0;
        } else if (wasOnGround) {
            coyoteFrames++;
            animGrounded = (coyoteFrames <= COYOTE_MAX);
        } else {
            animGrounded = false;
        }
        wasOnGround = animGrounded;


        String newState;
        if (isDead)                               newState = "death";
        else if (chargeHeld)                      newState = "charge";
        else if (meleeCooldown > 0)               newState = "melee";
        else if (dodgeActive)                     newState = "dodge";
        else if (!animGrounded && velY < -0.5f)   newState = "jump";
        else if (!animGrounded && velY >= -0.5f)  newState = "fall";
        else if (Math.abs(velX) > 0.5f)           newState = "run";
        else                                      newState = "idle";


        if (!newState.equals(lastAnimState)) {
            frameIndex    = 0;
            frameTick     = 0;
            lastAnimState = newState;
        }
        currentAnimState = newState;


        animState = "wanderer_" + currentAnimState;


        frameTick++;
        if (frameTick >= FRAME_DELAY) {
            frameTick = 0;
            int maxFrames = getFrameCount(currentAnimState);
            if (currentAnimState.equals("death")) {

                if (frameIndex < maxFrames - 1) frameIndex++;
            } else {
                frameIndex = (frameIndex + 1) % maxFrames;
            }
        }


        GameSession.getInstance().sendToServer(
            Protocol.PLAYER_POS + "|WANDERER|"
            + x + "|" + y + "|" + velX + "|" + velY + "|" + currentAnimState
            + "|" + health);
    }














    public void loseLife() {
        if (isDead) return;
        isDead    = true;
        deathTimer = 0;
        addFaithful(-2);
        deathX = x;
        deathY = Math.min(y, DEATH_ANIM_MAX_Y);
        restartToAct1Pending = true;
    }











    public void updateRespawn(long deltaMs) {
        if (isDead) {
            deathTimer += deltaMs;
            if (deathTimer >= DEATH_ANIM_MS) {
                x        = respawnX;
                y        = respawnY;
                velX     = 0;
                velY     = 0;
                grounded = false;
                isDead   = false;
                deathTimer = 0;
            }
        }
    }











    public void setRespawn(int rx, int ry) {
        this.respawnX = rx;
        this.respawnY = ry;
    }














    public void addMaxHealth(int amount) {
        maxHealth += amount;
    }






    public boolean isSightRestricted() {
        return sightRestricted;
    }






    public void setSightRestricted(boolean sightRestricted) {
        this.sightRestricted = sightRestricted;
    }










    public int getFaithful() { return faithful; }






    public void setFaithful(int v) { faithful = Math.max(0, Math.min(FAITHFUL_MAX, v)); }







    public void addFaithful(int delta) { setFaithful(faithful + delta); }

















    public void activateBoost(LoreFragment.AbilityUnlock boost) {
        if (boost == LoreFragment.AbilityUnlock.EMBER
                || boost == LoreFragment.AbilityUnlock.IRON) {
            activeBoosts.add(boost);
        }

    }









    public boolean hasBoost(LoreFragment.AbilityUnlock boost) {
        return activeBoosts.contains(boost);
    }







    public Set<LoreFragment.AbilityUnlock> getActiveBoosts() {
        return Collections.unmodifiableSet(activeBoosts);
    }








    public boolean isRadiantCollapseUnlocked() {
        return GameSession.getInstance().isRadiantCollapseUnlocked();
    }






    public RadiantState getRadiantState() { return radiantState; }










    public boolean isRadiantActive() {
        return System.currentTimeMillis() < radiantActiveUntilMs;
    }






    public long getRadiantActiveUntilMs() { return radiantActiveUntilMs; }








    public void setRadiantActiveUntilMs(long endMs) { radiantActiveUntilMs = endMs; }








    public long getRadiantCooldownRemainingMs() {
        if (radiantState != RadiantState.COOLDOWN) return 0L;
        long elapsed = System.currentTimeMillis() - radiantStateStartMs;
        return Math.max(0L, RADIANT_COOLDOWN_MS - elapsed);
    }

















    public boolean updateRadiantFsm(long nowMs, boolean shiftHeld) {
        boolean transitioned = false;
        switch (radiantState) {
            case IDLE:
                if (shiftHeld) {
                    radiantState        = RadiantState.CHARGING;
                    radiantStateStartMs = nowMs;
                    transitioned = true;
                }
                break;

            case CHARGING:
                if (!shiftHeld) {
                    radiantState        = RadiantState.IDLE;
                    radiantStateStartMs = nowMs;
                    transitioned = true;
                } else if (nowMs - radiantStateStartMs >= RADIANT_CHARGE_MS) {
                    radiantState         = RadiantState.ACTIVE;
                    radiantStateStartMs  = nowMs;
                    radiantActiveUntilMs = nowMs + RADIANT_ACTIVE_MS;



                    GameSession.getInstance().sendToServer(
                        Protocol.RADIANT_ACTIVE + "|" + radiantActiveUntilMs);

                    transitioned = true;
                }
                break;

            case ACTIVE:
                if (nowMs - radiantStateStartMs >= RADIANT_ACTIVE_MS) {
                    radiantState        = RadiantState.COOLDOWN;
                    radiantStateStartMs = nowMs;
                    transitioned = true;
                }
                break;

            case COOLDOWN:
                if (nowMs - radiantStateStartMs >= RADIANT_COOLDOWN_MS) {
                    radiantState        = RadiantState.IDLE;
                    radiantStateStartMs = nowMs;
                    transitioned = true;
                }
                break;
        }
        return transitioned;
    }






    public boolean isRestartToAct1Pending() {
        return restartToAct1Pending;
    }


    public void clearRestartToAct1Pending() {
        this.restartToAct1Pending = false;
    }






    public boolean isDead() {
        return isDead;
    }






    public void setDead(boolean dead) {
        this.isDead = dead;
    }






    public int getDeathX() {
        return deathX;
    }






    public int getDeathY() {
        return deathY;
    }






    public long getDeathTimer() {
        return deathTimer;
    }







    public void triggerCollectFlash() {
        this.collectFlash      = true;
        this.collectFlashStart = System.currentTimeMillis();
    }







    public boolean isCollectFlashActive() {
        if (!collectFlash) return false;
        if (System.currentTimeMillis() - collectFlashStart > 400L) {
            collectFlash = false;
            return false;
        }
        return true;
    }






    public long getCollectFlashStart() {
        return collectFlashStart;
    }










    public String getAnimState() {
        return animState;
    }






    public void setAnimState(String animState) {
        this.animState = animState;
    }






    public boolean isHealFlash() {
        return healFlash;
    }






    public boolean isDodgeActive() {
        return dodgeActive;
    }






    public boolean isChargeHeld() {
        return chargeHeld;
    }






    public CoreHealthBar getCoreHealthBar() {
        return coreHealthBar;
    }
















    public class CoreHealthBar {






        private int[] coreHealth;









        public CoreHealthBar() {
            this.coreHealth = new int[]{3, 3, 3, 3, 3};
        }












        public void render(Graphics2D g) {




        }









        public int[] getCoreHealth() {
            return coreHealth;
        }












        public void setCoreHealth(int coreIndex, int health) {
            if (coreIndex >= 0 && coreIndex < coreHealth.length) {
                this.coreHealth[coreIndex] = Math.max(0, Math.min(3, health));
            }
        }
    }
}
