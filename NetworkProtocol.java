/**
 * Defines the complete set of network packets exchanged between the Lumen Architect
 * server and its clients. Every packet type is declared as a serialisable static inner
 * class so instances can be written directly to and read from Java
 * {@link java.io.ObjectOutputStream} / {@link java.io.ObjectInputStream} pairs without
 * additional marshalling code. Adding new packet types here keeps the entire protocol
 * definition in one auditable location.
 *
 * <p>Architecture role: {@code NetworkProtocol} is a pure namespace for binary packet
 * types. It complements {@link Protocol} (which defines the string-based pipe-delimited
 * messages wrapped in {@link NetworkProtocol.StringPacket}) by providing strongly-typed
 * serialisable packets for structured state transfers that are too large or too structured
 * for the pipe-delimited format. {@link GameServer} writes these packets to each client's
 * {@link java.io.ObjectOutputStream}; {@link GameStarter} reads them from its
 * {@link java.io.ObjectInputStream} and dispatches based on {@code instanceof} checks.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.io.Serializable; // Marker interface required by Java Object Serialisation for all packet classes

public class NetworkProtocol { // Pure namespace class; never instantiated

    /**
     * Private constructor prevents instantiation. {@code NetworkProtocol} is a
     * namespace class containing only static inner type definitions; it has no
     * instance state or instance methods.
     */
    private NetworkProtocol() {} // Namespace class: all packet types are accessed as static inner classes

    // =========================================================================
    // PlayerStatePacket
    // =========================================================================

    /**
     * Carries a snapshot of the Wanderer's position, health, animation state, and
     * faithful-meter score as of the last game-loop tick. Sent from the Wanderer client
     * to the server and then included in the periodic {@link ServerStatePacket} broadcast
     * so the Apprentice client stays synchronised with the Wanderer's state.
     *
     * <p>Architecture role: Assembled each tick in {@link GameStarter} from the local
     * {@link Player} state and written to the server socket. The server stores the most
     * recent packet and embeds it into every {@link ServerStatePacket} broadcast without
     * re-validating the values.</p>
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-21
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class PlayerStatePacket implements Serializable {

        /**
         * Serialisation version identifier. Bumped to {@code 14L} in P8.7 when the
         * {@link #faithful} field was added so mismatched client-server builds fail
         * fast with a clear {@link java.io.InvalidClassException} rather than silently
         * deserialising corrupt data.
         */
        private static final long serialVersionUID = 14L; // Monotonically increasing; bump whenever fields are added or removed

        /** The Wanderer's x-coordinate in world space at packet-send time. */
        public int x; // World-space left edge of the Wanderer's AABB; used by Apprentice for ghost rendering

        /** The Wanderer's y-coordinate in world space at packet-send time. */
        public int y; // World-space top edge of the Wanderer's AABB; used together with x for position tracking

        /** The Wanderer's current health in the range [0, 5]. */
        public int health; // Displayed as heart icons in both clients' HUDs; game ends when this reaches 0

        /**
         * A short string key identifying the Wanderer's active animation clip at
         * packet-send time (e.g., {@code "idle"}, {@code "run"}, {@code "attack_melee"}).
         * Used by the Apprentice client's ghost renderer to play the matching animation.
         */
        public String animState; // Animation clip key; matched by the Apprentice's sprite-sheet lookup for ghost rendering

        /**
         * The Wanderer's current faithful-meter score in [0, {@code Player.FAITHFUL_MAX}].
         * Managed exclusively on the Wanderer client and relayed here so the Apprentice
         * can render the identical five-pip faithful HUD without a separate packet type.
         */
        public int faithful; // Faithful score; added in P8.7; drives the Apprentice's faithful HUD display

        /**
         * Constructs a {@code PlayerStatePacket} with the given positional and animation data.
         *
         * <p>Architecture role: Called by the Wanderer-side {@link GameStarter} each tick
         * to bundle the current player state into a packet for transmission to the server.</p>
         *
         * @param x         the Wanderer's x-coordinate in world space
         * @param y         the Wanderer's y-coordinate in world space
         * @param health    the Wanderer's current health value in [0, 5]
         * @param animState the active animation clip key; must not be {@code null}
         * @param faithful  the faithful-meter score in [0, Player.FAITHFUL_MAX]
         */
        public PlayerStatePacket(int x, int y, int health, String animState, int faithful) {
            this.x         = x;         // Store world-space x for transmission
            this.y         = y;         // Store world-space y for transmission
            this.health    = health;    // Store current health for HUD display on both clients
            this.animState = animState; // Store animation key for ghost rendering on the Apprentice side
            this.faithful  = faithful;  // Store faithful score for Apprentice HUD and StunMinigame difficulty
        }
    }

    // =========================================================================
    // CoreStatePacket
    // =========================================================================

    /**
     * Broadcasts the authoritative health values of all four {@link Core} entities from
     * the server to both clients. Sent whenever a Core absorbs damage so the HUD health
     * bars and Core visual states update consistently across both screens.
     *
     * <p>Architecture role: The server is the sole authority for Core health.
     * When the Wanderer's client detects a Core hit it sends a
     * {@link CoreHitPacket}; the server applies the damage and immediately broadcasts
     * a fresh {@code CoreStatePacket} carrying the updated health array.</p>
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-21
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class CoreStatePacket implements Serializable {

        /** Serialisation version identifier. */
        private static final long serialVersionUID = 3L; // Stable since initial implementation; bump if the health-array semantics change

        /**
         * A four-element array where index {@code i} is the current health of Core {@code i}
         * in the range [0, 3]. Index 0 = top-left Core, 1 = top-right, 2 = bottom-left,
         * 3 = bottom-right, matching the layout defined in {@link BossArenaGenerator}.
         */
        public int[] health; // Four-element health array; Core[i] is destroyed when health[i] reaches 0

        /**
         * Constructs a {@code CoreStatePacket} carrying the provided health snapshot.
         *
         * <p>Architecture role: Called by {@link GameServer} after applying Core damage
         * from a {@link CoreHitPacket} and immediately written to both client streams.</p>
         *
         * @param health a four-element integer array of Core health values; must not be
         *               {@code null} and must have length 4
         */
        public CoreStatePacket(int[] health) {
            this.health = health; // Store the health array reference; caller should not mutate the array after passing it
        }
    }

    // =========================================================================
    // VictoryPacket
    // =========================================================================

    /**
     * Signals the end of the match by conveying the final victory state to both clients.
     * Each client transitions to the appropriate victory or defeat screen on receipt.
     *
     * <p>Architecture role: Sent by {@link GameServer} when the match reaches a terminal
     * state: either all four Cores are destroyed (Wanderer wins) or the Wanderer's health
     * reaches zero (Apprentice wins). Both clients display their respective end-screen
     * based on their local role and the received {@link GameServer.VictoryState}.</p>
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-21
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class VictoryPacket implements Serializable {

        /** Serialisation version identifier. */
        private static final long serialVersionUID = 4L; // Stable; bump if VictoryState enum ordinals change

        /**
         * The terminal outcome of the match. Must be either
         * {@link GameServer.VictoryState#WANDERER_WIN} or
         * {@link GameServer.VictoryState#APPRENTICE_WIN}; never
         * {@link GameServer.VictoryState#IN_PROGRESS} in a sent packet.
         */
        public GameServer.VictoryState result; // Terminal match result; drives the end-screen display on both clients

        /**
         * Constructs a {@code VictoryPacket} carrying the specified match outcome.
         *
         * <p>Architecture role: Instantiated and written by {@link GameServer} exactly
         * once per match, at the moment the winning condition is detected.</p>
         *
         * @param result the winning {@link GameServer.VictoryState}; must not be {@code null}
         *               and must not be {@code IN_PROGRESS}
         */
        public VictoryPacket(GameServer.VictoryState result) {
            this.result = result; // Store the terminal outcome for transmission to both clients
        }
    }

    // =========================================================================
    // FragmentCollectedPacket
    // =========================================================================

    /**
     * Notifies both clients that a specific {@link LoreFragment} has been collected by
     * the Wanderer. The Apprentice client uses this to update the lore-log display;
     * the server uses it to unlock the corresponding ability on the Wanderer's player
     * record and to persist the collected state in the {@link SessionSnapshot}.
     *
     * <p>Architecture role: Sent by the Wanderer client to the server when
     * {@link CollisionDetector} triggers {@link LoreFragment#collect()}. The server
     * relays it to both clients and updates the authoritative fragment-collected set
     * so reconnects restore the correct collection state.</p>
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-21
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class FragmentCollectedPacket implements Serializable {

        /** Serialisation version identifier. */
        private static final long serialVersionUID = 6L; // Stable; bump if fragmentID semantics change

        /**
         * The unique identifier of the collected fragment, matching the
         * {@link LoreFragment#getFragmentID()} value of the entity that was touched.
         * Used by {@link FragmentLibrary} to look up the ability unlock and lore text.
         */
        public String fragmentID; // Fragment ID string; server and Apprentice client use this to identify which fragment was collected

        /**
         * Constructs a {@code FragmentCollectedPacket} for the specified fragment.
         *
         * <p>Architecture role: Instantiated by the Wanderer-side {@link GameStarter}
         * immediately after {@link LoreFragment#collect()} returns.</p>
         *
         * @param fragmentID the ID of the collected fragment; must not be {@code null}
         */
        public FragmentCollectedPacket(String fragmentID) {
            this.fragmentID = fragmentID; // Store the fragment ID for transmission to the server and relay to both clients
        }
    }

    // =========================================================================
    // StringPacket
    // =========================================================================

    /**
     * Wraps a single pipe-delimited {@link Protocol} message string so it can be
     * transmitted through the existing {@link java.io.ObjectOutputStream} channel
     * alongside strongly-typed packet objects. The server relays every
     * {@code StringPacket} it receives to both clients without inspecting the content,
     * preserving the pipe-delimited protocol's symmetry.
     *
     * <p>Architecture role: Provides a uniform transport for all {@link Protocol}
     * string messages (e.g. {@code "POS|WANDERER|100|400|0|0|idle"}) within the same
     * Object stream used for structured packets, eliminating the need for a separate
     * character-based socket channel.</p>
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-04-11
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class StringPacket implements java.io.Serializable {

        /** Serialisation version identifier. */
        private static final long serialVersionUID = 10L; // Stable; bump if the message-string format changes fundamentally

        /**
         * The pipe-delimited protocol message being transported. The first token before
         * the first {@code '|'} is always one of the constants in {@link Protocol};
         * subsequent tokens are payload fields specific to that message type.
         * Example: {@code "POS|WANDERER|100|400|0|0|idle"}.
         */
        public String message; // The raw pipe-delimited message; parsed by GameStarter's dispatch switch on receipt

        /**
         * Constructs a {@code StringPacket} carrying the given pipe-delimited message.
         *
         * <p>Architecture role: Instantiated wherever a {@link Protocol} string message
         * needs to be sent through the Object stream, e.g. when the Apprentice places a
         * block ({@code "PLACE|BRICK|400|300"}) or the Wanderer sends a position update
         * ({@code "POS|WANDERER|..."}).</p>
         *
         * @param message the pipe-delimited message string; must not be {@code null}
         */
        public StringPacket(String message) {
            this.message = message; // Store the raw message string; transmitted as-is to the server and relayed to both clients
        }
    }

    // =========================================================================
    // CutscenePacket
    // =========================================================================

    /**
     * Server-authoritative broadcast that instructs every connected client to enter the
     * cutscene-playback phase for the given {@link CutsceneID}, or to resume gameplay
     * when the cutscene ends.
     *
     * <p>Client behaviour on receipt:
     * <ul>
     *   <li>{@code start=true}: suspend world/HUD rendering, delegate painting to
     *       {@link CutsceneRenderer}, disable all gameplay input.</li>
     *   <li>{@code start=false}: call {@link CutsceneRenderer#stop()} to restore the
     *       pre-cutscene game phase and re-enable gameplay.</li>
     * </ul></p>
     *
     * <p>Lock-step protocol: The server only broadcasts the resume packet ({@code start=false})
     * after both clients have sent {@link Protocol#CUTSCENE_ACK} for the same cutscene
     * ID, or after a 30 s auto-advance guard expires.</p>
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-04-17
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class CutscenePacket implements Serializable {

        /** Serialisation version identifier. */
        private static final long serialVersionUID = 11L; // Bump if CutsceneID serialisation semantics change

        /**
         * The name string of the cutscene to play or stop, stored as
         * {@link CutsceneID#name()} rather than the enum ordinal so the wire format
         * survives enum reorderings or additions without breaking deserialisation.
         */
        public String cutsceneId; // CutsceneID.name() string; resolved back to an enum via CutsceneID.valueOf() on receipt

        /**
         * {@code true} when this packet triggers the start of cutscene playback on both
         * clients; {@code false} when both clients have acked and gameplay should resume.
         */
        public boolean start; // Start/stop flag; true = begin cutscene, false = end cutscene and resume gameplay

        /**
         * Constructs a {@code CutscenePacket} carrying the given cutscene identifier
         * and start/resume flag.
         *
         * <p>Architecture role: Instantiated by {@link GameServer} when a cutscene trigger
         * fires (e.g. Core destruction, level portal touch) and when both clients have
         * sent their {@link Protocol#CUTSCENE_ACK} replies.</p>
         *
         * @param cutsceneId the {@link CutsceneID#name()} string identifying the cutscene;
         *                   must not be {@code null}
         * @param start      {@code true} to begin playback, {@code false} to end it
         */
        public CutscenePacket(String cutsceneId, boolean start) {
            this.cutsceneId = cutsceneId; // Store the cutscene ID name string for transmission
            this.start      = start;      // Store the start/stop flag; interpreted by both clients on receipt
        }
    }

    // =========================================================================
    // CoreHitPacket
    // =========================================================================

    /**
     * Sent by the Wanderer client to the server when one of the Wanderer's attacks makes
     * contact with a {@link Core}. The server is the sole authority for Core health;
     * the client only reports the hit event. Damage application and destruction logic
     * are performed exclusively on the server, which broadcasts an updated
     * {@link CoreStatePacket} after processing each hit.
     *
     * <p>Architecture role: This packet is the client's "I hit Core N" signal. By
     * keeping damage application on the server side, both clients always agree on Core
     * health regardless of network latency between the Wanderer's melee strikes and the
     * server's acknowledgement.</p>
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-22
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class CoreHitPacket implements Serializable {

        /** Serialisation version identifier. */
        private static final long serialVersionUID = 9L; // Stable; bump if coreIndex range or semantics change

        /**
         * The zero-based index of the Core that was struck, in the range [0, 3].
         * Matches the ordering used by the four-element health array in
         * {@link CoreStatePacket} and the {@link BossArenaGenerator} corner mapping:
         * 0 = top-left, 1 = top-right, 2 = bottom-left, 3 = bottom-right.
         */
        public int coreIndex; // 0–3 inclusive; server validates the range before applying damage

        /**
         * Constructs a {@code CoreHitPacket} reporting a melee or projectile hit on the
         * specified Core.
         *
         * <p>Architecture role: Instantiated by the Wanderer-side {@link GameStarter}
         * when {@link CollisionDetector} detects a player-projectile or player-melee
         * intersection with a Core AABB.</p>
         *
         * @param coreIndex the zero-based index of the struck Core; must be in [0, 3]
         */
        public CoreHitPacket(int coreIndex) {
            this.coreIndex = coreIndex; // Store the Core index for server-side damage application
        }
    }

    // =========================================================================
    // RoleAssignmentPacket
    // =========================================================================

    /**
     * Sent by the server immediately after a client successfully claims a role in the
     * lobby to confirm the assignment. The receiving client uses the role string to
     * configure its input handling, UI layout, and rendering mode before the match begins.
     *
     * <p>Architecture role: Triggers the client's role-initialisation sequence: the
     * Wanderer client enables keyboard platformer controls; the Apprentice client enables
     * mouse-driven light and block-placement controls. After receiving this packet the
     * client transitions from the lobby screen to the in-game loading state.</p>
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-21
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class RoleAssignmentPacket implements Serializable {

        /** Serialisation version identifier. */
        private static final long serialVersionUID = 7L; // Stable; bump if the role string constants change

        /**
         * The role assigned to the receiving client. The two valid values are
         * {@code "WANDERER"} and {@code "APPRENTICE"}, matching the constants used by
         * {@link GameSession#setRole(String)}.
         */
        public String role; // Role string; client passes this to GameSession.setRole() to configure its gameplay systems

        /**
         * Constructs a {@code RoleAssignmentPacket} assigning the given role.
         *
         * <p>Architecture role: Instantiated by {@link GameServer} when it confirms
         * a {@link Protocol#LOBBY_SELECT} request and a role slot becomes occupied.</p>
         *
         * @param role the role string ({@code "WANDERER"} or {@code "APPRENTICE"});
         *             must not be {@code null}
         */
        public RoleAssignmentPacket(String role) {
            this.role = role; // Store the role string; client reads it immediately on receipt to begin role-specific setup
        }
    }

    // =========================================================================
    // ServerStatePacket
    // =========================================================================

    /**
     * Aggregates all server-side game state into a single packet for efficient periodic
     * broadcast. Both clients reconstruct the authoritative game state from a single
     * deserialised instance, reducing the impact of out-of-order or dropped packets
     * compared to sending individual sub-packets for each state field.
     *
     * <p>Architecture role: The primary authoritative update packet. Sent by
     * {@link GameServer} at the server tick rate (~60 Hz) to both clients. Both
     * clients apply the contained {@link PlayerStatePacket} for the ghost render,
     * the {@link CoreStatePacket} for the Core HUDs, the {@link GameServer.VictoryState}
     * for end-of-game detection, and the LightBall coordinates for boss-phase lighting.</p>
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-21
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class ServerStatePacket implements Serializable {

        /**
         * Serialisation version identifier. Bumped to {@code 15L} in P8.7 when
         * {@link PlayerStatePacket} gained the {@code faithful} field (version 14L),
         * keeping this aggregate packet's version monotonically increasing so
         * mismatched builds fail fast.
         */
        private static final long serialVersionUID = 15L; // Must be bumped whenever any contained packet's serialVersionUID changes

        /**
         * Snapshot of the Wanderer's position, health, animation state, and faithful
         * score at the time this packet was assembled. May be {@code null} before the
         * Wanderer client has connected and sent its first {@link PlayerStatePacket}.
         */
        public PlayerStatePacket playerState; // Null until the Wanderer connects; Apprentice null-checks before rendering the ghost

        /**
         * Snapshot of all four Core health values at packet-assembly time. Never
         * {@code null}; the server initialises Core health to 3 before the boss phase.
         */
        public CoreStatePacket coreState; // Always non-null; read by both clients to update Core HUD health bars

        /**
         * The current match outcome state. {@link GameServer.VictoryState#IN_PROGRESS}
         * for the vast majority of packets; transitions to a terminal state only when
         * the match ends. Both clients check this field each tick and trigger their
         * end-screen when it is not {@code IN_PROGRESS}.
         */
        public GameServer.VictoryState victoryState; // Match outcome; IN_PROGRESS until a win condition is detected

        /**
         * Whether the Apprentice has activated architect-override mode at packet-assembly
         * time. The Wanderer client uses this flag to suppress certain HUD elements while
         * the Apprentice has enhanced control.
         */
        public boolean architectOverride; // Architect-override flag; drives HUD suppression on the Wanderer side

        /**
         * Whether the Apprentice has signalled level-ready ({@link Protocol#LEVEL_READY})
         * by the time this packet was assembled. The Wanderer client's loading screen
         * spins until this flag becomes {@code true}.
         */
        public boolean levelReady; // Level-ready gate; Wanderer client waits for true before starting the game loop

        /**
         * {@code true} once both client-handler threads are running and both clients
         * have sent their initial handshake. Clients spin-wait on this flag before
         * starting the game loop to avoid acting on uninitialised state from the server.
         */
        public boolean bothConnected; // Both-clients-connected gate; prevents premature game-loop startup on either side

        /**
         * World-space x coordinate of the server-authoritative {@link LightBall} during
         * the BOSS phase. Both clients centre their light mask on this value to render
         * the inertia-based lighting identically. Outside the BOSS phase this field
         * carries {@code 0f} and is ignored.
         */
        public float lightX; // BOSS-phase light ball x; both clients use it for identical light-mask rendering

        /**
         * World-space y coordinate of the server-authoritative {@link LightBall} during
         * the BOSS phase. Mirror of {@link #lightX}.
         */
        public float lightY; // BOSS-phase light ball y; combined with lightX for the circular mask centre

        /**
         * Constructs a {@code ServerStatePacket} from the provided component states.
         *
         * <p>Architecture role: Called by {@link GameServer} each tick to assemble and
         * broadcast the authoritative game snapshot. The constructor simply copies all
         * arguments into fields; no computation is performed here.</p>
         *
         * @param playerState       the current Wanderer state snapshot; may be {@code null}
         * @param coreState         the current Core health snapshot; must not be {@code null}
         * @param victoryState      the current match outcome; must not be {@code null}
         * @param architectOverride whether architect-override is currently active
         * @param levelReady        whether the Apprentice has signalled level-ready
         * @param bothConnected     whether the server has confirmed both clients connected
         * @param lightX            world-space x of the boss-phase LightBall, or {@code 0f}
         * @param lightY            world-space y of the boss-phase LightBall, or {@code 0f}
         */
        public ServerStatePacket(PlayerStatePacket playerState,
                                 CoreStatePacket coreState,
                                 GameServer.VictoryState victoryState,
                                 boolean architectOverride,
                                 boolean levelReady,
                                 boolean bothConnected,
                                 float lightX,
                                 float lightY) {
            this.playerState      = playerState;      // Wanderer snapshot; null until the Wanderer connects
            this.coreState        = coreState;        // Core health array; always non-null
            this.victoryState     = victoryState;     // Match outcome; IN_PROGRESS until end
            this.architectOverride = architectOverride; // Override flag; drives Wanderer-side HUD suppression
            this.levelReady       = levelReady;       // Level-ready flag; Wanderer client gate
            this.bothConnected    = bothConnected;    // Both-connected flag; game-loop startup gate
            this.lightX           = lightX;           // BOSS-phase ball x for light-mask rendering
            this.lightY           = lightY;           // BOSS-phase ball y for light-mask rendering
        }
    }

    // =========================================================================
    // AltarChoicePacket (P8.6)
    // =========================================================================

    /**
     * Carries the server-authoritative outcome of a pre-boss altar interaction.
     * Broadcast to both clients after the server validates the Wanderer's
     * {@link Protocol#ALTAR_CHOICE} request; both clients apply the mechanical effect
     * on receipt.
     *
     * <p>Architecture role: Sent by {@link GameServer} after verifying the Wanderer's
     * choice is one of the two valid options ({@code POWER_SURGE} or
     * {@code SIGHT_RESTRICTION}). Both clients apply the buff (health increase or
     * lives increase + light radius reduction) on receipt so the effect is consistent
     * regardless of which client's rendering code runs the application logic.</p>
     */
    public static class AltarChoicePacket implements Serializable {

        /** Serialisation version identifier. */
        private static final long serialVersionUID = 13L; // Added in P8.6; bump if altarId or choice semantics change

        /**
         * Matches the {@code altarId} field of the originating altar {@link Trigger}
         * entity. Both clients use this to dismiss the overlay for the correct altar
         * in case multiple altars are visible simultaneously.
         */
        public int altarId; // The altar whose UI should be dismissed after the choice is applied

        /**
         * The chosen buff option as a string constant. Valid values:
         * <ul>
         *   <li>{@code "POWER_SURGE"} — increases the Wanderer's maximum HP by 10.</li>
         *   <li>{@code "SIGHT_RESTRICTION"} — grants one extra life and reduces the boss
         *       light radius by 25%, making the boss arena darker.</li>
         * </ul>
         */
        public String choice; // The validated choice string; both clients apply the mechanical effect on receipt

        /**
         * Constructs an {@code AltarChoicePacket} for the specified altar and choice.
         *
         * <p>Architecture role: Instantiated by {@link GameServer} after receiving and
         * validating a {@link Protocol#ALTAR_CHOICE} message from the Wanderer client.
         * Written to both client streams simultaneously.</p>
         *
         * @param altarId the altar's numeric identifier; matches the originating trigger's altarId
         * @param choice  the validated choice string ({@code "POWER_SURGE"} or
         *                {@code "SIGHT_RESTRICTION"})
         */
        public AltarChoicePacket(int altarId, String choice) {
            this.altarId = altarId; // Store the altar ID for overlay dismissal on both clients
            this.choice  = choice;  // Store the validated choice string for mechanical effect application
        }
    }
}
