














































import java.awt.*;
import java.io.*;
import java.net.*;
import javax.swing.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
public class GameStarter {





    private static final String LOOP_THREAD_NAME = "GameLoop";
    private static final String NET_THREAD_NAME = "NetworkIO";
    private static final long TICK_MS = 16L;
    private static final long TICK_NS = TICK_MS * 1_000_000L;
    private static final long NET_SEND_INTERVAL_MS = 16L;


    private static final long RECONNECT_DELAY_MS = 3000L;








    private static final int  RECONNECT_MAX_ATTEMPTS  = 5;
    private static final long RECONNECT_MAX_DELAY_MS  = 30_000L;


    private static final int PLAYER_SPAWN_X = 100;


    private static final int PLAYER_SPAWN_Y = 400;


    private static final long MENU_RETURN_DELAY_MS = 2000L;










    private static GameStarter instance;






    public static GameStarter getInstance() {
        return instance;
    }









    private Socket socket;





    private String role;








    private volatile ObjectOutputStream networkOut;






    private volatile ObjectInputStream networkIn;







    private final Object sendLock = new Object();





    private String lastHost;




    private int lastPort;







    private volatile boolean connectionLost;






    private volatile boolean connectionFailed;







    private volatile boolean victoryHandled;







    private volatile boolean gameLoopStarted = false;







    private volatile int lastHandledLevel = -1;







    private boolean levelReady = false;


    private int levelReadyDelay = 0;


    private static final int LEVEL_READY_FRAMES = 10;







    private int lightTargetTickCounter = 0;







    private static final int LIGHT_TARGET_TICK_INTERVAL = 3;












    private volatile boolean reconnecting = false;








    private final List<String> pendingSnapshotMessages =
            java.util.Collections.synchronizedList(new ArrayList<>());










    private volatile GameFrame gameFrame;






    private volatile GameCanvas canvas;











    private volatile boolean running;


    private volatile boolean paused;


    private Thread gameLoopThread;









    private final Player player;


    private final Physics physicsEngine;


    private final InputRouter inputRouter;





    private final KeyBindings keyBindings;






    private final List<GameElement> elements;






    private final List<Platform> placedBlocks;














    private final List<BossAttack> bossAttacks = new CopyOnWriteArrayList<>();






    private boolean altarActive = false;


    private static final long FAITHFUL_CYCLE_INTERVAL_MS = 20_000L;


    private long faithfulCycleLastMs = 0L;













    private final StunMinigame stunMinigame = new StunMinigame();






    private volatile long architectStunnedBannerUntilMs = 0L;





    private int altarActiveTrigger = -1;





    private final LevelState levelState;


















    public GameStarter() {
        instance           = this;
        this.player        = new Player(PLAYER_SPAWN_X, PLAYER_SPAWN_Y);
        this.physicsEngine = new Physics();
        this.inputRouter   = new InputRouter();
        this.keyBindings   = new KeyBindings();
        this.elements      = new CopyOnWriteArrayList<>();
        this.placedBlocks  = new CopyOnWriteArrayList<>();
        GameSession.getInstance().setPlacedBlocks(this.placedBlocks);
        this.levelState    = new LevelState();
        this.running       = false;
    }















    public static void main(String[] args) {
        GameStarter starter = new GameStarter();



        SwingUtilities.invokeLater(() -> {
            starter.gameFrame = new GameFrame();
            starter.gameFrame.setVisible(true);
        });

    }



















