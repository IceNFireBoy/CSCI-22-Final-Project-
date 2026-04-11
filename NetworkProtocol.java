/**
 * Defines the complete set of network packets exchanged between the Lumen Architect
 * server and its clients. Every packet type is declared as a serializable static inner
 * class so instances can be written directly to and read from Java
 * {@link java.io.ObjectOutputStream} / {@link java.io.ObjectInputStream} pairs without
 * additional marshalling code. Adding new packet types here keeps the entire protocol
 * definition in one auditable location.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.io.Serializable;

public class NetworkProtocol {

    /**
     * Private constructor prevents instantiation. {@code NetworkProtocol} is a
     * namespace class; all types are accessed as static inner classes.
     */
    private NetworkProtocol() {}

    // =========================================================================
    // PlayerStatePacket
    // =========================================================================

    /**
     * Carries a snapshot of the Wanderer's position, health, and current animation
     * state as of the last game-loop tick. Sent from the Wanderer client to the server
     * and then relayed to the Apprentice client so both sides stay in sync.
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-21
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class PlayerStatePacket implements Serializable {

        /** Serialisation version identifier for safe deserialization across builds. */
        private static final long serialVersionUID = 1L;

        /** The Wanderer's x-coordinate in world space at the time this packet was sent. */
        public int x;

        /** The Wanderer's y-coordinate in world space at the time this packet was sent. */
        public int y;

        /** The Wanderer's current health value, in the range [0, 5]. */
        public int health;

        /**
         * A short string key identifying the Wanderer's active animation clip
         * (e.g., {@code "idle"}, {@code "run"}, {@code "attack_melee"}).
         */
        public String animState;

        /**
         * Constructs a {@code PlayerStatePacket} with the given positional and
         * animation data.
         *
         * @param x         the Wanderer's x-coordinate in world space
         * @param y         the Wanderer's y-coordinate in world space
         * @param health    the Wanderer's current health
         * @param animState the active animation clip key; must not be {@code null}
         */
        public PlayerStatePacket(int x, int y, int health, String animState) {
            this.x = x;
            this.y = y;
            this.health = health;
            this.animState = animState;
        }
    }

    // =========================================================================
    // GesturePacket
    // =========================================================================

    /**
     * Carries a single gesture event detected by the Apprentice's webcam feed. The
     * server interprets the gesture identifier and applies the corresponding in-world
     * effect (platform placement, hazard activation, etc.) at the specified canvas
     * coordinates.
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-21
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class GesturePacket implements Serializable {

        /** Serialisation version identifier. */
        private static final long serialVersionUID = 2L;

        /**
         * A short string key identifying the recognised gesture
         * (e.g., {@code "swipe_left"}, {@code "pinch"}, {@code "open_palm"}).
         */
        public String gestureID;

        /**
         * The x-coordinate on the game canvas where the gesture action should be
         * applied.
         */
        public int x;

        /**
         * The y-coordinate on the game canvas where the gesture action should be
         * applied.
         */
        public int y;

        /**
         * Constructs a {@code GesturePacket} for the specified gesture and target
         * position.
         *
         * @param gestureID the recognised gesture key; must not be {@code null}
         * @param x         the target x-coordinate on the canvas
         * @param y         the target y-coordinate on the canvas
         */
        public GesturePacket(String gestureID, int x, int y) {
            this.gestureID = gestureID;
            this.x = x;
            this.y = y;
        }
    }

    // =========================================================================
    // CoreStatePacket
    // =========================================================================

    /**
     * Broadcasts the current health values of all four Cores from the server to both
     * clients. Sent whenever a Core absorbs damage so each client's HUD and game-world
     * visuals remain consistent.
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-21
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class CoreStatePacket implements Serializable {

        /** Serialisation version identifier. */
        private static final long serialVersionUID = 3L;

        /**
         * A four-element array where each index corresponds to a Core (0–3) and the
         * value is that Core's current health in the range [0, 3].
         */
        public int[] health;

        /**
         * Constructs a {@code CoreStatePacket} with the provided health snapshot.
         *
         * @param health a four-element integer array of Core health values; must not
         *               be {@code null} and must have length 4
         */
        public CoreStatePacket(int[] health) {
            this.health = health;
        }
    }

    // =========================================================================
    // VictoryPacket
    // =========================================================================

    /**
     * Signals the end of the match by conveying the final victory state to both
     * clients. Upon receiving this packet each client transitions to the appropriate
     * victory or defeat screen.
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-21
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class VictoryPacket implements Serializable {

        /** Serialisation version identifier. */
        private static final long serialVersionUID = 4L;

        /**
         * The terminal outcome of the match. Must be either
         * {@link GameServer.VictoryState#WANDERER_WIN} or
         * {@link GameServer.VictoryState#APPRENTICE_WIN}.
         */
        public GameServer.VictoryState result;

        /**
         * Constructs a {@code VictoryPacket} carrying the specified match outcome.
         *
         * @param result the winning {@link GameServer.VictoryState}; must not be
         *               {@code null}
         */
        public VictoryPacket(GameServer.VictoryState result) {
            this.result = result;
        }
    }

    // =========================================================================
    // CutscenePacket
    // =========================================================================

    /**
     * Instructs both clients to begin playing a specific cutscene, identified by a
     * string trigger key. The server emits this packet when scripted story events fire
     * (e.g., the Wanderer entering a boss room or a Core being destroyed for the first
     * time).
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-21
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class CutscenePacket implements Serializable {

        /** Serialisation version identifier. */
        private static final long serialVersionUID = 5L;

        /**
         * The identifier of the cutscene to play (e.g., {@code "INTRO"},
         * {@code "BOSS_REVEAL"}, {@code "CORE_DESTROYED_0"}).
         */
        public String triggerID;

        /**
         * Constructs a {@code CutscenePacket} for the given cutscene identifier.
         *
         * @param triggerID the cutscene trigger key; must not be {@code null}
         */
        public CutscenePacket(String triggerID) {
            this.triggerID = triggerID;
        }
    }

    // =========================================================================
    // FragmentCollectedPacket
    // =========================================================================

    /**
     * Notifies both clients that a specific {@link entities.LoreFragment} has been
     * collected by the Wanderer. The Apprentice client uses this to update the lore
     * log display, while the server uses it to unlock the corresponding ability on the
     * Wanderer's player record.
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-21
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class FragmentCollectedPacket implements Serializable {

        /** Serialisation version identifier. */
        private static final long serialVersionUID = 6L;

        /**
         * The unique identifier of the collected fragment, matching the
         * {@code fragmentID} field of the corresponding
         * {@link entities.LoreFragment} instance.
         */
        public String fragmentID;

        /**
         * Constructs a {@code FragmentCollectedPacket} for the specified fragment.
         *
         * @param fragmentID the ID of the collected fragment; must not be {@code null}
         */
        public FragmentCollectedPacket(String fragmentID) {
            this.fragmentID = fragmentID;
        }
    }

    // =========================================================================
    // CoreHitPacket
    // =========================================================================

    /**
     * Sent by the Wanderer client to the server when one of the Wanderer's attacks
     * makes contact with a Core. The server is the sole authority for Core health, so
     * the client only reports the hit event; damage application and destruction logic
     * are performed exclusively on the server side.
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-22
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class CoreHitPacket implements Serializable {

        /** Serialisation version identifier. */
        private static final long serialVersionUID = 9L;

        /**
         * The zero-based index of the Core that was struck, in the range [0, 3].
         * Matches the ordering used in {@link GameServer#getCoreHealth()}.
         */
        public int coreIndex;

        /**
         * Constructs a {@code CoreHitPacket} reporting a hit on the specified Core.
         *
         * @param coreIndex the zero-based index of the Core that was struck; must be
         *                  in the range [0, 3]
         */
        public CoreHitPacket(int coreIndex) {
            this.coreIndex = coreIndex;
        }
    }

    // =========================================================================
    // RoleAssignmentPacket
    // =========================================================================

    /**
     * Sent by the server immediately after a client connects to inform it of the role
     * it has been assigned for this session. The client uses the role string to
     * configure its input handling, UI layout, and rendering mode before the match
     * begins.
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-21
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class RoleAssignmentPacket implements Serializable {

        /** Serialisation version identifier. */
        private static final long serialVersionUID = 7L;

        /**
         * The role assigned to the receiving client. Expected values are
         * {@code "WANDERER"} and {@code "APPRENTICE"}.
         */
        public String role;

        /**
         * Constructs a {@code RoleAssignmentPacket} assigning the given role.
         *
         * @param role the role string to assign; must not be {@code null}
         */
        public RoleAssignmentPacket(String role) {
            this.role = role;
        }
    }

    // =========================================================================
    // ServerStatePacket
    // =========================================================================

    /**
     * Aggregates all server-side state into a single packet for efficient periodic
     * broadcast. Clients can reconstruct the full authoritative game state from a
     * single deserialized instance, reducing the need to merge multiple in-flight
     * packets.
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-21
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class ServerStatePacket implements Serializable {

        /** Serialisation version identifier. */
        private static final long serialVersionUID = 8L;

        /**
         * A snapshot of the Wanderer's position, health, and animation state at the
         * time this packet was assembled.
         */
        public PlayerStatePacket playerState;

        /**
         * The most recent gesture event from the Apprentice, or {@code null} if no
         * gesture has been received since the last broadcast.
         */
        public GesturePacket latestGesture;

        /**
         * A snapshot of all four Core health values at the time this packet was
         * assembled.
         */
        public CoreStatePacket coreState;

        /**
         * The current match victory state. Will be {@link GameServer.VictoryState#IN_PROGRESS}
         * for most packets and transitions to a terminal state only at match end.
         */
        public GameServer.VictoryState victoryState;

        /**
         * Whether the Apprentice has activated architect-override mode at the time
         * this packet was assembled.
         */
        public boolean architectOverride;

        /**
         * Whether the Apprentice has signalled level-ready (received a
         * {@link GesturePacket} with gestureID {@code "LEVEL_READY"}) at the time
         * this packet was assembled.
         */
        public boolean levelReady;

        /**
         * {@code true} once the server has accepted both clients and started both
         * {@link server.GameServer.ClientHandler} threads. Clients spin-wait on this
         * flag before starting the game loop to avoid acting on uninitialised state.
         */
        public boolean bothConnected;

        /**
         * Constructs a {@code ServerStatePacket} from the provided component states.
         *
         * @param playerState      the current Wanderer state snapshot; may be
         *                         {@code null} before the Wanderer has connected
         * @param latestGesture    the most recent gesture packet; may be {@code null}
         * @param coreState        the current Core health snapshot; must not be
         *                         {@code null}
         * @param victoryState     the current match outcome state; must not be
         *                         {@code null}
         * @param architectOverride whether architect-override is currently active
         * @param levelReady        whether the Apprentice has sent a LEVEL_READY gesture
         * @param bothConnected     whether the server has confirmed both clients connected
         */
        public ServerStatePacket(PlayerStatePacket playerState,
                                 GesturePacket latestGesture,
                                 CoreStatePacket coreState,
                                 GameServer.VictoryState victoryState,
                                 boolean architectOverride,
                                 boolean levelReady,
                                 boolean bothConnected) {
            this.playerState = playerState;
            this.latestGesture = latestGesture;
            this.coreState = coreState;
            this.victoryState = victoryState;
            this.architectOverride = architectOverride;
            this.levelReady = levelReady;
            this.bothConnected = bothConnected;
        }
    }
}
