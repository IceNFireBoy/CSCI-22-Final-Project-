/**
 Client-side entry point for a multiplayer Lumen Architect session. Establishes network
 connection to GameServer, receives role assignment (Wanderer or Apprentice), and launches
 the game loop thread that drives rendering, input, and physics. Separates networking and
 threading concerns from the UI layer (GameFrame) and game simulation.

 Three threads coordinate at 60 fps: GameLoop handles input/physics/rendering; NetworkIO
 exclusively owns the socket for sending state and receiving updates every 16 ms with a
 short socket timeout; EDT (Swing) paints when GameLoop calls canvas.repaint().
 */

// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 4c "Networking in Java" - client-side Socket creation, paired
//                                  ObjectInputStream / ObjectOutputStream
//                                  for typed object send/receive, and a
//                                  background NetworkIO thread that owns
//                                  the socket exclusively while the
//                                  GameLoop thread handles input/physics
//                                  /rendering. Direct application of the
//                                  client-side pattern from 4c.
// Module 4a "Threads"            - three named threads (GameLoop,
//                                  NetworkIO, ConnectThread), volatile
//                                  flags for cross-thread coordination
//                                  (running, paused, connectionLost,
//                                  victoryHandled, gameLoopStarted), and
//                                  Thread.sleep / nanoTime scheduling
//                                  inside the game loop. The threading
//                                  module's start() vs run() distinction
//                                  is honoured throughout.
// Module 4b "Key Bindings"       - delegates input handling to
//                                  KeyBindings#registerBindings(canvas)
//                                  and routes the resulting
//                                  PlayerInputState through InputRouter,
//                                  decoupling Swing's input system from
//                                  the game-loop tick (4b's intent).
// Module 1a "Modifiers"          - static final TICK_MS, TICK_NS, and
//                                  reconnect-tuning constants; volatile
//                                  for cross-thread visibility; private
//                                  fields with public getters for
//                                  controlled exposure.
// (The fixed-timestep nanoTime loop and exponential-backoff reconnect go
//  beyond module 4a's coverage and are flagged for external citation in
//  the Cowork research prompt.)
// =========================================================================

