/**
 * Acts as the authoritative host for a Lumen Architect multiplayer session, accepting
 * connections from exactly two clients (Wanderer and Apprentice), maintaining the shared
 * game state, and broadcasting authoritative state updates to all connected peers every
 * server tick at 60 fps. The server is the single source of truth for Core health,
 * victory conditions, fragment collection, and the architect-override flag that enables
 * the Apprentice's elevated intervention privileges.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-22
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

public class GameServer {

    // =========================================================================
    // Enum — VictoryState
    // =========================================================================

    /**
     * Represents the current outcome state of the Lumen Architect match. The server
     * transitions from {@link #IN_PROGRESS} to one of the terminal states when a
     * win condition is met, then broadcasts a {@link NetworkProtocol.VictoryPacket}
     * to both clients.
     */
    public enum VictoryState {

        /** The match is still ongoing; neither player has won yet. */
        IN_PROGRESS,

        /**
         * The Wanderer has destroyed all four Cores before time expired or being
         * defeated; the Wanderer player wins.
         */
        WANDERER_WIN,

        /**
         * The Apprentice has successfully defended all Cores until the timer expired
         * or the Wanderer's health reached zero; the Apprentice player wins.
         */
        APPRENTICE_WIN
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

    /** Array index for the Wanderer role. */
    private static final int WANDERER = 0;

    /** Array index for the Apprentice role. */
    private static final int APPRENTICE = 1;

    // =========================================================================
    // Fields — server infrastructure
    // =========================================================================

    /**
     * The server-side socket that listens for incoming client connections. Bound to
     * {@link #PORT} at startup and closed when the session ends.
     */
    private ServerSocket serverSocket;

    /**
     * One {@link ObjectOutputStream} per role (index 0 = Wanderer, index 1 = Apprentice).
     * Written only during {@link #startServer()} before the game loop begins, then
     * read-shared across threads; individual writes are serialised by
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

    /**
     * When {@code true}, the Apprentice has activated architect-override mode, allowing
     * direct intervention in the game world (boss fight phase). Set to {@code true} when
     * the first Core is destroyed, and cleared when all four Cores are destroyed.
     */
    private boolean architectOverride;

    /**
     * The current victory state of the match. Updated by the game loop when a terminal
     * condition is detected and included in every subsequent state broadcast.
     */
    private VictoryState victoryState;

    /**
     * The most recent {@link NetworkProtocol.PlayerStatePacket} received from the
     * Wanderer client. {@code null} until the first packet arrives.
     */
    private NetworkProtocol.PlayerStatePacket latestPlayerState;

    /**
     * The most recent {@link NetworkProtocol.GesturePacket} received from the Apprentice
     * client. {@code null} until the first packet arrives.
     */
    private NetworkProtocol.GesturePacket latestGesture;

    /**
     * Level-ready flags per role. {@code levelReady[APPRENTICE]} is set to {@code true}
     * when the server receives a {@link NetworkProtocol.GesturePacket} with gestureID
     * {@code "LEVEL_READY"}.
     */
    private final boolean[] levelReady = new boolean[2];

    /**
     * Server-authoritative set of fragment IDs that have been collected during this
     * session. Updated when a {@link NetworkProtocol.FragmentCollectedPacket} arrives
     * and the fragment ID has not yet been recorded.
     */
    private final Set<String> serverCollectedFragments = new HashSet<>();

    /**
     * Running count of Cores whose health has reached zero. Used to detect the first
     * destruction event (triggering architect-override) and the all-four condition
     * (triggering Wanderer victory).
     */
    private int destroyedCoreCount = 0;

    /**
     * Set to {@code true} once both {@link ClientHandler} threads have been started,
     * indicating that the server is ready to accept game-state packets from either
     * client. Included in every {@link NetworkProtocol.ServerStatePacket} so clients
     * can spin-wait on this flag before starting their game loops.
     */
    private boolean bothConnected = false;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Constructs a new {@code GameServer} with all four Core health values initialised
     * to {@link #CORE_MAX_HEALTH}, architect-override disabled, and the match state set
     * to {@link VictoryState#IN_PROGRESS}.
     */
    public GameServer() {
        this.coreHealth = new int[]{CORE_MAX_HEALTH, CORE_MAX_HEALTH, CORE_MAX_HEALTH, CORE_MAX_HEALTH};
        this.architectOverride = false;
        this.victoryState = VictoryState.IN_PROGRESS;
    }

    // =========================================================================
    // Entry point
    // =========================================================================

    /**
     * Application entry point for the dedicated game server. Prints a startup banner,
     * then instantiates a {@code GameServer} and calls {@link #startServer()}.
     *
     * @param args command-line arguments; unused
     */
    public static void main(String[] args) {
        try {
            System.out.println("Lumen Architect Server starting on port 9876...");
            GameServer server = new GameServer();
            server.startServer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================================================================
    // Server lifecycle
    // =========================================================================

    /**
     * Binds the {@link ServerSocket} to port {@link #PORT}, accepts exactly two client
     * connections in order (first = Wanderer, second = Apprentice), sends each a
     * {@link NetworkProtocol.RoleAssignmentPacket}, starts a {@link ClientHandler} thread
     * for each connection, starts the server game loop thread, then prints a confirmation
     * message.
     */
    public void startServer() {
        try {
            serverSocket = new ServerSocket(PORT);

            // ---- Accept first client: WANDERER ----
            Socket wandererSocket = serverSocket.accept();
            sockets[WANDERER] = wandererSocket;
            // Flush the header before any objects are written
            outs[WANDERER] = new ObjectOutputStream(wandererSocket.getOutputStream());
            outs[WANDERER].flush();
            clientConnected[WANDERER] = true;
            outs[WANDERER].writeObject(new NetworkProtocol.RoleAssignmentPacket("WANDERER"));
            outs[WANDERER].flush();

            // ---- Accept second client: APPRENTICE ----
            Socket apprenticeSocket = serverSocket.accept();
            sockets[APPRENTICE] = apprenticeSocket;
            outs[APPRENTICE] = new ObjectOutputStream(apprenticeSocket.getOutputStream());
            outs[APPRENTICE].flush();
            clientConnected[APPRENTICE] = true;
            outs[APPRENTICE].writeObject(new NetworkProtocol.RoleAssignmentPacket("APPRENTICE"));
            outs[APPRENTICE].flush();

            // ---- Start ClientHandler threads ----
            Thread wandererHandler = new Thread(
                    new ClientHandler(wandererSocket, WANDERER, "WANDERER"),
                    "ClientHandler-WANDERER");
            wandererHandler.setDaemon(true);
            wandererHandler.start();

            Thread apprenticeHandler = new Thread(
                    new ClientHandler(apprenticeSocket, APPRENTICE, "APPRENTICE"),
                    "ClientHandler-APPRENTICE");
            apprenticeHandler.setDaemon(true);
            apprenticeHandler.start();

            // Both handlers running — flag so clients can start their game loops
            this.bothConnected = true;

            // ---- Start server game loop on Thread 3 ----
            Thread gameLoop = new Thread(this::runGameLoop, "ServerLoop");
            gameLoop.setDaemon(true);
            gameLoop.start();

            System.out.println("Both players connected. Starting game.");

        } catch (IOException e) {
            System.err.println("Server startup failed: " + e.getMessage());
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
        while (victoryState == VictoryState.IN_PROGRESS) {
            long tickStart = System.currentTimeMillis();

            // Drain the shared queue and process each packet
            Object packet;
            while ((packet = sharedQueue.poll()) != null) {
                processPacket(packet);
            }

            // Broadcast consolidated state snapshot
            broadcastState();

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
            // Wanderer death → Apprentice wins
            if (p.health <= 0 && victoryState == VictoryState.IN_PROGRESS) {
                victoryState = VictoryState.APPRENTICE_WIN;
                broadcastToAll(new NetworkProtocol.VictoryPacket(VictoryState.APPRENTICE_WIN));
            }

        } else if (packet instanceof NetworkProtocol.GesturePacket) {
            NetworkProtocol.GesturePacket g = (NetworkProtocol.GesturePacket) packet;
            latestGesture = g;
            if ("LEVEL_READY".equals(g.gestureID)) {
                levelReady[APPRENTICE] = true;
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
        }
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

        if (coreHealth[coreIndex] <= 0) {
            destroyedCoreCount++;

            // First Core destroyed — boss fight phase begins
            if (destroyedCoreCount == 1) {
                architectOverride = true;
            }

            // Broadcast updated Core health immediately
            broadcastToAll(new NetworkProtocol.CoreStatePacket(coreHealth.clone()));

            // All four Cores destroyed — Wanderer wins
            if (destroyedCoreCount >= CORE_COUNT) {
                architectOverride = false;
                victoryState = VictoryState.WANDERER_WIN;
                broadcastToAll(new NetworkProtocol.VictoryPacket(VictoryState.WANDERER_WIN));
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
        NetworkProtocol.ServerStatePacket state = new NetworkProtocol.ServerStatePacket(
                latestPlayerState,
                latestGesture,
                new NetworkProtocol.CoreStatePacket(coreHealth.clone()),
                victoryState,
                architectOverride,
                levelReady[APPRENTICE],
                bothConnected
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
                    System.out.println("Write failed for client "
                            + (i == WANDERER ? "WANDERER" : "APPRENTICE")
                            + ": " + e.getMessage());
                    clientConnected[i] = false;
                }
            }
        }
    }

    // =========================================================================
    // handleClient — retained for interface compatibility
    // =========================================================================

    /**
     * Legacy stub preserved for interface compatibility. Client I/O is handled by
     * {@link ClientHandler} threads started in {@link #startServer()}.
     *
     * @param s the connected client socket; unused in the current implementation
     */
    public void handleClient(Socket s) {
        // Packet dispatch is delegated to ClientHandler inner class.
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
        private final Socket socket;

        /** Role index: {@link GameServer#WANDERER} or {@link GameServer#APPRENTICE}. */
        private final int roleIndex;

        /** Human-readable role string used in console output and disconnect packets ({@code "WANDERER"} or {@code "APPRENTICE"}). */
        private final String role;

        /** Input stream used to deserialise packets from this client; opened at the start of {@link #run()}. */
        private ObjectInputStream in;

        /**
         * Constructs a {@code ClientHandler} for the given client socket.
         *
         * @param socket    the connected client socket; must not be {@code null}
         * @param roleIndex {@link GameServer#WANDERER} (0) or {@link GameServer#APPRENTICE} (1)
         * @param role      the role label for logging and packet payloads
         */
        ClientHandler(Socket socket, int roleIndex, String role) {
            this.socket = socket;
            this.roleIndex = roleIndex;
            this.role = role;
        }

        /**
         * Reads deserialized objects from the client input stream continuously until the
         * connection closes or an I/O error occurs. Each object is placed in
         * {@link GameServer#sharedQueue} for the game loop thread to consume.
         */
        @Override
        public void run() {
            try {
                in = new ObjectInputStream(socket.getInputStream());
                while (clientConnected[roleIndex]) {
                    Object packet = in.readObject();
                    sharedQueue.put(packet);
                }
            } catch (IOException e) {
                System.out.println("Client disconnected: " + role);
                clientConnected[roleIndex] = false;
                broadcastToAll(new NetworkProtocol.CutscenePacket("PLAYER_DISCONNECTED"));
            } catch (ClassNotFoundException e) {
                System.err.println("Unknown packet class from " + role + ": " + e.getMessage());
                clientConnected[roleIndex] = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
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
