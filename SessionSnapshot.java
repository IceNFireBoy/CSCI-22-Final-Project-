/**
 * Immutable data class holding a point-in-time snapshot of the full game state at the
 * moment a client disconnects. The server captures one of these during a partial
 * disconnect and transmits its contents to the reconnecting client via the
 * {@link Protocol#SNAPSHOT_BEGIN} / {@link Protocol#SNAPSHOT_BLOCK} /
 * {@link Protocol#SNAPSHOT_END} message sequence so the new client can reconstruct the
 * exact game state and resume seamlessly.
 *
 * <p>Architecture role: {@code SessionSnapshot} is the persistence layer of the
 * reconnect system (P7). When {@link GameServer} detects that one client has dropped,
 * it captures the live simulation into an instance of this class: Wanderer position/
 * health/lives, Apprentice light state, block budget, and the full list of placed blocks.
 * This object is then serialised to the {@link Protocol#SNAPSHOT_BEGIN} wire format
 * and followed by one {@link Protocol#SNAPSHOT_BLOCK} message per placed block.
 * On the reconnecting client side, {@link GameStarter#applySnapshotBegin(String[])} and
 * {@link GameStarter#spawnBlock(String, int, int)} reconstruct the game world from these
 * messages so the new player experiences the same level state as the surviving player.</p>
 *
 * <p>All fields are public for direct access by the server's capture logic and the
 * client's restore logic; no encapsulation is needed because this is a pure data
 * transfer object with no invariants to protect.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-16
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */
public class SessionSnapshot { // Plain data-transfer object; no methods beyond field declarations; public fields for fast server/client access

    // -------------------------------------------------------------------------
    // Level state
    // -------------------------------------------------------------------------

    /**
     * The one-based level number that was active at capture time. Transmitted as
     * field [1] in the {@link Protocol#SNAPSHOT_BEGIN} wire message. Read by
     * {@link GameStarter#applySnapshotBegin(String[])} to call
     * {@link GameStarter#loadLevel(int, boolean)} with the correct level number so
     * the reconnecting client spawns on the same map the surviving player is on.
     */
    public int currentLevel; // 1-based level index; e.g. 3 = third level in Act 1

    /**
     * The act string that was active at capture time. One of {@code "ACT1"},
     * {@code "ACT2"}, {@code "ACT3"}, {@code "BOSS"}, or {@code "FINAL_CORRIDOR"}.
     * Transmitted as field [2]. Read by {@link GameStarter#applySnapshotBegin} to
     * call {@link GameSession#setCurrentAct(String)} on the reconnecting client so the
     * input routing and rendering paths behave correctly for the active act.
     */
    public String currentAct; // Act identifier string; determines rendering mode (light system on/off etc.)

    // -------------------------------------------------------------------------
    // Wanderer state
    // -------------------------------------------------------------------------

    /**
     * Wanderer world-space x coordinate at capture time. Transmitted as field [3].
     * Read by {@link GameStarter#applySnapshotBegin} to teleport the local
     * {@link Player} to the correct spawn point, preventing a jarring positional pop
     * on the first rendered frame after reconnect.
     */
    public float wandererX; // World-space x; always 0-based relative to the level's coordinate system

    /**
     * Wanderer world-space y coordinate at capture time. Transmitted as field [4].
     * Symmetric with {@link #wandererX}.
     */
    public float wandererY; // World-space y; positive downward in screen-space convention

    /**
     * Wanderer health (0–5) at capture time. Transmitted as field [5]. Applied to
     * {@link Player#setHealth(int)} during snapshot restore so the reconnecting client
     * starts with the same health the Wanderer had when the disconnect happened.
     */
    public int wandererHealth; // Integer HP; max is Player.MAX_HEALTH = 5

    /**
     * Wanderer remaining lives at capture time. Transmitted as field [6]. Applied to
     * {@link Player#setLives(int)} during restore; preserves the run's difficulty state
     * so the reconnecting client doesn't accidentally get bonus lives.
     */
    public int wandererLives; // Remaining attempt count; default start is 3

    // -------------------------------------------------------------------------
    // Apprentice / light state
    // -------------------------------------------------------------------------

