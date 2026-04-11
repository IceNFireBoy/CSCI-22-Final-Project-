
/**
 * Acts as the client-side entry point for a Lumen Architect session, establishing a
 * network connection to the running {@link server.GameServer}, receiving the role
 * assignment that determines whether this instance controls the Wanderer or the
 * Apprentice, and launching the game loop thread that drives the rendering and input
 * pipeline. Separating startup logic into this class keeps {@link GameFrame} focused
 * purely on the UI while {@code GameStarter} owns the network and threading lifecycle.
 *
 * <p>The game loop runs on a dedicated thread named {@value #LOOP_THREAD_NAME}, using
 * {@link System#nanoTime()} for drift-compensated fixed-timestep scheduling at
 * {@value #TICK_MS} ms per tick (targeting 60 fps). The loop body executes input
 * routing, physics, entity updates, lore-fragment collection, win/loss evaluation, and
 * a repaint request each tick. A {@code volatile} stop flag allows any thread to
 * terminate the loop cleanly via {@link #stop()}.
 *
 * <p>Network I/O runs on a third dedicated thread named {@value #NET_THREAD_NAME}.
 * The send half dispatches a state snapshot every 16 ms; the receive half polls for
 * inbound {@link server.NetworkProtocol.ServerStatePacket} instances with a short
 * socket timeout so neither phase blocks the other for more than one tick.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-22
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */


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

    /**
     * When {@code true} the client skips the server connection entirely and starts
     * an offline single-player test session as the Wanderer. Set to {@code false}
     * before building the networked release.
     */
    public static final boolean OFFLINE_TEST_MODE = true;

    /** Name assigned to the game loop thread for debugger and profiler visibility. */
    private static final String LOOP_THREAD_NAME = "GameLoop";

    /** Name assigned to the network I/O thread (Thread 3). */
    private static final String NET_THREAD_NAME = "NetworkIO";

    /** Fixed physics/render tick duration in milliseconds. */
    private static final long TICK_MS = 16L;

    /** Fixed tick duration in nanoseconds, used for nanosecond-precision scheduling. */
    private static final long TICK_NS = TICK_MS * 1_000_000L;

    /** Target interval in milliseconds between outbound network state packets. */
    private static final long NET_SEND_INTERVAL_MS = 16L;

    /**
     * Socket read timeout in milliseconds used by the network thread. Short enough to
     * keep the send loop on schedule; long enough that a single small packet arriving in
     * one OS read() will not trigger a spurious timeout.
     */
    private static final int NET_RECV_TIMEOUT_MS = 15;

    /** Milliseconds the network thread waits before attempting a single reconnect. */
    private static final long RECONNECT_DELAY_MS = 3000L;

    /** Horizontal spawn position of the Wanderer in world space. */
    private static final int PLAYER_SPAWN_X = 100;

    /** Vertical spawn position of the Wanderer in world space. */
    private static final int PLAYER_SPAWN_Y = 400;

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

        if (!OFFLINE_TEST_MODE) {
            String host = (args.length > 0) ? args[0] : "localhost";
            int    port = (args.length > 1) ? Integer.parseInt(args[1]) : 9876;
            starter.connectToServer(host, port);
        }

        // Open the game window on the EDT.
        SwingUtilities.invokeLater(() -> {
            starter.gameFrame = new GameFrame();
            starter.gameFrame.setVisible(true);
        });

        if (!OFFLINE_TEST_MODE) {
            // Thread 3 — network I/O (send + receive) runs independently of the game loop.
            // startGameLoop() is called by the NetworkIO thread once bothConnected is true.
            starter.startNetworkThread();
        }
        // In offline mode the connect button's ActionListener calls startOfflineGame().
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
            socket.setSoTimeout(5000); // 5-second timeout so readObject() never blocks forever

            networkOut = new ObjectOutputStream(socket.getOutputStream());
            networkOut.flush(); // flush header before constructing input stream — prevents deadlock

            networkIn = new ObjectInputStream(socket.getInputStream());

            Object firstPacket = networkIn.readObject();

            NetworkProtocol.RoleAssignmentPacket rap =
                    (NetworkProtocol.RoleAssignmentPacket) firstPacket;

            role = rap.role;
            GameSession.getInstance().setRole(role);

            // Gesture system removed — no camera pipeline needed

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

        try {
            socket.setSoTimeout(NET_RECV_TIMEOUT_MS);
        } catch (IOException ignored) {}

        // ---- Phase 0: wait until server confirms both players connected ----
        // Spin-reads ServerStatePackets until bothConnected == true, then
        // starts the game loop. This prevents the Wanderer from acting on
        // uninitialised state before the Apprentice has joined.
        spinWaitForBothConnected:
        while (true) {
            try {
                Object pkt = networkIn.readObject();
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
                // No data yet — keep polling
            } catch (ClassNotFoundException e) {
                System.err.println("[NetworkIO] Unknown packet class during wait: " + e.getMessage());
            } catch (IOException e) {
                System.err.println("[NetworkIO] Connection lost while waiting for both players.");
                return;
            }
        }

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
        if (GameSession.getInstance().isWanderer) {
            sendPacket(new NetworkProtocol.PlayerStatePacket(
                    player.getX(),
                    player.getY(),
                    player.getHealth(),
                    player.getAnimState()
            ));
            // If the Wanderer struck a Core this tick, forward it to the server.
            // pendingCoreHitIndex is set in Player.executeMelee() on the game-loop
            // thread; consumePendingCoreHit() reads and clears it here on the
            // NetworkIO thread (volatile guarantees visibility).
            int coreHit = player.consumePendingCoreHit();
            if (coreHit >= 0) {
                sendPacket(new NetworkProtocol.CoreHitPacket(coreHit));
            }
        } else if (GameSession.getInstance().isApprentice) {
            // Use mouse position from MouseApprentice (gesture system removed)
            int gx = MouseApprentice.getInstance().getX();
            int gy = MouseApprentice.getInstance().getY();
            sendPacket(new NetworkProtocol.GesturePacket("NONE", gx, gy));
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
            if ("PLAYER_DISCONNECTED".equals(cp.triggerID)) {
                if (canvas != null) {
                    canvas.setConnectionOverlay("Other player disconnected.");
                }
                paused = true;
                if (canvas != null) {
                    canvas.setPaused(true);
                }
                System.out.println("[NetworkIO] Remote player disconnected — game paused.");
            }
        }
    }

    /**
     * Applies a {@link NetworkProtocol.ServerStatePacket} to local game state.
     * What is updated depends on this client's role:
     *
     * <ul>
     *   <li><b>Wanderer:</b> light-source position (from latest gesture), core health,
     *       architect-override flag, victory check.</li>
     *   <li><b>Apprentice:</b> remote Wanderer ghost position/health, core health,
     *       victory check.</li>
     * </ul>
     *
     * @param s the server state snapshot; must not be {@code null}
     */
    private void applyServerState(NetworkProtocol.ServerStatePacket s) {
        GameSession session = GameSession.getInstance();

        if (session.isWanderer) {
            // Update the Apprentice's light position on the game canvas
            if (s.latestGesture != null && canvas != null) {
                canvas.setLightPosition(new Point(s.latestGesture.x, s.latestGesture.y));
            }

        } else if (session.isApprentice) {
            // Ghost the remote Wanderer in the Apprentice's view
            if (s.playerState != null && canvas != null) {
                canvas.setRemotePlayer(
                        s.playerState.x,
                        s.playerState.y,
                        s.playerState.health);
            }
        }

        // Both roles: update the server-authoritative core health snapshot in LevelState
        if (s.coreState != null) {
            levelState.coreHealth = s.coreState.health.clone();
        }

        // Both roles: apply architect-override flag so gesture routing is correct
        // regardless of which client is running
        inputRouter.setArchitectOverride(s.architectOverride);

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

        if (result == GameServer.VictoryState.WANDERER_WIN) {
            SwingUtilities.invokeLater(() -> {
                System.out.println("[GameStarter] WANDERER_VICTORY cutscene triggered (stub)");
                levelState.currentPhase = LevelState.GamePhase.CUTSCENE;
            });
            // Only the Wanderer client advances to level 10 (the epilogue)
            if (GameSession.getInstance().isWanderer) {
                levelState.currentLevel = 10;
            }

        } else { // APPRENTICE_WIN — both clients see Architect victory
            SwingUtilities.invokeLater(() -> {
                System.out.println("[GameStarter] ARCHITECT_VICTORY cutscene triggered (stub)");
                levelState.currentPhase = LevelState.GamePhase.CUTSCENE;
            });
        }
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

        try {
            Thread.sleep(RECONNECT_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        // Single reconnect attempt
        try {
            socket     = new Socket(lastHost, lastPort);
            networkOut = new ObjectOutputStream(socket.getOutputStream());
            networkOut.flush();
            networkIn  = new ObjectInputStream(socket.getInputStream());
            socket.setSoTimeout(NET_RECV_TIMEOUT_MS);

            connectionLost = false;
            if (canvas != null) {
                canvas.setConnectionOverlay(null);
            }
            return true;

        } catch (IOException e) {
            connectionFailed = true;
            if (canvas != null) {
                canvas.setConnectionOverlay("Connection failed. Please restart.");
            }
            running = false; // pause the game loop
            return false;
        }
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

            // Gesture system removed — no gesture input to wire

            // FIX 3 — Inject canvas into InputRouter for light source updates
            inputRouter.setGameCanvas(canvas);

            // -----------------------------------------------------------------
            // Phase B: register key bindings on the EDT, then wait for
            // completion before the loop starts so no input events are missed.
            // -----------------------------------------------------------------
            try {
                SwingUtilities.invokeAndWait(() -> {
                    keyBindings.registerBindings(canvas);
                    canvas.requestFocusInWindow();
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (java.lang.reflect.InvocationTargetException e) {
                e.printStackTrace();
                return;
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

                // (1) Read input state and route to PhysicsEngine/Player calls.
                inputRouter.routeKeyEvent(input, player, physicsEngine);

                // (2) Update player respawn state (death timer).
                player.updateRespawn(TICK_MS);

                // Game-over checks — before physics and portal checks
                if (player != null && player.getLives() <= 0 && !player.isGameOverPending() && player.isDead()) {
                    int currentLevel = levelState.currentLevel;
                    loadLevel(currentLevel);
                    canvas.showNotification("Attempt failed — " + player.getTotalAttempts() + " attempt(s) remaining");
                    continue;
                }
                if (player != null && player.isGameOverPending()) {
                    player.setGameOverPending(false);
                    GameStarter.getInstance().stopGame();
                    GameStarter.getInstance().resetToMenu();
                    canvas.showNotification("No attempts remaining — run ended");
                    new Thread(() -> {
                        try { Thread.sleep(2000); } catch (InterruptedException ex) {}
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            GameFrame frame = GameStarter.getInstance().getGameFrame();
                            if (frame != null) {
                                frame.getIpField().setVisible(true);
                                frame.getConnectButton().setVisible(true);
                            }
                            canvas.setPhase(LevelState.GamePhase.MENU);
                            canvas.repaint();
                        });
                    }).start();
                    continue;
                }

                // (3) Advance physics simulation by one fixed tick.
                if (!player.isDead()) {
                    physicsEngine.update(TICK_MS, player, elements);
                }

                // (3b) Update light source position from mouse (gesture system removed)
                if (canvas != null) {
                    Point mp = canvas.getMousePosition2();
                    if (mp != null) {
                        canvas.setLightSourcePosition(mp);
                    }
                }

                // (3c) Update platform lit state from light source (all types)
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
                                p2.setLit(dist <= litRadius + 20);
                            }
                        }
                    }
                }

                // (3d) Update light battery and radius for ACT2/ACT3 (mouse-driven)
                if (canvas != null
                        && (levelState.currentPhase == LevelState.GamePhase.ACT2
                            || levelState.currentPhase == LevelState.GamePhase.ACT3)) {
                    int currentRadius = canvas.getLightRadius();
                    boolean lightActive = (currentRadius > 0);
                    canvas.updateLightBattery(lightActive, currentRadius);
                    // Use mouse-driven radius from MouseApprentice
                    int targetRadius = MouseApprentice.getInstance().getLightRadius();
                    canvas.setLightRadius(targetRadius);
                }

                // (4) Update all active game elements (animations, timers, AI stubs).
                for (GameElement el : elements) {
                    if (el.isActive()
                            || (el instanceof Platform && !((Platform) el).fullyGone)) {
                        el.update(TICK_MS);
                    }
                }

                // Decrement the level countdown timer if it is running.
                if (levelState.timerActive) {
                    levelState.timeRemainingMs =
                            Math.max(0L, levelState.timeRemainingMs - TICK_MS);
                }

                // (5) Check whether the Wanderer has entered a LoreFragment's bounds.
                //     Fragment collection also sends a FragmentCollectedPacket to the
                //     server so the authoritative fragment set stays in sync.
                checkFragmentCollection();

                // (6) Check portal collision for level completion.
                checkPortalCollision();

                // (7) Evaluate win and loss conditions (local fallback; server
                //     is authoritative and will also send a VictoryPacket).
                checkWinLoss();

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
    // Offline game start
    // =========================================================================

    /**
     * Starts an offline single-player session as the Wanderer, bypassing the server
     * connection entirely. Loads level 1, configures the level state to ACT1, starts
     * the game loop, and attempts to play the Act 1 background music if the audio file
     * is present in {@code resources/audio/}.
     *
     * <p>Must be called on the Swing Event Dispatch Thread (from the connect button's
     * ActionListener) after {@link GameFrame} has already been made visible.
     */
    public void startOfflineGame() {
        levelState.currentLevel = 1;
        levelState.currentPhase = LevelState.GamePhase.ACT1;

        LevelLoader loader = new LevelLoader();
        LevelLoader.LoadResult result = loader.loadLevel(1);
        elements.addAll(result.elements);

        // Inject player reference into all DarkCrawlers
        if (player == null) {
            System.out.println("ERROR: player is null during level load — cannot inject into crawlers");
            return;
        }
        for (GameElement el : elements) {
            if (el instanceof DarkCrawler) {
                ((DarkCrawler) el).setPlayer(player);
            }
        }

        // Set Wanderer spawn/respawn for level 1
        player.setX(60);
        player.setY(652);
        player.setRespawn(60, 652);

        System.out.println("Game started with " + elements.size() + " active entities");
        System.out.println("Level loaded with blockBudget=" + levelState.blockBudget);

        // Inject dependencies into InputRouter for gesture/mouse routing (FIX 3)
        inputRouter.setLevelState(levelState);
        inputRouter.setPlayer(player);
        inputRouter.setActiveEntities(elements);
        inputRouter.setAudioManager(AudioManager.getInstance());

        // Gesture system removed — MouseApprentice handles input instead

        startGameLoop();

        if (new File("resources/audio/bgm_act1.wav").exists()) {
            AudioManager.getInstance().playBGM("bgm_act1.wav");
        }
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
        for (GameElement el : elements) {
            if (el instanceof Portal && el.isActive()) {
                if (player.getBounds().intersects(el.getBounds())) {
                    System.out.println("LEVEL COMPLETE — loading next level");
                    int nextLevel = levelState.currentLevel + 1;
                    if (nextLevel <= 9) {
                        loadLevel(nextLevel);
                    } else {
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
     * @param levelNum the one-based level number to load (1-9)
     */
    public void loadLevel(int levelNum) {
        elements.clear();
        LevelLoader loader = new LevelLoader();
        LevelLoader.LoadResult result = loader.loadLevel(levelNum);
        elements.addAll(result.elements);
        levelState.currentLevel = levelNum;

        // Inject player reference into all DarkCrawlers
        if (player == null) {
            System.out.println("ERROR: player is null during level load — cannot inject into crawlers");
            return;
        }
        for (GameElement el : elements) {
            if (el instanceof DarkCrawler) {
                ((DarkCrawler) el).setPlayer(player);
            }
        }

        // Reset player to level spawn
        player.setX(60);
        player.setY(652);
        player.setVelX(0);
        player.setVelY(0);
        player.setRespawn(60, 652);

        // Update phase based on act
        if (levelNum <= 3) {
            levelState.currentPhase = LevelState.GamePhase.ACT1;
        } else if (levelNum <= 6) {
            levelState.currentPhase = LevelState.GamePhase.ACT2;
        } else if (levelNum <= 9) {
            levelState.currentPhase = LevelState.GamePhase.ACT3;
        }

        // D1 — Initialize light source position for Act 2/3
        if (levelNum >= 4 && canvas != null) {
            canvas.setLightSourcePosition(new java.awt.Point(player.getX() + 32, player.getY() + 48));
        }

        // Update BGM based on act
        if (levelNum <= 3) AudioManager.getInstance().playBGM("bgm_act1.wav");
        else if (levelNum <= 6) AudioManager.getInstance().playBGM("bgm_act2.wav");
        else if (levelNum <= 9) AudioManager.getInstance().playBGM("bgm_act3.wav");

        System.out.println("Loaded level " + levelNum + " with " + elements.size() + " entities");
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

    // E2 — stopGame: stops game loop and BGM
    public void stopGame() {
        running = false;
        AudioManager.getInstance().stopBGM();
        System.out.println("Game stopped.");
    }

    // E2 — resetToMenu: resets game state for returning to main menu
    public void resetToMenu() {
        elements.clear();
        if (player != null) {
            player.setLives(3);
            player.setX(60);
            player.setY(652);
            player.setVelX(0);
            player.setVelY(0);
            player.setDead(false);
        }
        levelState.currentLevel = 1;
        System.out.println("Reset to main menu complete.");
    }

    /** Stub: gesture system removed. */
    public Object getGestureInput() { return null; }

    /** Stub: gesture system removed. */
    public void setGestureInput(Object gi) { /* no-op */ }

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
     * <p><b>Loss conditions:</b>
     * <ul>
     *   <li>The Wanderer's health reaches zero ({@link Player#isAlive()} returns
     *       {@code false}).</li>
     *   <li>The level countdown timer has reached zero while
     *       {@link LevelState#timerActive} is {@code true}.</li>
     * </ul>
     *
     * <p>Win is checked before loss so that a simultaneous last-hit and death resolves
     * as a win.
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

        // --- Loss: Wanderer out of health ---
        if (!player.isAlive()) {
            handleLoss();
            return;
        }

        // --- Loss: countdown expired ---
        if (levelState.timerActive && levelState.timeRemainingMs <= 0L) {
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
