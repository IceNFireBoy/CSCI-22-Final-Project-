

















































import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
public class GameServer {









    public enum VictoryState {
        IN_PROGRESS,
        WANDERER_WIN,
        APPRENTICE_WIN
    }






    private static final int PORT = 9876;


    private static final int CORE_COUNT = 5;


    private static final int CORE_MAX_HEALTH = 3;


    private static final long TICK_MS = 1000L / 60L;

    public static final int ARENA_W = 3072;
    public static final int ARENA_H = 2304;

    private static final int SLOT_0 = 0;
    private static final int SLOT_1 = 1;











    private final ObjectOutputStream[] outs = new ObjectOutputStream[2];






    private final boolean[] clientConnected = new boolean[2];






    private final LinkedBlockingQueue<Object> sharedQueue = new LinkedBlockingQueue<>();









    private int[] coreHealth;

    private boolean architectOverride;
    private VictoryState victoryState;

    private volatile VictoryState pendingVictoryState;
    private NetworkProtocol.PlayerStatePacket latestPlayerState;
    private final boolean[] levelReady = new boolean[2];
    private final Set<String> serverCollectedFragments = new HashSet<>();
    private int destroyedCoreCount = 0;





    private volatile String activeCutsceneId = null;
    private final boolean[] cutsceneAcked = new boolean[2];
    private volatile long cutsceneStartMs = 0L;
    private static final long CUTSCENE_GUARD_MS = 30_000L;





    private volatile long bossArenaSeed = 0L;
    private volatile boolean bossArenaStarted = false;





    private volatile LightBall lightBall = null;
    private volatile boolean bossPhaseActive = false;













    private static final String[] ATTACK_TYPES = {
            "SEARING_BEAM", "BLOCK_RAIN", "CRUSHER", "SPIKE_ARRAY", "SHIELD"
    };







    private static final long[] ATTACK_COOLDOWN_MS = {
            5_000L,
            8_000L,
            10_000L,
            4_000L,
            6_000L
    };








    private final long[] attackCooldownUntilMs = new long[ATTACK_TYPES.length];














    private volatile long architectStunnedUntilMs = 0L;










    private final java.util.Random stunRng = new java.util.Random(System.nanoTime());


    private static final long STUN_OPPORTUNITY_COOLDOWN_MS = 4_000L;


    private volatile long stunOpportunityCooldownUntilMs = 0L;







    private boolean bothConnected = false;






    private volatile boolean sessionActive = true;










    private final String[] lobbySelectedRole = new String[2];





    private final String[] lobbyHoverState = {"NONE", "NONE"};






    private ServerSocket serverSocket;






    private volatile String vacantRole = null;


    private volatile long partialDisconnectTime = -1;


    private static final long RECONNECT_TIMEOUT_MS = 90 * 1000L;


    private SessionSnapshot snapshot = null;







    private volatile boolean gamePaused = false;






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






    private final List<String> serverPlacedBlocks =
            java.util.Collections.synchronizedList(new ArrayList<>());





    public GameServer() {
        this.coreHealth = new int[CORE_COUNT];
        for (int i = 0; i < CORE_COUNT; i++) this.coreHealth[i] = CORE_MAX_HEALTH;
        this.architectOverride = false;
        this.victoryState = VictoryState.IN_PROGRESS;
    }





