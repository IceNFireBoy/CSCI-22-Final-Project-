/**
 Authoritative host for a multiplayer Lumen Architect session. Accepts exactly two clients
 (Wanderer and Apprentice), maintains all shared game state (Core health, victory conditions,
 fragment collection), and broadcasts authoritative updates to both peers every 16 ms at 60 fps.
 The server is the single source of truth — clients never trust their own local values for
 Core health, architect-override status, or attack cooldowns. All mutations to shared state
 occur only on the server game loop thread; the two ClientHandler threads feed packets into
 a thread-safe queue, ensuring no race conditions.
 */

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

public class GameServer {

    // =========================================================================
    // Enum — VictoryState
    // =========================================================================

    /**
     Possible match outcomes. The server transitions from IN_PROGRESS to one of the terminal
     states when a win condition is detected, then broadcasts a VictoryPacket to both clients.
     */
    public enum VictoryState {
        IN_PROGRESS,  // Match is ongoing; neither side has won yet
        WANDERER_WIN, // Wanderer destroyed all four Cores before time expired or health reached zero
        APPRENTICE_WIN // Apprentice defended all Cores until timer expired or Wanderer died
    }

    // =========================================================================
    // Constants
    // =========================================================================

    /** Port number the server listens on. */
    private static final int PORT = 9876;

    /** Number of Cores in the arena. */
    private static final int CORE_COUNT = 4;

    /** Starting health of each Core. */
    private static final int CORE_MAX_HEALTH = 3;

    /** Target tick duration in milliseconds for the 60 fps server loop. */
    private static final long TICK_MS = 1000L / 60L;

    public static final int ARENA_W = 3072; // Boss-arena world width in pixels (mirrored by Camera.ARENA_W on client)
    public static final int ARENA_H = 2304; // Boss-arena world height in pixels

    private static final int SLOT_0 = 0; // First connection slot (role assigned by lobby)
    private static final int SLOT_1 = 1; // Second connection slot (role assigned by lobby)

    // =========================================================================
    // Fields — server infrastructure
    // =========================================================================

    /**
     * One {@link ObjectOutputStream} per role (index 0 = Wanderer, index 1 = Apprentice).
     * Opened once at the start of each session in {@link #startSession(ServerSocket)},
     * then read-shared across threads; individual writes are serialised by
     * {@link #broadcastToAll(Object)}.
     */
    private final ObjectOutputStream[] outs = new ObjectOutputStream[2];

    /**
     * Tracks which clients are still connected. Index 0 = Wanderer, index 1 = Apprentice.
     * Set to {@code false} by a {@link ClientHandler} on any {@link IOException} and by
     * the game loop when a write fails.
     */
    private final boolean[] clientConnected = new boolean[2];

    /**
     * Thread-safe queue shared between the two {@link ClientHandler} threads (producers)
     * and the server game loop thread (consumer). Every inbound packet is placed here
     * regardless of which client sent it.
     */
    private final LinkedBlockingQueue<Object> sharedQueue = new LinkedBlockingQueue<>();

    // =========================================================================
    // Fields — game state (all written/read on the game loop thread unless noted)
    // =========================================================================

    /**
     * Mirrors the health of each of the four Cores. Each element corresponds to the
     * Core at that index (0–3) and starts at {@link #CORE_MAX_HEALTH}.
     */
    private int[] coreHealth;

    private boolean architectOverride; // Architect override flag; set true when first Core destroyed, cleared when all four destroyed
    private VictoryState victoryState; // Current match outcome; transitions from IN_PROGRESS to a terminal state when a win condition is detected

    private volatile VictoryState pendingVictoryState; // Victory outcome waiting to be committed once endgame cutscenes finish; null while no condition is pending
    private NetworkProtocol.PlayerStatePacket latestPlayerState; // Most recent PlayerStatePacket from Wanderer; null until first packet arrives
    private final boolean[] levelReady = new boolean[2]; // Per-slot level-ready flag; set true when Apprentice signals level completion
    private final Set<String> serverCollectedFragments = new HashSet<>(); // Server-authoritative set of collected fragment IDs during this session
    private int destroyedCoreCount = 0; // Running count of destroyed Cores; used to trigger architect-override (first) and Wanderer victory (all four)

    // =========================================================================
    // Fields — cutscene lock-step (P8.0)
    // =========================================================================

    private volatile String activeCutsceneId = null; // Name of currently playing cutscene; null when inactive; waits for both slots to ack before resuming
    private final boolean[] cutsceneAcked = new boolean[2]; // Per-slot ack flags for the active cutscene
    private volatile long cutsceneStartMs = 0L; // System.currentTimeMillis() when activeCutsceneId was started; used for auto-advance guard timeout
    private static final long CUTSCENE_GUARD_MS = 30_000L; // Auto-advance timeout; forces resume if both acks don't arrive in this time

    // =========================================================================
    // Fields — boss arena (P8.2)
    // =========================================================================

    private volatile long bossArenaSeed = 0L; // Deterministic seed for boss arena; set once on first BOSS_ENTER, broadcast so both clients generate identical arena locally
    private volatile boolean bossArenaStarted = false; // True once session enters boss arena; guards against duplicate BOSS_ENTER messages

    // =========================================================================
    // Fields — boss-phase light ball (P8.3)
    // =========================================================================

    private volatile LightBall lightBall = null; // Server-authoritative light instance in BOSS phase; eases toward Apprentice target each tick; position broadcast in ServerStatePacket
    private volatile boolean bossPhaseActive = false; // True once session enters BOSS phase; guards runGameLoop() so lightBall.step() only runs during boss fight

    // =========================================================================
    // Fields — boss attack dispatcher (P8.5)
    // =========================================================================

    /**
     * Canonical attack-type strings carried inside an
     * {@link Protocol#ATTACK} request from the Apprentice and echoed back to
     * both clients inside the resulting {@link Protocol#BOSS_ATK} dispatch.
     * The array order is also the index space used by
     * {@link #attackCooldownUntilMs} — any change here must be mirrored by
     * the client-side {@code GameStarter.spawnBossAttack(...)} switch.
     */
    private static final String[] ATTACK_TYPES = {
            "SEARING_BEAM", "BLOCK_RAIN", "CRUSHER", "SPIKE_ARRAY", "SHIELD"
    };

    /**
     * Per-attack-type cooldowns in milliseconds. Index matches
     * {@link #ATTACK_TYPES}. The Apprentice cannot spawn another attack of the
     * same type until this many ms have elapsed since the last successful
     * dispatch; requests that arrive earlier are silently dropped.
     */
    private static final long[] ATTACK_COOLDOWN_MS = {
            5_000L,  // SEARING_BEAM
            8_000L,  // BLOCK_RAIN
            10_000L, // CRUSHER
            4_000L,  // SPIKE_ARRAY
            6_000L   // SHIELD
    };

    /**
     * For each attack type (index matches {@link #ATTACK_TYPES}), the wall-clock
     * time (from {@link System#currentTimeMillis()}) at which the per-type
     * cooldown expires. Any dispatch request arriving while
     * {@code currentMs < attackCooldownUntilMs[type]} is rejected. Updated on
     * successful dispatch in {@link #handleAttackMessage(String, int)}.
     */
    private final long[] attackCooldownUntilMs = new long[ATTACK_TYPES.length];

    // =========================================================================
    // Fields — stun minigame (P8.8)
    // =========================================================================

    /**
     * Wall-clock ms until which the Architect is stunned (boss-attack dispatches
     * are suppressed). Initialised to 0 so no stun is active at session start;
     * {@link #handleStunResultMessage(String, int)} stamps this forward by
     * {@link StunMinigame#STUN_DURATION_MS} on a successful minigame. The
     * {@link #handleAttackMessage(String, int)} gate checks {@code now <
     * architectStunnedUntilMs} and silently drops the dispatch while
     * suppression is active.
     */
    private volatile long architectStunnedUntilMs = 0L;

    /**
     * RNG used to roll stun opportunities on each successful
     * {@link Protocol#BOSS_ATK} dispatch. Probability is
     * {@code 0.1 + 0.08 * faithful} where {@code faithful} is the Wanderer's
     * current meter carried in the most recent
     * {@link NetworkProtocol.PlayerStatePacket}. Seeded from
     * {@link System#nanoTime()} so two consecutive sessions do not produce
     * the same opportunity pattern.
     */
    private final java.util.Random stunRng = new java.util.Random(System.nanoTime());

    /** Minimum ms between opportunity rolls so rapid attacks cannot chain back-to-back windows. */
    private static final long STUN_OPPORTUNITY_COOLDOWN_MS = 4_000L;

    /** Wall-clock ms before which another stun opportunity cannot be rolled. */
    private volatile long stunOpportunityCooldownUntilMs = 0L;

    /**
     * Set to {@code true} once both {@link ClientHandler} threads have been started,
     * indicating that the server is ready to accept game-state packets from either
     * client. Included in every {@link NetworkProtocol.ServerStatePacket} so clients
     * can spin-wait on this flag before starting their game loops.
     */
    private boolean bothConnected = false;

    /**
     * Cleared to {@code false} by any {@link ClientHandler} the moment its client
     * disconnects. The game loop polls this flag so it exits cleanly even when no
     * formal victory packet is ever sent (e.g. a mid-game crash or early quit).
     */
    private volatile boolean sessionActive = true;