    public boolean connectToServer(String host, int port) {
        this.lastHost = host;
        this.lastPort = port;

        try {
            socket = new Socket(host, port);








            socket.setSoTimeout(0);

            networkOut = new ObjectOutputStream(socket.getOutputStream());
            networkOut.flush();







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



            reconnecting = role != null
                    && !"LOBBY".equals(role)
                    && ("WANDERER".equals(role) || "APPRENTICE".equals(role));
            if (reconnecting) {
                System.out.println("[connectToServer] Reconnecting client — role=" + role);
            }


            GameSession.getInstance().setSendCallback(
                msg -> sendPacket(new NetworkProtocol.StringPacket(msg)));

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }











    public void startNetworkThread() {
        Thread netThread = new Thread(this::runNetworkIO, NET_THREAD_NAME);
        netThread.setDaemon(true);
        netThread.start();
    }














    private void runNetworkIO() {
        if (networkOut == null || networkIn == null) return;




        try {
            socket.setSoTimeout(0);
        } catch (IOException ignored) {}


        spinWaitForBothConnected:
        while (true) {
            try {
                Object pkt = networkIn.readObject();
                if (pkt instanceof NetworkProtocol.StringPacket) {








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
            } catch (ClassNotFoundException e) {
                System.err.println("[NetworkIO] Unknown packet class during wait: " + e.getMessage());
            } catch (IOException e) {
                System.err.println("[NetworkIO] Connection lost while waiting for both players.");
                return;
            }
        }



        Thread senderThread = new Thread(this::runNetworkSender,
                NET_THREAD_NAME + "-Sender");
        senderThread.setDaemon(true);
        senderThread.start();


        while (running || !victoryHandled) {
            try {
                Object pkt = networkIn.readObject();
                handleInboundPacket(pkt);
            } catch (ClassNotFoundException e) {
                System.err.println("[NetworkIO] Unknown packet class: " + e.getMessage());
            } catch (IOException e) {

                if (!handleDisconnect()) {
                    return;
                }

            }
        }
    }









    private void runNetworkSender() {
        long lastSendTime = 0L;
        while (running || !victoryHandled) {
            long now = System.currentTimeMillis();
            if (now - lastSendTime >= NET_SEND_INTERVAL_MS) {
                lastSendTime = now;
                try {
                    sendOutboundState();
                } catch (Exception e) {


                }
            }
            try {
                Thread.sleep(4L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

















    private void sendOutboundState() {
        if (GameSession.getInstance().isWanderer()) {
            sendPacket(new NetworkProtocol.PlayerStatePacket(
                    player.getX(),
                    player.getY(),
                    player.getHealth(),
                    player.getAnimState(),
                    player.getFaithful()
            ));




            int coreHit = player.consumePendingCoreHit();
            if (coreHit >= 0) {
                sendPacket(new NetworkProtocol.CoreHitPacket(coreHit));
            }
        }
    }









    private void sendPacket(Object pkt) {
        synchronized (sendLock) {
            ObjectOutputStream out = networkOut;
            if (out == null) return;
            try {
                out.writeObject(pkt);
                out.flush();



                out.reset();
            } catch (IOException e) {


            }
        }
    }











    private void handleInboundPacket(Object pkt) {
        if (pkt instanceof NetworkProtocol.ServerStatePacket) {
            applyServerState((NetworkProtocol.ServerStatePacket) pkt);

        } else if (pkt instanceof NetworkProtocol.VictoryPacket) {
            handleVictory(((NetworkProtocol.VictoryPacket) pkt).result);

        } else if (pkt instanceof NetworkProtocol.FragmentCollectedPacket) {



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










    private void applyServerState(NetworkProtocol.ServerStatePacket s) {
        GameSession session = GameSession.getInstance();

        if (session.isApprentice()) {

            if (s.playerState != null) {
                levelState.setWandererHealth(s.playerState.health);
                if (player != null) {
                    player.setFaithful(s.playerState.faithful);
                }
            }
        }


        if (s.coreState != null) {
            levelState.coreHealth = s.coreState.health.clone();
        }



        GameSession.getInstance().setArchitectOverride(s.architectOverride);







        if (canvas != null) {
            canvas.setBossLightWorldPosition(s.lightX, s.lightY);
        }


        if (s.victoryState != GameServer.VictoryState.IN_PROGRESS) {
            handleVictory(s.victoryState);
        }
    }
















    private void handleVictory(GameServer.VictoryState result) {
        if (victoryHandled) return;
        victoryHandled = true;
        running = false;


        SwingUtilities.invokeLater(() -> {
            if (canvas != null) {
                canvas.setVictoryResult(result);
            }
            levelState.currentPhase = LevelState.GamePhase.END_SCREEN;
            if (canvas != null) canvas.repaint();
            System.out.println("[GameStarter] End screen — " + result);
        });
    }














    private boolean handleDisconnect() {
        connectionLost = true;
        if (canvas != null) {
            canvas.setConnectionOverlay("Connection lost. Waiting...");
        }







        long delay = RECONNECT_DELAY_MS;
        for (int attempt = 1; attempt <= RECONNECT_MAX_ATTEMPTS; attempt++) {


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
                return false;
            }


            try {
                socket     = new Socket(lastHost, lastPort);
                networkOut = new ObjectOutputStream(socket.getOutputStream());
                networkOut.flush();





                String priorRole = GameSession.getInstance().role;
                if (priorRole == null || "LOBBY".equals(priorRole)) {
                    priorRole = "UNKNOWN";
                }
                networkOut.writeObject(new NetworkProtocol.StringPacket(
                        Protocol.RECONNECT_HELLO + "|RECONNECT|" + priorRole));
                networkOut.flush();
                networkOut.reset();

                networkIn  = new ObjectInputStream(socket.getInputStream());


                socket.setSoTimeout(0);

                connectionLost = false;
                if (canvas != null) {
                    canvas.setConnectionOverlay(null);
                }
                System.out.println("[Reconnect] Succeeded on attempt " + attempt);
                return true;

            } catch (IOException e) {
                System.out.println("[Reconnect] Attempt " + attempt
                        + " failed: " + e.getMessage());

                delay = Math.min(delay * 2, RECONNECT_MAX_DELAY_MS);

            }
        }


        connectionFailed = true;
        if (canvas != null) {
            canvas.setConnectionOverlay(
                "Connection failed after " + RECONNECT_MAX_ATTEMPTS
                + " attempts. Please restart.");
        }
        running = false;
        return false;
    }





















    public void startGameLoop() {
        gameLoopThread = new Thread(() -> {




            while (gameFrame == null) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }



            canvas = gameFrame.getGameCanvas();


            canvas.setLevelState(levelState);
            canvas.setPlayer(player);
            canvas.setElements(elements);


            canvas.setBossAttacks(bossAttacks);


            canvas.setStunMinigame(stunMinigame);




            inputRouter.setGameCanvas(canvas);


            inputRouter.setStunMinigame(stunMinigame);





            try {
                SwingUtilities.invokeAndWait(() -> {
                    keyBindings.registerBindings(canvas);




                    inputRouter.registerCutsceneBindings(canvas);


                    CutsceneRenderer.get().setLevelState(levelState);


                    if (GameSession.getInstance().isApprentice()) {
                        inputRouter.registerApprenticeKeyBindings(canvas);
                    }



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













            canvas.setLevelState(levelState);

            if (reconnecting) {
                System.out.println("[GameLoop] Reconnecting — applying "
                        + pendingSnapshotMessages.size() + " buffered snapshot messages.");

                if (GameSession.getInstance().isApprentice()) {
                    try {
                        SwingUtilities.invokeAndWait(() ->
                                inputRouter.registerApprenticeKeyBindings(canvas));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }

                List<String> drained;
                synchronized (pendingSnapshotMessages) {
                    drained = new ArrayList<>(pendingSnapshotMessages);
                    pendingSnapshotMessages.clear();
                }
                for (String msg : drained) {
                    handleMessage(msg);
                }
            } else {
                levelState.currentPhase = LevelState.GamePhase.LOBBY;
                System.out.println("[GameLoop] Lobby phase entered - waiting for role selection.");
            }












            running = true;
            long nextTick = System.nanoTime();

            while (running) {






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


                KeyBindings.PlayerInputState input = keyBindings.getInputState();
                if (input.pausePressed) {
                    input.pausePressed = false;
                    togglePause();
                }




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





                if (!GameSession.getInstance().isApprentice()) {


                    inputRouter.routeKeyEvent(input, player, physicsEngine);


                    player.updateRespawn(TICK_MS);




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


                    if (!player.isDead()) {
                        physicsEngine.update(TICK_MS, player, elements);
                    }


                    player.update(TICK_MS);

                }










                if (GameSession.getInstance().isApprentice()) {
                    MouseApprentice ma = MouseApprentice.getInstance();
                    boolean bossPhase =
                            levelState.currentPhase == LevelState.GamePhase.BOSS;
                    if (bossPhase) {
                        lightTargetTickCounter++;
                        if (lightTargetTickCounter >= LIGHT_TARGET_TICK_INTERVAL) {
                            lightTargetTickCounter = 0;




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



                if (canvas != null && GameSession.getInstance().isApprentice()) {
                    Point mp = canvas.getMousePosition2();
                    if (mp != null) {
                        canvas.setLightSourcePosition(mp);
                    }
                }


                if (GameSession.getInstance().isApprentice()) {
                    if (levelState.remoteWandererX >= 0) {
                        player.setPosition(levelState.remoteWandererX, levelState.remoteWandererY);
                        player.setAnimationState(levelState.remoteWandererState);
                    }
                }






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

                                if (p2 instanceof PhantomBlock) {
                                    float intensity = 0f;
                                    if (lit && litRadius > 0) {
                                        intensity = 1.0f - (float)(dist / (litRadius + 20));
                                        if (intensity < 0.4f) intensity = 0.4f;
                                        if (intensity > 1.0f) intensity = 1.0f;
                                    }
                                    ((PhantomBlock) p2).setLightIntensity(intensity);
                                }
                            }
                        }

                        if (player != null) {
                            for (GameElement el : elements) {
                                if (el instanceof CorruptedWall) {
                                    ((CorruptedWall) el).checkTrigger(player);
                                }
                            }
                        }
                    }
                }


                if (canvas != null
                        && (levelState.currentPhase == LevelState.GamePhase.ACT2
                            || levelState.currentPhase == LevelState.GamePhase.ACT3)) {
                    if (GameSession.getInstance().isApprentice()) {
                        MouseApprentice ma = MouseApprentice.getInstance();
                        boolean lightOn = ma.isLightActive();
                        int radius = ma.getLightRadius();
                        GameSession.getInstance().updateBattery(lightOn, radius);

                        canvas.setLightRadius(radius);
                    }
                }


                for (GameElement el : elements) {
                    if (el.isActive()
                            || (el instanceof Platform && !((Platform) el).fullyGone)) {
                        el.update(TICK_MS);
                    }
                }










                if (!bossAttacks.isEmpty()) {
                    List<BossAttack> expired = new ArrayList<>();
                    for (BossAttack ba : bossAttacks) {
                        ba.update(TICK_MS);
                        if (ba.isExpired() || !ba.isActive()) expired.add(ba);
                    }
                    if (!expired.isEmpty()) bossAttacks.removeAll(expired);
                }




                if (GameSession.getInstance().isWanderer()) {
                    List<Platform> goneBlocks = new ArrayList<>();
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



                if (!GameSession.getInstance().isApprentice()) {
                    checkFragmentCollection();
                    checkPortalCollision();
                    checkWinLoss();
                    checkAltarTrigger();
                    checkFaithfulCycle();
                    checkHazardContact();

                    if (altarActive && canvas != null) {
                        String choice = canvas.getAndClearPendingAltarChoice();
                        if (choice != null) {
                            GameSession.getInstance().sendToServer(
                                Protocol.ALTAR_CHOICE + "|" + altarActiveTrigger + "|" + choice);
                        }
                    }



                    long nowMsStun = System.currentTimeMillis();
                    stunMinigame.tick(nowMsStun);
                    int stunResult = stunMinigame.consumePendingResult();
                    if (stunResult == 0 || stunResult == 1) {
                        GameSession.getInstance().sendToServer(
                                Protocol.STUN_RESULT + "|" + stunResult);
                    }
                }


                canvas.repaint();


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


                    nextTick = System.nanoTime();
                }
            }

        }, LOOP_THREAD_NAME);

        gameLoopThread.setDaemon(true);
        gameLoopThread.start();
    }











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

                    if (canvas != null && GameSession.getInstance().isWanderer()) {
                        int effectiveRadius = la ? lr : 0;
                        canvas.setLightSource(new java.awt.Point(lx, ly), effectiveRadius, 0f);
                    }
                }
                break;

            case Protocol.LEVEL_CHANGE:
                if (parts.length >= 2) {
                    int newLevel = Integer.parseInt(parts[1]);



                    if (newLevel != lastHandledLevel) {
                        lastHandledLevel = newLevel;
                        loadLevel(newLevel, false);
                        GameSession.getInstance().resetBlockBudget();
                    }
                }
                break;

            case Protocol.CLEAR_BLOCKS:
                clearAllBlocks();
                break;

            case Protocol.BOSS_ARENA:






                if (parts.length >= 2) {
                    try {
                        long seed = Long.parseLong(parts[1]);
                        enterBossArena(seed);
                    } catch (NumberFormatException ignored) {}
                }
                break;

            case Protocol.BOSS_ATK:





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






                if (parts.length >= 2 && canvas != null) {
                    try {
                        int coreIdx = Integer.parseInt(parts[1]);
                        canvas.showNotification("CORE " + (coreIdx + 1) + " HIT");
                    } catch (NumberFormatException ignored) {}
                }
                break;

            case Protocol.ALTAR_RESULT:



                if (parts.length >= 3) {
                    applyAltarResult(parts[2]);
                }
                break;

            case Protocol.STUN_OPPORTUNITY:





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















            case Protocol.RADIANT_ACTIVE:
                if (parts.length >= 2 && player != null) {
                    try {
                        long endMs = Long.parseLong(parts[1]);
                        player.setRadiantActiveUntilMs(endMs);
                    } catch (NumberFormatException ignored) {}
                }
                break;

            case Protocol.LOBBY_STATE:

                if (canvas != null) {
                    LobbyState ls = new LobbyState();
                    ls.applyMessage(msg);
                    canvas.setLobbyState(ls);
                    canvas.repaint();
                }
                break;

            case Protocol.LOBBY_START:

                if (parts.length >= 2) {
                    String assignedRole = parts[1];
                    System.out.println("[GameStarter] LOBBY_START → role=" + assignedRole);
                    role = assignedRole;
                    GameSession.getInstance().setRole(assignedRole);


                    if (GameSession.getInstance().isApprentice() && canvas != null) {
                        SwingUtilities.invokeLater(() -> {
                            inputRouter.registerApprenticeKeyBindings(canvas);
                            System.out.println("[GameStarter] Apprentice key bindings registered after lobby.");
                        });
                    }


                    lastHandledLevel = 1;



                    loadLevel(1, false);
                    GameSession.getInstance().resetBlockBudget();
                }
                break;





            case Protocol.PARTNER_DISCONNECTED:

                if (parts.length >= 2) {
                    String dcRole = parts[1];
                    System.out.println("[GameStarter] PARTNER_DC → " + dcRole);
                    if (canvas != null) {
                        canvas.setPartnerDCRole(dcRole);





                        canvas.setReconnectCountdown(90);
                    }

                    prePausePhase = levelState.currentPhase;
                    levelState.currentPhase = LevelState.GamePhase.PAUSED_WAITING;
                    if (canvas != null) canvas.repaint();
                }
                break;

            case Protocol.PARTNER_RECONNECTED:

                System.out.println("[GameStarter] PARTNER_RC received — resuming.");
                if (canvas != null) {
                    canvas.setPartnerDCRole(null);
                    canvas.setReconnectCountdown(-1);
                }
                break;

            case Protocol.RECONNECT_TIMER:

                if (parts.length >= 2) {
                    try {
                        long secs = Long.parseLong(parts[1]);
                        if (canvas != null) canvas.setReconnectCountdown(secs);
                    } catch (NumberFormatException ignored) {}
                }
                break;

            case Protocol.SESSION_EXPIRED:

                System.out.println("[GameStarter] SESSION_EXP — reconnect window expired.");
                if (canvas != null) {
                    canvas.setPartnerDCRole(null);
                    canvas.setReconnectCountdown(-1);
                    canvas.showNotification("Session expired — partner did not return.");
                }
                running = false;
                break;

            case Protocol.GAME_PAUSE:

                if (levelState.currentPhase != LevelState.GamePhase.PAUSED_WAITING) {
                    prePausePhase = levelState.currentPhase;
                    levelState.currentPhase = LevelState.GamePhase.PAUSED_WAITING;
                }
                break;

            case Protocol.GAME_RESUME:


                System.out.println("[GameStarter] GAME_RESUME — restoring phase "
                        + prePausePhase);
                if (prePausePhase != null
                        && prePausePhase != LevelState.GamePhase.PAUSED_WAITING) {
                    levelState.currentPhase = prePausePhase;
                }
                prePausePhase = null;
                break;

            case Protocol.LOBBY_VACANT:

                if (parts.length >= 2 && canvas != null) {
                    LobbyState ls = (canvas.getLobbyState() != null)
                            ? canvas.getLobbyState() : new LobbyState();
                    ls.isReconnectSession = true;
                    ls.vacantRole         = parts[1];
                    canvas.setLobbyState(ls);
                    canvas.repaint();
                }
                break;





            case Protocol.SNAPSHOT_BEGIN:
                applySnapshotBegin(parts);
                break;

            case Protocol.SNAPSHOT_BLOCK:

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


                break;

            default:

                break;
        }
    }







    private LevelState.GamePhase prePausePhase;










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


            clearAllBlocks();


            lastHandledLevel = level;
            loadLevel(level, false);


            if (player != null) {
                player.setX((int) wandX);
                player.setY((int) wandY);
                player.setVelX(0);
                player.setVelY(0);
                player.setHealth(wandHealth);


            }
            levelState.setWandererPosition(wandX, wandY);
            levelState.setWandererHealth(wandHealth);


            levelState.setLightPosition(lightX, lightY);
            levelState.setLightRadius(lightRadius);
            levelState.setLightActive(lightActive);
            if (canvas != null && GameSession.getInstance().isWanderer()) {
                int effectiveRadius = lightActive ? lightRadius : 0;
                canvas.setLightSource(new java.awt.Point(lightX, lightY),
                        effectiveRadius, 0f);
            }


            GameSession.getInstance().setBlockBudget(blockBudget);
            GameSession.getInstance().setCurrentBlockType(blockType);
            GameSession.getInstance().setLightBattery(lightBat);
            GameSession.getInstance().setCurrentAct(act);

        } catch (NumberFormatException e) {
            System.out.println("[GameStarter] SNAP_BEGIN parse error: " + e.getMessage());
        }
    }




























    private void spawnBossAttack(String type, int x, int y) {


        Player authoritativePlayer = GameSession.getInstance().isApprentice()
                ? null : player;
        BossAttack attack = null;
        switch (type) {
            case "SEARING_BEAM":
                attack = new SearingBeam(new Point(x, y), authoritativePlayer);
                break;
            case "BLOCK_RAIN": {


                List<Platform> plats = new ArrayList<Platform>();
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



                attack = new Shield(new ArrayList<Projectile>());
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










    private int inferBossGroundY() {
        int defaultGround = Camera.ARENA_H - Platform.TILE_SIZE;
        if (player != null && player.getY() > Camera.ARENA_H / 2) {

            return Math.min(defaultGround, player.getY() + Platform.TILE_SIZE);
        }
        return defaultGround;
    }






    private void spawnBlock(String type, int x, int y) {

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


    private void clearAllBlocks() {
        elements.removeAll(placedBlocks);
        placedBlocks.clear();
        System.out.println("[GameState] Block list cleared for new level.");
    }









    public void togglePause() {
        paused = !paused;
        if (canvas != null) {
            canvas.setPaused(paused);

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


    public boolean isPaused() {
        return paused;
    }









    private void checkPortalCollision() {


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





                    if (levelState.currentLevel >= 3) {
                        System.out.println("LEVEL COMPLETE — entering boss arena (P8.2)");
                        levelReady = false;
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

                        System.out.println("All levels complete — proceeding to boss");
                    }
                    break;
                }
            }
        }
    }









    public void loadLevel(int levelNum) {
        loadLevel(levelNum, true);
    }










    private void loadLevel(int levelNum, boolean broadcast) {
        elements.clear();
        placedBlocks.clear();



        levelReady = false;
        levelReadyDelay = 0;



        if (broadcast && GameSession.getInstance().isWanderer()) {
            lastHandledLevel = levelNum;
            GameSession.getInstance().sendToServer(Protocol.LEVEL_CHANGE + "|" + levelNum);
            GameSession.getInstance().sendToServer(Protocol.CLEAR_BLOCKS);
        }





        LevelRegistry.LoadResult result = LevelRegistry.load(levelNum, 0L);
        elements.addAll(result.elements);
        levelState.currentLevel = levelNum;






        if (player == null) {
            System.out.println("ERROR: player is null during level load");
            return;
        }


        player.setX(60);
        player.setY(652);
        player.setVelX(0);
        player.setVelY(0);
        player.setRespawn(60, 652);





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


        if (levelNum >= 2 && canvas != null) {
            canvas.setLightSourcePosition(new java.awt.Point(
                    player.getX() + Player.SPRITE_WIDTH  / 2,
                    player.getY() + Player.SPRITE_HEIGHT / 2));
        }

        System.out.println("Loaded level " + levelNum + " with " + elements.size() + " entities");
    }























    private void enterBossArena(long seed) {
        System.out.println("[BossArena] Entering boss arena with seed=" + seed);


        faithfulCycleLastMs = 0L;


        stunMinigame.forceClose();
        architectStunnedBannerUntilMs = 0L;
        if (canvas != null) canvas.setArchitectStunnedUntilMs(0L);





        elements.clear();
        placedBlocks.clear();
        LevelRegistry.LoadResult bossResult = LevelRegistry.load(4, seed);
        elements.addAll(bossResult.elements);



        levelReady = false;
        levelReadyDelay = 0;





        if (player != null) {


            final int spawnX = BossArenaGenerator.CENTER_X;
            final int spawnY = BossArenaGenerator.ARENA_H - BossArenaGenerator.BOSS_SPAWN_Y_OFFSET;
            player.setX(spawnX);
            player.setY(spawnY);
            player.setVelX(0);
            player.setVelY(0);
            player.setRespawn(spawnX, spawnY);
        }


        levelState.currentPhase = LevelState.GamePhase.BOSS;
        GameSession.getInstance().setCurrentAct("BOSS");




        if (player != null) {
            Camera.getInstance().snapTo(player.getX(), player.getY());
        } else {
            Camera.getInstance().reset();
        }



        System.out.println("[BossArena] Arena built with " + elements.size()
                + " entities; phase=BOSS");
    }










    public void stop() {
        running = false;
        if (gameLoopThread != null) {
            gameLoopThread.interrupt();
        }
    }


    public void stopGame() {
        running = false;
        System.out.println("Game stopped.");
    }


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

















    private void checkFragmentCollection() {
        for (GameElement el : elements) {
            if (!(el instanceof LoreFragment) || !el.isActive()) continue;
            LoreFragment frag = (LoreFragment) el;
            if (!frag.isCollected()
                    && player.getBounds().intersects(frag.getBounds())) {
                frag.collect();
                grantAbility(frag.getUnlock());
                player.addFaithful(1);

                player.triggerCollectFlash();
                if (canvas != null) {
                    canvas.showNotification(getFragmentNotificationText(frag));
                }

                sendPacket(new NetworkProtocol.FragmentCollectedPacket(frag.getFragmentID()));
            }
        }
    }



















    private void checkAltarTrigger() {
        if (altarActive) return;
        for (GameElement el : elements) {

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














    private void checkHazardContact() {
        if (player == null || player.isDead()) return;
        for (GameElement el : elements) {
            if (!el.isActive()) continue;
            if (el instanceof CorruptedSpike) {
                CorruptedSpike spike = (CorruptedSpike) el;
                if (player.getBounds().intersects(spike.getBounds())) {
                    spike.tryDamage(player);
                }
            } else if (el instanceof CorruptedWall) {
                CorruptedWall wall = (CorruptedWall) el;
                if (!wall.isDangerous()) continue;
                if (player.getBounds().intersects(wall.getBounds())) {
                    player.takeDamage(wall.getDamage());
                }
            }
        }
    }







    private void checkFaithfulCycle() {
        if (player == null) return;
        if (levelState.currentPhase != LevelState.GamePhase.BOSS) return;
        if (player.isDead()) return;
        long now = System.currentTimeMillis();
        if (faithfulCycleLastMs == 0L) {
            faithfulCycleLastMs = now;
            return;
        }
        if (now - faithfulCycleLastMs >= FAITHFUL_CYCLE_INTERVAL_MS) {
            player.addFaithful(1);
            faithfulCycleLastMs = now;
            if (canvas != null) canvas.showNotification("Survived an attack cycle — Faith +1");
        }
    }









    private void applyAltarResult(String choice) {
        if (player == null) return;
        if ("POWER_SURGE".equals(choice)) {
            player.addMaxHealth(10);
            if (canvas != null) canvas.showNotification("Fragment offered — Max HP +10");
        } else if ("SIGHT_RESTRICTION".equals(choice)) {
            player.addMaxHealth(20);
            player.setSightRestricted(true);
            player.addFaithful(1);
            if (canvas != null) canvas.showNotification("Sight Restricted — Max HP +20, Light -25%, Faith +1");
        }
        altarActive = false;
        altarActiveTrigger = -1;
        SwingUtilities.invokeLater(() -> {
            if (canvas != null) canvas.closeAltarOverlay();
        });
    }









    private void grantAbility(LoreFragment.AbilityUnlock unlock) {
        switch (unlock) {
            case MELEE:       player.setHasMelee(true);       break;
            case PROJECTILE:  player.setHasProjectile(true);  break;
            case DODGE:       player.setHasDodge(true);       break;
            case WALL_CLING:  player.setHasWallCling(true);   break;
            case SHADOW_DASH: player.setHasShadowDash(true);  break;



            case EMBER:
            case IRON:
                player.activateBoost(unlock);
                break;





            case RADIANT_COLLAPSE:
                GameSession.getInstance().setRadiantCollapseUnlocked(true);
                break;

            default:          break;
        }
    }





    private String getFragmentNotificationText(LoreFragment frag) {
        String base = "Fragment collected: " + frag.getFragmentID();
        if (frag.getUnlock() != LoreFragment.AbilityUnlock.NONE) {
            base += "  —  " + frag.getUnlock().name() + " unlocked!";
        }
        return base;
    }













    private void checkWinLoss() {

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


        if (levelState.currentPhase == LevelState.GamePhase.BOSS && !player.isAlive()) {
            handleLoss();
        }
    }






    private void handleWin() {
        if (!victoryHandled) {
            running = false;

        }
    }






    private void handleLoss() {
        if (!victoryHandled) {
            running = false;
        }
    }











    public Socket getSocket() {
        return socket;
    }







    public String getRole() {
        return role;
    }






    public GameFrame getGameFrame() {
        return gameFrame;
    }






    public Player getPlayer() {
        return player;
    }









    public List<Platform> getPlacedBlocks() {
        return placedBlocks;
    }







    public List<GameElement> getElements() {
        return elements;
    }






    public LevelState getLevelState() {
        return levelState;
    }







    public KeyBindings getKeyBindings() {
        return keyBindings;
    }







    public InputRouter getInputRouter() {
        return inputRouter;
    }






    public boolean isRunning() {
        return running;
    }
}
