/**
 * Client-side session that handles networking, local state, and role assignment.
 *
 * @author Marxus Antonio L. Magisa (253602) & Antonio Sebastian B. Pasia (254505)
 * @version May 12, 2026
 *
 * I have not discussed the Java language code in my program
 * with anyone other than my instructor or the teaching assistants
 * assigned to this course.
 *
 * I have not used Java language code obtained from another student,
 * or any other unauthorized source, either modified or unmodified.
 * If any Java language code or documentation used in my program
 * was obtained from another source, such as a textbook or website,
 * that has been clearly noted with a proper citation in the comments
 * of my program.
 */
import java.util.*;

public class GameSession {

    private static GameSession instance;

    public static GameSession getInstance() {
        if (instance == null) {
            instance = new GameSession();
        }
        return instance;
    }

    public String role;
    private boolean isWandererFlag;
    private boolean isApprenticeFlag;

    private GameSession() {}

    public void setRole(String role) {
        this.role             = role;
        this.isWandererFlag   = "WANDERER".equals(role);
        this.isApprenticeFlag = "APPRENTICE".equals(role);
    }

    public boolean isWanderer()   { return isWandererFlag;   }

    public boolean isApprentice() { return isApprenticeFlag; }

    private java.util.function.Consumer<String> sendCallback;

    public void setSendCallback(java.util.function.Consumer<String> cb) {
        this.sendCallback = cb;
    }

    public void sendToServer(String msg) {
        if (sendCallback != null) sendCallback.accept(msg);
    }

    private int blockBudget = 12;

    private String currentBlockType = "BRICK";

    private int blockCycleIndex = 0;

    private static final String[] BLOCK_CYCLE = {"BRICK", "SLIDE", "SPRING", "WALL", "CRUMBLE"};

    private boolean architectOverride = false;

    public int getBlockBudget() { return blockBudget; }

    public void setBlockBudget(int b) { blockBudget = Math.max(0, b); }

    public void decrementBlockBudget() { if (blockBudget > 0) blockBudget--; }

    public String getCurrentBlockType() { return currentBlockType; }

    public void setCurrentBlockType(String t) { currentBlockType = t; }

    public boolean isArchitectOverride() { return architectOverride; }

    public void setArchitectOverride(boolean b) { architectOverride = b; }

    public void cycleBlockType() {
        blockCycleIndex = (blockCycleIndex + 1) % BLOCK_CYCLE.length;
        currentBlockType = BLOCK_CYCLE[blockCycleIndex];
    }

    public void resetBlockBudget() {
        blockBudget = 12;
        blockCycleIndex = 0;
        currentBlockType = "BRICK";
    }

    public void placeBlock(String type, int x, int y) {
        sendToServer(Protocol.PLACE_BLOCK + "|" + type + "|" + x + "|" + y);
    }

    private java.util.List<Platform> placedBlocksRef;

    public void setPlacedBlocks(java.util.List<Platform> blocks) {
        placedBlocksRef = blocks;
    }

    public void removeNearestBlock(int x, int y, int radius) {
        if (placedBlocksRef != null) {
            double best = Double.MAX_VALUE;
            for (Platform b : placedBlocksRef) {
                java.awt.Rectangle bounds = b.getBounds();
                double d = Math.hypot(bounds.x + bounds.width / 2.0 - x,
                                      bounds.y + bounds.height / 2.0 - y);
                if (d < best) best = d;
            }
            if (best > radius) return;
        }
        sendToServer(Protocol.REMOVE_BLOCK + "|" + x + "|" + y);
    }

    public void fireSearingBeam(int x, int y) {
        sendToServer(Protocol.ATTACK + "|SEARING_BEAM|" + x + "|" + y);
    }

    public void fireBlockRain() {
        sendToServer(Protocol.ATTACK + "|BLOCK_RAIN|0|0");
    }

    public void fireCrusherBlock(int x, int y) {
        sendToServer(Protocol.ATTACK + "|CRUSHER|" + x + "|" + y);
    }

    public void fireSpikeArray() {
        sendToServer(Protocol.ATTACK + "|SPIKE_ARRAY|0|0");
    }

    public void fireShield() {
        sendToServer(Protocol.ATTACK + "|SHIELD|0|0");
    }

    public void signalLevelReady() {
        sendToServer(Protocol.LEVEL_READY + "|" + blockBudget);
    }

    private static final float MAX_BATTERY    = 100f;

    private static final float DRAIN_HIGH     =  0.1667f;

    private static final float DRAIN_MED      = 0.0833f;

    private static final float DRAIN_LOW      = 0.0417f;

    private static final float DRAIN_MIN      = 0.0278f;

    private static final float REGEN_PER_TICK = 0.0139f;

    private float lightBattery = MAX_BATTERY;

    private boolean radiantCollapseUnlocked = false;

    public boolean isRadiantCollapseUnlocked() { return radiantCollapseUnlocked; }

    public void setRadiantCollapseUnlocked(boolean v) { radiantCollapseUnlocked = v; }

    private String currentAct = "ACT1";

    public String getCurrentAct() { return currentAct; }

    public void setCurrentAct(String act) { currentAct = (act != null) ? act : "ACT1"; }

    public float getLightBattery() { return lightBattery; }

    public void setLightBattery(float b) { lightBattery = Math.max(0f, Math.min(MAX_BATTERY, b)); }

    private float getDrainRate(int radius) {
        float base;
        if (radius > 140) base = DRAIN_HIGH;
        else if (radius > 100) base = DRAIN_MED;
        else if (radius > 60)  base = DRAIN_LOW;
        else base = DRAIN_MIN;

        if ("ACT2".equals(currentAct) || "ACT3".equals(currentAct)) base *= 0.5f;
        return base;
    }

    public void updateBattery(boolean lightActive, int lightRadius) {
        if (!lightActive) {
            lightBattery = Math.min(MAX_BATTERY, lightBattery + REGEN_PER_TICK);
            return;
        }
        float drain = getDrainRate(lightRadius);
        lightBattery = Math.max(0f, lightBattery - drain);
        if (lightBattery <= 0f) {
            lightBattery = 0f;

            MouseApprentice.getInstance().setLightForcedOff(true);
        }
    }

    public float getLightBrightness(int radius) {
        float normalized = (float)(radius - 20) / (float)(180 - 20);
        return 0.15f + (normalized * 0.85f);
    }
}