    // =========================================================================
    // Fields — lobby state
    // =========================================================================

    /**
     * The role each slot has claimed so far, indexed by slot (0 = first client,
     * 1 = second client). {@code null} means that slot has not chosen yet.
     * Values are {@code "WANDERER"} or {@code "APPRENTICE"}.
     */
    private final String[] lobbySelectedRole = new String[2];

    /**
     * The role each slot is currently hovering over, e.g. {@code "WANDERER"},
     * {@code "APPRENTICE"}, or {@code "NONE"}.
     */
    private final String[] lobbyHoverState = {"NONE", "NONE"};

    // =========================================================================
    // Fields — session persistence / reconnect
    // =========================================================================

    /** Reference to the listening socket, stored for the reconnect acceptor thread. */
    private ServerSocket serverSocket;

    /**
     * The role name of the slot that disconnected during a partial-disconnect event,
     * or {@code null} when both clients are connected (or both are gone). E.g.
     * {@code "WANDERER"} means the Wanderer dropped and the Apprentice is waiting.
     */
    private volatile String vacantRole = null;

    /** {@link System#currentTimeMillis()} when the partial disconnect was detected. */
    private volatile long partialDisconnectTime = -1;

    /** Maximum milliseconds the server will hold a session for a single disconnected client. */
    private static final long RECONNECT_TIMEOUT_MS = 90 * 1000L;

    /** Snapshot of all game state captured at the moment of partial disconnect. */
    private SessionSnapshot snapshot = null;

    /**
     * When {@code true}, the game loop skips packet processing and physics — it only
     * broadcasts state so the surviving client keeps receiving heartbeat packets and can
     * render its pause overlay. Set to {@code true} by {@link #handlePartialDisconnect}
     * and cleared by the reconnect handler.
     */
    private volatile boolean gamePaused = false;

    // -------------------------------------------------------------------------
    // Last-known state fields — updated every tick from incoming packets so the
    // snapshot is accurate at the moment of disconnect.
    // -------------------------------------------------------------------------

    private int lastKnownLevel = 1;
    private String lastKnownAct = "ACT1";
    private float lastKnownWandererX = 100, lastKnownWandererY = 400;
    private int lastKnownWandererHealth = 5;
    private int lastKnownWandererLives = 3;
    private float lastKnownLightBattery = 100f;
    private int lastKnownLightX = 512, lastKnownLightY = 384;
    private int lastKnownLightRadius = 180;
    private boolean lastKnownLightActive = true;
    private int lastKnownBlockBudget = 12;
    private String lastKnownBlockType = "BRICK";

    /**
     * Server-side record of every placed block that is currently on the map.
     * Each entry is {@code "type|x|y"}.  Updated from {@link Protocol#PLACE_BLOCK}
     * and {@link Protocol#REMOVE_BLOCK} messages so the snapshot is accurate.
     */
    private final java.util.List<String> serverPlacedBlocks =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    // =========================================================================
    // Constructor
    // =========================================================================

    public GameServer() { // Initializes all Cores to CORE_MAX_HEALTH, architect-override disabled, victoryState to IN_PROGRESS
        this.coreHealth = new int[]{CORE_MAX_HEALTH, CORE_MAX_HEALTH, CORE_MAX_HEALTH, CORE_MAX_HEALTH};
        this.architectOverride = false;
        this.victoryState = VictoryState.IN_PROGRESS;
    }

    // =========================================================================
    // Entry point
    // =========================================================================