import java.io.File;
import javax.swing.SwingUtilities;
import java.awt.Point;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameStarter {

    // =========================================================================
    // Constants
    // =========================================================================

    private static final String LOOP_THREAD_NAME = "GameLoop"; // Game loop thread name for debugger visibility
    private static final String NET_THREAD_NAME = "NetworkIO"; // Network I/O thread name
    private static final long TICK_MS = 16L; // Fixed physics/render tick duration in milliseconds (60 fps)
    private static final long TICK_NS = TICK_MS * 1_000_000L; // Fixed tick duration in nanoseconds for precision scheduling
    private static final long NET_SEND_INTERVAL_MS = 16L; // Target interval between outbound network state packets

    /**
     * Socket read timeout in milliseconds used by the network thread. Short enough to
     * keep the send loop on schedule; long enough that a single small packet arriving in
     * one OS read() will not trigger a spurious timeout.
     */
    private static final int NET_RECV_TIMEOUT_MS = 15;

    /** Initial milliseconds the network thread waits before the first reconnect attempt. */
    private static final long RECONNECT_DELAY_MS = 3000L;

    /**
     * P9.5 — exponential-backoff reconnect tuning. After each failed attempt
     * the delay doubles, up to {@link #RECONNECT_MAX_DELAY_MS}, before the
     * next try. {@link #RECONNECT_MAX_ATTEMPTS} bounds the total retry budget
     * so a permanently-down server eventually surfaces a "give up" overlay
     * instead of looping forever.
     */
    private static final int  RECONNECT_MAX_ATTEMPTS  = 5;
    private static final long RECONNECT_MAX_DELAY_MS  = 30_000L; // Cap doubling at 30 s

    /** Horizontal spawn position of the Wanderer in world space. */
    private static final int PLAYER_SPAWN_X = 100;

    /** Vertical spawn position of the Wanderer in world space. */
    private static final int PLAYER_SPAWN_Y = 400;

    /** Delay in milliseconds before the UI is restored after a game-over sequence. */
    private static final long MENU_RETURN_DELAY_MS = 2000L; // 2 s: gives the game-over notification time to read before the connect UI reappears

    // =========================================================================
    // Network fields
    // =========================================================================

    // =========================================================================
    // Singleton
    // =========================================================================

    /** The single running instance; set in the constructor. */
    private static GameStarter instance;

    /**
     * Returns the singleton {@code GameStarter} created by {@link #main(String[])}.
     *
     * @return the running instance; {@code null} before {@code main} has executed
     */
    public static GameStarter getInstance() {
        return instance;
    }

    // =========================================================================
    // Network fields
    // =========================================================================

    /**
     * The TCP socket connecting this client to the {@link server.GameServer}. May be
     * replaced by the reconnect path if the original connection drops.
     */
    private Socket socket;

    /**
     * The role assigned to this client by the server at connection time. Expected
     * values are {@code "WANDERER"} and {@code "APPRENTICE"}.
     */
    private String role;

    /**
     * Serialised object output stream wrapping the socket's output channel. All sends
     * are synchronised on this object to allow both the NetworkIO thread and the game
     * loop thread (fragment collection) to write safely.
     */
    private ObjectOutputStream networkOut;

    /**
     * Serialised object input stream wrapping the socket's input channel. Read
     * exclusively by the NetworkIO thread.
     */
    private ObjectInputStream networkIn;

    /**
     * Hostname or IP address of the server, retained for use by the single reconnect
     * attempt if the initial connection drops.
     */
    private String lastHost;

    /**
     * Port number of the server, retained for the reconnect attempt.
     */
    private int lastPort;

    /**
     * Set to {@code true} by the NetworkIO thread the moment a connection loss is
     * detected. Cleared if the reconnect succeeds; stays {@code true} until the user
     * restarts if the reconnect fails. {@code volatile} so the EDT observes it
     * immediately during the next repaint.
     */
    private volatile boolean connectionLost;

    /**
     * Set to {@code true} if the single reconnect attempt after a connection loss also
     * fails. When {@code true} the game loop is stopped and a permanent error overlay
     * is shown. {@code volatile} for cross-thread visibility.
     */
    private volatile boolean connectionFailed;

    /**
     * Set to {@code true} the first time a {@link NetworkProtocol.VictoryPacket}
     * (or a terminal {@link GameServer.VictoryState} inside a
     * {@link NetworkProtocol.ServerStatePacket}) is processed so that the victory
     * handler fires exactly once even if duplicate packets arrive.
     */
    private volatile boolean victoryHandled;

    /**
     * Set to {@code true} by the NetworkIO thread once a
     * {@link server.NetworkProtocol.ServerStatePacket} with {@code bothConnected == true}
     * is received. Prevents the game loop from starting before the server confirms both
     * clients are connected.
     */
    private volatile boolean gameLoopStarted = false;

    /**
     * The level number of the last LEVEL_CHANGE message processed by
     * {@link #handleMessage(String)}. Guards against the feedback loop where the
     * Wanderer sends LEVEL_CHANGE, the server echoes it back, and the Wanderer
     * calls loadLevel() a second time, which sends another LEVEL_CHANGE, and so on.
     */
    private volatile int lastHandledLevel = -1;

    /**
     * Set to {@code false} immediately after {@link #loadLevel(int)} completes. Set to
     * {@code true} after {@link #LEVEL_READY_FRAMES} ticks have elapsed. While
     * {@code false}, {@link #checkPortalCollision()} is skipped so a level cannot
     * immediately re-trigger a transition on the very first frame it is loaded.
     */
    private boolean levelReady = false;

    /** Tick counter used to delay setting {@link #levelReady} to {@code true}. */
    private int levelReadyDelay = 0;

    /** Number of ticks to wait after a level load before portal/win checks resume. */
    private static final int LEVEL_READY_FRAMES = 10;

    /**
     * Per-tick counter used to throttle the P8.3 {@link Protocol#LIGHT_TARGET}
     * send to ~20 Hz during the BOSS phase. The game loop runs at 60 fps, so a
     * modulo check against {@link #LIGHT_TARGET_TICK_INTERVAL} == 3 yields one
     * outgoing {@code LT|x|y} message every third tick.
     */
    private int lightTargetTickCounter = 0;

    /**
     * Modulo interval for the P8.3 {@link Protocol#LIGHT_TARGET} throttle. Must
     * be {@code > 0}; a value of {@code 3} at a 60 fps client loop produces a
     * ~20 Hz send rate which pairs well with the server's per-tick
     * {@link LightBall#step()} integration.
     */
    private static final int LIGHT_TARGET_TICK_INTERVAL = 3;

    // =========================================================================
    // Session persistence / reconnect fields
    // =========================================================================

    /**
     * Set to {@code true} in {@link #connectToServer(String, int)} when the server's
     * initial {@link NetworkProtocol.RoleAssignmentPacket} carries a real role string
     * ({@code "WANDERER"} or {@code "APPRENTICE"}) rather than {@code "LOBBY"}. When
     * {@code true} the game loop skips the lobby phase entirely and applies the
     * buffered snapshot during Phase C-pre.
     */
    private volatile boolean reconnecting = false;

    /**
     * Snapshot messages ({@link Protocol#SNAPSHOT_BEGIN},
     * {@link Protocol#SNAPSHOT_BLOCK}, {@link Protocol#SNAPSHOT_END}) that arrived on
     * the NetworkIO thread before the game loop had finished initialising its
     * entities. Drained on the GameLoop thread in Phase C-pre once {@code canvas}
     * and {@code player} are ready.
     */
    private final List<String> pendingSnapshotMessages =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    // =========================================================================
    // UI fields
    // =========================================================================

    /**
     * The top-level window that hosts the game canvas and UI overlays. Declared
     * {@code volatile} so the game loop thread observes the write made by the Swing
     * EDT inside {@link #main(String[])}'s {@code invokeLater} callback.
     */
    private volatile GameFrame gameFrame;

    /**
     * The rendering surface, stored as a field so the NetworkIO thread can push state
     * updates (light position, connection overlay, remote player) to it without going
     * through the game loop thread.
     */
    private volatile GameCanvas canvas;

    // =========================================================================
    // Game loop control
    // =========================================================================

    /**
     * Stop flag read by the game loop on every tick. Setting this to {@code false}
     * from any thread causes the loop to exit cleanly after the current tick
     * completes. {@code volatile} ensures immediate cross-thread visibility without
     * the overhead of locking.
     */
    private volatile boolean running;

    /** Whether the game is currently paused. */
    private volatile boolean paused;

    /** The dedicated game loop thread. Retained so {@link #stop()} can interrupt it. */
    private Thread gameLoopThread;

    // =========================================================================
    // Simulation objects
    // =========================================================================

    /**
     * The Wanderer player instance. Holds all physics state (velocity, grounded flag,
     * etc.) and ability unlock flags.
     */
    private final Player player;

    /** The physics engine that integrates velocity, applies gravity, and resolves collisions. */
    private final PhysicsEngine physicsEngine;

    /** Translates the per-tick {@link KeyBindings.PlayerInputState} into engine method calls. */
    private final InputRouter inputRouter;

    /**
     * Owns the {@link KeyBindings.PlayerInputState} snapshot and registers
     * InputMap/ActionMap bindings on the game canvas.
     */
    private final KeyBindings keyBindings;

    /**
     * The live list of all active game entities (platforms, hazards, fragments, cores,
     * etc.). Uses {@link CopyOnWriteArrayList} so the network I/O thread can safely
     * add or remove elements while the game loop iterates.
     */
    private final List<GameElement> elements;

    /**
     * Tracks only the Platform objects spawned via the network protocol (PLACE_BLOCK).
     * Used by {@link #clearAllBlocks()} to remove placed blocks without touching
     * level-loaded entities. Also a CopyOnWriteArrayList for thread safety.
     */
    private final List<Platform> placedBlocks;

    /**
     * P8.5 — live list of {@link BossAttack} instances spawned locally in
     * response to server-authored {@link Protocol#BOSS_ATK} broadcasts. On
     * the Wanderer client each entry is constructed against the local
     * {@link Player} reference so its {@code update()} can apply contact
     * damage; on the Apprentice client {@code null} is passed in lieu of a
     * player so the attack renders but never damages a non-authoritative
     * local placeholder. Ticked and culled inside the main game loop; read
     * by {@link GameCanvas#renderBoss(java.awt.Graphics2D)} via
     * {@link GameCanvas#setBossAttacks(List)}. Uses
     * {@link CopyOnWriteArrayList} so the NetworkIO thread can append a new
     * entry while the game loop is iterating for update / render.
     */
    private final List<BossAttack> bossAttacks = new CopyOnWriteArrayList<>();

    // -------------------------------------------------------------------------
    // P8.6 — pre-boss altar state
    // -------------------------------------------------------------------------

    /** True while the altar overlay is open, waiting for the Wanderer's choice. */
    private boolean altarActive = false;

    /** ms between faithful-cycle awards during the boss fight (20 seconds). */
    private static final long FAITHFUL_CYCLE_INTERVAL_MS = 20_000L;

    /** Timestamp of the last faithful-cycle award, reset on boss entry. */
    private long faithfulCycleLastMs = 0L;

    // -------------------------------------------------------------------------
    // P8.8 — stun minigame state
    // -------------------------------------------------------------------------

    /**
     * Shared stun-minigame controller. Lives for the entire session but is
     * only {@link StunMinigame#open(int)}ed during BOSS when the server
     * broadcasts {@link Protocol#STUN_OPPORTUNITY}. The same instance is
     * rendered by {@link GameCanvas}, ticked from the game loop, and polled
     * for {@link StunMinigame#consumePendingResult()} on the Wanderer side
     * to emit {@link Protocol#STUN_RESULT}.
     */
    private final StunMinigame stunMinigame = new StunMinigame();

    /**
     * Wall-clock ms until which a client-side "ARCHITECT STUNNED" banner is
     * shown (on both roles) after a successful minigame. Set on receipt of
     * {@code STUN_RES|1}; passive at session start and outside BOSS.
     */
    private volatile long architectStunnedBannerUntilMs = 0L;

    /**
     * The altarId of the currently active altar trigger. Matches the
     * {@code altarId} param in the JSON and the {@code ALTAR_CHOICE} wire token.
     */
    private int altarActiveTrigger = -1;

    /**
     * The mutable runtime state of the current level: active phase, block budget,
     * countdown timer, and timer-active flag.
     */
    private final LevelState levelState;

    /**
     * OpenCV webcam pipeline, non-{@code null} only on the Apprentice client. Started
     * in {@link #connectToServer(String, int)} after the role is confirmed, and used by
     * the NetworkIO thread's send loop to read the latest gesture and centroid.
     */
    // gestureInput removed — gesture system replaced by MouseApprentice

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Constructs a {@code GameStarter}, instantiating all simulation objects in their
     * initial state. The game loop is not started here — call {@link #startGameLoop()}
     * (or let {@link #main(String[])} do so) after the network connection is
     * established and the UI is queued for creation.
     */
    public GameStarter() {
        instance           = this;
        this.player        = new Player(PLAYER_SPAWN_X, PLAYER_SPAWN_Y);
        this.physicsEngine = new PhysicsEngine();
        this.inputRouter   = new InputRouter();
        this.keyBindings   = new KeyBindings();
        this.elements      = new CopyOnWriteArrayList<>();
        this.placedBlocks  = new CopyOnWriteArrayList<>();
        GameSession.getInstance().setPlacedBlocks(this.placedBlocks);
        this.levelState    = new LevelState();
        this.running       = false;
    }

    // =========================================================================
    // Entry point
    // =========================================================================

    /**
     * Application entry point for the Lumen Architect client. Creates a
     * {@code GameStarter}, connects to the server on port {@value server.GameServer#PORT}
     * (or the port provided in {@code args[1]}), opens the game window on the Swing
     * Event Dispatch Thread, starts the NetworkIO thread, and starts the game loop
     * thread.
     *
     * @param args command-line arguments; {@code args[0]} may optionally specify the
     *             server hostname; {@code args[1]} the port number
     */
    public static void main(String[] args) {
        GameStarter starter = new GameStarter();

        // Open the game window on the EDT; the Connect button handles the server
        // handshake from here. No offline fallback — a live server is always required.
        SwingUtilities.invokeLater(() -> {
            starter.gameFrame = new GameFrame();
            starter.gameFrame.setVisible(true);
        });

    }

    // =========================================================================
    // Network — connection
    // =========================================================================

    /**
     * Opens a TCP connection to the server at the specified host and port, wraps it in
     * {@link ObjectOutputStream} / {@link ObjectInputStream} pairs (output flushed first
     * to prevent the deadlock that arises when both sides construct their input streams
     * before either flushes the output-stream header), and reads the
     * {@link NetworkProtocol.RoleAssignmentPacket} the server sends immediately after
     * accepting the connection. The assigned role is stored in {@link GameSession} and
     * as a local field. If this client is assigned the Apprentice role, the OpenCV
     * camera capture pipeline is also started here.
     *
     * @param host the hostname or IP address of the game server; must not be
     *             {@code null}
     * @param port the TCP port the server is listening on
     */
    public boolean connectToServer(String host, int port) {
        this.lastHost = host;
        this.lastPort = port;

        try {
            socket = new Socket(host, port);
            // DO NOT set a short SO_TIMEOUT here. ObjectInputStream's constructor
            // performs a BLOCKING read of the 4-byte stream header, and if a
            // timeout fires mid-header the stream is unrecoverable. On the
            // reconnect path the server's handleReconnectingClient is
            // synchronized with the 60 Hz broadcastToAll monitor, so the flush
            // of the OOS header may be delayed past any short timeout. We use a
            // blocking handshake here and set a non-blocking timeout only for
            // the runtime message loop in runNetworkIO().
            socket.setSoTimeout(0);

            networkOut = new ObjectOutputStream(socket.getOutputStream());
            networkOut.flush(); // flush header before constructing input stream — prevents deadlock

            // Announce ourselves to the server BEFORE reading anything so the
            // server's readHello() validation can identify us as a legitimate
            // new client. A socket that fails to send a valid RC_HELLO within
            // 3 seconds is rejected by the server — this prevents stray sockets
            // (e.g. a previously-disconnected JVM's auto-reconnect) from being
            // misclassified as the reconnecting player.
            networkOut.writeObject(new NetworkProtocol.StringPacket(
                    Protocol.RECONNECT_HELLO + "|FRESH"));
            networkOut.flush();
            networkOut.reset();

            networkIn = new ObjectInputStream(socket.getInputStream());

            Object firstPacket = networkIn.readObject();

            NetworkProtocol.RoleAssignmentPacket rap =
                    (NetworkProtocol.RoleAssignmentPacket) firstPacket;

            role = rap.role;
            GameSession.getInstance().setRole(role);
            // If the server assigned a real role immediately (not "LOBBY") then
            // this is a reconnecting client — the server will stream the snapshot
            // before the first ServerStatePacket, and we must skip the lobby phase.
            reconnecting = role != null
                    && !"LOBBY".equals(role)
                    && ("WANDERER".equals(role) || "APPRENTICE".equals(role));
            if (reconnecting) {
                System.out.println("[connectToServer] Reconnecting client — role=" + role);
            }

            // Wire GameSession.sendToServer() to this client's network output stream.
            GameSession.getInstance().setSendCallback(
                msg -> sendPacket(new NetworkProtocol.StringPacket(msg)));

            return true; // handshake complete, role assigned

        } catch (Exception e) {
            e.printStackTrace();
            return false; // any error → caller shows failure message
        }
    }

    // =========================================================================
    // Network — Thread 3 (NetworkIO)
    // =========================================================================

    /**
     * Spawns the dedicated network I/O thread named {@value #NET_THREAD_NAME} and
     * starts it. The thread runs {@link #runNetworkIO()} which drives both the outbound
     * state send loop and the inbound packet receive loop until {@link #running} becomes
     * {@code false} or the connection is lost and the single reconnect attempt fails.
     */
    public void startNetworkThread() {
        Thread netThread = new Thread(this::runNetworkIO, NET_THREAD_NAME);
        netThread.setDaemon(true);
        netThread.start();
    }

    /**
     * The body of the NetworkIO thread. On each iteration it:
     * <ol>
     *   <li>Sends a state snapshot if at least {@value #NET_SEND_INTERVAL_MS} ms have
     *       elapsed since the last send.</li>
     *   <li>Attempts a non-blocking receive with a {@value #NET_RECV_TIMEOUT_MS} ms
     *       socket timeout; processes the packet if one arrived.</li>
     * </ol>
     *
     * <p>On any {@link IOException} that is not a mere {@link SocketTimeoutException},
     * the method delegates to {@link #handleDisconnect()} which waits
     * {@value #RECONNECT_DELAY_MS} ms then tries once to re-establish the connection.
     * If the reconnect fails, the game loop is stopped.
     */
    private void runNetworkIO() {
        if (networkOut == null || networkIn == null) return;

        // ---- Phase 0: wait until server confirms both players connected ----
        // Do NOT set a short socket timeout before this loop. A SocketTimeoutException
        // that fires mid-deserialization corrupts the ObjectInputStream — the partial
        // object bytes are consumed but never returned, so the next readObject() sees
        // garbage and throws a non-timeout IOException, which we then misread as a
        // dropped connection. Use blocking reads here; the server sends bothConnected=true
        // immediately after the second client joins, so the wait is at most a few ms.
        try {
            socket.setSoTimeout(0); // blocking reads during handshake
        } catch (IOException ignored) {}

        spinWaitForBothConnected:
        while (true) {
            try {
                Object pkt = networkIn.readObject();
                if (pkt instanceof NetworkProtocol.StringPacket) {
                    // Buffer any snapshot / control messages that arrive before the
                    // game loop starts. Normal first-time clients will not receive
                    // StringPackets here (the server only sends LOBBY_STATE after a
                    // HOVER/SELECT, which cannot happen before startGameLoop). A
                    // reconnecting client WILL receive the full snapshot sequence
                    // (SNAP_BEGIN/SNAP_BLOCK*/SNAP_END) here before the first
                    // ServerStatePacket — buffer them so Phase C-pre can drain and
                    // apply them on the GameLoop thread once entities are ready.
                    String msg = ((NetworkProtocol.StringPacket) pkt).message;
                    if (msg != null) pendingSnapshotMessages.add(msg);
                    continue;
                }
                if (pkt instanceof NetworkProtocol.ServerStatePacket) {
                    NetworkProtocol.ServerStatePacket ssp =
                            (NetworkProtocol.ServerStatePacket) pkt;
                    if (ssp.bothConnected) {
                        System.out.println("Both players confirmed, starting game loop");
                        startGameLoop();
                        break spinWaitForBothConnected;
                    }
                }
            } catch (SocketTimeoutException e) {
                // No data yet — keep polling (should not occur with timeout=0)
            } catch (ClassNotFoundException e) {
                System.err.println("[NetworkIO] Unknown packet class during wait: " + e.getMessage());
            } catch (IOException e) {
                System.err.println("[NetworkIO] Connection lost while waiting for both players.");
                return;
            }
        }

        // Handshake complete — switch to short timeout so the main loop
        // never blocks longer than one tick waiting for inbound packets.
        try {
            socket.setSoTimeout(NET_RECV_TIMEOUT_MS);
        } catch (IOException ignored) {}

        long lastSendTime = 0L;

        while (running || !victoryHandled) {
            long now = System.currentTimeMillis();

            // ---- Send phase ----
            if (now - lastSendTime >= NET_SEND_INTERVAL_MS) {
                lastSendTime = now;
                sendOutboundState();
            }

            // ---- Receive phase (non-blocking with socket timeout) ----
            try {
                Object pkt = networkIn.readObject();
                handleInboundPacket(pkt);
            } catch (SocketTimeoutException e) {
                // No data ready within the timeout — normal, continue to next tick
            } catch (ClassNotFoundException e) {
                System.err.println("[NetworkIO] Unknown packet class: " + e.getMessage());
            } catch (IOException e) {
                // Real connection error — attempt one reconnect
                if (!handleDisconnect()) {
                    return; // reconnect failed; loop exits
                }
                // Reconnect succeeded; reset send timer and carry on
                lastSendTime = 0L;
            }

            // Brief yield to avoid busy-spinning at 100 % CPU between ticks
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // =========================================================================
    // Network — outbound
    // =========================================================================

    /**
     * Assembles and sends the role-appropriate outbound packet:
     * <ul>
     *   <li><b>Wanderer:</b> {@link NetworkProtocol.PlayerStatePacket} carrying
     *       current position, health, and animation state.</li>
     *   <li><b>Apprentice:</b> {@link NetworkProtocol.GesturePacket} carrying the
     *       latest recognised gesture ID and centroid coordinates.</li>
     * </ul>
     *
     * <p>Both paths delegate to {@link #sendPacket(Object)} which handles
     * serialisation, flushing, and stream-reset under a lock.
     */
    private void sendOutboundState() {
        if (GameSession.getInstance().isWanderer()) {
            sendPacket(new NetworkProtocol.PlayerStatePacket(
                    player.getX(),
                    player.getY(),
                    player.getHealth(),
                    player.getAnimState(),
                    player.getFaithful()
            ));
            // If the Wanderer struck a Core this tick, forward it to the server.
            // pendingCoreHitIndex is set in Player.executeMelee() on the game-loop
            // thread; consumePendingCoreHit() reads and clears it here on the
            // NetworkIO thread (volatile guarantees visibility).
            int coreHit = player.consumePendingCoreHit();
            if (coreHit >= 0) {
                sendPacket(new NetworkProtocol.CoreHitPacket(coreHit));
            }
        }
    }

    /**
     * Writes {@code pkt} to the server via the shared {@link ObjectOutputStream}.
     * Synchronises on {@link #networkOut} so both the NetworkIO thread (periodic state)
     * and the game loop thread ({@link NetworkProtocol.FragmentCollectedPacket}) can
     * call this method safely without interleaving bytes on the stream.
     *
     * @param pkt the serializable packet to send; must not be {@code null}
     */
    private void sendPacket(Object pkt) {
        if (networkOut == null) return;
        synchronized (networkOut) {
            try {
                networkOut.writeObject(pkt);
                networkOut.flush();
                // Reset prevents ObjectOutputStream from caching references, which
                // would cause subsequent writes of the same object to send a back-
                // reference instead of the updated field values.
                networkOut.reset();
            } catch (IOException e) {
                // Swallow here; the receive loop will detect the broken connection
                // on the next read attempt and call handleDisconnect().
            }
        }
    }

    // =========================================================================
    // Network — inbound
    // =========================================================================

    /**
     * Dispatches a single inbound packet received from the server. Called exclusively
     * on the NetworkIO thread.
     *
     * @param pkt the deserialized packet; must not be {@code null}
     */
    private void handleInboundPacket(Object pkt) {
        if (pkt instanceof NetworkProtocol.ServerStatePacket) {
            applyServerState((NetworkProtocol.ServerStatePacket) pkt);

        } else if (pkt instanceof NetworkProtocol.VictoryPacket) {
            handleVictory(((NetworkProtocol.VictoryPacket) pkt).result);

        } else if (pkt instanceof NetworkProtocol.FragmentCollectedPacket) {
            // Server re-broadcasts fragment collection to both clients for dedup.
            // On the Wanderer this is a no-op (already collected locally).
            // On the Apprentice this marks the entity so the UI shows it collected.
            NetworkProtocol.FragmentCollectedPacket f =
                    (NetworkProtocol.FragmentCollectedPacket) pkt;
            for (GameElement el : elements) {
                if (el instanceof LoreFragment) {
                    LoreFragment frag = (LoreFragment) el;
                    if (f.fragmentID.equals(frag.getFragmentID()) && !frag.isCollected()) {
                        frag.collect();
                        System.out.println("[NetworkIO] Fragment relayed by server: " + f.fragmentID);
                        break;
                    }
                }
            }

        } else if (pkt instanceof NetworkProtocol.CutscenePacket) {
            NetworkProtocol.CutscenePacket cp = (NetworkProtocol.CutscenePacket) pkt;
            CutsceneID id;
            try { id = CutsceneID.valueOf(cp.cutsceneId); }
            catch (IllegalArgumentException ex) { id = null; }
            if (cp.start && id != null) {
                CutsceneRenderer.get().play(id);
            } else {
                CutsceneRenderer.get().stop();
            }

        } else if (pkt instanceof NetworkProtocol.StringPacket) {
            handleMessage(((NetworkProtocol.StringPacket) pkt).message);
        }
    }

    /**
     * Applies a {@link NetworkProtocol.ServerStatePacket} to local game state.
     * Both roles update core health, architect-override, and check for victory;
     * the Apprentice additionally syncs the remote Wanderer health. Light-source
     * position is delivered via the text-protocol {@code LIGHT} broadcast, not
     * through this packet.
     *
     * @param s the server state snapshot; must not be {@code null}
     */
    private void applyServerState(NetworkProtocol.ServerStatePacket s) {
        GameSession session = GameSession.getInstance();

        if (session.isApprentice()) {
            // Sync Wanderer health and faithful meter from server state packet
            if (s.playerState != null) {
                levelState.setWandererHealth(s.playerState.health);
                if (player != null) {
                    player.setFaithful(s.playerState.faithful);
                }
            }
        }

        // Both roles: update the server-authoritative core health snapshot in LevelState
        if (s.coreState != null) {
            levelState.coreHealth = s.coreState.health.clone();
        }

        // Both roles: propagate server-authoritative architect-override flag
        // into the local GameSession so InputRouter's weapon checks see it.
        GameSession.getInstance().setArchitectOverride(s.architectOverride);

        // P8.3 — propagate the server-authoritative LightBall world-space
        // position into the canvas so renderBoss() can centre the light mask
        // on it. Applied on both clients regardless of current phase: the
        // canvas only reads these fields inside renderBoss(), and accepting
        // them during BOSS entry (even before the local phase flips to BOSS)
        // avoids a single-frame mask pop on transition.
        if (canvas != null) {
            canvas.setBossLightWorldPosition(s.lightX, s.lightY);
        }

        // Victory check — same logic for both roles
        if (s.victoryState != GameServer.VictoryState.IN_PROGRESS) {
            handleVictory(s.victoryState);
        }
    }

    /**
     * Triggers the role-appropriate victory cutscene and advances the level counter
     * once, even if multiple packets carry the same terminal state.
     *
     * <ul>
     *   <li><b>WANDERER_WIN, this client = Wanderer:</b>
     *       trigger {@code WANDERER_VICTORY} cutscene, advance to level 10.</li>
     *   <li><b>WANDERER_WIN, this client = Apprentice:</b>
     *       trigger {@code WANDERER_VICTORY} cutscene (shared story beat).</li>
     *   <li><b>APPRENTICE_WIN (either client):</b>
     *       trigger {@code ARCHITECT_VICTORY} cutscene.</li>
     * </ul>
     *
     * @param result the terminal match outcome; must not be {@code null}
     */
    private void handleVictory(GameServer.VictoryState result) {
        if (victoryHandled) return;
        victoryHandled = true;
        running = false;
        // P8.10 — all endgame cutscenes have already played server-side before the
        // VictoryPacket is broadcast; flip to END_SCREEN and show the final summary.
        SwingUtilities.invokeLater(() -> {
            if (canvas != null) {
                canvas.setVictoryResult(result);
            }
            levelState.currentPhase = LevelState.GamePhase.END_SCREEN;
            if (canvas != null) canvas.repaint();
            System.out.println("[GameStarter] End screen — " + result);
        });
    }

    // =========================================================================
    // Network — disconnect / reconnect
    // =========================================================================

    /**
     * Called when the NetworkIO thread catches an {@link IOException} on the receive
     * path, indicating the server connection has been lost. Displays a
     * "Connection lost" overlay, waits {@value #RECONNECT_DELAY_MS} ms, then makes
     * one attempt to re-establish the TCP connection and re-open the object streams.
     *
     * @return {@code true} if the reconnect succeeded (the caller should resume the
     *         network loop); {@code false} if it failed (the caller should exit)
     */
    private boolean handleDisconnect() {
        connectionLost = true;
        if (canvas != null) {
            canvas.setConnectionOverlay("Connection lost. Waiting...");
        }

        // P9.5 — exponential backoff. The first wait is RECONNECT_DELAY_MS (3 s);
        // each subsequent failure doubles the wait, capped at
        // RECONNECT_MAX_DELAY_MS (30 s). Total attempts capped at
        // RECONNECT_MAX_ATTEMPTS so a permanently-down server eventually gives
        // up instead of busy-looping. The loop exits early on the first
        // success and returns true to the caller.
        long delay = RECONNECT_DELAY_MS;
        for (int attempt = 1; attempt <= RECONNECT_MAX_ATTEMPTS; attempt++) {
            // Surface the current attempt + remaining-attempt count to the
            // canvas overlay so the user knows the client is still trying.
            if (canvas != null) {
                canvas.setConnectionOverlay(
                    "Connection lost. Retrying " + attempt
                    + "/" + RECONNECT_MAX_ATTEMPTS
                    + " (next in " + (delay / 1000) + "s)...");
            }

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false; // Interrupt = caller wants us to abort, not retry
            }

            // ---- Try one reconnect ----
            try {
                socket     = new Socket(lastHost, lastPort);
                networkOut = new ObjectOutputStream(socket.getOutputStream());
                networkOut.flush();

                // Announce ourselves as a RECONNECTING client with our prior role.
                // The server's reconnect acceptor validates this hello and only
                // accepts the socket if the role matches the vacantRole — this
                // prevents the accept-any-socket ghost-reconnect path.
                String priorRole = GameSession.getInstance().role;
                if (priorRole == null || "LOBBY".equals(priorRole)) {
                    priorRole = "UNKNOWN";
                }
                networkOut.writeObject(new NetworkProtocol.StringPacket(
                        Protocol.RECONNECT_HELLO + "|RECONNECT|" + priorRole));
                networkOut.flush();
                networkOut.reset();

                networkIn  = new ObjectInputStream(socket.getInputStream());
                socket.setSoTimeout(NET_RECV_TIMEOUT_MS);

                connectionLost = false;
                if (canvas != null) {
                    canvas.setConnectionOverlay(null);
                }
                System.out.println("[Reconnect] Succeeded on attempt " + attempt);
                return true;

            } catch (IOException e) {
                System.out.println("[Reconnect] Attempt " + attempt
                        + " failed: " + e.getMessage());
                // Exponentially increase the next wait, capped at the maximum.
                delay = Math.min(delay * 2, RECONNECT_MAX_DELAY_MS);
                // Loop continues — try again after the new (longer) delay.
            }
        }

        // Exhausted all attempts — surface the final failure and stop the loop.
        connectionFailed = true;
        if (canvas != null) {
            canvas.setConnectionOverlay(
                "Connection failed after " + RECONNECT_MAX_ATTEMPTS
                + " attempts. Please restart.");
        }
        running = false; // pause the game loop
        return false;
    }

    // =========================================================================
    // Game loop
    // =========================================================================

    /**
     * Spawns the dedicated game loop thread and starts it. The thread:
     * <ol>
     *   <li>Spin-waits (with 5 ms sleeps) until the EDT has finished constructing the
     *       {@link GameFrame} and its {@link GameCanvas}.</li>
     *   <li>Registers keyboard bindings on the canvas via
     *       {@link KeyBindings#registerBindings(javax.swing.JComponent)}, posted to the
     *       EDT with {@link SwingUtilities#invokeAndWait} to guarantee completion before
     *       the loop starts.</li>
     *   <li>Runs the fixed-timestep loop until {@link #running} becomes
     *       {@code false}.</li>
     * </ol>
     *
     * <p>The thread is configured as a daemon so it does not prevent JVM shutdown if
     * the Swing EDT exits.
     */
    public void startGameLoop() {
        gameLoopThread = new Thread(() -> {
            // -----------------------------------------------------------------
            // Phase A: wait for the EDT to finish creating the game frame.
            // gameFrame is volatile so this thread observes the EDT write.
            // -----------------------------------------------------------------
            while (gameFrame == null) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            // Store the canvas as a class field so the NetworkIO thread can also
            // reference it for light-position updates and overlays.
            canvas = gameFrame.getGameCanvas();

            // Push simulation state into the canvas so it renders the right scene.
            canvas.setLevelState(levelState);
            canvas.setPlayer(player);
            canvas.setElements(elements);
            // P8.5 — expose the authoritative boss-attack list so
            // GameCanvas.renderBoss can paint each active attack each frame.
            canvas.setBossAttacks(bossAttacks);
            // P8.8 — hand the shared StunMinigame to the canvas so renderBoss
            // can paint the overlay + stunned banner from a single source of truth.
            canvas.setStunMinigame(stunMinigame);

            // Gesture system removed — no gesture input to wire

            // FIX 3 — Inject canvas into InputRouter for light source updates
            inputRouter.setGameCanvas(canvas);
            // P8.8 — inject the shared StunMinigame so the SPACE binding can
            // consume hits during an opportunity window without triggering jump.
            inputRouter.setStunMinigame(stunMinigame);

            // -----------------------------------------------------------------
            // Phase B: register key bindings on the EDT, then wait for
            // completion before the loop starts so no input events are missed.
            // -----------------------------------------------------------------
            try {
                SwingUtilities.invokeAndWait(() -> {
                    keyBindings.registerBindings(canvas);

                    // Cutscene bindings (SPACE advance, F9 debug) — installed for
                    // both roles and registered AFTER KeyBindings so the SPACE
                    // override cleanly supersedes the jump binding during cutscenes.
                    inputRouter.registerCutsceneBindings(canvas);

                    // CutsceneRenderer needs the shared LevelState to flip phase.
                    CutsceneRenderer.get().setLevelState(levelState);

                    // Apprentice keys override F and add E/Q using same WHEN_IN_FOCUSED_WINDOW map
                    if (GameSession.getInstance().isApprentice()) {
                        inputRouter.registerApprenticeKeyBindings(canvas);
                    }

                    // Disable focus-traversal keys on the canvas so Swing stops treating
                    // TAB as a focus-move key and the InputMap receives it normally.
                    canvas.setFocusTraversalKeysEnabled(false);

                    canvas.setFocusable(true);
                    canvas.requestFocusInWindow();
                    canvas.addFocusListener(new java.awt.event.FocusAdapter() {
                        @Override
                        public void focusLost(java.awt.event.FocusEvent e) {
                            canvas.requestFocusInWindow();
                        }
                    });
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (java.lang.reflect.InvocationTargetException e) {
                e.printStackTrace();
                return;
            }

            // -----------------------------------------------------------------
            // Phase C-pre: enter LOBBY phase OR apply a reconnect snapshot.
            //
            // Fresh clients: the server sent role "LOBBY" — we enter the lobby
            // phase and wait for the NetworkIO thread to receive LOBBY_START.
            //
            // Reconnecting clients: the server sent our real role and streamed a
            // SessionSnapshot before the first ServerStatePacket. The NetworkIO
            // thread buffered those snapshot messages in pendingSnapshotMessages;
            // drain and apply them here (loadLevel runs inside handleMessage for
            // SNAP_BEGIN, which transitions the phase directly to ACT*/BOSS).
            // -----------------------------------------------------------------
            canvas.setLevelState(levelState);

            if (reconnecting) {
                System.out.println("[GameLoop] Reconnecting — applying "
                        + pendingSnapshotMessages.size() + " buffered snapshot messages.");
                // Apprentice needs its key bindings registered before any input.
                if (GameSession.getInstance().isApprentice()) {
                    try {
                        SwingUtilities.invokeAndWait(() ->
                                inputRouter.registerApprenticeKeyBindings(canvas));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                // Drain the buffered snapshot messages in order.
                java.util.List<String> drained;
                synchronized (pendingSnapshotMessages) {
                    drained = new java.util.ArrayList<>(pendingSnapshotMessages);
                    pendingSnapshotMessages.clear();
                }
                for (String msg : drained) {
                    handleMessage(msg);
                }
            } else {
                levelState.currentPhase = LevelState.GamePhase.LOBBY;
                System.out.println("[GameLoop] Lobby phase entered - waiting for role selection.");
            }

            // -----------------------------------------------------------------
            // Phase C: fixed-timestep game loop.
            //
            // Drift compensation strategy:
            //   nextTick tracks the nanosecond timestamp at which the NEXT tick
            //   should begin.  After each tick body, we sleep for the remaining
            //   time until nextTick.  If the body overran (sleepNs <= 0), we
            //   reset nextTick to System.nanoTime() rather than trying to catch
            //   up with a burst of rapid ticks (prevents spiral-of-death after
            //   GC pauses or temporary CPU starvation).
            // -----------------------------------------------------------------
            running = true;
            long nextTick = System.nanoTime();

            while (running) {

                // (0-pre) LOBBY / PAUSED_WAITING phase: skip all physics and
                // gameplay — just repaint and sleep. LOBBY exits when the
                // NetworkIO thread receives LOBBY_START; PAUSED_WAITING exits
                // when the server sends GAME_RESUME (partner reconnected) or
                // SESSION_EXP (reconnect window expired, game ends).
                if (levelState.currentPhase == LevelState.GamePhase.LOBBY
                        || levelState.currentPhase == LevelState.GamePhase.PAUSED_WAITING) {
                    canvas.repaint();
                    nextTick += TICK_NS;
                    long lobbySlpNs = nextTick - System.nanoTime();
                    if (lobbySlpNs > 0L) {
                        try {
                            Thread.sleep(lobbySlpNs / 1_000_000L,
                                         (int)(lobbySlpNs % 1_000_000L));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            running = false;
                        }
                    } else {
                        nextTick = System.nanoTime();
                    }
                    continue;
                }

                // (0) Check pause toggle (ESC key).
                KeyBindings.PlayerInputState input = keyBindings.getInputState();
                if (input.pausePressed) {
                    input.pausePressed = false;
                    togglePause();
                }

                // Camera overlay toggle removed (gesture system removed).

                // If paused, handle F key for fragment library, then skip simulation.
                if (paused) {
                    if (input.fragmentsPressed) {
                        input.fragmentsPressed = false;
                        if (canvas != null) {
                            canvas.setShowFragmentLibrary(!canvas.isShowingFragmentLibrary());
                        }
                    }
                    canvas.repaint();
                    nextTick += TICK_NS;
                    long sleepNs2 = nextTick - System.nanoTime();
                    if (sleepNs2 > 0L) {
                        try {
                            Thread.sleep(sleepNs2 / 1_000_000L,
                                         (int)(sleepNs2 % 1_000_000L));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            running = false;
                        }
                    } else {
                        nextTick = System.nanoTime();
                    }
                    continue;
                }

                // (1-3) Wanderer-only: physics, respawn, game-over, and portal checks.
                // The Apprentice client skips these — its local Player object is a
                // placeholder only; running physics on it would cause it to fall off
                // the world and trigger spurious game-over resets.
                if (!GameSession.getInstance().isApprentice()) {

                    // (1) Route keyboard input to physics calls.
                    inputRouter.routeKeyEvent(input, player, physicsEngine);

                    // (2) Update player respawn state (death timer).
                    player.updateRespawn(TICK_MS);

                    // Death-restart check — Acts 1-3 reload Act 1 with full health.
                    // BOSS phase deaths flow through the server (ARCHITECT_VICTORY)
                    // and are not handled here.
                    if (player != null && player.isRestartToAct1Pending()) {
                        player.clearRestartToAct1Pending();
                        if (levelState.currentPhase != LevelState.GamePhase.BOSS) {
                            player.setHealth(player.getMaxHealth());
                            player.setDead(false);
                            loadLevel(1);
                            if (canvas != null) {
                                canvas.showNotification("You fell — restarting at Act 1");
                            }
                            continue;
                        }
                    }

                    // (3) Advance physics simulation by one fixed tick.
                    if (!player.isDead()) {
                        physicsEngine.update(TICK_MS, player, elements);
                    }

                    // (3.5) Update player animation and send position to server.
                    player.update(TICK_MS);

                } // end Wanderer-only block

                // (3b.5) Apprentice: broadcast light-source state every tick.
                //
                // P8.3 — during BOSS the light is server-authoritative and
                // obeys inertia physics. Instead of the per-tick teleport
                // LIGHT_UPDATE we send a throttled LIGHT_TARGET (~20 Hz, every
                // third tick) carrying the world-space cursor position; the
                // server's LightBall lerps toward it and broadcasts the
                // resulting x/y back to both clients in ServerStatePacket.
                // Outside BOSS the existing LIGHT_UPDATE path is unchanged.
                if (GameSession.getInstance().isApprentice()) {
                    MouseApprentice ma = MouseApprentice.getInstance();
                    boolean bossPhase =
                            levelState.currentPhase == LevelState.GamePhase.BOSS;
                    if (bossPhase) {
                        lightTargetTickCounter++;
                        if (lightTargetTickCounter >= LIGHT_TARGET_TICK_INTERVAL) {
                            lightTargetTickCounter = 0;
                            // MouseApprentice.getX/Y already hold world-space
                            // coords during BOSS because InputRouter
                            // mouseMoved/mouseDragged transform through
                            // Camera.screenToWorld before storing them.
                            GameSession.getInstance().sendToServer(
                                    Protocol.LIGHT_TARGET + "|"
                                            + ma.getX() + "|" + ma.getY());
                        }
                    } else {
                        boolean lightOn = ma.isLightActive()
                                && GameSession.getInstance().getLightBattery() > 0;
                        GameSession.getInstance().sendToServer(Protocol.LIGHT_UPDATE + "|"
                                + ma.getX() + "|" + ma.getY() + "|" + ma.getLightRadius()
                                + "|" + (lightOn ? "1" : "0"));
                    }
                }

                // (3b) Update light source position from mouse — Apprentice only.
                // On the Wanderer client, light comes from the network (LIGHT_SYNC).
                if (canvas != null && GameSession.getInstance().isApprentice()) {
                    Point mp = canvas.getMousePosition2();
                    if (mp != null) {
                        canvas.setLightSourcePosition(mp);
                    }
                }

                // (3b.6) Sync Wanderer entity position from network state on the Apprentice.
                if (GameSession.getInstance().isApprentice()) {
                    if (levelState.remoteWandererX >= 0) {
                        player.setPosition(levelState.remoteWandererX, levelState.remoteWandererY);
                        player.setAnimationState(levelState.remoteWandererState);
                    }
                }

                // (3c) Update platform lit state from light source (all types).
                // P9.3' — extended to also feed PhantomBlock with a continuous
                // intensity factor (1.0 at the light centre, falling to 0.4 at
                // the edge of the lit radius), and to drive CorruptedWall's
                // trigger detection by passing the Wanderer reference.
                if (canvas != null) {
                    Point lightPos = canvas.getLightSourcePosition();
                    if (lightPos != null) {
                        int litRadius = canvas.getLightRadius();
                        if (litRadius <= 0) litRadius = 120;
                        for (GameElement el : elements) {
                            if (el instanceof Platform) {
                                Platform p2 = (Platform) el;
                                java.awt.Rectangle bounds = p2.getBounds();
                                int platCX = bounds.x + bounds.width / 2;
                                int platCY = bounds.y + bounds.height / 2;
                                double dist = Math.sqrt(
                                    Math.pow(platCX - lightPos.x, 2) +
                                    Math.pow(platCY - lightPos.y, 2));
                                boolean lit = (dist <= litRadius + 20);
                                p2.setLit(lit);
                                // PhantomBlock charge model — feed a smooth intensity factor.
                                if (p2 instanceof PhantomBlock) {
                                    float intensity = 0f;
                                    if (lit && litRadius > 0) {
                                        intensity = 1.0f - (float)(dist / (litRadius + 20));
                                        if (intensity < 0.4f) intensity = 0.4f; // Floor so the edge of the cone still charges (slowly)
                                        if (intensity > 1.0f) intensity = 1.0f;
                                    }
                                    ((PhantomBlock) p2).setLightIntensity(intensity);
                                }
                            }
                        }
                        // CorruptedWall trigger detection — pass the Wanderer to the FSM.
                        if (player != null) {
                            for (GameElement el : elements) {
                                if (el instanceof CorruptedWall) {
                                    ((CorruptedWall) el).checkTrigger(player);
                                }
                            }
                        }
                    }
                }

                // (3d) Update light battery and radius for ACT2/ACT3 (mouse-driven)
                if (canvas != null
                        && (levelState.currentPhase == LevelState.GamePhase.ACT2
                            || levelState.currentPhase == LevelState.GamePhase.ACT3)) {
                    if (GameSession.getInstance().isApprentice()) {
                        MouseApprentice ma = MouseApprentice.getInstance();
                        boolean lightOn = ma.isLightActive(); // includes forcedOff check
                        int radius = ma.getLightRadius();
                        GameSession.getInstance().updateBattery(lightOn, radius);
                        // Always pass the real radius — darkness handled by renderer
                        canvas.setLightRadius(radius);
                    }
                }

                // (4) Update all active game elements (animations, timers, AI stubs).
                for (GameElement el : elements) {
                    if (el.isActive()
                            || (el instanceof Platform && !((Platform) el).fullyGone)) {
                        el.update(TICK_MS);
                    }
                }

                // (4b) P8.5 — tick every active BossAttack, then cull any
                // that have self-expired (duration exceeded or deactivated).
                // Both roles run this: the Wanderer applies contact damage
                // via each subclass's own update() loop, while the Apprentice
                // runs it too so render state (e.g. SpikeArray rise/retract
                // frames, CrusherBlock lerp position) stays in sync with
                // elapsed time on both screens. CopyOnWriteArrayList makes
                // the iterate-then-remove pattern safe even if a new attack
                // arrives on the NetworkIO thread mid-loop.
                if (!bossAttacks.isEmpty()) {
                    java.util.List<BossAttack> expired = new java.util.ArrayList<>();
                    for (BossAttack ba : bossAttacks) {
                        ba.update(TICK_MS);
                        if (ba.isExpired() || !ba.isActive()) expired.add(ba);
                    }
                    if (!expired.isEmpty()) bossAttacks.removeAll(expired);
                }

                // Remove fully-crumbled placed blocks and notify the server (Wanderer only).
                // CopyOnWriteArrayList does not support iterator.remove(); collect first,
                // then remove in a separate pass to avoid UnsupportedOperationException.
                if (GameSession.getInstance().isWanderer()) {
                    java.util.List<Platform> goneBlocks = new java.util.ArrayList<>();
                    for (Platform pb : placedBlocks) {
                        if (pb.fullyGone) goneBlocks.add(pb);
                    }
                    for (Platform pb : goneBlocks) {
                        elements.remove(pb);
                        placedBlocks.remove(pb);
                        GameSession.getInstance().sendToServer(
                            Protocol.REMOVE_BLOCK + "|"
                                + pb.getBounds().x + "|" + pb.getBounds().y);
                    }
                }

                // (5-7) Wanderer-only: these checks all act on the local Player's
                // position, which is only meaningful on the Wanderer client.
                if (!GameSession.getInstance().isApprentice()) {
                    checkFragmentCollection();
                    checkPortalCollision();
                    checkWinLoss();
                    checkAltarTrigger(); // P8.6
                    checkFaithfulCycle(); // P8.7
                    checkHazardContact(); // P9.3' — CorruptedSpike + falling CorruptedWall damage
                    // Send pending altar choice selected via canvas 1/2 keys
                    if (altarActive && canvas != null) {
                        String choice = canvas.getAndClearPendingAltarChoice();
                        if (choice != null) {
                            GameSession.getInstance().sendToServer(
                                Protocol.ALTAR_CHOICE + "|" + altarActiveTrigger + "|" + choice);
                        }
                    }
                    // P8.8 — tick the stun minigame and forward any latched result.
                    // Only the Wanderer drives the window because only the Wanderer
                    // plays it; the Apprentice reacts to the echoed STUN_RES.
                    long nowMsStun = System.currentTimeMillis();
                    stunMinigame.tick(nowMsStun);
                    int stunResult = stunMinigame.consumePendingResult();
                    if (stunResult == 0 || stunResult == 1) {
                        GameSession.getInstance().sendToServer(
                                Protocol.STUN_RESULT + "|" + stunResult);
                    }
                }

                // (6) Request a repaint of the game canvas on the EDT.
                canvas.repaint();

                // ----- Drift-compensating sleep -----
                nextTick += TICK_NS;
                long sleepNs = nextTick - System.nanoTime();

                if (sleepNs > 0L) {
                    try {
                        Thread.sleep(sleepNs / 1_000_000L,
                                     (int)(sleepNs % 1_000_000L));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        running = false;
                    }
                } else {
                    // Tick body took longer than TICK_NS — reset the schedule
                    // to the current time to avoid queuing up catch-up ticks.
                    nextTick = System.nanoTime();
                }
            }

        }, LOOP_THREAD_NAME);

        gameLoopThread.setDaemon(true);
        gameLoopThread.start();
    }

    // =========================================================================
    // Text-protocol message handling (Part 5 — network state sync)
    // =========================================================================

    /**
     * Parses and applies a pipe-delimited {@link Protocol} message received from the
     * server relay. Called exclusively on the NetworkIO thread.
     *
     * @param msg the raw message string; ignored if {@code null} or empty
     */
    private void handleMessage(String msg) {
        if (msg == null || msg.isEmpty()) return;
        String[] parts = msg.split("\\|");
        switch (parts[0]) {
            case Protocol.PLAYER_POS:
                if (parts.length >= 7 && parts[1].equals("WANDERER")) {
                    float wx = Float.parseFloat(parts[2]);
                    float wy = Float.parseFloat(parts[3]);
                    levelState.setWandererPosition(wx, wy);
                    levelState.setWandererState(parts[6]);
                    if (parts.length >= 8) {
                        levelState.setWandererHealth(Integer.parseInt(parts[7]));
                    }
                }
                break;

            case Protocol.PLACE_BLOCK:
            case Protocol.BLOCK_ADDED:
                if (parts.length >= 4) {
                    spawnBlock(parts[1],
                            Integer.parseInt(parts[2]),
                            Integer.parseInt(parts[3]));
                }
                break;

            case Protocol.REMOVE_BLOCK:
            case Protocol.BLOCK_REMOVED:
                if (parts.length >= 3) {
                    removeBlockAt(Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]));
                }
                break;

            case Protocol.LIGHT_UPDATE:
            case Protocol.LIGHT_SYNC:
                if (parts.length >= 4) {
                    int lx = Integer.parseInt(parts[1]);
                    int ly = Integer.parseInt(parts[2]);
                    int lr = Integer.parseInt(parts[3]);
                    boolean la = parts.length < 5 || "1".equals(parts[4]);
                    levelState.setLightPosition(lx, ly);
                    levelState.setLightRadius(lr);
                    levelState.setLightActive(la);
                    // Push light state into canvas on the Wanderer client
                    if (canvas != null && GameSession.getInstance().isWanderer()) {
                        int effectiveRadius = la ? lr : 0;
                        canvas.setLightSource(new java.awt.Point(lx, ly), effectiveRadius, 0f);
                    }
                }
                break;

            case Protocol.LEVEL_CHANGE:
                if (parts.length >= 2) {
                    int newLevel = Integer.parseInt(parts[1]);
                    // Dedup guard: ignore the echo if this client already loaded this level.
                    // The Wanderer pre-sets lastHandledLevel before broadcasting, so its own
                    // echo is silently dropped here. The Apprentice processes it normally.
                    if (newLevel != lastHandledLevel) {
                        lastHandledLevel = newLevel;
                        loadLevel(newLevel, false); // false = do NOT re-broadcast
                        GameSession.getInstance().resetBlockBudget();
                    }
                }
                break;

            case Protocol.CLEAR_BLOCKS:
                clearAllBlocks();
                break;

            case Protocol.BOSS_ARENA:
                // Server-authoritative boss arena trigger (P8.2): generate
                // the arena locally from the broadcast seed so both clients
                // produce identical layouts, transition phase to BOSS, and
                // snap the camera onto the Wanderer's arena spawn. The
                // ARCHITECT_SPEAKS cutscene packet follows immediately and
                // is handled by the existing P8.0 lock-step path.
                if (parts.length >= 2) {
                    try {
                        long seed = Long.parseLong(parts[1]);
                        enterBossArena(seed);
                    } catch (NumberFormatException ignored) {}
                }
                break;

            case Protocol.BOSS_ATK:
                // P8.5 — authoritative boss-attack spawn from the server.
                // Format: BATK|<type>|<x>|<y>|<spawnMs>. The spawnMs field is
                // stamped by the server at dispatch time and currently only
                // used for telemetry, but kept on the wire so future client-
                // side interpolation can line-sync with the server.
                if (parts.length >= 4) {
                    try {
                        String atkType = parts[1];
                        int    ax      = Integer.parseInt(parts[2]);
                        int    ay      = Integer.parseInt(parts[3]);
                        spawnBossAttack(atkType, ax, ay);
                    } catch (NumberFormatException ignored) {}
                }
                break;

            case Protocol.CORE_DAMAGED:
                // P8.5 — hit confirmation broadcast (every hit, not just
                // destructions). The authoritative CoreStatePacket follows
                // right behind and drives the HUD numbers; here we just
                // surface a transient notification so the hit is obvious on
                // both screens. A future polish pass can replace this with a
                // timed flash / SFX.
                if (parts.length >= 2 && canvas != null) {
                    try {
                        int coreIdx = Integer.parseInt(parts[1]);
                        canvas.showNotification("CORE " + (coreIdx + 1) + " HIT");
                    } catch (NumberFormatException ignored) {}
                }
                break;

            case Protocol.ALTAR_RESULT:
                // P8.6 — server-authoritative altar outcome. Both clients apply
                // the mechanical effect; the Wanderer client also dismisses the
                // overlay via applyAltarResult().
                if (parts.length >= 3) {
                    applyAltarResult(parts[2]);
                }
                break;

            case Protocol.STUN_OPPORTUNITY:
                // P8.8 — server-authoritative stun opportunity broadcast.
                // Format: STUN_OPP|<durationMs>|<faithful>. The Wanderer
                // client opens the minigame overlay; the Apprentice ignores
                // the payload but still receives it so the protocol is
                // symmetric and future Apprentice-side telemetry can hook in.
                if (parts.length >= 3 && GameSession.getInstance().isWanderer()) {
                    try {
                        int faithful = Integer.parseInt(parts[2]);
                        stunMinigame.open(faithful);
                        if (canvas != null) {
                            canvas.showNotification("STUN OPPORTUNITY — mash SPACE!");
                        }
                    } catch (NumberFormatException ignored) {}
                }
                break;

            case Protocol.STUN_RESULT:
                // P8.8 — server-authoritative stun minigame outcome. On
                // success=1 show the "ARCHITECT STUNNED" banner for
                // STUN_DURATION_MS on both clients; the Wanderer also clears
                // any residual window state via forceClose().
                if (parts.length >= 2) {
                    try {
                        int success = Integer.parseInt(parts[1]);
                        if (success == 1) {
                            architectStunnedBannerUntilMs = System.currentTimeMillis()
                                    + StunMinigame.STUN_DURATION_MS;
                            if (canvas != null) {
                                canvas.showNotification("ARCHITECT STUNNED — 5s!");
                            }
                        } else {
                            if (canvas != null) {
                                canvas.showNotification("Stun failed");
                            }
                        }
                        stunMinigame.forceClose();
                        if (canvas != null) {
                            canvas.setArchitectStunnedUntilMs(architectStunnedBannerUntilMs);
                        }
                    } catch (NumberFormatException ignored) {}
                }
                break;

            // P9.3' — Protocol.CRAWLER_UPDATE removed alongside DarkCrawler.
            // The new hazard suite is fully deterministic / locally driven and
            // needs no per-entity AI state relay over the wire.

            // P8.9 — Radiant Collapse mirror.
            //
            // The Wanderer stamps its own {@code radiantActiveUntilMs} when
            // the FSM transitions CHARGING → ACTIVE and sends this broadcast
            // so the Apprentice can mirror the same end timestamp locally.
            // We also accept the message on the Wanderer (the server echoes
            // broadcasts back to the originator) but keep the stamp
            // idempotent — re-stamping to the same value is harmless, and a
            // second Wanderer-side CHARGING → ACTIVE cannot fire while the
            // ACTIVE/COOLDOWN windows are running.
            case Protocol.RADIANT_ACTIVE:
                if (parts.length >= 2 && player != null) {
                    try {
                        long endMs = Long.parseLong(parts[1]);
                        player.setRadiantActiveUntilMs(endMs);
                    } catch (NumberFormatException ignored) {}
                }
                break;

            case Protocol.LOBBY_STATE:
                // Update the lobby card state (taken/hovered flags) for rendering.
                if (canvas != null) {
                    LobbyState ls = new LobbyState();
                    ls.applyMessage(msg);
                    canvas.setLobbyState(ls);
                    canvas.repaint();
                }
                break;

            case Protocol.LOBBY_START:
                // Server has assigned us a real role — finish lobby and start play.
                if (parts.length >= 2) {
                    String assignedRole = parts[1];
                    System.out.println("[GameStarter] LOBBY_START → role=" + assignedRole);
                    role = assignedRole;
                    GameSession.getInstance().setRole(assignedRole);

                    // Register Apprentice key bindings on the EDT now that we know the role.
                    if (GameSession.getInstance().isApprentice() && canvas != null) {
                        SwingUtilities.invokeLater(() -> {
                            inputRouter.registerApprenticeKeyBindings(canvas);
                            System.out.println("[GameStarter] Apprentice key bindings registered after lobby.");
                        });
                    }

                    // Pre-set dedup guard so any echoed LEVEL|1 from the server is ignored.
                    lastHandledLevel = 1;

                    // Load level 1 — this also sets levelState.currentPhase = ACT1,
                    // which causes the game loop to exit its LOBBY skip on the next tick.
                    loadLevel(1, false); // false = Wanderer broadcasts level changes via portal
                    GameSession.getInstance().resetBlockBudget();
                }
                break;

            // =====================================================================
            // Session persistence / reconnect messages
            // =====================================================================

            case Protocol.PARTNER_DISCONNECTED:
                // Surviving client: show the paused-waiting overlay.
                if (parts.length >= 2) {
                    String dcRole = parts[1];
                    System.out.println("[GameStarter] PARTNER_DC → " + dcRole);
                    if (canvas != null) {
                        canvas.setPartnerDCRole(dcRole);
                        // Seed the countdown to the full 90-second window so
                        // the overlay shows a reasonable value immediately. The
                        // first RC_TIMER broadcast from the server will
                        // overwrite this with the authoritative remaining time
                        // within ~1 s, so any off-by-one here is invisible.
                        canvas.setReconnectCountdown(90);
                    }
                    // Save the pre-pause phase so GAME_RESUME can restore it.
                    prePausePhase = levelState.currentPhase;
                    levelState.currentPhase = LevelState.GamePhase.PAUSED_WAITING;
                    if (canvas != null) canvas.repaint();
                }
                break;

            case Protocol.PARTNER_RECONNECTED:
                // Surviving client: partner is back — clear the overlay.
                System.out.println("[GameStarter] PARTNER_RC received — resuming.");
                if (canvas != null) {
                    canvas.setPartnerDCRole(null);
                    canvas.setReconnectCountdown(-1);
                }
                break;

            case Protocol.RECONNECT_TIMER:
                // Update the countdown shown inside the PAUSED_WAITING overlay.
                if (parts.length >= 2) {
                    try {
                        long secs = Long.parseLong(parts[1]);
                        if (canvas != null) canvas.setReconnectCountdown(secs);
                    } catch (NumberFormatException ignored) {}
                }
                break;

            case Protocol.SESSION_EXPIRED:
                // Reconnect window elapsed — the server is ending the session.
                System.out.println("[GameStarter] SESSION_EXP — reconnect window expired.");
                if (canvas != null) {
                    canvas.setPartnerDCRole(null);
                    canvas.setReconnectCountdown(-1);
                    canvas.showNotification("Session expired — partner did not return.");
                }
                running = false;
                break;

            case Protocol.GAME_PAUSE:
                // Redundant safeguard in case PARTNER_DC did not arrive first.
                if (levelState.currentPhase != LevelState.GamePhase.PAUSED_WAITING) {
                    prePausePhase = levelState.currentPhase;
                    levelState.currentPhase = LevelState.GamePhase.PAUSED_WAITING;
                }
                break;

            case Protocol.GAME_RESUME:
                // Partner has reconnected — restore the phase that was active
                // before the pause so physics and rendering resume cleanly.
                System.out.println("[GameStarter] GAME_RESUME — restoring phase "
                        + prePausePhase);
                if (prePausePhase != null
                        && prePausePhase != LevelState.GamePhase.PAUSED_WAITING) {
                    levelState.currentPhase = prePausePhase;
                }
                prePausePhase = null;
                break;

            case Protocol.LOBBY_VACANT:
                // A reconnecting-lobby client has been told which role is open.
                if (parts.length >= 2 && canvas != null) {
                    LobbyState ls = (canvas.getLobbyState() != null)
                            ? canvas.getLobbyState() : new LobbyState();
                    ls.isReconnectSession = true;
                    ls.vacantRole         = parts[1];
                    canvas.setLobbyState(ls);
                    canvas.repaint();
                }
                break;

            // =====================================================================
            // Snapshot delivery (reconnecting client only)
            // =====================================================================

            case Protocol.SNAPSHOT_BEGIN:
                applySnapshotBegin(parts);
                break;

            case Protocol.SNAPSHOT_BLOCK:
                // SNAP_BLOCK|type|x|y  — spawn on whichever level was just loaded.
                if (parts.length >= 4) {
                    try {
                        spawnBlock(parts[1],
                                Integer.parseInt(parts[2]),
                                Integer.parseInt(parts[3]));
                    } catch (NumberFormatException ignored) {}
                }
                break;

            case Protocol.SNAPSHOT_END:
                System.out.println("[GameStarter] SNAP_END — snapshot restore complete.");
                // Snapshot fully applied — session is already live on the server,
                // no further action needed.
                break;

            default:
                // Unknown or unhandled protocol token — silently ignore.
                break;
        }
    }

    /**
     * Stores the phase that was active immediately before a {@link Protocol#GAME_PAUSE}
     * or {@link Protocol#PARTNER_DISCONNECTED} put this client into
     * {@link LevelState.GamePhase#PAUSED_WAITING}, so {@link Protocol#GAME_RESUME} can
     * restore it without guessing.
     */
    private LevelState.GamePhase prePausePhase;

    /**
     * Applies a {@link Protocol#SNAPSHOT_BEGIN} header on the reconnecting client:
     * clears current blocks, loads the snapshotted level, and restores the Wanderer
     * position/health, light state, block budget, and current block type. Individual
     * placed blocks arrive as follow-up {@link Protocol#SNAPSHOT_BLOCK} messages and
     * are spawned via {@link #spawnBlock(String, int, int)}.
     *
     * @param parts the pipe-split header; expected 14 fields (see Protocol javadoc)
     */
    private void applySnapshotBegin(String[] parts) {
        if (parts.length < 14) {
            System.out.println("[GameStarter] Malformed SNAP_BEGIN — " + parts.length + " fields");
            return;
        }
        try {
            int     level       = Integer.parseInt(parts[1]);
            String  act         = parts[2];
            float   wandX       = Float.parseFloat(parts[3]);
            float   wandY       = Float.parseFloat(parts[4]);
            int     wandHealth  = Integer.parseInt(parts[5]);
            int     wandLives   = Integer.parseInt(parts[6]);
            float   lightBat    = Float.parseFloat(parts[7]);
            int     lightX      = Integer.parseInt(parts[8]);
            int     lightY      = Integer.parseInt(parts[9]);
            int     lightRadius = Integer.parseInt(parts[10]);
            boolean lightActive = "1".equals(parts[11]);
            int     blockBudget = Integer.parseInt(parts[12]);
            String  blockType   = parts[13];

            System.out.println("[GameStarter] SNAP_BEGIN → level=" + level
                    + " act=" + act + " wand=(" + wandX + "," + wandY + ")"
                    + " wandHealth=" + wandHealth + " budget=" + blockBudget);

            // Wipe the local world before reapplying snapshot entities.
            clearAllBlocks();

            // Load the snapshot's level (no broadcast — server already knows).
            lastHandledLevel = level;
            loadLevel(level, false);

            // Restore wanderer state.
            if (player != null) {
                player.setX((int) wandX);
                player.setY((int) wandY);
                player.setVelX(0);
                player.setVelY(0);
                player.setHealth(wandHealth);
                // Lives field is no longer used; the wandLives value in the
                // snapshot is read above for wire-format parity and ignored here.
            }
            levelState.setWandererPosition(wandX, wandY);
            levelState.setWandererHealth(wandHealth);

            // Restore light state (both clients track it; only Wanderer renders it).
            levelState.setLightPosition(lightX, lightY);
            levelState.setLightRadius(lightRadius);
            levelState.setLightActive(lightActive);
            if (canvas != null && GameSession.getInstance().isWanderer()) {
                int effectiveRadius = lightActive ? lightRadius : 0;
                canvas.setLightSource(new java.awt.Point(lightX, lightY),
                        effectiveRadius, 0f);
            }

            // Restore Apprentice-side state.
            GameSession.getInstance().setBlockBudget(blockBudget);
            GameSession.getInstance().setCurrentBlockType(blockType);
            GameSession.getInstance().setLightBattery(lightBat);
            GameSession.getInstance().setCurrentAct(act);

        } catch (NumberFormatException e) {
            System.out.println("[GameStarter] SNAP_BEGIN parse error: " + e.getMessage());
        }
    }

    /**
     * P8.5 — instantiates the appropriate {@link BossAttack} subclass for an
     * authoritative {@link Protocol#BOSS_ATK} broadcast and appends it to
     * {@link #bossAttacks}. Each subclass's construction signature differs, so
     * this switch adapts the flat {@code (type, x, y)} wire format to the
     * subclass's expected arguments (e.g. {@link SpikeArray} needs the ground
     * Y of the arena; {@link BlockRain} needs the list of existing platforms
     * so falling bricks can land on them; {@link Shield} only needs the
     * projectile list since its position is fixed on the architect panel).
     *
     * <p>On the Apprentice client the local {@code player} reference is
     * replaced with {@code null} before construction — the Apprentice's
     * {@link Player} object is a non-authoritative placeholder (see the
     * "Apprentice skips physics" comment inside {@link #startGameLoop()})
     * and letting a {@link BossAttack} call {@code takeDamage()} on it would
     * desync health reporting. All five existing subclasses already null-
     * check the player reference inside their {@code update()} loops, so
     * passing null is safe.
     *
     * @param type the canonical attack-type string; one of the literals used
     *             by {@code GameSession.fire*()} and echoed by the server
     *             ({@code SEARING_BEAM | BLOCK_RAIN | CRUSHER | SPIKE_ARRAY |
     *             SHIELD})
     * @param x    world-space x carried by the broadcast (the intended target
     *             / centre; interpretation varies per subclass)
     * @param y    world-space y carried by the broadcast
     */
    private void spawnBossAttack(String type, int x, int y) {
        // Null out the Player reference on the Apprentice so its placeholder
        // is never handed to a damage-dealing attack loop.
        Player authoritativePlayer = GameSession.getInstance().isApprentice()
                ? null : player;
        BossAttack attack = null;
        switch (type) {
            case "SEARING_BEAM":
                attack = new SearingBeam(new Point(x, y), authoritativePlayer);
                break;
            case "BLOCK_RAIN": {
                // Collect the level's current Platforms so falling bricks can
                // land on existing geometry rather than only the ground line.
                java.util.List<Platform> plats = new java.util.ArrayList<Platform>();
                for (GameElement el : elements) {
                    if (el instanceof Platform) plats.add((Platform) el);
                }
                int groundY = inferBossGroundY();
                attack = new BlockRain(authoritativePlayer, plats, groundY);
                break;
            }
            case "CRUSHER":
                attack = new CrusherBlock(new Point(x, y), authoritativePlayer);
                break;
            case "SPIKE_ARRAY":
                attack = new SpikeArray(x, inferBossGroundY(),
                        authoritativePlayer);
                break;
            case "SHIELD":
                // Shield intercepts player projectiles; on the Apprentice
                // client there are no locally tracked projectiles, so an
                // empty list is fine — the shield still renders.
                attack = new Shield(new java.util.ArrayList<Projectile>());
                break;
            default:
                System.out.println("[GameStarter] Ignoring unknown BOSS_ATK type: " + type);
                return;
        }
        if (attack != null) {
            bossAttacks.add(attack);
            System.out.println("[GameStarter] Spawned boss attack: " + type
                    + " at (" + x + ", " + y + ")");
        }
    }

    /**
     * Infers the "ground Y" to feed into attacks that need a reference floor
     * (e.g. {@link SpikeArray} and {@link BlockRain}). Boss-arena geometry is
     * radially symmetric around the throne at {@code (ARENA_W/2, ARENA_H/2)},
     * so the arena's lower boundary wall is a safe default when we have no
     * better signal. If the Wanderer is below mid-arena we prefer their
     * current foot line so ground spikes don't spawn above the player's head
     * when they've dropped into the lower half.
     */
    private int inferBossGroundY() {
        int defaultGround = Camera.ARENA_H - Platform.TILE_SIZE; // one tile above the arena floor
        if (player != null && player.getY() > Camera.ARENA_H / 2) {
            // Foot line estimate: player top-left Y + one tile; clamp inside arena.
            return Math.min(defaultGround, player.getY() + Platform.TILE_SIZE);
        }
        return defaultGround;
    }

    /**
     * Creates a new {@link Platform} of the given type at {@code (x, y)} and adds it
     * to the shared elements list. Called when either client receives a
     * {@link Protocol#PLACE_BLOCK} or {@link Protocol#BLOCK_ADDED} message.
     */
    private void spawnBlock(String type, int x, int y) {
        // Uppercase for case-insensitive matching — JSON may use "crumble" or "CRUMBLE".
        String t = (type != null) ? type.toUpperCase() : "BRICK";
        Platform block;
        switch (t) {
            case "SLIDE":   block = new Platform(Platform.PlatformType.SLIDE,  x, y); break;
            case "SPRING":  block = new Platform(Platform.PlatformType.SPRING, x, y); break;
            case "WALL":    block = new Platform(Platform.PlatformType.WALL,   x, y); break;
            case "CRUMBLE": block = new Platform(Platform.PlatformType.CRUMBLE, x, y); break;
            default:        block = new Platform(Platform.PlatformType.BRICK, x, y);   break;
        }
        elements.add(block);
        placedBlocks.add(block);
        System.out.println("[GameState] Spawned block type: " + t + " at " + x + "," + y);
    }

    /**
     * Removes any {@link Platform} whose origin exactly matches {@code (x, y)}.
     * Called when either client receives a {@link Protocol#REMOVE_BLOCK} or
     * {@link Protocol#BLOCK_REMOVED} message.
     */
    private void removeBlockAt(int x, int y) {
        final double SNAP_TOLERANCE = 48.0;
        Platform nearest = null;
        double nearestDist = Double.MAX_VALUE;
        int nearestIndex = -1;

        for (int i = 0; i < placedBlocks.size(); i++) {
            Platform b = placedBlocks.get(i);
            java.awt.Rectangle bounds = b.getBounds();
            double centerX = bounds.x + bounds.width / 2.0;
            double centerY = bounds.y + bounds.height / 2.0;
            double dist = Math.hypot(centerX - x, centerY - y);

            boolean closer = dist < nearestDist;
            // On exact tie, prefer most recently placed (highest index)
            boolean equalButMoreRecent = (dist == nearestDist && i > nearestIndex);

            if (closer || equalButMoreRecent) {
                nearestDist = dist;
                nearest = b;
                nearestIndex = i;
            }
        }

        if (nearest != null && nearestDist <= SNAP_TOLERANCE) {
            elements.remove(nearest);
            placedBlocks.remove(nearest);
        }
    }

    /** Removes all network-placed Platform blocks from the elements list. */
    private void clearAllBlocks() {
        elements.removeAll(placedBlocks);
        placedBlocks.clear();
        System.out.println("[GameState] Block list cleared for new level.");
    }

    // =========================================================================
    // Pause
    // =========================================================================

    /**
     * Toggles the game between paused and unpaused states. Updates both the
     * local flag and the canvas display state.
     */
    public void togglePause() {
        paused = !paused;
        if (canvas != null) {
            canvas.setPaused(paused);
            // Reset fragment library view when unpausing
            if (!paused) {
                canvas.setShowFragmentLibrary(false);
            }
        }
        if (gameFrame != null) {
            if (paused) {
                gameFrame.showPauseButtons();
            } else {
                gameFrame.hidePauseButtons();
            }
        }
    }

    /** Returns whether the game is currently paused. */
    public boolean isPaused() {
        return paused;
    }

    // =========================================================================
    // Portal and level loading
    // =========================================================================

    /**
     * Checks whether the Wanderer has touched the level's portal to trigger
     * level completion.
     */
    private void checkPortalCollision() {
        // Grace period after a level load — skip portal checks for LEVEL_READY_FRAMES
        // ticks so the Wanderer cannot immediately re-trigger a transition.
        if (!levelReady) {
            levelReadyDelay++;
            if (levelReadyDelay >= LEVEL_READY_FRAMES) {
                levelReady = true;
            }
            return;
        }

        for (GameElement el : elements) {
            if (el instanceof Portal && el.isActive()) {
                if (player.getBounds().intersects(el.getBounds())) {
                    // P10.4 — campaign reduced to 3 platforming levels + boss.
                    // Touching the portal at level 3 flips the session into the
                    // boss arena via the server-authoritative BOSS_ENTER path
                    // (P8.2). Subsequent collisions are debounced by the
                    // server's bossArenaStarted guard.
                    if (levelState.currentLevel >= 3) {
                        System.out.println("LEVEL COMPLETE — entering boss arena (P8.2)");
                        levelReady = false;    // suppress further portal hits
                        levelReadyDelay = 0;
                        if (GameSession.getInstance().isWanderer()) {
                            GameSession.getInstance().sendToServer(Protocol.BOSS_ENTER);
                        }
                        break;
                    }

                    System.out.println("LEVEL COMPLETE — loading next level");
                    int nextLevel = levelState.currentLevel + 1;
                    if (nextLevel <= 3) {
                        loadLevel(nextLevel);
                    } else {
                        // Should not reach here; BOSS_ENTER above covers level 3.
                        System.out.println("All levels complete — proceeding to boss");
                    }
                    break;
                }
            }
        }
    }

    /**
     * Loads the specified level number, clearing existing entities and resetting
     * the player position.
     *
     * @param levelNum the one-based level number to load (1-3); level 4 is the
     *                 boss arena, which is loaded via {@link #enterBossArena(long)}
     *                 in response to {@link Protocol#BOSS_ARENA}, not here
     */
    public void loadLevel(int levelNum) {
        loadLevel(levelNum, true);
    }

    /**
     * Loads the specified level, optionally broadcasting the transition to the server.
     *
     * @param levelNum  the one-based level number to load (1-3)
     * @param broadcast {@code true} when the Wanderer is the initiator (portal
     *                  collision) and must notify the Apprentice via the server;
     *                  {@code false} when called in response to a server message
     *                  so the broadcast does not create a feedback loop
     */
    private void loadLevel(int levelNum, boolean broadcast) {
        elements.clear();
        placedBlocks.clear(); // network-placed blocks cleared with elements

        // Reset levelReady guard so portal/win checks are suppressed for the
        // first LEVEL_READY_FRAMES ticks of the new level.
        levelReady = false;
        levelReadyDelay = 0;

        // Wanderer is the authority on level transitions — broadcast only when
        // this call originates from local logic, not from a server message echo.
        if (broadcast && GameSession.getInstance().isWanderer()) {
            lastHandledLevel = levelNum; // pre-set dedup so the echo is ignored
            GameSession.getInstance().sendToServer(Protocol.LEVEL_CHANGE + "|" + levelNum);
            GameSession.getInstance().sendToServer(Protocol.CLEAR_BLOCKS);
        }

        // P10.4 — single dispatch point for level construction. Levels 1-3 are
        // programmatic (Act1Level / Act2Level / Act3Level); level 4 is the boss
        // arena (handled by the separate enterBossArena path). The seed param
        // is irrelevant for levels 1-3 — pass 0L.
        LevelRegistry.LoadResult result = LevelRegistry.load(levelNum, 0L);
        elements.addAll(result.elements);
        levelState.currentLevel = levelNum;

        // P9.3' — DarkCrawler removed; the new hazard suite (CorruptedSpike,
        // CorruptedWall, PhantomBlock, LightLockedMover) does not need a
        // back-reference to the Player. CorruptedWall queries the player each
        // tick from GameStarter directly. Keep the null-guard so the rest of
        // this method bails cleanly if the Player wasn't constructed.
        if (player == null) {
            System.out.println("ERROR: player is null during level load");
            return;
        }

        // Reset player to level spawn
        player.setX(60);
        player.setY(652);
        player.setVelX(0);
        player.setVelY(0);
        player.setRespawn(60, 652);

        // P10.4 — phase mapping collapses to 1=ACT1, 2=ACT2, 3=ACT3 (boss is
        // separately handled by enterBossArena via Protocol.BOSS_ARENA). The
        // FINAL_CORRIDOR enum value is intentionally retained for backward
        // compatibility with any persisted state but is no longer assigned here.
        if (levelNum == 1) {
            levelState.currentPhase = LevelState.GamePhase.ACT1;
            GameSession.getInstance().setCurrentAct("ACT1");
        } else if (levelNum == 2) {
            levelState.currentPhase = LevelState.GamePhase.ACT2;
            GameSession.getInstance().setCurrentAct("ACT2");
        } else if (levelNum == 3) {
            levelState.currentPhase = LevelState.GamePhase.ACT3;
            GameSession.getInstance().setCurrentAct("ACT3");
        }
        System.out.println("[Level] Loaded level " + levelNum
            + " act=" + GameSession.getInstance().getCurrentAct());

        // D1 — Initialize light source position for Act 2/3 (now levels 2-3).
        if (levelNum >= 2 && canvas != null) {
            canvas.setLightSourcePosition(new java.awt.Point(
                    player.getX() + Player.SPRITE_WIDTH  / 2,   // horizontal centre of the Wanderer sprite
                    player.getY() + Player.SPRITE_HEIGHT / 2)); // vertical centre of the Wanderer sprite
        }

        System.out.println("Loaded level " + levelNum + " with " + elements.size() + " entities");
    }

    // =========================================================================
    // Boss-arena entry (P8.2)
    // =========================================================================

    /**
     * Replaces the current level entities with a freshly-generated boss arena
     * layout (four {@link Core}s, throne marker, radially-symmetric platforms,
     * boundary walls) produced by {@link BossArenaGenerator#generate(long)} and
     * transitions the session into the boss phase. Called on both clients in
     * response to the server's {@link Protocol#BOSS_ARENA} broadcast so each
     * side produces the same arena from the same seed.
     *
     * <p>After rebuilding the element list the Wanderer is placed at the arena
     * spawn position (bottom-centre, {@code (1536, 2048)}) so the camera lerp
     * does not have to chase a stale pre-boss world position, and the camera
     * is snapped directly onto the Wanderer so the ARCHITECT_SPEAKS cutscene
     * resolves back to a centred viewport.
     *
     * @param seed the deterministic arena seed broadcast by the server; the
     *             same value must be fed into
     *             {@link BossArenaGenerator#generate(long)} on both clients
     */
    private void enterBossArena(long seed) {
        System.out.println("[BossArena] Entering boss arena with seed=" + seed);

        // Reset faithful cycle timer so the first award is measured from arena entry.
        faithfulCycleLastMs = 0L;

        // P8.8 — ensure no stale minigame / stunned banner leaks into a fresh run.
        stunMinigame.forceClose();
        architectStunnedBannerUntilMs = 0L;
        if (canvas != null) canvas.setArchitectStunnedUntilMs(0L);

        // P10.4 — boss arena is now level 4 in the unified LevelRegistry.
        // The registry wraps BossArenaGenerator.generate(seed) into the same
        // LoadResult bundle the regular levels produce, keeping a single
        // dispatch point for level construction.
        elements.clear();
        placedBlocks.clear();
        LevelRegistry.LoadResult bossResult = LevelRegistry.load(4, seed);
        elements.addAll(bossResult.elements);

        // Reset the portal-ready guard so any residual portal collision state
        // from the pre-boss level cannot fire against a boss-phase entity.
        levelReady = false;
        levelReadyDelay = 0;

        // P9.3' — DarkCrawler removed; no per-entity Player injection needed
        // here. The boss-attack subclasses already null-check the Player on
        // the Apprentice client (see GameStarter.spawnBossAttack), so the
        // arena loads cleanly without an injection pass.
        if (player != null) {
            // Boss arena spawn: bottom-centre of the arena so the Wanderer
            // starts facing the throne.
            final int spawnX = BossArenaGenerator.CENTER_X;
            final int spawnY = BossArenaGenerator.ARENA_H - BossArenaGenerator.BOSS_SPAWN_Y_OFFSET;
            player.setX(spawnX);
            player.setY(spawnY);
            player.setVelX(0);
            player.setVelY(0);
            player.setRespawn(spawnX, spawnY);
        }

        // Flip the game phase; renderBoss() + P8.1 camera will take over.
        levelState.currentPhase = LevelState.GamePhase.BOSS;
        GameSession.getInstance().setCurrentAct("BOSS");

        // Snap the camera to the Wanderer spawn so the cutscene's post-resume
        // viewport is already centred on the player (prevents a one-second
        // lerp chase the moment gameplay resumes).
        if (player != null) {
            Camera.getInstance().snapTo(player.getX(), player.getY());
        } else {
            Camera.getInstance().reset();
        }

        // Boss-phase BGM: audio not yet implemented.

        System.out.println("[BossArena] Arena built with " + elements.size()
                + " entities; phase=BOSS");
    }

    // =========================================================================
    // Stop
    // =========================================================================

    /**
     * Gracefully stops the game loop. Sets the stop flag so the loop exits after its
     * current tick, then interrupts the thread to wake it immediately if it is
     * sleeping between ticks.
     */
    public void stop() {
        running = false;
        if (gameLoopThread != null) {
            gameLoopThread.interrupt();
        }
    }

    // E2 — stopGame: stops game loop
    public void stopGame() {
        running = false;
        System.out.println("Game stopped.");
    }

    // E2 — resetToMenu: resets game state for returning to main menu
    public void resetToMenu() {
        elements.clear();
        if (player != null) {
            player.setHealth(player.getMaxHealth());
            player.setX(60);
            player.setY(652);
            player.setVelX(0);
            player.setVelY(0);
            player.setDead(false);
        }
        levelState.currentLevel = 1;
        System.out.println("Reset to main menu complete.");
    }


    // =========================================================================
    // Per-tick helpers
    // =========================================================================

    /**
     * Iterates over all active {@link LoreFragment} instances in the element list and
     * checks whether the Wanderer's bounding rectangle overlaps each one. On overlap,
     * {@link LoreFragment#collect()} is called (which deactivates the fragment), the
     * associated ability is granted to the player immediately, and a
     * {@link NetworkProtocol.FragmentCollectedPacket} is sent to the server so the
     * authoritative fragment set stays consistent.
     *
     * <p>The method tolerates concurrent modification of {@code elements} because the
     * backing list is a {@link CopyOnWriteArrayList}; the iterator works on a snapshot.
     */
    private void checkFragmentCollection() {
        for (GameElement el : elements) {
            if (!(el instanceof LoreFragment) || !el.isActive()) continue;
            LoreFragment frag = (LoreFragment) el;
            if (!frag.isCollected()
                    && player.getBounds().intersects(frag.getBounds())) {
                frag.collect();
                grantAbility(frag.getUnlock());
                player.addFaithful(1); // each fragment is a step toward faith
                // Visual feedback: flash + notification
                player.triggerCollectFlash();
                if (canvas != null) {
                    canvas.showNotification(getFragmentNotificationText(frag));
                }
                // Notify the server so both clients' fragment logs stay in sync
                sendPacket(new NetworkProtocol.FragmentCollectedPacket(frag.getFragmentID()));
            }
        }
    }

    // -------------------------------------------------------------------------
    // P8.6 — altar trigger detection and effect application
    // -------------------------------------------------------------------------

    /**
     * Iterates over altar-bearing elements and, on the first un-activated one
     * whose bounding box the Wanderer overlaps, opens the altar overlay on the
     * canvas and sets {@link #altarActive}. Both the legacy invisible
     * {@link Trigger} {@code "ALTAR"} entries (P8.6) and the visible
     * {@link Altar} entities (P10.3) are accepted; both paths latch the same
     * {@code altarActive} flag, so an altar that places both for redundancy
     * (the {@link LevelGenerator#altar(int, int, int, String, String, String)}
     * helper does this) cannot double-fire.
     *
     * <p>For Altar entities, an installed {@link AltarConcealment} is checked
     * first via {@link AltarConcealment#isRevealed(LevelState, GameStarter)};
     * concealed altars are skipped until their reveal condition is met.</p>
     */
    private void checkAltarTrigger() {
        if (altarActive) return;
        for (GameElement el : elements) {
            // ---- Path 1: visible Altar entity (P10.3) ----
            if (el instanceof Altar) {
                Altar a = (Altar) el;
                if (a.isActivated()) continue;
                if (a.getConcealment() != null
                        && !a.getConcealment().isRevealed(levelState, this)) continue;
                if (!player.getBounds().intersects(a.getBounds())) continue;

                int id = a.getAltarId();
                altarActive = true;
                altarActiveTrigger = id;
                a.setActivated(true);
                final int finalId = id;
                SwingUtilities.invokeLater(() -> canvas.openAltarOverlay(finalId));
                break;
            }

            // ---- Path 2: legacy Trigger("ALTAR", ...) entry (P8.6) ----
            if (!(el instanceof Trigger)) continue;
            Trigger t = (Trigger) el;
            if (!"ALTAR".equals(t.getType()) || t.isFired()) continue;
            if (!player.getBounds().intersects(t.getBounds())) continue;

            int id = 1;
            Object rawId = t.getParams().get("altarId");
            if (rawId instanceof Number) {
                id = ((Number) rawId).intValue();
            } else if (rawId instanceof String) {
                try { id = Integer.parseInt((String) rawId); } catch (NumberFormatException ignored) {}
            }
            altarActive = true;
            altarActiveTrigger = id;
            t.fire();
            final int finalId = id;
            SwingUtilities.invokeLater(() -> canvas.openAltarOverlay(finalId));
            break;
        }
    }

    // =========================================================================
    // P9.3' — Hazard contact detection
    // =========================================================================

    /**
     * Tests every {@link CorruptedSpike} and dangerous {@link CorruptedWall}
     * against the Wanderer's bounds and applies contact damage where
     * appropriate. Spike damage is rate-limited per-instance via
     * {@link CorruptedSpike#tryDamage(Player)}; wall damage is gated by the
     * wall's FSM via {@link CorruptedWall#isDangerous()}. Called once per
     * tick on the Wanderer client only — damage authority lives on the
     * Wanderer (the Apprentice mirrors HP via PlayerStatePacket).
     */
    private void checkHazardContact() {
        if (player == null || player.isDead()) return;
        for (GameElement el : elements) {
            if (!el.isActive()) continue;
            if (el instanceof CorruptedSpike) {
                CorruptedSpike spike = (CorruptedSpike) el;
                if (player.getBounds().intersects(spike.getBounds())) {
                    spike.tryDamage(player); // Honours the per-instance cooldown internally
                }
            } else if (el instanceof CorruptedWall) {
                CorruptedWall wall = (CorruptedWall) el;
                if (!wall.isDangerous()) continue; // IDLE / WARNING walls are harmless backdrops
                if (player.getBounds().intersects(wall.getBounds())) {
                    player.takeDamage(wall.getDamage());
                }
            }
        }
    }

    /**
     * P8.7 — Grants +1 faithful every {@link #FAITHFUL_CYCLE_INTERVAL_MS} ms that the
     * Wanderer survives during the boss fight. The timer resets to the current time on
     * each award so the interval is measured from the last grant, not from boss entry.
     * Only runs on the Wanderer client; only active during the BOSS phase.
     */
    private void checkFaithfulCycle() {
        if (player == null) return;
        if (levelState.currentPhase != LevelState.GamePhase.BOSS) return;
        if (player.isDead()) return;
        long now = System.currentTimeMillis();
        if (faithfulCycleLastMs == 0L) {
            faithfulCycleLastMs = now; // initialise on first BOSS-phase tick
            return;
        }
        if (now - faithfulCycleLastMs >= FAITHFUL_CYCLE_INTERVAL_MS) {
            player.addFaithful(1);
            faithfulCycleLastMs = now;
            if (canvas != null) canvas.showNotification("Survived an attack cycle — Faith +1");
        }
    }

    /**
     * Applies the mechanical effect of an authoritative {@link Protocol#ALTAR_RESULT}
     * broadcast. Called on both clients so stats stay in sync; the overlay
     * dismissal is also called here (it is a no-op on the Apprentice side since
     * the overlay was never opened there).
     *
     * @param choice either {@code "POWER_SURGE"} or {@code "SIGHT_RESTRICTION"}
     */
    private void applyAltarResult(String choice) {
        if (player == null) return;
        if ("POWER_SURGE".equals(choice)) {
            player.addMaxHealth(10);
            if (canvas != null) canvas.showNotification("Fragment offered — Max HP +10");
        } else if ("SIGHT_RESTRICTION".equals(choice)) {
            player.addMaxHealth(20);
            player.setSightRestricted(true);
            player.addFaithful(1); // accepting the harder path earns faith
            if (canvas != null) canvas.showNotification("Sight Restricted — Max HP +20, Light -25%, Faith +1");
        }
        altarActive = false;
        altarActiveTrigger = -1;
        SwingUtilities.invokeLater(() -> {
            if (canvas != null) canvas.closeAltarOverlay();
        });
    }

    /**
     * Grants the player the ability associated with the given
     * {@link LoreFragment.AbilityUnlock} token by calling the corresponding setter on
     * the {@link Player} instance. Fragments with {@link LoreFragment.AbilityUnlock#NONE}
     * are purely narrative and produce no mechanical change.
     *
     * @param unlock the ability to grant; must not be {@code null}
     */
    private void grantAbility(LoreFragment.AbilityUnlock unlock) {
        switch (unlock) {
            case MELEE:       player.setHasMelee(true);       break;
            case PROJECTILE:  player.setHasProjectile(true);  break;
            case DODGE:       player.setHasDodge(true);       break;
            case WALL_CLING:  player.setHasWallCling(true);   break;
            case SHADOW_DASH: player.setHasShadowDash(true);  break;

            // P8.9 — Boost unlocks feed Player.activeBoosts so the combat hot
            // path (executeMelee / takeDamage) can branch on them in O(1).
            case EMBER:
            case IRON:
                player.activateBoost(unlock);
                break;

            // P8.9 — Radiant Collapse persists on GameSession so it survives
            // the Player instance being rebuilt across level transitions. The
            // Player accessor delegates to GameSession, keeping the call
            // surface consistent with the other unlock setters.
            case RADIANT_COLLAPSE:
                GameSession.getInstance().setRadiantCollapseUnlocked(true);
                break;

            default:          break; // NONE — no ability to grant
        }
    }

    /**
     * Returns a notification string for the collected fragment, including any
     * ability unlock text.
     */
    private String getFragmentNotificationText(LoreFragment frag) {
        String base = "Fragment collected: " + frag.getFragmentID();
        if (frag.getUnlock() != LoreFragment.AbilityUnlock.NONE) {
            base += "  —  " + frag.getUnlock().name() + " unlocked!";
        }
        return base;
    }

    /**
     * Evaluates the current win and loss conditions once per tick. The server is
     * authoritative and will send a {@link NetworkProtocol.VictoryPacket} when either
     * condition is met; this local check acts as a fallback for offline or latency-spike
     * scenarios.
     *
     * <p><b>Win condition:</b> at least one {@link Core} exists in the element list
     * and every Core is destroyed ({@link Core#isDestroyed()} returns {@code true}).
     *
     * <p><b>Loss condition:</b> the Wanderer is dead during the BOSS phase. In
     * Acts 1-3 a death triggers an Act-1 restart instead of a loss.
     */
    private void checkWinLoss() {
        // --- Win: all Cores present in the world must be destroyed ---
        boolean hasCores     = false;
        boolean allDestroyed = true;
        for (GameElement el : elements) {
            if (el instanceof Core) {
                hasCores = true;
                if (!((Core) el).isDestroyed()) {
                    allDestroyed = false;
                    break;
                }
            }
        }
        if (hasCores && allDestroyed) {
            handleWin();
            return;
        }

        // --- Loss: Wanderer out of health during boss fight only ---
        if (levelState.currentPhase == LevelState.GamePhase.BOSS && !player.isAlive()) {
            handleLoss();
        }
    }

    /**
     * Called when all Cores have been destroyed locally. Defers the actual win sequence
     * to the server's {@link NetworkProtocol.VictoryPacket} where possible; stops the
     * loop here only if the server packet has not arrived yet.
     */
    private void handleWin() {
        if (!victoryHandled) {
            running = false;
            // Server VictoryPacket will trigger the cutscene when it arrives
        }
    }

    /**
     * Called when the Wanderer dies or the countdown reaches zero locally. As with
     * {@link #handleWin()}, the server VictoryPacket is the authoritative trigger;
     * this stops the loop locally in case of packet delay.
     */
    private void handleLoss() {
        if (!victoryHandled) {
            running = false;
        }
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    /**
     * Returns the network socket connecting this client to the game server.
     *
     * @return the {@link Socket}; may be {@code null} if
     *         {@link #connectToServer(String, int)} has not yet been called
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * Returns the role string assigned to this client by the server.
     *
     * @return {@code "WANDERER"}, {@code "APPRENTICE"}, or {@code null} if the
     *         role has not yet been received
     */
    public String getRole() {
        return role;
    }

    /**
     * Returns the {@link GameFrame} window managed by this starter.
     *
     * @return the game frame; may be {@code null} before the EDT runnable has executed
     */
    public GameFrame getGameFrame() {
        return gameFrame;
    }

    /**
     * Returns the Wanderer {@link Player} instance.
     *
     * @return the player; never {@code null}
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Returns the live list of Apprentice-placed blocks. Read by P9.2'
     * concealments (e.g. {@link PressurePlateConcealment}) to test for
     * overlap with a paired plate location.
     *
     * @return the {@link CopyOnWriteArrayList}-backed placed-block list;
     *         never {@code null}
     */
    public List<Platform> getPlacedBlocks() {
        return placedBlocks;
    }

    /**
     * Returns the live entity list. The network I/O thread and level loader add and
     * remove elements here; the game loop iterates it each tick.
     *
     * @return the {@link CopyOnWriteArrayList}-backed entity list; never {@code null}
     */
    public List<GameElement> getElements() {
        return elements;
    }

    /**
     * Returns the current level state shared by all game subsystems.
     *
     * @return the {@link LevelState}; never {@code null}
     */
    public LevelState getLevelState() {
        return levelState;
    }

    /**
     * Returns the {@link KeyBindings} instance that owns the
     * {@link KeyBindings.PlayerInputState} and the registered InputMap/ActionMap.
     *
     * @return the key bindings; never {@code null}
     */
    public KeyBindings getKeyBindings() {
        return keyBindings;
    }

    /**
     * Returns the {@link InputRouter} that translates per-tick input state into
     * {@link PhysicsEngine} calls.
     *
     * @return the input router; never {@code null}
     */
    public InputRouter getInputRouter() {
        return inputRouter;
    }

    /**
     * Returns whether the game loop is currently running.
     *
     * @return {@code true} while the loop thread is executing
     */
    public boolean isRunning() {
        return running;
    }
}