    /**
     * Light battery percentage (0–100) at capture time. Transmitted as field [7].
     * Applied to {@link GameSession#setLightBattery(float)} during restore so the
     * Apprentice's battery does not reset to full on reconnect, preserving the
     * resource pressure the team was operating under.
     */
    public float lightBattery; // Float percentage 0–100; 100 = full battery, 0 = empty

    /**
     * Light-source canvas-space x at capture time. Transmitted as field [8]. Allows
     * the Wanderer client to immediately render the darkness mask in the correct
     * position on the first post-reconnect frame without waiting for the Apprentice's
     * next mouse-move event.
     */
    public int lightX; // Canvas-space x of the Apprentice's light cursor at snapshot time

    /**
     * Light-source canvas-space y at capture time. Transmitted as field [9].
     * Symmetric with {@link #lightX}.
     */
    public int lightY; // Canvas-space y of the Apprentice's light cursor at snapshot time

    /**
     * Light radius in pixels at capture time. Transmitted as field [10]. Applied to
     * {@link LevelState#setLightRadius(int)} so the darkness mask is correctly sized
     * on the first rendered frame after reconnect.
     */
    public int lightRadius; // Pixel radius; range 20–180 matching MouseApprentice MIN/MAX_RADIUS constants

    /**
     * Whether the Apprentice's light was active at capture time. Transmitted as
     * field [11] ({@code "1"} / {@code "0"}). Applied to
     * {@link LevelState#setLightActive(boolean)} so the darkness state is preserved
     * exactly — the reconnecting client will not suddenly gain or lose the light.
     */
    public boolean lightActive; // true = light was on when snapshot was taken; false = light was off

    /**
     * Remaining block budget at capture time. Transmitted as field [12]. Applied to
     * {@link GameSession#setBlockBudget(int)} so the Apprentice's available placements
     * are preserved rather than reset to the default.
     */
    public int blockBudget; // Integer remaining placement tokens; decremented by GameSession.decrementBlockBudget()

    /**
     * Currently selected block type string (e.g. {@code "BRICK"}, {@code "SLIDE"},
     * {@code "SPRING"}, {@code "WALL"}). Transmitted as field [13]. Applied to
     * {@link GameSession#setCurrentBlockType(String)} so the Apprentice's active block
     * selection is restored without requiring a UI interaction.
     */
    public String currentBlockType; // Type name; must match a Platform.PlatformType constant name (case-insensitive matching in GameStarter.spawnBlock)

    // -------------------------------------------------------------------------
    // Placed blocks
    // -------------------------------------------------------------------------

    /**
     * Every block placed by the Apprentice that was still on the map at capture time.
     * Each entry is a pipe-delimited string {@code "type|x|y"} matching the format used
     * by {@link Protocol#SNAPSHOT_BLOCK} wire messages. After the SNAPSHOT_BEGIN header
     * is applied, the server sends one SNAPSHOT_BLOCK message per entry and
     * {@link GameStarter#handleMessage} routes each to
     * {@link GameStarter#spawnBlock(String, int, int)} to recreate the platform entity.
     */
    public java.util.List<String> placedBlocks = new java.util.ArrayList<>(); // Mutable list; each entry "type|x|y" maps to one SNAPSHOT_BLOCK wire message and one spawnBlock() call

    // -------------------------------------------------------------------------
    // Core health
    // -------------------------------------------------------------------------

    /**
     * Server-authoritative health of each of the four Cores (index 0–3). Values are
     * in the range [0, 3]; 0 means the Core is destroyed. Not currently transmitted
     * as a separate snapshot field — the authoritative {@link NetworkProtocol.CoreStatePacket}
     * embedded in each {@link NetworkProtocol.ServerStatePacket} keeps the client in sync
     * immediately after reconnect, making explicit snapshot transmission of core health
     * redundant. The field is retained here for completeness and future expansion.
     */
    public int[] coreHealth; // 4-element array indexed 0–3; each value is the remaining HP of that Core

    // -------------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------------

    /**
     * Wall-clock timestamp at the instant the snapshot was captured, measured by
     * {@link System#currentTimeMillis()}. Used by the server to calculate how much
     * time elapsed during the disconnect period (relevant if the countdown timer was
     * running when the client dropped). Not transmitted directly in the wire protocol;
     * stored in the server-side snapshot object only.
     */
    public long capturedAt; // Epoch ms at capture; used for timer-adjustment logic during reconnect
}