    public static void main(String[] args) { // Application entry point; binds ServerSocket once and runs infinite outer loop accepting fresh client pairs
        System.out.println("Lumen Architect Server starting on port " + PORT + "...");
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(PORT);
            serverSocket.setReuseAddress(true);   // fast restart on same port

            while (true) {                        // outer loop — one iteration per session
                System.out.println("[Server] Waiting for players...");
                GameServer server = new GameServer();
                server.startSession(serverSocket);
                System.out.println("[Server] Session ended. Ready for new players.");
            }

        } catch (IOException e) {
            System.err.println("Server fatal error: " + e.getMessage());
        } finally {
            closeQuietly(serverSocket);
        }
    }

    private static void closeQuietly(ServerSocket s) { // Closes a ServerSocket without throwing
        if (s != null && !s.isClosed()) {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    private static void closeQuietly(Socket s) { // Closes a Socket without throwing
        if (s != null && !s.isClosed()) {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    private ObjectInputStream readHello(Socket sock) { // Validates freshly-accepted socket by reading RECONNECT_HELLO; returns OIS on success, null if invalid or timeout
        ObjectInputStream in = null;
        try {
            sock.setSoTimeout(HELLO_TIMEOUT_MS);
            in = new ObjectInputStream(sock.getInputStream());
            Object pkt = in.readObject();
            if (pkt instanceof NetworkProtocol.StringPacket) {
                String msg = ((NetworkProtocol.StringPacket) pkt).message;
                if (msg != null && msg.startsWith(Protocol.RECONNECT_HELLO + "|")) {
                    // Reset the socket timeout so game-loop reads are blocking
                    // again — a short timeout leaking into the session would
                    // make the ClientHandler prematurely throw on idle ticks.
                    sock.setSoTimeout(0);
                    return in;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            // fall through to the null return below
        }
        // Best-effort reset of the socket timeout even when rejecting, so a
        // caller that decides to reuse the socket doesn't inherit the short
        // validation window.
        try { sock.setSoTimeout(0); } catch (IOException ignored) {}
        return null;
    }

    private static final int HELLO_TIMEOUT_MS = 3000; // Timeout in milliseconds for readHello() to validate client socket

    public void startSession(ServerSocket serverSocket) { // Runs one complete game session; accepts 2 clients, starts handler and game loop threads, blocks until session ends
        this.serverSocket = serverSocket; // store for reconnect acceptor thread
        try {
            // Defensive: any prior reconnect-acceptor thread may have leaked a
            // non-zero SO_TIMEOUT onto this shared ServerSocket. The acceptor's
            // finally block normally clears it, but a swallowed IOException or
            // an abrupt thread exit could leave the timeout set, causing this
            // accept() to throw SocketTimeoutException and kill the new session
            // before it even begins. Reset to blocking (0) unconditionally.
            try { serverSocket.setSoTimeout(0); }
            catch (IOException ignored) {}

            // ---- Accept first client into SLOT_0 ----
            // Loop until we get a socket whose RC_HELLO validates. Invalid
            // sockets (no hello, malformed hello, stray reconnect attempt from
            // a dead prior session) are silently closed and we accept again.
            Socket socket0 = null;
            ObjectInputStream in0 = null;
            while (in0 == null) {
                socket0 = serverSocket.accept();
                in0 = readHello(socket0);
                if (in0 == null) {
                    System.out.println("[Server] Rejected socket with invalid hello from "
                            + socket0.getInetAddress());
                    closeQuietly(socket0);
                    socket0 = null;
                }
            }
            sockets[SLOT_0] = socket0;
            outs[SLOT_0] = new ObjectOutputStream(socket0.getOutputStream());
            outs[SLOT_0].flush();   // flush OOS header before client reads it
            clientConnected[SLOT_0] = true;
            // Send LOBBY role — actual role is assigned after lobby selection.
            outs[SLOT_0].writeObject(new NetworkProtocol.RoleAssignmentPacket("LOBBY"));
            outs[SLOT_0].flush();

            // ---- Accept second client into SLOT_1 ----
            Socket socket1 = null;
            ObjectInputStream in1 = null;
            while (in1 == null) {
                socket1 = serverSocket.accept();
                in1 = readHello(socket1);
                if (in1 == null) {
                    System.out.println("[Server] Rejected socket with invalid hello from "
                            + socket1.getInetAddress());
                    closeQuietly(socket1);
                    socket1 = null;
                }
            }
            sockets[SLOT_1] = socket1;
            outs[SLOT_1] = new ObjectOutputStream(socket1.getOutputStream());
            outs[SLOT_1].flush();
            clientConnected[SLOT_1] = true;
            outs[SLOT_1].writeObject(new NetworkProtocol.RoleAssignmentPacket("LOBBY"));
            outs[SLOT_1].flush();

            // ---- Start ClientHandler reader threads ----
            Thread handler0 = new Thread(
                    new ClientHandler(socket0, in0, SLOT_0),
                    "ClientHandler-SLOT0");
            handler0.setDaemon(true);
            handler0.start();

            Thread handler1 = new Thread(
                    new ClientHandler(socket1, in1, SLOT_1),
                    "ClientHandler-SLOT1");
            handler1.setDaemon(true);
            handler1.start();

            // Both handlers running — signal so clients start their game loops
            this.bothConnected = true;

            // ---- Start server game loop ----
            Thread gameLoop = new Thread(this::runGameLoop, "ServerLoop");
            gameLoop.setDaemon(true);   // daemon: outer main() loop keeps JVM alive
            gameLoop.start();

            System.out.println("[Server] Both players connected. Starting game.");
            gameLoop.join();   // block until the session ends

        } catch (IOException e) {
            System.out.println("[Server] Session I/O error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // Close every socket that may have been used during this session,
            // including any replacement sockets opened by the reconnect handler.
            for (Socket s : sockets) closeQuietly(s);
            System.out.println("[Server] Session sockets closed.");
        }
    }

    // =========================================================================
    // Game loop (Thread 3 — 60 fps)
    // =========================================================================

    /**
     * Runs the server game loop at approximately 60 fps. Each tick drains the
     * {@link #sharedQueue}, processes every pending packet, then broadcasts the
     * consolidated {@link NetworkProtocol.ServerStatePacket} to both clients. Exits
     * when {@link #victoryState} is no longer {@link VictoryState#IN_PROGRESS}.
     */
    private void runGameLoop() {
        try {
            while (victoryState == VictoryState.IN_PROGRESS && sessionActive) {
                long tickStart = System.currentTimeMillis();

                if (gamePaused) {
                    // PAUSED_WAITING: do not process incoming game packets (they are
                    // stale from before the disconnect). Just broadcast so the
                    // surviving client keeps receiving heartbeat state updates.
                    sharedQueue.clear();
                    broadcastState();
                } else {
                    // Drain the shared queue and process each packet
                    Object packet;
                    while ((packet = sharedQueue.poll()) != null) {
                        processPacket(packet);
                    }
                    // Cutscene auto-advance guard: if a cutscene has been
                    // active longer than CUTSCENE_GUARD_MS without both acks,
                    // force the resume so the session cannot hang on a dropped
                    // CUT_ACK from either slot.
                    if (activeCutsceneId != null
                            && (System.currentTimeMillis() - cutsceneStartMs)
                                    > CUTSCENE_GUARD_MS) {
                        System.out.println("[Server] Cutscene guard fired — "
                                + "forcing resume of " + activeCutsceneId);
                        endCutscene(activeCutsceneId);
                    }
                    // P8.3 — step the server-authoritative light ball each
                    // tick during BOSS so its position eases toward the most
                    // recent Apprentice target without teleporting. The ball
                    // is null outside BOSS; inside BOSS, stepping continues
                    // even when a cutscene is active so the light is already
                    // in the right place on cutscene resume.
                    if (bossPhaseActive && lightBall != null) {
                        lightBall.step();
                    }
                    // Broadcast consolidated state snapshot
                    broadcastState();
                }

                // Sleep for the remainder of this tick
                long elapsed = System.currentTimeMillis() - tickStart;
                long remaining = TICK_MS - elapsed;
                if (remaining > 0) {
                    try {
                        Thread.sleep(remaining);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            System.out.println("[GameServer] Game loop ended. Victory state: " + victoryState);
        } catch (Exception e) {
            System.out.println("[GameServer] Fatal error in game loop:");
            e.printStackTrace();
        }
    }

    // =========================================================================
    // Packet processing (called on the game loop thread)
    // =========================================================================

    /**
     * Dispatches a single inbound packet to the appropriate handler. All mutations to
     * shared game state are performed here, ensuring they occur on the single game loop
     * thread.
     *
     * @param packet the deserialized packet object; never {@code null}
     */
    private void processPacket(Object packet) {
        if (packet instanceof NetworkProtocol.PlayerStatePacket) {
            NetworkProtocol.PlayerStatePacket p = (NetworkProtocol.PlayerStatePacket) packet;
            latestPlayerState = p;
            // Track last-known wanderer state for snapshot accuracy
            lastKnownWandererX = p.x;
            lastKnownWandererY = p.y;
            lastKnownWandererHealth = p.health;
            // Wanderer death → P8.10: play ARCHITECT_VICTORY cutscene first,
            // then commit VictoryState and broadcast VictoryPacket in endCutscene.
            if (p.health <= 0 && victoryState == VictoryState.IN_PROGRESS
                    && pendingVictoryState == null) {
                pendingVictoryState = VictoryState.APPRENTICE_WIN;
                startCutscene(CutsceneID.ARCHITECT_VICTORY.name());
            }

        } else if (packet instanceof NetworkProtocol.FragmentCollectedPacket) {
            NetworkProtocol.FragmentCollectedPacket f = (NetworkProtocol.FragmentCollectedPacket) packet;
            // Only relay if this fragment has not already been recorded (dedup guard)
            if (serverCollectedFragments.add(f.fragmentID)) {
                broadcastToAll(f);
            }

        } else if (packet instanceof NetworkProtocol.CoreHitPacket) {
            NetworkProtocol.CoreHitPacket h = (NetworkProtocol.CoreHitPacket) packet;
            handleCoreHit(h.coreIndex);

        } else if (packet instanceof NetworkProtocol.StringPacket) {
            // Parse the text-protocol message to update last-known state for snapshot,
            // then relay to both clients without modification.
            String msg = ((NetworkProtocol.StringPacket) packet).message;
            if (msg != null) updateLastKnownFromMessage(msg);
            broadcastToAll(packet);
        }
    }

    /**
     * Inspects an inbound pipe-delimited protocol message and, if it represents a
     * state-relevant event (position, light, block placement/removal, level change),
     * updates the corresponding {@code lastKnown*} field so {@link #captureSnapshot()}
     * reflects the true most-recent state. Unknown or non-state messages are ignored.
     *
     * @param msg the inbound message string, e.g. {@code "POS|WANDERER|150|400|0|0|run"}
     */
    private void updateLastKnownFromMessage(String msg) {
        try {
            String[] p = msg.split("\\|");
            if (p.length == 0) return;
            switch (p[0]) {
                case Protocol.PLAYER_POS:
                    // POS|WANDERER|x|y|vx|vy|state
                    if (p.length >= 4 && "WANDERER".equals(p[1])) {
                        lastKnownWandererX = Float.parseFloat(p[2]);
                        lastKnownWandererY = Float.parseFloat(p[3]);
                    }
                    break;
                case Protocol.LIGHT_UPDATE:
                    // LIGHT|x|y|radius
                    if (p.length >= 4) {
                        lastKnownLightX = Integer.parseInt(p[1]);
                        lastKnownLightY = Integer.parseInt(p[2]);
                        lastKnownLightRadius = Integer.parseInt(p[3]);
                    }
                    break;
                case Protocol.PLACE_BLOCK:
                    // PLACE|type|x|y
                    if (p.length >= 4) {
                        serverPlacedBlocks.add(p[1] + "|" + p[2] + "|" + p[3]);
                        if (lastKnownBlockBudget > 0) lastKnownBlockBudget--;
                        lastKnownBlockType = p[1];
                    }
                    break;
                case Protocol.REMOVE_BLOCK:
                    // REMOVE|x|y — remove the block whose centre is closest to (x,y)
                    if (p.length >= 3) {
                        try {
                            int rx = Integer.parseInt(p[1]);
                            int ry = Integer.parseInt(p[2]);
                            synchronized (serverPlacedBlocks) {
                                int bestIdx = -1;
                                double bestDist = Double.MAX_VALUE;
                                for (int i = 0; i < serverPlacedBlocks.size(); i++) {
                                    String[] bp = serverPlacedBlocks.get(i).split("\\|");
                                    if (bp.length < 3) continue;
                                    int bx = Integer.parseInt(bp[1]);
                                    int by = Integer.parseInt(bp[2]);
                                    double d = Math.hypot(bx - rx, by - ry);
                                    if (d < bestDist) { bestDist = d; bestIdx = i; }
                                }
                                if (bestIdx >= 0) serverPlacedBlocks.remove(bestIdx);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                    break;
                case Protocol.LEVEL_CHANGE:
                    // LEVEL|levelIndex — reset budget and update act string
                    if (p.length >= 2) {
                        try {
                            int lvl = Integer.parseInt(p[1]);
                            lastKnownLevel = lvl;
                            lastKnownAct = actFromLevel(lvl);
                            lastKnownBlockBudget = 12;
                            lastKnownBlockType = "BRICK";
                            serverPlacedBlocks.clear();
                        } catch (NumberFormatException ignored) {}
                    }
                    break;
                default:
                    break;
            }
        } catch (NumberFormatException ignored) {
            // Malformed message — ignore for snapshot purposes, relay still happens
        }
    }

    /**
     * Maps a one-based level index to its corresponding act string for snapshot
     * metadata. Must exactly mirror the mapping used by
     * {@link GameStarter#loadLevel(int, boolean)} on the client:
     * levels 1–3 → ACT1, 4–6 → ACT2, 7–9 → ACT3.
     */
    private static String actFromLevel(int lvl) {
        if (lvl <= 3) return "ACT1";
        if (lvl <= 6) return "ACT2";
        if (lvl <= 9) return "ACT3";
        return "ACT3"; // fallback — no level beyond 9 in the current build
    }

    /**
     * Convenience method: wraps {@code message} in a {@link NetworkProtocol.StringPacket}
     * and broadcasts it to both connected clients.
     *
     * @param message the pipe-delimited protocol message to send
     */
    private void broadcast(String message) {
        broadcastToAll(new NetworkProtocol.StringPacket(message));
    }

    // =========================================================================
    // Cutscene lock-step (P8.0)
    // =========================================================================

    /**
     * Handles a {@code CUT_TRIGGER|<id>} or {@link Protocol#CUTSCENE_ACK} message
     * from the given slot. Triggers are authoritative: the first valid trigger
     * broadcasts a start {@link NetworkProtocol.CutscenePacket} to both clients
     * and resets the per-slot ack tracker. Acks are counted per-slot so a
     * duplicate ack from the same client does not prematurely resume; once both
     * slots have acked (or the 30 s guard has expired in {@link #runGameLoop()})
     * the server broadcasts the resume packet.
     *
     * @param msg  the raw pipe-delimited message
     * @param slot the slot that sent it (0 or 1)
     */
    private void handleCutsceneMessage(String msg, int slot) {
        String[] parts = msg.split("\\|");
        if (parts.length < 2) return;
        String tok = parts[0];
        String id  = parts[1];

        if ("CUT_TRIGGER".equals(tok)) {
            startCutscene(id);
        } else if (Protocol.CUTSCENE_ACK.equals(tok)) {
            if (id.equals(activeCutsceneId) && slot >= 0 && slot < 2) {
                cutsceneAcked[slot] = true;
                System.out.println("[Server] Cutscene ack from slot " + slot
                        + " for " + id);
                if (cutsceneAcked[0] && cutsceneAcked[1]) {
                    endCutscene(id);
                }
            }
        }
    }

    /**
     * Starts a server-authoritative cutscene: records the id, zeroes per-slot
     * acks, captures the start timestamp for the 30 s guard, and broadcasts a
     * start {@link NetworkProtocol.CutscenePacket} to both clients.
     *
     * @param id the {@link CutsceneID} name string; ignored if invalid
     */
    void startCutscene(String id) {
        if (id == null) return;
        try { CutsceneID.valueOf(id); }
        catch (IllegalArgumentException ex) { return; }
        this.activeCutsceneId = id;
        this.cutsceneAcked[0] = false;
        this.cutsceneAcked[1] = false;
        this.cutsceneStartMs  = System.currentTimeMillis();
        broadcastToAll(new NetworkProtocol.CutscenePacket(id, true));
        System.out.println("[Server] Cutscene start broadcast: " + id);
    }

    /**
     * Ends the currently-active cutscene by broadcasting a resume packet and
     * clearing the ack tracker. Only called when both clients have acked or the
     * 30 s guard fires in {@link #runGameLoop()}.
     *
     * @param id the cutscene id that is being resumed
     */
    private void endCutscene(String id) {
        if (id == null || !id.equals(activeCutsceneId)) return;
        broadcastToAll(new NetworkProtocol.CutscenePacket(id, false));
        System.out.println("[Server] Cutscene end broadcast: " + id);
        this.activeCutsceneId = null;
        this.cutsceneAcked[0] = false;
        this.cutsceneAcked[1] = false;
        this.cutsceneStartMs  = 0L;

        // P8.10 — endgame cutscene chain.
        // Wanderer path: CORE_4_DESTROYED → WANDERER_VICTORY → HOME → VictoryPacket
        // Architect path: ARCHITECT_VICTORY → VictoryPacket
        if (pendingVictoryState == VictoryState.WANDERER_WIN) {
            if (CutsceneID.CORE_4_DESTROYED.name().equals(id)) {
                startCutscene(CutsceneID.WANDERER_VICTORY.name());
            } else if (CutsceneID.WANDERER_VICTORY.name().equals(id)) {
                startCutscene(CutsceneID.HOME.name());
            } else if (CutsceneID.HOME.name().equals(id)) {
                victoryState = pendingVictoryState;
                pendingVictoryState = null;
                broadcastToAll(new NetworkProtocol.VictoryPacket(victoryState));
                System.out.println("[Server] Wanderer victory — VictoryPacket broadcast.");
            }
        } else if (pendingVictoryState == VictoryState.APPRENTICE_WIN
                && CutsceneID.ARCHITECT_VICTORY.name().equals(id)) {
            victoryState = pendingVictoryState;
            pendingVictoryState = null;
            broadcastToAll(new NetworkProtocol.VictoryPacket(victoryState));
            System.out.println("[Server] Architect victory — VictoryPacket broadcast.");
        }
    }

    // =========================================================================
    // Boss-arena transition (P8.2)
    // =========================================================================

    /**
     * Handles a {@link Protocol#BOSS_ENTER} message from the Wanderer client,
     * which is sent when the Wanderer collides with the portal on the final
     * regular level. On the first call per session, the server picks a
     * deterministic seed, broadcasts {@link Protocol#BOSS_ARENA} so both clients
     * generate the identical arena locally, then launches the
     * {@link CutsceneID#ARCHITECT_SPEAKS} cutscene via {@link #startCutscene}
     * (which reuses the existing P8.0 lock-step path so both clients ack before
     * gameplay resumes in the boss arena). Subsequent triggers during the same
     * session are ignored.
     *
     * @param slot the slot index (0 or 1) that sent the message; only the slot
     *             currently holding the {@code WANDERER} role is allowed to
     *             trigger the transition
     */
    private synchronized void handleBossEnter(int slot) {
        if (bossArenaStarted) return;
        if (slot < 0 || slot >= 2) return;
        if (!"WANDERER".equals(roleForSlot(slot))) {
            System.out.println("[Server] Ignoring BOSS_ENTER from non-Wanderer slot " + slot);
            return;
        }
        bossArenaStarted = true;
        bossArenaSeed = System.currentTimeMillis();
        System.out.println("[Server] BOSS_ENTER received — arena seed=" + bossArenaSeed);

        // P8.3 — instantiate the server-authoritative LightBall at arena centre
        // so the very first ServerStatePacket after arena entry carries a
        // sensible light position. runGameLoop() will step() it each tick from
        // here on, and the ball will start chasing real Apprentice input as
        // soon as the first Protocol.LIGHT_TARGET arrives.
        this.lightBall = new LightBall(ARENA_W / 2f, ARENA_H / 2f);
        this.bossPhaseActive = true;

        // Broadcast the arena seed first so both clients have the boss arena
        // built (and their phase set to BOSS) by the time the cutscene ends.
        broadcast(Protocol.BOSS_ARENA + "|" + bossArenaSeed);

        // Now drive the server-authoritative ARCHITECT_SPEAKS cutscene via the
        // existing P8.0 lock-step path. Clients will suspend world/HUD rendering
        // and ack individually; resume fires automatically via endCutscene once
        // both acks arrive (or after the 30 s auto-advance guard).
        startCutscene(CutsceneID.ARCHITECT_SPEAKS.name());
    }

    // =========================================================================
    // Light ball — inbound target handling (P8.3)
    // =========================================================================

    /**
     * Handles a {@link Protocol#LIGHT_TARGET} message from the Apprentice,
     * updating the chase target of the server-authoritative {@link LightBall}.
     * Ignored when the session is not in BOSS or the ball has not yet been
     * instantiated; ignored when sent from the non-Apprentice slot (the
     * Wanderer has no authority over the light during boss).
     *
     * @param msg  the raw pipe-delimited message, e.g. {@code "LT|1536.0|1152.0"}
     * @param slot the slot index (0 or 1) that sent it
     */
    private void handleLightTargetMessage(String msg, int slot) {
        if (!bossPhaseActive || lightBall == null) return;
        if (slot < 0 || slot >= 2) return;
        if (!"APPRENTICE".equals(roleForSlot(slot))) return;
        String[] parts = msg.split("\\|");
        if (parts.length < 3) return;
        try {
            float tx = Float.parseFloat(parts[1]);
            float ty = Float.parseFloat(parts[2]);
            lightBall.setTarget(tx, ty);
        } catch (NumberFormatException ignored) {
            // Malformed LT message — silently drop.
        }
    }

    // =========================================================================
    // Pre-boss altar handler (P8.6)
    // =========================================================================

    /**
     * Handles an {@link Protocol#ALTAR_CHOICE} message from the Wanderer client.
     * Validates that the sender is the Wanderer and that the {@code choice} is
     * one of the two known values, then broadcasts an authoritative
     * {@link Protocol#ALTAR_RESULT} string packet to both clients so each side
     * can apply the mechanical effect and dismiss the overlay.
     *
     * @param msg  raw pipe-delimited message: {@code ALTAR_CHOICE|<altarId>|<choice>}
     * @param slot the slot index (0 or 1) that sent it
     */
    private synchronized void handleAltarChoice(String msg, int slot) {
        if (!"WANDERER".equals(roleForSlot(slot))) return;
        String[] parts = msg.split("\\|");
        if (parts.length < 3) return;
        int altarId;
        try {
            altarId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return;
        }
        String choice = parts[2];
        if (!"POWER_SURGE".equals(choice) && !"SIGHT_RESTRICTION".equals(choice)) return;
        String result = Protocol.ALTAR_RESULT + "|" + altarId + "|" + choice;
        broadcast(result);
        System.out.println("[Server] Altar " + altarId + " resolved: " + choice);
    }

    // =========================================================================
    // Boss attack dispatcher (P8.5)
    // =========================================================================

    /**
     * Handles an {@link Protocol#ATTACK} request message from the Apprentice
     * client. The message format is {@code ATTACK|<type>|<x>|<y>} where the
     * type must match one of {@link #ATTACK_TYPES}. Dispatch is gated on:
     * <ol>
     *   <li>sender slot role = {@code "APPRENTICE"} (the Wanderer cannot fire
     *       boss attacks; doing so would let the victim conjure their own
     *       hazards and desync damage accounting);</li>
     *   <li>{@link #bossPhaseActive} — attacks are a BOSS-only feature;</li>
     *   <li>{@link #architectOverride} — the first Core destruction flips this
     *       flag, which is the design-level unlock for the full boss kit;</li>
     *   <li>{@code currentMs >= attackCooldownUntilMs[type]} — the per-type
     *       spawn cooldown.</li>
     * </ol>
     * On acceptance the cooldown is stamped forward by {@link #ATTACK_COOLDOWN_MS}
     * and a {@link Protocol#BOSS_ATK} message is broadcast to both clients so
     * each side locally instantiates the matching {@link BossAttack} subclass
     * and renders / applies damage from the same authoritative spawn event.
     *
     * <p>Intercepted and handled on the {@link ClientHandler} thread (mirroring
     * the {@code BOSS_ENTER}/{@code LIGHT_TARGET}/{@code CUT_TRIGGER} pattern)
     * so the dispatch broadcast fires immediately rather than at the next
     * game-loop tick, and so the original Apprentice-side {@code ATTACK}
     * string is never relayed verbatim to both clients.
     *
     * @param msg  the raw pipe-delimited message, e.g. {@code "ATTACK|SEARING_BEAM|1536|1152"}
     * @param slot the slot index (0 or 1) that sent it
     */
    private synchronized void handleAttackMessage(String msg, int slot) {
        if (!bossPhaseActive) return;
        if (!architectOverride) return;
        if (slot < 0 || slot >= 2) return;
        if (!"APPRENTICE".equals(roleForSlot(slot))) {
            // Silently drop — only the Apprentice may spawn boss attacks.
            return;
        }
        String[] parts = msg.split("\\|");
        if (parts.length < 4) return;
        String type = parts[1];
        int typeIdx = -1;
        for (int i = 0; i < ATTACK_TYPES.length; i++) {
            if (ATTACK_TYPES[i].equals(type)) { typeIdx = i; break; }
        }
        if (typeIdx < 0) {
            // Unknown attack type — drop without echoing to clients.
            return;
        }
        long now = System.currentTimeMillis();
        // P8.8 — the Architect is stunned while `now < architectStunnedUntilMs`.
        // All dispatches during the window are silently dropped; the per-type
        // cooldown is intentionally NOT stamped so the Apprentice's usual
        // rhythm resumes cleanly once the stun expires.
        if (now < architectStunnedUntilMs) {
            return;
        }
        if (now < attackCooldownUntilMs[typeIdx]) {
            // Per-type cooldown still active — drop without echoing.
            return;
        }

        int x, y;
        try {
            x = Integer.parseInt(parts[2]);
            y = Integer.parseInt(parts[3]);
        } catch (NumberFormatException ignored) {
            return;
        }

        // Stamp the cooldown forward and broadcast the authoritative dispatch.
        attackCooldownUntilMs[typeIdx] = now + ATTACK_COOLDOWN_MS[typeIdx];
        broadcast(Protocol.BOSS_ATK + "|" + type + "|" + x + "|" + y + "|" + now);
        System.out.println("[Server] Boss attack dispatched: " + type
                + " at (" + x + ", " + y + ")");

        // P8.8 — per attack interval, roll a stun opportunity weighted by the
        // Wanderer's faithful meter: P = 0.1 + 0.08 * faithful. The roll is
        // gated on STUN_OPPORTUNITY_COOLDOWN_MS so two attacks fired within
        // the same cooldown window cannot spam back-to-back opportunities.
        maybeRollStunOpportunity(now);
    }

    /**
     * Rolls a stun-opportunity chance tied to the most recent Wanderer
     * {@link NetworkProtocol.PlayerStatePacket#faithful} value. On success,
     * broadcasts {@code STUN_OPP|<durationMs>|<faithful>} so the Wanderer
     * client opens the {@link StunMinigame} overlay; the Apprentice ignores
     * the payload. A global cooldown ({@link #STUN_OPPORTUNITY_COOLDOWN_MS})
     * prevents rapid attacks from chaining windows.
     *
     * @param now wall-clock ms at the time of the roll
     */
    private void maybeRollStunOpportunity(long now) {
        if (now < stunOpportunityCooldownUntilMs) return;
        // Opportunities are pointless while the Architect is already stunned.
        if (now < architectStunnedUntilMs) return;
        int faithful = (latestPlayerState != null) ? latestPlayerState.faithful : 0;
        faithful = Math.max(0, Math.min(5, faithful));
        double p = 0.10 + 0.08 * faithful; // faithful=0 → 10%, faithful=5 → 50%
        if (stunRng.nextDouble() >= p) return;
        stunOpportunityCooldownUntilMs = now + STUN_OPPORTUNITY_COOLDOWN_MS;
        broadcast(Protocol.STUN_OPPORTUNITY + "|"
                + StunMinigame.OPPORTUNITY_DURATION_MS + "|" + faithful);
        System.out.println("[Server] Stun opportunity broadcast"
                + " (faithful=" + faithful
                + ", P=" + String.format("%.2f", p) + ")");
    }

    /**
     * Handles a {@link Protocol#STUN_RESULT} message reported by the Wanderer
     * client. On {@code success=1} the Architect is stunned for
     * {@link StunMinigame#STUN_DURATION_MS} ms — {@link #handleAttackMessage(String, int)}
     * silently drops BOSS_ATK dispatches for that window. Both outcomes are
     * echoed back to clients so the Apprentice HUD can flash a banner. The
     * Apprentice cannot originate a STUN_RES (defence against a desync-griefing
     * client); the check is enforced by slot role.
     *
     * @param msg  raw pipe-delimited message: {@code STUN_RES|<success>}
     * @param slot slot index (0 or 1) that sent it
     */
    private synchronized void handleStunResultMessage(String msg, int slot) {
        if (!bossPhaseActive) return;
        if (slot < 0 || slot >= 2) return;
        if (!"WANDERER".equals(roleForSlot(slot))) return; // only the Wanderer may report
        String[] parts = msg.split("\\|");
        if (parts.length < 2) return;
        int success;
        try {
            success = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return;
        }
        long now = System.currentTimeMillis();
        if (success == 1) {
            architectStunnedUntilMs = now + StunMinigame.STUN_DURATION_MS;
            System.out.println("[Server] Architect STUNNED for "
                    + StunMinigame.STUN_DURATION_MS + " ms");
        } else {
            System.out.println("[Server] Stun minigame failed");
        }
        // Echo the authoritative outcome back to both clients.
        broadcast(Protocol.STUN_RESULT + "|" + success);
    }

    // =========================================================================
    // Core destruction logic
    // =========================================================================

    /**
     * Applies one point of damage to the Core at {@code coreIndex}. If the Core's
     * health reaches zero it is marked destroyed: the first destruction activates
     * architect-override (boss fight begins) and all four destructions trigger a
     * Wanderer victory.
     *
     * @param coreIndex the zero-based index of the Core that was hit; out-of-range or
     *                  already-destroyed Cores are silently ignored
     */
    private void handleCoreHit(int coreIndex) {
        if (coreIndex < 0 || coreIndex >= CORE_COUNT) return;
        if (coreHealth[coreIndex] <= 0) return; // already destroyed

        coreHealth[coreIndex]--;

        // P8.5 — broadcast every hit (not just destructions) so the client HUD
        // can flash / play a hit-confirm SFX alongside the authoritative
        // CoreStatePacket that carries the new health values.
        broadcast(Protocol.CORE_DAMAGED + "|" + coreIndex);

        if (coreHealth[coreIndex] <= 0) {
            destroyedCoreCount++;

            // First Core destroyed — boss fight phase begins
            if (destroyedCoreCount == 1) {
                architectOverride = true;
            }

            // Broadcast updated Core health immediately
            broadcastToAll(new NetworkProtocol.CoreStatePacket(coreHealth.clone()));

            // P8.5 — fire the paired CORE_n_DESTROYED cutscene via the P8.0
            // lock-step path. coreIndex is 0-based on the wire but the cutscene
            // names are 1-based (CORE_1_DESTROYED … CORE_4_DESTROYED), so we
            // add one when resolving the enum. The all-four-destroyed case
            // still broadcasts VictoryPacket below; the WANDERER_VICTORY →
            // HOME chain itself belongs to P8.10.
            String cutsceneName = "CORE_" + (coreIndex + 1) + "_DESTROYED";
            try {
                CutsceneID cid = CutsceneID.valueOf(cutsceneName);
                startCutscene(cid.name());
            } catch (IllegalArgumentException ex) {
                System.out.println("[Server] No cutscene mapping for " + cutsceneName
                        + " — skipping trigger.");
            }

            // All four Cores destroyed — P8.10: set pending state; the
            // WANDERER_VICTORY → HOME chain starts in endCutscene after the
            // CORE_4_DESTROYED cutscene (started above) acks on both clients.
            if (destroyedCoreCount >= CORE_COUNT) {
                architectOverride = false;
                pendingVictoryState = VictoryState.WANDERER_WIN;
            }
        }
    }

    // =========================================================================
    // Broadcasting
    // =========================================================================

    /**
     * Serialises the current server state into a {@link NetworkProtocol.ServerStatePacket}
     * and writes it to every connected client's output stream. Called once per game loop
     * tick after all queued packets have been processed.
     */
    public synchronized void broadcastState() {
        // P8.3 — expose the server-authoritative LightBall position so both
        // clients centre the boss-phase light mask on the same world-space
        // point. Outside BOSS (lightBall == null) we carry 0/0; the client
        // explicitly ignores these fields in non-BOSS rendering paths.
        float lx = (lightBall != null) ? lightBall.getX() : 0f;
        float ly = (lightBall != null) ? lightBall.getY() : 0f;
        NetworkProtocol.ServerStatePacket state = new NetworkProtocol.ServerStatePacket(
                latestPlayerState,
                new NetworkProtocol.CoreStatePacket(coreHealth.clone()),
                victoryState,
                architectOverride,
                apprenticeLevelReady(),
                bothConnected,
                lx,
                ly
        );
        broadcastToAll(state);
    }

    /**
     * Writes {@code obj} to each connected client's {@link ObjectOutputStream}. If a
     * write fails for a particular client, that client is marked disconnected. The method
     * is {@code synchronized} so that the game loop thread and the {@link ClientHandler}
     * disconnect-notification path cannot interleave writes on the same stream.
     *
     * @param obj the serializable packet to send; must not be {@code null}
     */
    private synchronized void broadcastToAll(Object obj) {
        for (int i = 0; i < 2; i++) {
            if (clientConnected[i] && outs[i] != null) {
                try {
                    outs[i].writeObject(obj);
                    outs[i].flush();
                    // Reset the stream to prevent ObjectOutputStream from caching
                    // object references, which would stop updated fields from being sent.
                    outs[i].reset();
                } catch (IOException e) {
                    System.out.println("Write failed for client slot " + i
                            + " (" + roleForSlot(i) + "): " + e.getMessage());
                    clientConnected[i] = false;
                }
            }
        }
    }

    // =========================================================================
    // Slot ↔ role resolution helpers
    // =========================================================================

    /**
     * Returns the role string currently bound to the given slot, or {@code null}
     * if the lobby has not yet assigned a role to that slot.
     */
    private String roleForSlot(int slot) {
        if (slot < 0 || slot >= lobbySelectedRole.length) return null;
        return lobbySelectedRole[slot];
    }

    /**
     * Returns the slot index that has committed to the given role, or {@code -1}
     * if no slot currently holds that role.
     */
    private int slotForRole(String role) {
        if (role == null) return -1;
        for (int i = 0; i < lobbySelectedRole.length; i++) {
            if (role.equals(lobbySelectedRole[i])) return i;
        }
        return -1;
    }

    /** Convenience: whether the slot playing the Apprentice has signalled level ready. */
    private boolean apprenticeLevelReady() {
        int slot = slotForRole("APPRENTICE");
        return slot >= 0 && levelReady[slot];
    }

    // =========================================================================
    // Lobby logic (called from ClientHandler threads — all methods synchronized)
    // =========================================================================

    /**
     * Handles a single LOBBY_HOVER, LOBBY_SELECT, or LOBBY_CANCEL message from a client.
     * Updates the per-slot lobby state and broadcasts the new {@link Protocol#LOBBY_STATE}
     * to both clients. When both slots have selected different roles, also calls
     * {@link #checkLobbyComplete()}.
     *
     * @param msg        the raw pipe-delimited message string
     * @param senderSlot the slot index (0 or 1) of the client that sent the message
     */
    private synchronized void handleLobbyMessage(String msg, int senderSlot) {
        String[] parts = msg.split("\\|");
        if (parts.length < 2) return;
        String rolePart = parts[1].toUpperCase();

        switch (parts[0]) {
            case Protocol.LOBBY_HOVER:
                lobbyHoverState[senderSlot] = rolePart;
                broadcastLobbyState();
                break;

            case Protocol.LOBBY_SELECT:
                if (!rolePart.equals("WANDERER") && !rolePart.equals("APPRENTICE")) break;
                // Deny if the other slot already holds this role
                int other = 1 - senderSlot;
                if (rolePart.equals(lobbySelectedRole[other])) {
                    broadcastLobbyState(); // send update so UI reflects blocked state
                    break;
                }
                lobbySelectedRole[senderSlot] = rolePart;
                System.out.println("[Lobby] Slot " + senderSlot + " selected " + rolePart);
                broadcastLobbyState();
                checkLobbyComplete();
                break;

            case Protocol.LOBBY_CANCEL:
                if (rolePart.equals(lobbySelectedRole[senderSlot])) {
                    lobbySelectedRole[senderSlot] = null;
                    System.out.println("[Lobby] Slot " + senderSlot + " cancelled " + rolePart);
                    broadcastLobbyState();
                }
                break;

            default:
                break;
        }
    }

    /**
     * Builds and broadcasts a {@link Protocol#LOBBY_STATE} message encoding the current
     * taken/hovered state of both role cards. Called after every lobby state mutation.
     */
    private synchronized void broadcastLobbyState() {
        boolean wTaken = "WANDERER".equals(lobbySelectedRole[0])
                      || "WANDERER".equals(lobbySelectedRole[1]);
        boolean aTaken = "APPRENTICE".equals(lobbySelectedRole[0])
                      || "APPRENTICE".equals(lobbySelectedRole[1]);
        boolean wHover = "WANDERER".equals(lobbyHoverState[0])
                      || "WANDERER".equals(lobbyHoverState[1]);
        boolean aHover = "APPRENTICE".equals(lobbyHoverState[0])
                      || "APPRENTICE".equals(lobbyHoverState[1]);
        String state = Protocol.LOBBY_STATE + "|"
                + (wTaken ? "1" : "0") + "|"
                + (aTaken ? "1" : "0") + "|"
                + (wHover ? "1" : "0") + "|"
                + (aHover ? "1" : "0");
        broadcastToAll(new NetworkProtocol.StringPacket(state));
    }

    /**
     * Checks whether both lobby slots have selected a role. If so, writes a
     * {@link Protocol#LOBBY_START} message directly to each slot's output stream so each
     * client learns its own assigned role and can begin loading level 1.
     */
    private synchronized void checkLobbyComplete() {
        if (lobbySelectedRole[0] == null || lobbySelectedRole[1] == null) return;
        System.out.println("[Lobby] Both roles selected — sending LOBBY_START to each slot.");
        for (int i = 0; i < 2; i++) {
            if (clientConnected[i] && outs[i] != null) {
                try {
                    String startMsg = Protocol.LOBBY_START + "|" + lobbySelectedRole[i];
                    outs[i].writeObject(new NetworkProtocol.StringPacket(startMsg));
                    outs[i].flush();
                    outs[i].reset();
                } catch (IOException e) {
                    System.out.println("[Lobby] Failed to send LOBBY_START to slot "
                            + i + ": " + e.getMessage());
                    clientConnected[i] = false;
                }
            }
        }
    }

    // =========================================================================
    // Session persistence / reconnect logic
    // =========================================================================

    /**
     * Captures a point-in-time snapshot of all server-authoritative game state. The
     * returned {@link SessionSnapshot} is used by {@link #sendSnapshot} to rebuild the
     * game world on a reconnecting client. All {@code lastKnown*} fields are maintained
     * by {@link #updateLastKnownFromMessage} and {@link #processPacket} so the snapshot
     * reflects the true most-recent state at the moment of capture.
     *
     * @return a fresh {@code SessionSnapshot} populated with the current game state
     */
    private synchronized SessionSnapshot captureSnapshot() {
        SessionSnapshot snap = new SessionSnapshot();
        snap.currentLevel     = lastKnownLevel;
        snap.currentAct       = lastKnownAct;
        snap.wandererX        = lastKnownWandererX;
        snap.wandererY        = lastKnownWandererY;
        snap.wandererHealth   = lastKnownWandererHealth;
        snap.wandererLives    = lastKnownWandererLives;
        snap.lightBattery     = lastKnownLightBattery;
        snap.lightX           = lastKnownLightX;
        snap.lightY           = lastKnownLightY;
        snap.lightRadius      = lastKnownLightRadius;
        snap.lightActive      = lastKnownLightActive;
        snap.blockBudget      = lastKnownBlockBudget;
        snap.currentBlockType = lastKnownBlockType;
        synchronized (serverPlacedBlocks) {
            snap.placedBlocks = new java.util.ArrayList<>(serverPlacedBlocks);
        }
        snap.coreHealth  = coreHealth.clone();
        snap.capturedAt  = System.currentTimeMillis();
        return snap;
    }

    /**
     * Transitions the session from active play into the paused-waiting state. Called by
     * {@link ClientHandler#handleClientDisconnect} when exactly one of the two clients
     * drops and the other is still alive. Captures a snapshot, marks the session
     * paused, closes the disconnected client's socket, notifies the surviving client
     * via {@link Protocol#PARTNER_DISCONNECTED} and {@link Protocol#GAME_PAUSE}, and
     * spawns the reconnect acceptor thread.
     *
     * @param disconnectedSlot slot index of the client that just left (0 = Wanderer,
     *                         1 = Apprentice)
     */
    private synchronized void handlePartialDisconnect(int disconnectedSlot) {
        if (vacantRole != null) return;   // already in partial-disconnect state
        if (!sessionActive)     return;   // session is ending anyway

        // Lobby must have completed before we can meaningfully hold a session
        // for a single-client reconnect — we need a committed role-to-slot
        // mapping. If a client drops during the lobby phase, just terminate so
        // main() spawns a fresh server for the next pair.
        String disconnectedRole = lobbySelectedRole[disconnectedSlot];
        if (disconnectedRole == null) {
            System.out.println("[Server] Client dropped before lobby completed — "
                    + "ending session.");
            sessionActive = false;
            return;
        }
        int survivingSlot = 1 - disconnectedSlot;

        System.out.println("[Server] Partial disconnect: " + disconnectedRole
                + " left. Holding session for reconnection (up to "
                + (RECONNECT_TIMEOUT_MS / 1000) + "s).");

        vacantRole            = disconnectedRole;
        partialDisconnectTime = System.currentTimeMillis();
        snapshot              = captureSnapshot();
        gamePaused            = true;

        // Close the disconnected client's socket cleanly; leave outs[]/sockets[] null
        // so that broadcastToAll skips the empty slot.
        closeQuietly(sockets[disconnectedSlot]);
        sockets[disconnectedSlot] = null;
        outs[disconnectedSlot]    = null;

        // Notify the surviving client so it can raise the PAUSED_WAITING overlay.
        if (clientConnected[survivingSlot] && outs[survivingSlot] != null) {
            try {
                outs[survivingSlot].writeObject(new NetworkProtocol.StringPacket(
                        Protocol.PARTNER_DISCONNECTED + "|" + disconnectedRole));
                outs[survivingSlot].flush();
                outs[survivingSlot].writeObject(new NetworkProtocol.StringPacket(
                        Protocol.GAME_PAUSE));
                outs[survivingSlot].flush();
                outs[survivingSlot].reset();
            } catch (IOException e) {
                System.out.println("[Server] Failed to notify surviving client: "
                        + e.getMessage());
                clientConnected[survivingSlot] = false;
                sessionActive = false;   // nobody left — bail out
                return;
            }
        }

        startReconnectAcceptor();
    }

    /**
     * Spawns a daemon thread that waits on the shared {@link ServerSocket} for a
     * reconnecting client. Uses a short accept timeout so it can periodically
     * broadcast the remaining reconnect window via {@link Protocol#RECONNECT_TIMER}
     * and abort if the five-minute cap expires.
     */
    private void startReconnectAcceptor() {
        Thread t = new Thread(() -> {
            long lastTimerBroadcast = 0;
            try {
                serverSocket.setSoTimeout(2000); // 2s poll interval
                while (gamePaused && sessionActive && vacantRole != null) {
                    try {
                        Socket newSocket = serverSocket.accept();
                        // Validate RC_HELLO BEFORE committing the socket to
                        // the session. This is the critical guard against
                        // ghost reconnects — a stray TCP connection (e.g. a
                        // dying client's auto-reconnect attempt, a port scan,
                        // a stale background process) that cannot produce a
                        // valid RC_HELLO within 3 seconds is closed and the
                        // acceptor keeps waiting for the real returning
                        // player.
                        ObjectInputStream newIn = readHello(newSocket);
                        if (newIn == null) {
                            System.out.println("[Server] Reconnect acceptor "
                                    + "rejected invalid hello from "
                                    + newSocket.getInetAddress()
                                    + " — continuing to wait.");
                            closeQuietly(newSocket);
                            continue; // keep accepting
                        }
                        System.out.println("[Server] Reconnecting client accepted from "
                                + newSocket.getInetAddress());
                        handleReconnectingClient(newSocket, newIn);
                        return; // reconnect complete — stop accepting
                    } catch (SocketTimeoutException ignored) {
                        long elapsed = System.currentTimeMillis() - partialDisconnectTime;
                        long remaining = RECONNECT_TIMEOUT_MS - elapsed;
                        if (remaining <= 0) {
                            System.out.println("[Server] Reconnect window expired — "
                                    + "ending session.");
                            broadcast(Protocol.SESSION_EXPIRED);
                            sessionActive = false;
                            return;
                        }
                        // Broadcast RC_TIMER once per second (approximately)
                        if (System.currentTimeMillis() - lastTimerBroadcast >= 1000) {
                            broadcast(Protocol.RECONNECT_TIMER + "|" + (remaining / 1000));
                            lastTimerBroadcast = System.currentTimeMillis();
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("[Server] Reconnect acceptor I/O error: "
                        + e.getMessage());
                sessionActive = false;
            } finally {
                // CRITICAL: always reset SO_TIMEOUT so the next call to
                // serverSocket.accept() (in startSession) blocks normally. A
                // leaked 2000ms timeout here produces the "Session I/O error:
                // Accept timed out" symptom on the next session.
                try {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        serverSocket.setSoTimeout(0);
                    }
                } catch (IOException e) {
                    System.out.println("[Server] WARNING: failed to reset "
                            + "serverSocket SO_TIMEOUT after reconnect acceptor: "
                            + e.getMessage());
                }
            }
        }, "ReconnectAcceptor");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Wires a newly accepted reconnecting socket into the vacant role slot. Opens
     * the new {@link ObjectOutputStream}, sends the {@link NetworkProtocol.RoleAssignmentPacket}
     * with the vacant role, streams the full snapshot, starts a fresh
     * {@link ClientHandler} reader thread, and finally notifies the surviving client
     * via {@link Protocol#PARTNER_RECONNECTED} and {@link Protocol#GAME_RESUME}. On
     * any failure the new socket is closed and the session terminates.
     *
     * @param newSocket the freshly accepted client socket from the reconnect listener
     */
    private synchronized void handleReconnectingClient(Socket newSocket,
                                                       ObjectInputStream newIn) {
        if (vacantRole == null || snapshot == null) {
            closeQuietly(newSocket);
            return;
        }
        // Snapshot the vacant role and payload into locals BEFORE the long I/O
        // section, then clear the shared state immediately. This prevents the
        // reconnect-acceptor thread from firing a stale RC_TIMER broadcast
        // during the (potentially lengthy) sendSnapshot call — the acceptor's
        // while-loop condition (vacantRole != null) now goes false right away.
        String            reconnectingRole = vacantRole;
        SessionSnapshot   snap             = snapshot;
        int               slot             = slotForRole(reconnectingRole);
        if (slot < 0) {
            // No committed slot for this role — impossible in a normal paused
            // session, but fail safely rather than corrupting the mapping.
            System.out.println("[Server] No slot matches reconnecting role '"
                    + reconnectingRole + "' — closing reconnect socket.");
            closeQuietly(newSocket);
            return;
        }
        int               other            = 1 - slot;

        // Clear reconnect-tracking state up front so the acceptor thread sees
        // a clean slate and exits its poll loop before we start the snapshot
        // transfer (Debug Task 3 Lead 1).
        vacantRole            = null;
        partialDisconnectTime = -1;
        snapshot              = null;

        try {
            sockets[slot] = newSocket;
            outs[slot] = new ObjectOutputStream(newSocket.getOutputStream());
            outs[slot].flush(); // write OOS header before client reads it

            // 1) Send the role assignment so the client skips the lobby.
            outs[slot].writeObject(new NetworkProtocol.RoleAssignmentPacket(reconnectingRole));
            outs[slot].flush();

            // 2) Stream the snapshot.
            sendSnapshot(outs[slot], snap);

            clientConnected[slot] = true;

            // 3) Start a new reader thread for this client, reusing the OIS
            //    already built by readHello() during the validation step.
            Thread handler = new Thread(
                    new ClientHandler(newSocket, newIn, slot),
                    "ClientHandler-" + reconnectingRole + "-RC");
            handler.setDaemon(true);
            handler.start();

            // 4) Notify the surviving client.
            if (clientConnected[other] && outs[other] != null) {
                try {
                    outs[other].writeObject(new NetworkProtocol.StringPacket(
                            Protocol.PARTNER_RECONNECTED + "|" + reconnectingRole));
                    outs[other].flush();
                    outs[other].writeObject(new NetworkProtocol.StringPacket(
                            Protocol.GAME_RESUME));
                    outs[other].flush();
                    outs[other].reset();
                } catch (IOException e) {
                    System.out.println("[Server] Failed to notify surviving client of "
                            + "reconnect: " + e.getMessage());
                    clientConnected[other] = false;
                }
            }

            // 5) Unpause the game loop. The tracking fields (vacantRole,
            // partialDisconnectTime, snapshot) were already cleared at the top
            // of this method; this just flips the pause flag for the game loop.
            gamePaused = false;

            System.out.println("[Server] Reconnect complete: " + reconnectingRole
                    + " resumed. Session unpaused.");

        } catch (IOException e) {
            System.out.println("[Server] Failed to wire reconnecting client: "
                    + e.getMessage());
            closeQuietly(newSocket);
            clientConnected[slot] = false;
            // The reconnect failed mid-handshake — restore the tracking fields
            // so the acceptor thread (if still alive) knows a reconnect is
            // still pending, OR the session cleanly ends if it's already gone.
            sessionActive = false;
        }
    }

    /**
     * Streams a full snapshot to the reconnecting client's output stream as a
     * sequence of {@link NetworkProtocol.StringPacket}s: one {@link Protocol#SNAPSHOT_BEGIN}
     * header containing all scalar state, one {@link Protocol#SNAPSHOT_BLOCK} per
     * placed block, and one terminating {@link Protocol#SNAPSHOT_END}.
     *
     * @param out  the reconnecting client's output stream
     * @param snap the snapshot to serialise
     * @throws IOException on any write or flush failure
     */
    private void sendSnapshot(ObjectOutputStream out, SessionSnapshot snap)
            throws IOException {
        // SNAP_BEGIN|level|act|wandX|wandY|wandHealth|wandLives|lightBattery|
        //           lightX|lightY|lightRadius|lightActive|blockBudget|blockType
        String header = Protocol.SNAPSHOT_BEGIN + "|"
                + snap.currentLevel     + "|"
                + snap.currentAct       + "|"
                + snap.wandererX        + "|"
                + snap.wandererY        + "|"
                + snap.wandererHealth   + "|"
                + snap.wandererLives    + "|"
                + snap.lightBattery     + "|"
                + snap.lightX           + "|"
                + snap.lightY           + "|"
                + snap.lightRadius      + "|"
                + (snap.lightActive ? "1" : "0") + "|"
                + snap.blockBudget      + "|"
                + snap.currentBlockType;
        out.writeObject(new NetworkProtocol.StringPacket(header));
        out.flush();

        if (snap.placedBlocks != null) {
            for (String block : snap.placedBlocks) {
                out.writeObject(new NetworkProtocol.StringPacket(
                        Protocol.SNAPSHOT_BLOCK + "|" + block));
                out.flush();
            }
        }

        out.writeObject(new NetworkProtocol.StringPacket(Protocol.SNAPSHOT_END));
        out.flush();
        out.reset();
    }

    // =========================================================================
    // ClientHandler inner class
    // =========================================================================

    /**
     * Reads packets from one connected client's {@link ObjectInputStream} in a loop and
     * places each packet into the server's {@link GameServer#sharedQueue} for processing
     * on the game loop thread. On any {@link IOException} (including a clean disconnect),
     * the handler marks the client as disconnected and broadcasts a notification to the
     * remaining client via a {@link NetworkProtocol.CutscenePacket}.
     */
    private class ClientHandler implements Runnable {

        /** The socket belonging to the client this handler serves. */
        @SuppressWarnings("unused") private final Socket socket;

        /** Slot index this client occupies: {@link GameServer#SLOT_0} or {@link GameServer#SLOT_1}. */
        private final int slot;

        /** Input stream used to deserialise packets from this client; opened at the start of {@link #run()}. */
        private ObjectInputStream in;

        /**
         * Constructs a {@code ClientHandler} for the given client socket using
         * an already-built {@link ObjectInputStream}. The OIS is created in the
         * {@link GameServer#readHello(Socket)} validation path so the RC_HELLO
         * packet can be consumed before any game traffic; we cannot re-create it
         * here because ObjectInputStream headers may only be read once per
         * underlying stream.
         *
         * @param socket the connected client socket; must not be {@code null}
         * @param in     the already-built OIS whose header and first (hello)
         *               packet have already been consumed; must not be {@code null}
         * @param slot   {@link GameServer#SLOT_0} (0) or {@link GameServer#SLOT_1} (1)
         */
        ClientHandler(Socket socket, ObjectInputStream in, int slot) {
            this.socket = socket;
            this.in = in;
            this.slot = slot;
        }

        /** Returns the current role label assigned to this slot, or "SLOT_<i>" if unassigned. */
        private String currentRole() {
            String r = lobbySelectedRole[slot];
            return r != null ? r : ("SLOT_" + slot);
        }

        /**
         * Reads deserialized objects from the client input stream continuously until the
         * connection closes or an I/O error occurs. Each object is placed in
         * {@link GameServer#sharedQueue} for the game loop thread to consume.
         */
        @Override
        public void run() {
            try {
                // OIS was built by readHello() during the handshake — don't
                // re-create it here, doing so would re-read the stream header
                // on a stream that has already advanced past it.
                while (clientConnected[slot] && sessionActive) {
                    Object packet = in.readObject();

                    // Intercept LOBBY_* text messages before they reach the game-loop queue.
                    // These are handled synchronously here in the ClientHandler thread.
                    if (packet instanceof NetworkProtocol.StringPacket) {
                        String msg = ((NetworkProtocol.StringPacket) packet).message;
                        if (msg != null
                                && (msg.startsWith(Protocol.LOBBY_HOVER + "|")
                                 || msg.startsWith(Protocol.LOBBY_SELECT + "|")
                                 || msg.startsWith(Protocol.LOBBY_CANCEL + "|"))) {
                            handleLobbyMessage(msg, slot);
                            continue; // do not queue
                        }
                        if (msg != null
                                && (msg.startsWith("CUT_TRIGGER|")
                                 || msg.startsWith(Protocol.CUTSCENE_ACK + "|"))) {
                            handleCutsceneMessage(msg, slot);
                            continue; // do not queue
                        }
                        if (msg != null && Protocol.BOSS_ENTER.equals(msg)) {
                            // Wanderer → server boss-arena trigger (P8.2).
                            // Handled here rather than queued so the broadcast
                            // of BOSS_ARENA + the ARCHITECT_SPEAKS cutscene
                            // fires without being subject to the game-loop
                            // packet-processing cadence.
                            handleBossEnter(slot);
                            continue; // do not queue
                        }
                        if (msg != null && msg.startsWith(Protocol.ATTACK + "|")) {
                            // P8.5 — Apprentice → server boss-attack request.
                            // Intercepted here (rather than queued + relayed
                            // verbatim) so the dispatcher owns the
                            // architectOverride + cooldown gates and only
                            // the server-authored BOSS_ATK broadcast reaches
                            // clients. Passing through the game-loop queue
                            // would still fire but the raw ATTACK string
                            // would also be relayed to the Wanderer,
                            // causing a double-spawn.
                            handleAttackMessage(msg, slot);
                            continue; // do not queue, do not relay
                        }
                        if (msg != null && msg.startsWith(Protocol.LIGHT_TARGET + "|")) {
                            // P8.3 — Apprentice → server light-ball chase
                            // target. Intercepted here (rather than queued +
                            // relayed) because the server is the authority
                            // for the BOSS-phase light: clients read the
                            // authoritative position from ServerStatePacket,
                            // not from a relayed LT message. Skipping the
                            // queue also avoids broadcasting 20 Hz of LT
                            // strings back to clients that would just throw
                            // them away.
                            handleLightTargetMessage(msg, slot);
                            continue; // do not queue, do not relay
                        }
                        if (msg != null && msg.startsWith(Protocol.ALTAR_CHOICE + "|")) {
                            // P8.6 — Wanderer → server altar selection.
                            // Intercepted here so the server can validate the
                            // choice (only Wanderer may choose, and only known
                            // choices are accepted) and broadcast the authoritative
                            // ALTAR_RESULT to both clients.
                            handleAltarChoice(msg, slot);
                            continue; // do not queue, do not relay raw request
                        }
                        if (msg != null && msg.startsWith(Protocol.STUN_RESULT + "|")) {
                            // P8.8 — Wanderer → server stun minigame outcome.
                            // Intercepted here so the server owns the
                            // architectStunnedUntilMs authority and broadcasts
                            // the validated outcome back to both clients.
                            handleStunResultMessage(msg, slot);
                            continue; // do not queue, do not relay raw request
                        }
                    }

                    sharedQueue.put(packet);
                }
            } catch (IOException e) {
                System.out.println("[Server] Client disconnected: " + currentRole()
                        + " (" + e.getMessage() + ")");
                clientConnected[slot] = false;
                handleClientDisconnect();
            } catch (ClassNotFoundException e) {
                System.err.println("[Server] Unknown packet class from " + currentRole()
                        + ": " + e.getMessage());
                clientConnected[slot] = false;
                handleClientDisconnect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * Decides whether this disconnect is a partial or full drop. If the partner is
         * still connected and the session is not already in a paused-waiting state,
         * escalates to {@link #handlePartialDisconnect(int)}. Otherwise (partner gone
         * or the vacant slot just left mid-pause), ends the session so main() can
         * spawn a fresh {@code GameServer} for the next pair of players.
         */
        private void handleClientDisconnect() {
            int other = 1 - slot;
            // If partner is still alive and we are not already in a paused state,
            // hold the session and wait for a reconnecting client.
            if (clientConnected[other] && vacantRole == null && sessionActive) {
                handlePartialDisconnect(slot);
            } else {
                // Either both clients are gone, or the surviving client just dropped
                // during the paused-waiting window. Terminate the session.
                sessionActive = false;
            }
        }
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    /**
     * Returns the current health array for all four Cores.
     *
     * @return a four-element integer array indexed by Core index [0–3]
     */
    public int[] getCoreHealth() {
        return coreHealth;
    }

    /**
     * Returns whether architect-override mode is currently active.
     *
     * @return {@code true} if the Apprentice has activated override; {@code false}
     *         otherwise
     */
    public boolean isArchitectOverride() {
        return architectOverride;
    }

    /**
     * Sets the architect-override flag, enabling or disabling the Apprentice's
     * elevated intervention privileges.
     *
     * @param architectOverride {@code true} to enable override; {@code false} to
     *                          disable it
     */
    public void setArchitectOverride(boolean architectOverride) {
        this.architectOverride = architectOverride;
    }

    /**
     * Returns the current victory state of the match.
     *
     * @return the active {@link VictoryState}
     */
    public VictoryState getVictoryState() {
        return victoryState;
    }

    // =========================================================================
    // Private socket array (used internally alongside outs[])
    // =========================================================================

    /**
     * Raw sockets for each role (index 0 = Wanderer, index 1 = Apprentice). Stored so
     * they can be passed to {@link ClientHandler} after the output streams are opened in
     * {@link #startServer()}.
     */
    private final Socket[] sockets = new Socket[2];
}
