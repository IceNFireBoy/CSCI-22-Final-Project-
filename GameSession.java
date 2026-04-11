/**
 * Singleton that stores the role assignment the server gives this client at connection
 * time and exposes convenience boolean flags so any class can quickly determine whether
 * the local machine is running as the Wanderer or the Apprentice without passing the
 * role string through every constructor. The instance is populated once during the
 * network handshake in {@link GameStarter#connectToServer(String, int)} and remains
 * valid for the lifetime of the process.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-22
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

public class GameSession {

    // =========================================================================
    // Singleton
    // =========================================================================

    /** The single instance; created lazily on first access. */
    private static GameSession instance;

    /**
     * Returns the singleton {@code GameSession} instance, creating it if this is the
     * first call.
     *
     * @return the shared {@code GameSession}; never {@code null}
     */
    public static GameSession getInstance() {
        if (instance == null) {
            instance = new GameSession();
        }
        return instance;
    }

    // =========================================================================
    // Fields
    // =========================================================================

    /**
     * The role string received from the server — either {@code "WANDERER"} or
     * {@code "APPRENTICE"}. {@code null} until {@link #setRole(String)} is called.
     */
    public String role;

    /**
     * {@code true} when this client has been assigned the Wanderer role. Equivalent to
     * {@code "WANDERER".equals(role)} but avoids repeated string comparisons in
     * hot-path code.
     */
    public boolean isWanderer;

    /**
     * {@code true} when this client has been assigned the Apprentice role. Equivalent
     * to {@code "APPRENTICE".equals(role)} but avoids repeated string comparisons.
     */
    public boolean isApprentice;

    /**
     * {@code true} when the game is running in offline test mode with no server
     * connection. Set by the connect button's action listener before
     * {@link GameStarter#startOfflineGame()} is called.
     */
    public boolean isOffline;

    // =========================================================================
    // Constructor
    // =========================================================================

    /** Private — use {@link #getInstance()}. */
    private GameSession() {}

    // =========================================================================
    // Role assignment
    // =========================================================================

    /**
     * Records the role string received from the server and derives the convenience
     * boolean flags from it. Must be called exactly once during the network handshake.
     *
     * @param role the role string; expected to be {@code "WANDERER"} or
     *             {@code "APPRENTICE"}; must not be {@code null}
     */
    public void setRole(String role) {
        this.role       = role;
        this.isWanderer  = "WANDERER".equals(role);
        this.isApprentice = "APPRENTICE".equals(role);
    }

    /**
     * Sets the offline flag. When {@code true} no server connection is active and
     * all network I/O is skipped.
     *
     * @param offline {@code true} to mark this session as offline
     */
    public void setOffline(boolean offline) {
        this.isOffline = offline;
    }

    // =========================================================================
    // Block budget management (stubs called by InputRouter)
    // =========================================================================

    private int blockBudget = 12;
    private String currentBlockType = "BRICK";
    private boolean architectOverride = false;

    public int getBlockBudget() { return blockBudget; }
    public void decrementBlockBudget() { if (blockBudget > 0) blockBudget--; }
    public String getCurrentBlockType() { return currentBlockType; }
    public void setCurrentBlockType(String t) { currentBlockType = t; }
    public boolean isArchitectOverride() { return architectOverride; }
    public void setArchitectOverride(boolean b) { architectOverride = b; }

    public void placeBlock(String type, int x, int y) {
        System.out.println("[GameSession] placeBlock stub: " + type + " at " + x + "," + y);
    }
    public void removeNearestBlock(int x, int y, int radius) {
        System.out.println("[GameSession] removeNearestBlock stub at " + x + "," + y);
    }
    public void fireSearingBeam(int x, int y) {
        System.out.println("[GameSession] fireSearingBeam stub at " + x + "," + y);
    }
    public void fireBlockRain() {
        System.out.println("[GameSession] fireBlockRain stub");
    }
    public void fireCrusherBlock(int x, int y) {
        System.out.println("[GameSession] fireCrusherBlock stub at " + x + "," + y);
    }
    public void fireSpikeArray() {
        System.out.println("[GameSession] fireSpikeArray stub");
    }
    public void fireShield() {
        System.out.println("[GameSession] fireShield stub");
    }
    public void signalLevelReady() {
        System.out.println("[GameSession] signalLevelReady stub");
    }
}