    public static void main(String[] args) {
        System.out.println("Lumen Architect Server starting on port " + PORT + "...");
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(PORT);
            serverSocket.setReuseAddress(true);

            while (true) {
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

    private static void closeQuietly(ServerSocket s) {
        if (s != null && !s.isClosed()) {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    private static void closeQuietly(Socket s) {
        if (s != null && !s.isClosed()) {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    private ObjectInputStream readHello(Socket sock) {
        ObjectInputStream in = null;
        try {
            sock.setSoTimeout(HELLO_TIMEOUT_MS);
            in = new ObjectInputStream(sock.getInputStream());
            Object pkt = in.readObject();
            if (pkt instanceof NetworkProtocol.StringPacket) {
                String msg = ((NetworkProtocol.StringPacket) pkt).message;
                if (msg != null && msg.startsWith(Protocol.RECONNECT_HELLO + "|")) {



                    sock.setSoTimeout(0);
                    return in;
                }
            }
        } catch (IOException | ClassNotFoundException e) {

        }



        try { sock.setSoTimeout(0); } catch (IOException ignored) {}
        return null;
    }

    private static final int HELLO_TIMEOUT_MS = 3000;

    public void startSession(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
        try {






            try { serverSocket.setSoTimeout(0); }
            catch (IOException ignored) {}





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
            outs[SLOT_0].flush();
            clientConnected[SLOT_0] = true;

            outs[SLOT_0].writeObject(new NetworkProtocol.RoleAssignmentPacket("LOBBY"));
            outs[SLOT_0].flush();


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


            this.bothConnected = true;


            Thread gameLoop = new Thread(this::runGameLoop, "ServerLoop");
            gameLoop.setDaemon(true);
            gameLoop.start();

            System.out.println("[Server] Both players connected. Starting game.");
            gameLoop.join();

        } catch (IOException e) {
            System.out.println("[Server] Session I/O error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {


            for (Socket s : sockets) closeQuietly(s);
            System.out.println("[Server] Session sockets closed.");
        }
    }











    private void runGameLoop() {
        try {
            while (victoryState == VictoryState.IN_PROGRESS && sessionActive) {
                long tickStart = System.currentTimeMillis();

                if (gamePaused) {



                    sharedQueue.clear();
                    broadcastState();
                } else {

                    Object packet;
                    while ((packet = sharedQueue.poll()) != null) {
                        processPacket(packet);
                    }




                    if (activeCutsceneId != null
                            && (System.currentTimeMillis() - cutsceneStartMs)
                                    > CUTSCENE_GUARD_MS) {
                        System.out.println("[Server] Cutscene guard fired — "
                                + "forcing resume of " + activeCutsceneId);
                        endCutscene(activeCutsceneId);
                    }






                    if (bossPhaseActive && lightBall != null) {
                        lightBall.step();
                    }

                    broadcastState();
                }


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












    private void processPacket(Object packet) {
        if (packet instanceof NetworkProtocol.PlayerStatePacket) {
            NetworkProtocol.PlayerStatePacket p = (NetworkProtocol.PlayerStatePacket) packet;
            latestPlayerState = p;

            lastKnownWandererX = p.x;
            lastKnownWandererY = p.y;
            lastKnownWandererHealth = p.health;




            if (p.health <= 0 && bossPhaseActive
                    && victoryState == VictoryState.IN_PROGRESS
                    && pendingVictoryState == null) {
                pendingVictoryState = VictoryState.APPRENTICE_WIN;
                startCutscene(CutsceneID.ARCHITECT_VICTORY.name());
            }

        } else if (packet instanceof NetworkProtocol.FragmentCollectedPacket) {
            NetworkProtocol.FragmentCollectedPacket f = (NetworkProtocol.FragmentCollectedPacket) packet;

            if (serverCollectedFragments.add(f.fragmentID)) {
                broadcastToAll(f);
            }

        } else if (packet instanceof NetworkProtocol.CoreHitPacket) {
            NetworkProtocol.CoreHitPacket h = (NetworkProtocol.CoreHitPacket) packet;
            handleCoreHit(h.coreIndex);

        } else if (packet instanceof NetworkProtocol.StringPacket) {


            String msg = ((NetworkProtocol.StringPacket) packet).message;
            if (msg != null) updateLastKnownFromMessage(msg);
            broadcastToAll(packet);
        }
    }









    private void updateLastKnownFromMessage(String msg) {
        try {
            String[] p = msg.split("\\|");
            if (p.length == 0) return;
            switch (p[0]) {
                case Protocol.PLAYER_POS:

                    if (p.length >= 4 && "WANDERER".equals(p[1])) {
                        lastKnownWandererX = Float.parseFloat(p[2]);
                        lastKnownWandererY = Float.parseFloat(p[3]);
                    }
                    break;
                case Protocol.LIGHT_UPDATE:

                    if (p.length >= 4) {
                        lastKnownLightX = Integer.parseInt(p[1]);
                        lastKnownLightY = Integer.parseInt(p[2]);
                        lastKnownLightRadius = Integer.parseInt(p[3]);
                    }
                    break;
                case Protocol.PLACE_BLOCK:

                    if (p.length >= 4) {
                        serverPlacedBlocks.add(p[1] + "|" + p[2] + "|" + p[3]);
                        if (lastKnownBlockBudget > 0) lastKnownBlockBudget--;
                        lastKnownBlockType = p[1];
                    }
                    break;
                case Protocol.REMOVE_BLOCK:

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

        }
    }









    private static String actFromLevel(int lvl) {
        if (lvl == 1) return "ACT1";
        if (lvl == 2) return "ACT2";
        if (lvl == 3) return "ACT3";
        return "ACT3";
    }







    private void broadcast(String message) {
        broadcastToAll(new NetworkProtocol.StringPacket(message));
    }

















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








    private void endCutscene(String id) {
        if (id == null || !id.equals(activeCutsceneId)) return;
        broadcastToAll(new NetworkProtocol.CutscenePacket(id, false));
        System.out.println("[Server] Cutscene end broadcast: " + id);
        this.activeCutsceneId = null;
        this.cutsceneAcked[0] = false;
        this.cutsceneAcked[1] = false;
        this.cutsceneStartMs  = 0L;




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






        this.lightBall = new LightBall(ARENA_W / 2f, ARENA_H / 2f);
        this.bossPhaseActive = true;



        broadcast(Protocol.BOSS_ARENA + "|" + bossArenaSeed);





        startCutscene(CutsceneID.ARCHITECT_SPEAKS.name());
    }















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

        }
    }















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

































    private synchronized void handleAttackMessage(String msg, int slot) {
        if (!bossPhaseActive) return;
        if (!architectOverride) return;
        if (slot < 0 || slot >= 2) return;
        if (!"APPRENTICE".equals(roleForSlot(slot))) {

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

            return;
        }
        long now = System.currentTimeMillis();




        if (now < architectStunnedUntilMs) {
            return;
        }
        if (now < attackCooldownUntilMs[typeIdx]) {

            return;
        }

        int x, y;
        try {
            x = Integer.parseInt(parts[2]);
            y = Integer.parseInt(parts[3]);
        } catch (NumberFormatException ignored) {
            return;
        }


        attackCooldownUntilMs[typeIdx] = now + ATTACK_COOLDOWN_MS[typeIdx];
        broadcast(Protocol.BOSS_ATK + "|" + type + "|" + x + "|" + y + "|" + now);
        System.out.println("[Server] Boss attack dispatched: " + type
                + " at (" + x + ", " + y + ")");





        maybeRollStunOpportunity(now);
    }











    private void maybeRollStunOpportunity(long now) {
        if (now < stunOpportunityCooldownUntilMs) return;

        if (now < architectStunnedUntilMs) return;
        int faithful = (latestPlayerState != null) ? latestPlayerState.faithful : 0;
        faithful = Math.max(0, Math.min(5, faithful));
        double p = 0.10 + 0.08 * faithful;
        if (stunRng.nextDouble() >= p) return;
        stunOpportunityCooldownUntilMs = now + STUN_OPPORTUNITY_COOLDOWN_MS;
        broadcast(Protocol.STUN_OPPORTUNITY + "|"
                + StunMinigame.OPPORTUNITY_DURATION_MS + "|" + faithful);
        System.out.println("[Server] Stun opportunity broadcast"
                + " (faithful=" + faithful
                + ", P=" + String.format("%.2f", p) + ")");
    }













    private synchronized void handleStunResultMessage(String msg, int slot) {
        if (!bossPhaseActive) return;
        if (slot < 0 || slot >= 2) return;
        if (!"WANDERER".equals(roleForSlot(slot))) return;
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

        broadcast(Protocol.STUN_RESULT + "|" + success);
    }














    private void handleCoreHit(int coreIndex) {
        if (coreIndex < 0 || coreIndex >= CORE_COUNT) return;
        if (coreHealth[coreIndex] <= 0) return;

        coreHealth[coreIndex]--;




        broadcast(Protocol.CORE_DAMAGED + "|" + coreIndex);

        if (coreHealth[coreIndex] <= 0) {
            destroyedCoreCount++;


            if (destroyedCoreCount == 1) {
                architectOverride = true;
            }


            broadcastToAll(new NetworkProtocol.CoreStatePacket(coreHealth.clone()));







            String cutsceneName = "CORE_" + (coreIndex + 1) + "_DESTROYED";
            try {
                CutsceneID cid = CutsceneID.valueOf(cutsceneName);
                startCutscene(cid.name());
            } catch (IllegalArgumentException ex) {
                System.out.println("[Server] No cutscene mapping for " + cutsceneName
                        + " — skipping trigger.");
            }




            if (destroyedCoreCount >= CORE_COUNT) {
                architectOverride = false;
                pendingVictoryState = VictoryState.WANDERER_WIN;
            }
        }
    }










    public synchronized void broadcastState() {




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









    private synchronized void broadcastToAll(Object obj) {
        for (int i = 0; i < 2; i++) {
            if (clientConnected[i] && outs[i] != null) {
                try {
                    outs[i].writeObject(obj);
                    outs[i].flush();


                    outs[i].reset();
                } catch (IOException e) {
                    System.out.println("Write failed for client slot " + i
                            + " (" + roleForSlot(i) + "): " + e.getMessage());
                    clientConnected[i] = false;
                }
            }
        }
    }









    private String roleForSlot(int slot) {
        if (slot < 0 || slot >= lobbySelectedRole.length) return null;
        return lobbySelectedRole[slot];
    }





    private int slotForRole(String role) {
        if (role == null) return -1;
        for (int i = 0; i < lobbySelectedRole.length; i++) {
            if (role.equals(lobbySelectedRole[i])) return i;
        }
        return -1;
    }


    private boolean apprenticeLevelReady() {
        int slot = slotForRole("APPRENTICE");
        return slot >= 0 && levelReady[slot];
    }














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

                int other = 1 - senderSlot;
                if (rolePart.equals(lobbySelectedRole[other])) {
                    broadcastLobbyState();
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
            snap.placedBlocks = new ArrayList<>(serverPlacedBlocks);
        }
        snap.coreHealth  = coreHealth.clone();
        snap.capturedAt  = System.currentTimeMillis();
        return snap;
    }












    private synchronized void handlePartialDisconnect(int disconnectedSlot) {
        if (vacantRole != null) return;
        if (!sessionActive)     return;





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



        closeQuietly(sockets[disconnectedSlot]);
        sockets[disconnectedSlot] = null;
        outs[disconnectedSlot]    = null;


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
                sessionActive = false;
                return;
            }
        }

        startReconnectAcceptor();
    }







    private void startReconnectAcceptor() {
        Thread t = new Thread(() -> {
            long lastTimerBroadcast = 0;
            try {
                serverSocket.setSoTimeout(2000);
                while (gamePaused && sessionActive && vacantRole != null) {
                    try {
                        Socket newSocket = serverSocket.accept();








                        ObjectInputStream newIn = readHello(newSocket);
                        if (newIn == null) {
                            System.out.println("[Server] Reconnect acceptor "
                                    + "rejected invalid hello from "
                                    + newSocket.getInetAddress()
                                    + " — continuing to wait.");
                            closeQuietly(newSocket);
                            continue;
                        }
                        System.out.println("[Server] Reconnecting client accepted from "
                                + newSocket.getInetAddress());
                        handleReconnectingClient(newSocket, newIn);
                        return;
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











    private synchronized void handleReconnectingClient(Socket newSocket,
                                                       ObjectInputStream newIn) {
        if (vacantRole == null || snapshot == null) {
            closeQuietly(newSocket);
            return;
        }





        String            reconnectingRole = vacantRole;
        SessionSnapshot   snap             = snapshot;
        int               slot             = slotForRole(reconnectingRole);
        if (slot < 0) {


            System.out.println("[Server] No slot matches reconnecting role '"
                    + reconnectingRole + "' — closing reconnect socket.");
            closeQuietly(newSocket);
            return;
        }
        int               other            = 1 - slot;




        vacantRole            = null;
        partialDisconnectTime = -1;
        snapshot              = null;

        try {
            sockets[slot] = newSocket;
            outs[slot] = new ObjectOutputStream(newSocket.getOutputStream());
            outs[slot].flush();


            outs[slot].writeObject(new NetworkProtocol.RoleAssignmentPacket(reconnectingRole));
            outs[slot].flush();


            sendSnapshot(outs[slot], snap);

            clientConnected[slot] = true;



            Thread handler = new Thread(
                    new ClientHandler(newSocket, newIn, slot),
                    "ClientHandler-" + reconnectingRole + "-RC");
            handler.setDaemon(true);
            handler.start();


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




            gamePaused = false;

            System.out.println("[Server] Reconnect complete: " + reconnectingRole
                    + " resumed. Session unpaused.");

        } catch (IOException e) {
            System.out.println("[Server] Failed to wire reconnecting client: "
                    + e.getMessage());
            closeQuietly(newSocket);
            clientConnected[slot] = false;



            sessionActive = false;
        }
    }











    private void sendSnapshot(ObjectOutputStream out, SessionSnapshot snap)
            throws IOException {


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












    private class ClientHandler implements Runnable {


        @SuppressWarnings("unused") private final Socket socket;


        private final int slot;


        private ObjectInputStream in;














        ClientHandler(Socket socket, ObjectInputStream in, int slot) {
            this.socket = socket;
            this.in = in;
            this.slot = slot;
        }


        private String currentRole() {
            String r = lobbySelectedRole[slot];
            return r != null ? r : ("SLOT_" + slot);
        }






        @Override
        public void run() {
            try {



                while (clientConnected[slot] && sessionActive) {
                    Object packet = in.readObject();



                    if (packet instanceof NetworkProtocol.StringPacket) {
                        String msg = ((NetworkProtocol.StringPacket) packet).message;
                        if (msg != null
                                && (msg.startsWith(Protocol.LOBBY_HOVER + "|")
                                 || msg.startsWith(Protocol.LOBBY_SELECT + "|")
                                 || msg.startsWith(Protocol.LOBBY_CANCEL + "|"))) {
                            handleLobbyMessage(msg, slot);
                            continue;
                        }
                        if (msg != null
                                && (msg.startsWith("CUT_TRIGGER|")
                                 || msg.startsWith(Protocol.CUTSCENE_ACK + "|"))) {
                            handleCutsceneMessage(msg, slot);
                            continue;
                        }
                        if (msg != null && Protocol.BOSS_ENTER.equals(msg)) {





                            handleBossEnter(slot);
                            continue;
                        }
                        if (msg != null && msg.startsWith(Protocol.ATTACK + "|")) {









                            handleAttackMessage(msg, slot);
                            continue;
                        }
                        if (msg != null && msg.startsWith(Protocol.LIGHT_TARGET + "|")) {









                            handleLightTargetMessage(msg, slot);
                            continue;
                        }
                        if (msg != null && msg.startsWith(Protocol.ALTAR_CHOICE + "|")) {





                            handleAltarChoice(msg, slot);
                            continue;
                        }
                        if (msg != null && msg.startsWith(Protocol.STUN_RESULT + "|")) {




                            handleStunResultMessage(msg, slot);
                            continue;
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








        private void handleClientDisconnect() {
            int other = 1 - slot;


            if (clientConnected[other] && vacantRole == null && sessionActive) {
                handlePartialDisconnect(slot);
            } else {


                sessionActive = false;
            }
        }
    }










    public int[] getCoreHealth() {
        return coreHealth;
    }







    public boolean isArchitectOverride() {
        return architectOverride;
    }








    public void setArchitectOverride(boolean architectOverride) {
        this.architectOverride = architectOverride;
    }






    public VictoryState getVictoryState() {
        return victoryState;
    }










    private final Socket[] sockets = new Socket[2];
}
