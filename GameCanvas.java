/**
 * The primary rendering surface for the Lumen Architect client, occupying the full
 * 1024x768 game window and dispatching every draw call through a {@link java.awt.Graphics2D}
 * context. It routes the active game state to the appropriate scene renderer
 * (main menu, acts 1-3, boss, cutscene, or pause menu) and hosts the inner
 * {@link GestureListener} that translates raw mouse or touch events from the
 * Apprentice's webcam overlay into game actions.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 3a "Graphics"           - extends JComponent and overrides
//                                  paintComponent(Graphics g), casts to
//                                  Graphics2D, applies setRenderingHints
//                                  for anti-aliasing, and uses fillRect
//                                  / drawImage / drawString. Canvas size
//                                  is locked to 1024x768 per the spec
//                                  and per the Graphics module's
//                                  setPreferredSize pattern.
// Module 3b "More Graphics"      - AffineTransform for camera
//                                  translation in renderBoss, custom
//                                  RadialGradientPaint and AlphaComposite
//                                  for the light radius and darkness
//                                  mask, RescaleOp for sprite tinting.
//                                  Builds directly on the
//                                  transformations and composition ideas
//                                  from 3b.
// Module 1d "Inner Classes"      - inner GestureListener (MouseAdapter
//                                  subclass) handles Apprentice mouse
//                                  input; anonymous MouseAdapter for
//                                  Wanderer mouse-motion tracking.
// Module 4a "Threads"            - javax.swing.Timer drives the menu
//                                  particle animation off the EDT; the
//                                  game-loop thread (in GameStarter)
//                                  calls canvas.repaint() each tick
//                                  while the EDT actually paints.
// Module 2a "Event Handling"     - GestureListener registers as a
//                                  MouseListener / MouseMotionListener;
//                                  embeds key bindings via KeyBindings.
// YouTube zCiMlbu1-aQ            - "Java Graphics Programming Tutorial -
//                                  Shapes, Paths, Curves, and
//                                  Transformations" informed the scene
//                                  rendering and transformation usage.
// YouTube pdtEB3R4MZI            - "Animation Introduction (Java
//                                  Graphics)" informed the menu
//                                  particle animation loop.
// =========================================================================

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import javax.swing.*;
import javax.swing.Timer;
import java.util.*;
import java.util.List;

public class GameCanvas extends JComponent {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** The fixed rendering width of the canvas in pixels. */
    public static final int CANVAS_WIDTH = 1024;

    /** The fixed rendering height of the canvas in pixels. */
    public static final int CANVAS_HEIGHT = 768;

    private static final Color BG_MENU      = new Color(0x0A, 0x0A, 0x0F);
    private static final Color BG_ACT1      = new Color(0x0E, 0x0E, 0x14);
    private static final Color BG_ACT2      = new Color(0x05, 0x05, 0x08);
    private static final Color BG_ACT3      = new Color(0x05, 0x05, 0x08);
    private static final Color BG_BOSS      = new Color(0x0A, 0x08, 0x08);
    private static final Color GOLD         = new Color(0xC9, 0xA8, 0x4C);
    private static final Color SUBTITLE_CLR = new Color(0x88, 0x80, 0x70);
    private static final Color PANEL_BG     = new Color(0x0E, 0x0E, 0x14);

    private static final Font TITLE_FONT    = new Font("Serif", Font.PLAIN, 48);
    private static final Font SUBTITLE_FONT = new Font("Serif", Font.ITALIC, 16);
    private static final Font BUTTON_FONT   = new Font("SansSerif", Font.PLAIN, 14);
    private static final Font PAUSE_TITLE   = new Font("Serif", Font.PLAIN, 32);
    private static final Font PAUSE_BTN     = new Font("SansSerif", Font.PLAIN, 16);

    private static final int CAM_PANEL_X = 717;
    private static final int CAM_PANEL_W = CANVAS_WIDTH - CAM_PANEL_X; // 307
    private static final int CAM_PANEL_H = CANVAS_HEIGHT;              // 768

    private static final int PARTICLE_COUNT = 20;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private LevelState levelState;
    private Player player;
    private List<GameElement> elements;

    private LightRenderer lightRenderer;

    /** Current light source position (canvas-space). */
    private Point lightSource;
    /** Current light radius in pixels. */
    private int lightRadius;
    /** Apprentice light velocity factor (0.0 to 1.0). */
    private float lightVelocityFactor;

    // -------------------------------------------------------------------------
    // Boss-phase light-ball state (P8.3)
    // -------------------------------------------------------------------------

    /**
     * World-space x of the server-authoritative {@link LightBall} for the
     * BOSS phase. Updated every time a
     * {@link NetworkProtocol.ServerStatePacket} arrives; read by
     * {@link #renderBoss(Graphics2D)} and converted to screen coordinates
     * through {@link Camera} so both clients render the mask at the same
     * world position.
     */
    private float bossLightWorldX = Camera.ARENA_W / 2f;

    /**
     * World-space y of the server-authoritative {@link LightBall}. Mirror of
     * {@link #bossLightWorldX}.
     */
    private float bossLightWorldY = Camera.ARENA_H / 2f;

    /**
     * {@code true} once at least one BOSS-phase
     * {@link NetworkProtocol.ServerStatePacket} has populated
     * {@link #bossLightWorldX}/{@link #bossLightWorldY}. Before the first
     * authoritative sample the renderer falls back to the arena centre so
     * the first frame after boss entry does not flash a mask at (0, 0).
     */
    private boolean bossLightReceived = false;

    /**
     * P8.5 — shared reference to the authoritative list of active
     * {@link BossAttack} instances maintained by
     * {@link GameStarter#spawnBossAttack(String, int, int)}. Drawn from
     * inside {@link #renderBoss(Graphics2D)} under the world-space camera
     * translation so attacks track the same coordinate frame as platforms,
     * cores, and the Wanderer. Never mutated from the canvas; the list is a
     * {@link java.util.concurrent.CopyOnWriteArrayList} owned by
     * {@link GameStarter} so iteration here is safe against concurrent
     * append/remove.
     */
    private List<BossAttack> bossAttacks;

    // -------------------------------------------------------------------------
    // P8.8 — stun minigame overlay + stunned banner
    // -------------------------------------------------------------------------

    /**
     * Shared {@link StunMinigame} controller owned by {@link GameStarter}.
     * {@code null} until {@link #setStunMinigame(StunMinigame)} is called on
     * game-loop startup; {@link #renderStunOverlay(Graphics2D)} bails out
     * silently while null or when the minigame is not active. The canvas
     * never mutates the controller — all open/close/tick/input calls live
     * in {@link GameStarter} / {@link InputRouter}.
     */
    private StunMinigame stunMinigame;

    /**
     * Wall-clock ms until which the "ARCHITECT STUNNED" banner is rendered
     * on top of the boss scene. Set from {@link GameStarter#handleMessage}
     * on receipt of {@code STUN_RES|1}; compared against
     * {@link System#currentTimeMillis()} each paint.
     */
    private volatile long architectStunnedUntilMs = 0L;

    // -------------------------------------------------------------------------
    // P8.6 — pre-boss altar overlay
    // -------------------------------------------------------------------------

    /** Whether the altar choice overlay is currently showing. */
    private boolean altarOpen = false;

    /** The altarId of the active altar trigger, or -1 when no altar is open. */
    private int altarId = -1;

    /**
     * Pending altar choice set by the 1/2 key bindings registered in
     * {@link #openAltarOverlay(int)}. Read and cleared by
     * {@link GameStarter} in the game loop.
     */
    private volatile String pendingAltarChoice = null;

    /** Breadcrumb trail positions for Act 2. */
    private Point[] breadcrumbs;
    /** Alpha values for each breadcrumb (fading). */
    private float[] breadcrumbAlphas;

    /** Whether the game is paused. */
    private boolean paused;

    /** Whether the fragment library view is being shown. */
    private boolean showFragmentLibrary;

    /** Active on-screen notifications (each is [text, startTimeMs]). */
    private final java.util.concurrent.CopyOnWriteArrayList<String[]> notifications =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** IP address text typed into the menu input field. */
    private String ipInputText;

    /** Mouse position for offline mouse fallback (C1). */
    private Point mousePosition = new Point(512, 384);

    /** Light source position for Act 2/3 (D2). */
    private Point lightSourcePosition = new Point(512, 384);

    /** Current game phase cached for mouse click routing (C1). */
    private LevelState.GamePhase currentPhase = LevelState.GamePhase.MENU;

    /** P8.10 — Outcome stored when VictoryPacket arrives; drives renderVictoryScreen. */
    private GameServer.VictoryState victoryResult;

    /** Shared lobby state updated from LOBBY_STATE server messages. */
    private LobbyState lobbyState = new LobbyState();

    /**
     * Role string ({@code "WANDERER"} or {@code "APPRENTICE"}) of the partner who
     * disconnected. {@code null} while both clients are connected. Set when a
     * {@link Protocol#PARTNER_DISCONNECTED} message arrives and cleared on
     * {@link Protocol#PARTNER_RECONNECTED} or {@link Protocol#SESSION_EXPIRED}.
     * Used by the {@code PAUSED_WAITING} overlay to describe who left.
     */
    private volatile String partnerDCRole = null;

    /**
     * Seconds remaining on the server's five-minute reconnect window. Updated every
     * second from {@link Protocol#RECONNECT_TIMER} messages; {@code -1} when no
     * countdown is active.
     */
    private volatile long reconnectCountdown = -1;

    /**
     * Bounding rectangle of the Wanderer role card in the lobby screen.
     * Kept as a field so InputRouter can perform hit-tests without knowing
     * the layout constants.
     */
    private final java.awt.Rectangle wandererCardBounds   = new java.awt.Rectangle(162, 230, 280, 320);

    /**
     * Bounding rectangle of the Apprentice role card in the lobby screen.
     * Mirror of {@link #wandererCardBounds} on the right side.
     */
    private final java.awt.Rectangle apprenticeCardBounds = new java.awt.Rectangle(582, 230, 280, 320);


    /** Ghost block preview position for Apprentice gesture placement. */
    private Point gesturePosition;

    /**
     * Network connection status message displayed as a full-screen overlay when
     * non-{@code null}. Set by the network I/O thread; read by the EDT during paint.
     * {@code volatile} so the write from the NetworkIO thread is immediately visible to
     * the Swing EDT without acquiring a lock.
     */
    private volatile String connectionOverlay;

    /**
     * Short status line shown near the bottom of the main menu during the connection
     * handshake. Updated by the Connect button's ActionListener on the EDT at each
     * stage: "Connecting…", "Connected as …", or "Connection failed: …".
     * Empty string means nothing is shown.
     */
    private volatile String connectionStatus = "";

    /**
     * Cached singleton reference to avoid calling {@link GameSession#getInstance()}
     * inside {@code paintComponent()} (which fires 60 times per second). The singleton
     * is created lazily on first access; any later {@code setRole()} call mutates the
     * same object so this reference always reflects the current role state.
     */
    private final GameSession session = GameSession.getInstance();

    // Menu particle system
    private float[] particleX;
    private float[] particleY;
    private float[] particleSpeed;
    private Random particleRng;

    /**
     * Swing timer that fires every 16 ms during the main menu to drive the particle
     * animation. Stopped when the game phase leaves MENU so the game loop thread
     * takes over repaint scheduling.
     */
    private Timer menuTimer;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs the {@code GameCanvas}, locks its preferred and maximum size to
     * 1024x768, enables double-buffering via Swing's built-in mechanism, and
     * registers the {@link GestureListener} for pointer input.
     */
    public GameCanvas() {
        Dimension size = new Dimension(CANVAS_WIDTH, CANVAS_HEIGHT);
        setPreferredSize(size);
        setMaximumSize(size);
        setDoubleBuffered(true);
        setFocusable(true);

        // Mouse position tracking — used for light source in Act 2/3
        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { mousePosition = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) { mousePosition = e.getPoint(); }
        });

        // Defaults
        levelState = new LevelState();
        lightRenderer = new LightRenderer();
        lightSource = new Point(CANVAS_WIDTH / 2, CANVAS_HEIGHT / 2);
        lightRadius = 180;
        lightVelocityFactor = 0.0f;
        paused = false;
        ipInputText = "";
        gesturePosition = null;

        // Initialise menu particles
        particleRng = new Random(42);
        particleX = new float[PARTICLE_COUNT];
        particleY = new float[PARTICLE_COUNT];
        particleSpeed = new float[PARTICLE_COUNT];
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particleX[i] = particleRng.nextFloat() * CANVAS_WIDTH;
            particleY[i] = particleRng.nextFloat() * CANVAS_HEIGHT;
            particleSpeed[i] = 0.3f + particleRng.nextFloat() * 0.7f; // 0.3 to 1.0 px/frame
        }

        // Drive particle animation during the menu before the game loop starts.
        menuTimer = new Timer(16, e -> repaint());
        menuTimer.start();
    }

    // -------------------------------------------------------------------------
    // Setters for game state references
    // -------------------------------------------------------------------------

    /** Sets the level state reference used for phase dispatch. */
    public void setLevelState(LevelState levelState) {
        this.levelState = levelState;
        this.currentPhase = levelState.currentPhase;
        // Stop the menu animation timer once the game phase moves away from MENU;
        // the game loop thread drives repaint from this point on.
        if (levelState.currentPhase != LevelState.GamePhase.MENU && menuTimer != null && menuTimer.isRunning()) {
            menuTimer.stop();
        }
    }

    /** Sets the current game phase directly (B5). */
    public void setPhase(LevelState.GamePhase phase) {
        this.currentPhase = phase;
        this.levelState.currentPhase = phase;
        if (phase != LevelState.GamePhase.MENU && menuTimer != null && menuTimer.isRunning()) {
            menuTimer.stop();
        }
        repaint();
    }

    /** Sets the light source position for Act 2/3 (D2). */
    public void setLightSourcePosition(Point p) {
        this.lightSourcePosition = p;
    }

    /** Returns the current light source position. */
    public Point getLightSourcePosition() {
        return lightSourcePosition;
    }

    /** Sets the light radius for Act 2/3. */
    public void setLightRadius(int r) {
        this.lightRadius = r;
    }

    /** Sets the player reference used for rendering. */
    public void setPlayer(Player player) {
        this.player = player;
    }

    /** Sets the list of active game elements. */
    public void setElements(List<GameElement> elements) {
        this.elements = elements;
    }

    /**
     * P8.5 — stores a reference to {@link GameStarter}'s live list of active
     * {@link BossAttack} instances so {@link #renderBoss(Graphics2D)} can
     * paint them under the camera-translated world transform. Called once
     * during game-loop startup; the canvas never mutates the list.
     *
     * @param attacks the shared attack list; may be {@code null} to disable
     *                rendering temporarily (e.g. before the boss phase is
     *                entered — no-op guarded by a null check inside
     *                {@code renderBoss})
     */
    public void setBossAttacks(List<BossAttack> attacks) {
        this.bossAttacks = attacks;
    }

    /**
     * P8.8 — stores the shared {@link StunMinigame} controller so the canvas
     * can render the overlay during its active window. Called once by
     * {@link GameStarter} on game-loop startup; the canvas never mutates the
     * controller.
     *
     * @param m the minigame instance; may be {@code null} to disable rendering
     */
    public void setStunMinigame(StunMinigame m) {
        this.stunMinigame = m;
    }

    /**
     * P8.8 — sets the wall-clock ms until which the "ARCHITECT STUNNED"
     * banner is drawn on top of the boss scene. Called from
     * {@link GameStarter#handleMessage} on {@code STUN_RES|1}.
     *
     * @param ms absolute {@link System#currentTimeMillis()} value; {@code 0}
     *           to clear the banner
     */
    public void setArchitectStunnedUntilMs(long ms) {
        this.architectStunnedUntilMs = ms;
    }

    // -------------------------------------------------------------------------
    // P8.6 — altar overlay API
    // -------------------------------------------------------------------------

    /**
     * Opens the pre-boss altar choice overlay and registers temporary 1/2
     * key bindings so the Wanderer can select an option. Called by
     * {@link GameStarter#checkAltarTrigger()} on the EDT.
     *
     * @param altarId the id matching the JSON trigger's {@code altarId} param
     */
    public void openAltarOverlay(int altarId) {
        this.altarOpen = true;
        this.altarId   = altarId;
        this.pendingAltarChoice = null;
        javax.swing.InputMap  im = getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        javax.swing.ActionMap am = getActionMap();
        im.put(javax.swing.KeyStroke.getKeyStroke('1'), "altar_opt1");
        im.put(javax.swing.KeyStroke.getKeyStroke('2'), "altar_opt2");
        am.put("altar_opt1", new javax.swing.AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (altarOpen) pendingAltarChoice = "POWER_SURGE";
            }
        });
        am.put("altar_opt2", new javax.swing.AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (altarOpen) pendingAltarChoice = "SIGHT_RESTRICTION";
            }
        });
    }

    /** Dismisses the altar overlay and unregisters the 1/2 key bindings. */
    public void closeAltarOverlay() {
        altarOpen = false;
        altarId   = -1;
        pendingAltarChoice = null;
        javax.swing.InputMap im = getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        im.remove(javax.swing.KeyStroke.getKeyStroke('1'));
        im.remove(javax.swing.KeyStroke.getKeyStroke('2'));
    }

    /** Returns whether the altar overlay is currently visible. */
    public boolean isAltarOpen() { return altarOpen; }

    /**
     * Returns the pending altar choice set by the 1/2 key bindings and clears
     * it atomically. Returns {@code null} if no key has been pressed since the
     * last call.
     */
    public String getAndClearPendingAltarChoice() {
        String c = pendingAltarChoice;
        pendingAltarChoice = null;
        return c;
    }

    /** Sets the current light source position and radius. */
    public void setLightSource(Point source, int radius, float velocityFactor) {
        this.lightSource = source;
        this.lightSourcePosition = source;
        this.lightRadius = radius;
        this.lightVelocityFactor = velocityFactor;
    }

    /**
     * P8.3 — stores the server-authoritative {@link LightBall} world-space
     * position carried in every BOSS-phase
     * {@link NetworkProtocol.ServerStatePacket}. Called from
     * {@link GameStarter#applyServerState}. During {@link #renderBoss(Graphics2D)}
     * the stored coordinates are transformed through {@link Camera} to screen
     * space before being fed into {@link LightRenderer#renderLightMask(Graphics2D, Point, int, float)}
     * so both clients paint the mask at the same world point.
     *
     * @param worldX world-space x of the light ball
     * @param worldY world-space y of the light ball
     */
    public void setBossLightWorldPosition(float worldX, float worldY) {
        this.bossLightWorldX = worldX;
        this.bossLightWorldY = worldY;
        this.bossLightReceived = true;
    }

    /** P8.10 — Stores the match outcome so renderVictoryScreen can display it. */
    public void setVictoryResult(GameServer.VictoryState result) {
        this.victoryResult = result;
    }

    /**
     * Returns true when the light is effectively on for rendering purposes.
     * Apprentice: checks toggle + battery. Wanderer: reads from levelState.
     */
    private boolean isLightEffectivelyOn() {
        if (session.isApprentice()) {
            MouseApprentice ma = MouseApprentice.getInstance();
            return ma.isLightActive() && GameSession.getInstance().getLightBattery() > 0;
        }
        return levelState == null || levelState.getLightActive();
    }

    /** Returns true when the current act uses the light system (ACT2 or ACT3). */
    private boolean isActWithLight() {
        if (levelState == null) return false;
        return levelState.currentPhase == LevelState.GamePhase.ACT2
            || levelState.currentPhase == LevelState.GamePhase.ACT3;
    }

    /**
     * Returns light brightness 0.15–1.0 based on the current light radius.
     * Drives the inside-circle darkness overlay alpha in ACT2/3.
     */
    private float getLightBrightness() {
        int radius;
        if (session.isApprentice()) {
            radius = MouseApprentice.getInstance().getLightRadius();
        } else {
            radius = (levelState != null) ? levelState.getLightRadius() : 120;
        }
        return GameSession.getInstance().getLightBrightness(radius);
    }

    /**
     * Draws a partial black overlay inside the lit circle to implement brightness
     * separation from radius. At minimum radius brightness ≈ 0.15 (dim); at max ≈ 1.0.
     */
    private void applyBrightnessOverlay(Graphics2D g, Point center, int radius) {
        float brightness = getLightBrightness();
        if (brightness >= 1.0f || center == null || radius <= 0) return;
        float insideAlpha = (1f - brightness) * 0.80f; // max 0.8 darkness at min radius
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, insideAlpha));
        g.setColor(Color.BLACK);
        g.fillOval(center.x - radius, center.y - radius, radius * 2, radius * 2);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }

    /**
     * Two-pass darkness renderer. Fills a BufferedImage mask with opaque black,
     * then punches a soft radial hole using DST_OUT + RadialGradientPaint.
     * Replaces the old LightRenderer.renderLightMask() calls for Act 2/3.
     * PART 4: returns immediately with solid black when radius < 60.
     */
    private void renderDarkness(Graphics2D g, Point center, int radius) {
        if (center == null || radius < 60) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
            return;
        }
        float brightness = computeBrightness(radius);
        BufferedImage mask = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D mg = mask.createGraphics();
        mg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Fill mask with opaque black — everything outside the radius is dark
        mg.setColor(Color.BLACK);
        mg.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        // DST_OUT punch: alpha driven by brightness so low brightness = dim centre
        mg.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OUT));
        float[] fractions = {0.0f, 0.75f, 1.0f};
        Color[] colors = {
            new Color(0, 0, 0, (int)(brightness * 255)),        // centre — brightness * 255
            new Color(0, 0, 0, (int)(brightness * 255 * 0.5f)), // 75% falloff
            new Color(0, 0, 0, 0)                               // edge — leave black
        };
        RadialGradientPaint rgp = new RadialGradientPaint(
                center.x, center.y, radius, fractions, colors);
        mg.setPaint(rgp);
        mg.fillOval(center.x - radius, center.y - radius, radius * 2, radius * 2);
        mg.dispose();
        g.drawImage(mask, 0, 0, null);
    }

    /**
     * Maps light radius to a brightness value in [0.02, 1.0].
     * At minimum radius (20 px) returns 0.02; at maximum (180 px) returns 1.0.
     */
    private float computeBrightness(int radius) {
        float normalized = (float)(radius - 20) / 160f;
        normalized = Math.max(0f, Math.min(1f, normalized));
        return 0.02f + (normalized * 0.98f);
    }

    /** Sets the breadcrumb trail for Act 2. */
    public void setBreadcrumbs(Point[] breadcrumbs, float[] alphas) {
        this.breadcrumbs = breadcrumbs;
        this.breadcrumbAlphas = alphas;
    }

    /** Sets the pause state. */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /** Sets the ghost block preview position. */
    public void setGesturePosition(Point pos) {
        this.gesturePosition = pos;
    }

    /** Sets the menu IP input text. */
    public void setIpInputText(String text) {
        this.ipInputText = text;
    }

    /**
     * Sets or clears the network connection overlay message. Pass {@code null} to hide
     * the overlay. Called from the NetworkIO thread; rendered by the EDT on the next
     * repaint.
     *
     * @param msg the message to display, or {@code null} to clear the overlay
     */
    public void setConnectionOverlay(String msg) {
        this.connectionOverlay = msg;
    }

    /**
     * Sets the connection status line shown at the bottom of the main menu during
     * the server handshake. Pass an empty string to clear.
     *
     * @param status the status text to display; must not be {@code null}
     */
    public void setConnectionStatus(String status) {
        this.connectionStatus = (status != null) ? status : "";
    }

    /**
     * Replaces the current lobby state with the one parsed from the latest
     * {@link Protocol#LOBBY_STATE} server message.
     *
     * @param ls the updated lobby state; ignored if {@code null}
     */
    public void setLobbyState(LobbyState ls) {
        if (ls != null) this.lobbyState = ls;
    }

    /**
     * Returns the current lobby state object so callers (e.g. {@link GameStarter#handleMessage})
     * can mutate reconnect-session flags without overwriting the whole reference.
     *
     * @return the shared {@link LobbyState}; never {@code null}
     */
    public LobbyState getLobbyState() { return lobbyState; }

    /**
     * Sets the role label of the partner who just disconnected. Pass {@code null}
     * once the partner has reconnected or the reconnect window has expired, so the
     * PAUSED_WAITING overlay disappears.
     *
     * @param role {@code "WANDERER"}, {@code "APPRENTICE"}, or {@code null}
     */
    public void setPartnerDCRole(String role) { this.partnerDCRole = role; }

    /**
     * Sets the current countdown for the reconnect window. Pass {@code -1} to hide
     * the timer display. Updated every second by {@link Protocol#RECONNECT_TIMER}.
     *
     * @param seconds seconds remaining until the reconnect window expires
     */
    public void setReconnectCountdown(long seconds) { this.reconnectCountdown = seconds; }

    /**
     * Returns the role name whose card contains the point {@code (x, y)}, or
     * {@code "NONE"} if the point is not inside any card. Used by
     * {@link InputRouter} to route lobby mouse events without duplicating the
     * card layout constants.
     *
     * @param x canvas-space x coordinate
     * @param y canvas-space y coordinate
     * @return {@code "WANDERER"}, {@code "APPRENTICE"}, or {@code "NONE"}
     */
    public String getLobbyRoleAtPoint(int x, int y) {
        if (wandererCardBounds.contains(x, y))   return "WANDERER";
        if (apprenticeCardBounds.contains(x, y)) return "APPRENTICE";
        return "NONE";
    }

    /**
     * Returns the current light-source radius in pixels.
     *
     * @return the light radius
     */
    public int getLightRadius() {
        return lightRadius;
    }

    /** Returns the current level state (public accessor for InputRouter). */
    public LevelState getLevelStatePublic() {
        return levelState;
    }

    /** Returns the current mouse position on the canvas. */
    public Point getMousePosition2() {
        return mousePosition;
    }

    /**
     * Stores the most recent remote Wanderer state received from the server for ghost
     * rendering on the Apprentice client. Pass {@code x = -1} to indicate no data.
     *
     * @param x      world-space x-coordinate of the remote Wanderer
     * @param y      world-space y-coordinate of the remote Wanderer
     * @param health current health of the remote Wanderer
     */
    /** Shows or hides the fragment library overlay. */
    public void setShowFragmentLibrary(boolean show) {
        this.showFragmentLibrary = show;
    }

    /** Returns whether the fragment library is being shown. */
    public boolean isShowingFragmentLibrary() {
        return showFragmentLibrary;
    }

    /**
     * Toggles the in-game camera feed overlay (no-op: camera system removed).
     */
    public void toggleCameraOverlay() {
        // no-op
    }

    /**
     * Queues an on-screen notification that auto-expires after 3 seconds.
     *
     * @param text the notification message to display
     */
    public void showNotification(String text) {
        notifications.add(new String[]{ text, String.valueOf(System.currentTimeMillis()) });
    }

    // -------------------------------------------------------------------------
    // Painting
    // -------------------------------------------------------------------------

    /**
     * Entry point for all Swing-driven repaint calls. Casts the provided
     * {@link Graphics} context to {@link Graphics2D}, enables antialiasing and
     * rendering quality, and delegates to the appropriate scene-specific render
     * method based on the current game phase.
     *
     * @param g the {@link Graphics} context supplied by the Swing paint subsystem;
     *          never {@code null}
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                             RenderingHints.VALUE_RENDER_QUALITY);

        // Sync currentPhase from levelState (FIX 3)
        if (levelState != null) currentPhase = levelState.currentPhase;

        // Cutscene takes absolute precedence over every scene, including pause —
        // the renderer draws opaque black and suppresses world/HUD entirely.
        if (CutsceneRenderer.get().isPlaying()) {
            CutsceneRenderer.get().render(g2d, CANVAS_WIDTH, CANVAS_HEIGHT);
            if (connectionOverlay != null) {
                renderConnectionOverlay(g2d, connectionOverlay);
            }
            return;
        }

        // Scene dispatch — using else-if so the connection overlay can always be
        // painted last, on top of every scene including pause and cutscene.
        if (paused && levelState.currentPhase != LevelState.GamePhase.MENU) {
            renderPauseMenu(g2d);
        } else if (levelState.currentPhase == LevelState.GamePhase.CUTSCENE) {
            renderCutscene(g2d);
        } else {
            switch (levelState.currentPhase) {
                case MENU:
                    renderMainMenu(g2d);
                    break;
                case ACT1:
                    renderAct1(g2d);
                    break;
                case ACT2:
                    renderAct2(g2d);
                    break;
                case ACT3:
                    renderAct3(g2d);
                    break;
                case BOSS:
                    renderBoss(g2d);
                    break;
                case FINAL_CORRIDOR:
                    renderAct3(g2d); // reuse Act 3 renderer for final corridor
                    break;
                case LOBBY:
                    renderLobby(g2d);
                    break;
                case PAUSED_WAITING:
                    renderPausedWaiting(g2d);
                    break;
                case END_SCREEN:
                    renderVictoryScreen(g2d);
                    break;
                default:
                    renderMainMenu(g2d);
                    break;
            }

            // Wanderer is rendered via player.render() in renderPlayer() — no separate ghost needed.
        }

        // P8.6 — altar choice overlay on top of the game scene (but below connection overlay)
        if (altarOpen) {
            renderAltarOverlay(g2d);
        }

        // P8.8 — stun minigame overlay and "ARCHITECT STUNNED" banner. Only
        // relevant during the BOSS phase; both paths are guarded inside the
        // helpers so calling them outside BOSS is a cheap no-op.
        if (levelState != null && levelState.currentPhase == LevelState.GamePhase.BOSS) {
            renderStunOverlay(g2d);
            renderArchitectStunnedBanner(g2d);
        }

        // Connection-status overlay — rendered on top of everything, every frame
        if (connectionOverlay != null) {
            renderConnectionOverlay(g2d, connectionOverlay);
        }

        g2d.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        g2d.setColor(new Color(0x00, 0xFF, 0x00));
        String roleLabel = "ROLE: " + (session.role != null ? session.role : "NONE");
        g2d.drawString(roleLabel, 10, 758);
    }

    // -------------------------------------------------------------------------
    // Scene renderers
    // -------------------------------------------------------------------------

    /**
     * Renders the main menu screen with title, subtitle, IP input, connect button,
     * and drifting particle effect.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    public void renderMainMenu(Graphics2D g) {
        // Background
        g.setColor(BG_MENU);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        // Particle dots drifting upward
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        g.setColor(GOLD);
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particleY[i] -= particleSpeed[i];
            if (particleY[i] < -4) {
                particleY[i] = CANVAS_HEIGHT + 4;
                particleX[i] = particleRng.nextFloat() * CANVAS_WIDTH;
            }
            g.fill(new Ellipse2D.Float(particleX[i] - 2, particleY[i] - 2, 4, 4));
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        // Title
        g.setFont(TITLE_FONT);
        g.setColor(GOLD);
        FontMetrics fm = g.getFontMetrics();
        String title = "LUMEN ARCHITECT";
        int titleX = (CANVAS_WIDTH - fm.stringWidth(title)) / 2;
        g.drawString(title, titleX, 280);

        // Subtitle
        g.setFont(SUBTITLE_FONT);
        g.setColor(SUBTITLE_CLR);
        fm = g.getFontMetrics();
        String subtitle = "A cooperative puzzle of light and shadow";
        int subX = (CANVAS_WIDTH - fm.stringWidth(subtitle)) / 2;
        g.drawString(subtitle, subX, 310);

        // Connection status line (shown during and after the server handshake)
        String status = connectionStatus;
        if (status != null && !status.isEmpty()) {
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            Color statusColor;
            if (status.startsWith("Connecting")) {
                statusColor = GOLD; // gold while connecting
            } else if (status.startsWith("Connected")) {
                statusColor = new Color(0x44, 0xCC, 0x66); // green when connected
            } else if (status.startsWith("Starting")) {
                statusColor = GOLD;
            } else {
                statusColor = new Color(0xCC, 0x44, 0x44); // red on failure
            }
            g.setColor(statusColor);
            fm = g.getFontMetrics();
            int statusX = (CANVAS_WIDTH - fm.stringWidth(status)) / 2;
            g.drawString(status, statusX, 600);
        }

    }

    // -------------------------------------------------------------------------
    // Lobby renderer
    // -------------------------------------------------------------------------

    /**
     * Renders the pre-game role-selection lobby with two clickable role cards.
     * Updates {@link #wandererCardBounds} and {@link #apprenticeCardBounds} so that
     * {@link #getLobbyRoleAtPoint(int, int)} stays consistent with what is drawn.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    public void renderLobby(Graphics2D g) {
        // Background
        g.setColor(BG_MENU);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        // Particle drift (reuses the same particle system as the main menu)
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
        g.setColor(GOLD);
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particleY[i] -= particleSpeed[i] * 0.5f;
            if (particleY[i] < -4) {
                particleY[i] = CANVAS_HEIGHT + 4;
                particleX[i] = particleRng.nextFloat() * CANVAS_WIDTH;
            }
            g.fill(new Ellipse2D.Float(particleX[i] - 2, particleY[i] - 2, 4, 4));
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        boolean reconnect = lobbyState.isReconnectSession;

        // Title
        g.setFont(TITLE_FONT);
        g.setColor(reconnect ? new Color(0xCC, 0x88, 0x44) : GOLD);
        FontMetrics fm = g.getFontMetrics();
        String title = reconnect ? "SESSION IN PROGRESS" : "LUMEN ARCHITECT";
        g.drawString(title, (CANVAS_WIDTH - fm.stringWidth(title)) / 2, 130);

        // Subtitle
        g.setFont(SUBTITLE_FONT);
        g.setColor(SUBTITLE_CLR);
        fm = g.getFontMetrics();
        String sub = reconnect
                ? "Rejoin the vacant role to resume the game"
                : "Choose your role to begin";
        g.drawString(sub, (CANVAS_WIDTH - fm.stringWidth(sub)) / 2, 162);

        // Separator line
        g.setColor(new Color(0x3A, 0x3A, 0x48));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(160, 178, CANVAS_WIDTH - 160, 178);

        // Card state differs in reconnect mode: the occupied role shows as ACTIVE
        // (taken, not clickable) and the vacant role shows as clickable.
        boolean wTaken;
        boolean aTaken;
        boolean wHover;
        boolean aHover;
        if (reconnect) {
            // Only the vacant role is open — the other is marked ACTIVE (taken).
            wTaken = !"WANDERER".equals(lobbyState.vacantRole);
            aTaken = !"APPRENTICE".equals(lobbyState.vacantRole);
            // Suppress hover on the occupied card; allow it on the vacant one.
            wHover = !wTaken && lobbyState.wandererHovered;
            aHover = !aTaken && lobbyState.apprenticeHovered;
        } else {
            wTaken = lobbyState.wandererTaken;
            aTaken = lobbyState.apprenticeTaken;
            wHover = lobbyState.wandererHovered;
            aHover = lobbyState.apprenticeHovered;
        }

        // Render both role cards
        renderRoleCard(g, wandererCardBounds, "WANDERER",
                "Traverse the darkness\nand destroy the Cores.",
                wTaken, wHover);
        renderRoleCard(g, apprenticeCardBounds, "APPRENTICE",
                "Wield the light\nand shape the path.",
                aTaken, aHover);

        // VS divider
        g.setFont(new Font("Serif", Font.BOLD, 22));
        g.setColor(new Color(0x3A, 0x3A, 0x48));
        fm = g.getFontMetrics();
        String vs = "VS";
        g.drawString(vs, (CANVAS_WIDTH - fm.stringWidth(vs)) / 2,
                wandererCardBounds.y + wandererCardBounds.height / 2 + fm.getAscent() / 2);

        // Status hint at the bottom
        boolean bothTaken = lobbyState.wandererTaken && lobbyState.apprenticeTaken;
        g.setFont(new Font("Monospaced", Font.PLAIN, 13));
        String hint;
        Color hintColor;
        if (reconnect) {
            hint = "Click the vacant role to reconnect";
            hintColor = new Color(0xCC, 0x88, 0x44);
        } else if (bothTaken) {
            hint = "Both roles selected — starting…";
            hintColor = new Color(0x44, 0xCC, 0x66);
        } else {
            hint = "Click a card to claim your role";
            hintColor = SUBTITLE_CLR;
        }
        g.setColor(hintColor);
        fm = g.getFontMetrics();
        g.drawString(hint, (CANVAS_WIDTH - fm.stringWidth(hint)) / 2, 620);
    }

    /**
     * Renders a single role-selection card at the given bounds.
     *
     * @param g           rendering context
     * @param bounds      position and size of the card
     * @param title       role name shown in the card header
     * @param description two-line description (lines separated by {@code '\n'})
     * @param taken        whether this role has already been claimed
     * @param hovered     whether a client is hovering over this card
     */
    private void renderRoleCard(Graphics2D g, java.awt.Rectangle bounds,
            String title, String description, boolean taken, boolean hovered) {
        if (bounds == null) return;

        // Card background
        Color bgColor = taken  ? new Color(0x12, 0x22, 0x12)
                      : hovered ? new Color(0x14, 0x14, 0x22)
                                : new Color(0x0E, 0x0E, 0x14);
        g.setColor(bgColor);
        g.fill(new java.awt.geom.RoundRectangle2D.Float(
                bounds.x, bounds.y, bounds.width, bounds.height, 12, 12));

        // Border — green when taken, gold when hovered, dim otherwise
        Color borderColor = taken  ? new Color(0x44, 0xCC, 0x66)
                          : hovered ? GOLD
                                    : new Color(0x3A, 0x3A, 0x4A);
        float strokeW = (taken || hovered) ? 2f : 1f;
        g.setColor(borderColor);
        g.setStroke(new BasicStroke(strokeW));
        g.draw(new java.awt.geom.RoundRectangle2D.Float(
                bounds.x, bounds.y, bounds.width, bounds.height, 12, 12));
        g.setStroke(new BasicStroke(1f));

        // Role icon circle
        int iconR  = 40;
        int iconCX = bounds.x + bounds.width / 2;
        int iconCY = bounds.y + 86;
        g.setColor(taken ? new Color(0x1A, 0x3A, 0x1A) : new Color(0x12, 0x18, 0x2E));
        g.fill(new Ellipse2D.Float(iconCX - iconR, iconCY - iconR, iconR * 2, iconR * 2));
        g.setColor(borderColor);
        g.setStroke(new BasicStroke(strokeW));
        g.draw(new Ellipse2D.Float(iconCX - iconR, iconCY - iconR, iconR * 2, iconR * 2));
        g.setStroke(new BasicStroke(1f));

        // First letter of role as large icon glyph
        g.setFont(new Font("Serif", Font.BOLD, 38));
        g.setColor(taken ? new Color(0x44, 0xCC, 0x66) : GOLD);
        FontMetrics fm = g.getFontMetrics();
        String letter = String.valueOf(title.charAt(0));
        g.drawString(letter,
                iconCX - fm.stringWidth(letter) / 2,
                iconCY + fm.getAscent() / 2 - 2);

        // Role title
        g.setFont(new Font("Serif", Font.PLAIN, 20));
        g.setColor(taken ? new Color(0x44, 0xCC, 0x66) : GOLD);
        fm = g.getFontMetrics();
        g.drawString(title,
                bounds.x + (bounds.width - fm.stringWidth(title)) / 2,
                bounds.y + 168);

        // Description lines
        g.setFont(SUBTITLE_FONT);
        g.setColor(SUBTITLE_CLR);
        fm = g.getFontMetrics();
        String[] lines = description.split("\n");
        int descY = bounds.y + 196;
        for (String line : lines) {
            g.drawString(line,
                    bounds.x + (bounds.width - fm.stringWidth(line)) / 2,
                    descY);
            descY += fm.getHeight() + 2;
        }

        // Status badge at the bottom of the card
        if (taken) {
            g.setFont(new Font("Monospaced", Font.BOLD, 12));
            g.setColor(new Color(0x44, 0xCC, 0x66));
            fm = g.getFontMetrics();
            String badge = "CLAIMED";
            g.drawString(badge,
                    bounds.x + (bounds.width - fm.stringWidth(badge)) / 2,
                    bounds.y + bounds.height - 18);
        } else if (hovered) {
            g.setFont(new Font("Monospaced", Font.PLAIN, 12));
            g.setColor(GOLD);
            fm = g.getFontMetrics();
            String badge = "Click to select";
            g.drawString(badge,
                    bounds.x + (bounds.width - fm.stringWidth(badge)) / 2,
                    bounds.y + bounds.height - 18);
        }
    }

    // -------------------------------------------------------------------------
    // Paused-waiting overlay (partial-disconnect pause)
    // -------------------------------------------------------------------------

    /**
     * Renders the full-screen overlay shown when a partner has disconnected and the
     * server is holding the session open for a reconnect. Displays the partner's
     * role, the remaining countdown (green above 60 s, red below), and a waiting
     * message. Active only while {@link LevelState.GamePhase#PAUSED_WAITING} is the
     * current phase.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    public void renderPausedWaiting(Graphics2D g) {
        // Dark, slightly desaturated backdrop
        g.setColor(new Color(0x06, 0x06, 0x0E));
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        // Dim vignette
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        // Title
        g.setFont(TITLE_FONT);
        g.setColor(new Color(0xCC, 0x44, 0x44));
        FontMetrics fm = g.getFontMetrics();
        String title = "PARTNER DISCONNECTED";
        g.drawString(title, (CANVAS_WIDTH - fm.stringWidth(title)) / 2, 260);

        // Who left
        g.setFont(SUBTITLE_FONT);
        g.setColor(SUBTITLE_CLR);
        fm = g.getFontMetrics();
        String role = (partnerDCRole != null) ? partnerDCRole : "YOUR PARTNER";
        String line = "The " + role + " has left the session.";
        g.drawString(line, (CANVAS_WIDTH - fm.stringWidth(line)) / 2, 300);

        // Countdown — colour shifts from green to amber to red as time runs out
        if (reconnectCountdown >= 0) {
            long s   = reconnectCountdown;
            long mm  = s / 60;
            long ss  = s % 60;
            Color timerColor = (s > 120) ? new Color(0x44, 0xCC, 0x66)
                             : (s > 60)  ? GOLD
                                         : new Color(0xCC, 0x44, 0x44);
            g.setFont(new Font("Monospaced", Font.BOLD, 48));
            g.setColor(timerColor);
            fm = g.getFontMetrics();
            String clock = String.format("%d:%02d", mm, ss);
            g.drawString(clock, (CANVAS_WIDTH - fm.stringWidth(clock)) / 2, 400);

            g.setFont(new Font("Monospaced", Font.PLAIN, 14));
            g.setColor(SUBTITLE_CLR);
            fm = g.getFontMetrics();
            String hint = "Waiting for reconnection…";
            g.drawString(hint, (CANVAS_WIDTH - fm.stringWidth(hint)) / 2, 440);
        } else {
            // Fallback if no timer packet has arrived yet
            g.setFont(new Font("Monospaced", Font.PLAIN, 16));
            g.setColor(SUBTITLE_CLR);
            fm = g.getFontMetrics();
            String hint = "Waiting for reconnection…";
            g.drawString(hint, (CANVAS_WIDTH - fm.stringWidth(hint)) / 2, 400);
        }

        // Footer hint
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.setColor(new Color(0x66, 0x66, 0x7A));
        fm = g.getFontMetrics();
        String foot = "Session will end if no one reconnects in time.";
        g.drawString(foot, (CANVAS_WIDTH - fm.stringWidth(foot)) / 2, 600);
    }

    /**
     * Renders Act 1: background, game elements, player, HUD with health bar,
     * budget display, timer, and ghost platform preview.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    public void renderAct1(Graphics2D g) {
        // Background
        g.setColor(BG_ACT1);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        // Draw all active game elements
        renderElements(g);

        // Draw player
        renderPlayer(g);

        // Ghost block preview
        renderGhostBlock(g);

        // HUD: health bar top-left, budget top-right
        renderHUD(g, true);

        // On-screen notifications
        renderNotifications(g);
    }

    /**
     * Renders Act 2: darker background, non-invisible platforms, player, light mask,
     * HUD without budget, and breadcrumb trail.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    public void renderAct2(Graphics2D g) {
        // Background
        g.setColor(BG_ACT2);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        // Absolute darkness when light is off — skip all world geometry
        if (!isLightEffectivelyOn()) {
            g.setColor(new Color(0, 0, 0, 255));
            g.fillRect(0, 0, getWidth(), getHeight());
            renderHUD(g, false);
            return;
        }

        // Draw non-invisible platforms and other elements
        if (elements != null) {
            for (GameElement elem : elements) {
                if (!elem.isActive()) continue;
                if (elem instanceof Platform) {
                    Platform plat = (Platform) elem;
                    if (plat.getType() == Platform.PlatformType.INVISIBLE) continue;
                }
                elem.render(g);
            }
        }

        // Draw player
        renderPlayer(g);

        // Breadcrumb trail (fading gold circles)
        renderBreadcrumbs(g);

        if (lightSourcePosition == null) lightSourcePosition = new Point(512, 384);

        // Flicker at < 10% battery
        float batt2 = session.isApprentice() ? GameSession.getInstance().getLightBattery() : 100f;
        boolean flicker2 = batt2 < 10f && (System.currentTimeMillis() / 120) % 2 == 0;
        int effectiveRadius2 = flicker2 ? (int)(lightRadius * 0.85f) : lightRadius;

        renderDarkness(g, lightSourcePosition, effectiveRadius2);

        // HUD: health bar only (no budget in Act 2)
        renderHUD(g, false);

        // On-screen notifications
        renderNotifications(g);
    }

    /**
     * Renders Act 3: dark background, only lit platforms visible, player, light mask,
     * HUD with budget and light radius indicator.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    public void renderAct3(Graphics2D g) {
        // Background
        g.setColor(BG_ACT3);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        // Absolute darkness when light is off — skip all world geometry
        if (!isLightEffectivelyOn()) {
            g.setColor(new Color(0, 0, 0, 255));
            g.fillRect(0, 0, getWidth(), getHeight());
            renderHUD(g, true);
            return;
        }

        // Draw only lit platforms and other non-platform elements
        if (elements != null) {
            for (GameElement elem : elements) {
                if (!elem.isActive()) continue;
                if (elem instanceof Platform) {
                    Platform plat = (Platform) elem;
                    if (!plat.isLit()) continue;
                }
                elem.render(g);
            }
        }

        // Draw player
        renderPlayer(g);

        if (lightSourcePosition == null) lightSourcePosition = new Point(512, 384);

        // Flicker at < 10% battery
        float batt3 = session.isApprentice() ? GameSession.getInstance().getLightBattery() : 100f;
        boolean flicker3 = batt3 < 10f && (System.currentTimeMillis() / 120) % 2 == 0;
        int effectiveRadius3 = flicker3 ? (int)(lightRadius * 0.85f) : lightRadius;

        renderDarkness(g, lightSourcePosition, effectiveRadius3);

        // HUD: health bar and budget
        renderHUD(g, true);

        // On-screen notifications
        renderNotifications(g);
    }

    /**
     * Renders the boss encounter: game world on left 70%, camera feed on right 30%,
     * corruption overlay, core tracker, arena elements, player, and boss attacks.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    public void renderBoss(Graphics2D g) {
        // Background (screen-space)
        g.setColor(BG_BOSS);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        // World-space camera: follow Wanderer and LERP toward it each frame.
        Camera cam = Camera.getInstance();
        cam.follow(player);
        cam.update(1f / 60f);

        // Translate the world so world-space entity coords render relative to
        // the camera; pop the transform before drawing HUD/architect-panel.
        float ox = cam.getRenderOffsetX();
        float oy = cam.getRenderOffsetY();
        g.translate(ox, oy);
        renderElements(g);
        renderPlayer(g);
        // P8.5 — paint every active server-dispatched BossAttack under the
        // same world-space translation so their bounds line up with
        // platforms, cores, and the Wanderer. Each subclass's own render()
        // handles its glow/fill; we just walk the list and delegate.
        // Shield is intentionally positioned at fixed screen-space panel
        // coords (717, 364) inside its own render(), so it still lands on
        // the architect panel even through the camera translate — the
        // panel is on the right edge regardless of camera offset.
        if (bossAttacks != null && !bossAttacks.isEmpty()) {
            for (BossAttack ba : bossAttacks) {
                if (ba.isActive()) ba.render(g);
            }
        }
        g.translate(-ox, -oy);

        // Right 30% = architect panel (no camera feed)
        int panelX = CAM_PANEL_X;
        int panelW = CANVAS_WIDTH - panelX;
        g.setColor(new java.awt.Color(15, 10, 20));
        g.fillRect(panelX, 0, panelW, getHeight());
        g.setColor(java.awt.Color.RED);
        g.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 16));
        g.drawString("ARCHITECT", panelX + 12, 30);
        if (player != null && player.getCoreHealthBar() != null) {
            int[] coreHealth = player.getCoreHealthBar().getCoreHealth();
            for (int i = 0; i < 4; i++) {
                int ch = coreHealth[i];
                g.setColor(new java.awt.Color(60, 20, 20));
                g.fillRect(panelX + 12, 50 + i * 40, panelW - 24, 28);
                for (int h = 0; h < ch; h++) {
                    g.setColor(java.awt.Color.RED);
                    g.fillRect(panelX + 14 + h * 22, 53 + i * 40, 18, 22);
                }
                g.setColor(java.awt.Color.DARK_GRAY);
                g.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11));
                g.drawString("CORE " + (i + 1), panelX + 14, 48 + i * 40);
            }
        }
        g.setColor(new java.awt.Color(200, 200, 200));
        g.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        g.drawString("ATTACK:", panelX + 12, 220);
        g.setColor(java.awt.Color.ORANGE);
        g.drawString("NONE", panelX + 12, 238);

        // P8.3 — Light mask over game world, centred on the server-authoritative
        // LightBall. We read the world-space lightX/Y cached from the most
        // recent ServerStatePacket, convert it to screen space through the
        // same Camera used for entity rendering, and hand that Point to the
        // existing LightRenderer. Before the first state packet arrives we
        // fall back to the Wanderer's on-screen position so the player is
        // never left in pitch black while the boss-arena handshake completes.
        Point bossLightScreen;
        if (bossLightReceived) {
            bossLightScreen = new Point(
                    cam.worldToScreenX(Math.round(bossLightWorldX)),
                    cam.worldToScreenY(Math.round(bossLightWorldY)));
        } else if (player != null) {
            bossLightScreen = new Point(
                    cam.worldToScreenX(player.getX()),
                    cam.worldToScreenY(player.getY()));
        } else {
            bossLightScreen = new Point(CANVAS_WIDTH / 2, CANVAS_HEIGHT / 2);
        }
        int bossLightRadius = (lightRadius > 0) ? lightRadius : 180;
        // P8.6 — Sight Restriction altar penalty: shrink boss light radius by 25%.
        if (player != null && player.isSightRestricted()) {
            bossLightRadius = Math.round(bossLightRadius * 0.75f);
        }
        // P8.9 — During Radiant Collapse ACTIVE the full arena is revealed:
        // the darkness mask is suppressed and no shadow polygons are drawn so
        // the Wanderer sees every platform and boss silhouette for the 3s
        // window. Both clients read {@code isRadiantActive()} from the same
        // {@code radiantActiveUntilMs} timestamp (stamped locally on the
        // Wanderer, mirrored on the Apprentice via {@link Protocol#RADIANT_ACTIVE}).
        boolean radiantReveal = (player != null) && player.isRadiantActive();

        lightRenderer.renderLightMask(g, bossLightScreen, bossLightRadius,
                lightVelocityFactor, radiantReveal);

        // P8.4 — Cast hard-edged shadow polygons from every arena platform
        // within range of the light. Fed a throwaway LightBall constructed
        // from the authoritative world-space position cached by the most
        // recent ServerStatePacket (the client has no persistent LightBall
        // instance; the server owns the physics). Platforms are filtered out
        // of the active elements list; the method itself handles culling,
        // invisibility, degenerate "light-inside-platform" geometry, and the
        // half-resolution off-screen buffer called for in the Phase 8 plan.
        //
        // P8.9 — Skipped while Radiant Collapse is ACTIVE; the reveal is
        // supposed to expose platforms that would otherwise be in shadow, so
        // suppressing the fan pass is required for the override to read as
        // "full arena visible" rather than "dark mask off but silhouettes
        // still painted".
        if (!radiantReveal && elements != null) {
            List<Platform> plats = new ArrayList<Platform>();
            for (GameElement elem : elements) {
                if (elem instanceof Platform) {
                    plats.add((Platform) elem);
                }
            }
            if (!plats.isEmpty()) {
                LightBall lbView = new LightBall(bossLightWorldX, bossLightWorldY);
                lightRenderer.renderShadowFans(g, lbView, plats);
            }
        }

        // HUD: health bar (no budget in boss)
        renderHUD(g, false);

        // On-screen notifications
        renderNotifications(g);
    }

    /**
     * Renders the cutscene overlay: draws the appropriate game state underneath,
     * then draws the CutsceneRenderer overlay on top.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    public void renderCutscene(Graphics2D g) {
        // Draw the underlying game state based on the current level
        switch (levelState.currentLevel) {
            case 1:  renderAct1(g);  break;
            case 2:  renderAct2(g);  break;
            case 3:  renderAct3(g);  break;
            case 4:  renderBoss(g);  break;
            default: renderAct1(g);  break;
        }

        // Cutscene overlay (renderer removed — show text placeholder)
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.7f));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 1.0f));
        g.setFont(TITLE_FONT);
        g.setColor(GOLD);
        g.drawString("CUTSCENE", 380, 384);
    }

    /**
     * P8.10 — Renders the end-game victory or defeat screen. Displayed after the
     * final cutscene in the endgame chain completes and {@link GameServer} broadcasts
     * the terminal {@link NetworkProtocol.VictoryPacket}. Shows the match outcome,
     * role-specific flavour text, and a simple footer prompt.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    private void renderVictoryScreen(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        boolean wandererWin = (victoryResult == GameServer.VictoryState.WANDERER_WIN);

        // Dim accent line at top
        g.setColor(wandererWin ? new Color(0xFF, 0xD7, 0x00, 80) : new Color(0x66, 0x00, 0xCC, 80));
        g.fillRect(0, 0, CANVAS_WIDTH, 4);

        // Headline
        String headline = wandererWin ? "THE LIGHT RETURNS" : "THE DARK ENDURES";
        g.setFont(new Font("Serif", Font.BOLD, 52));
        g.setColor(wandererWin ? new Color(0xFF, 0xD7, 0x00) : new Color(0xAA, 0x88, 0xFF));
        FontMetrics hfm = g.getFontMetrics();
        g.drawString(headline, (CANVAS_WIDTH - hfm.stringWidth(headline)) / 2, CANVAS_HEIGHT / 2 - 60);

        // Subtitle — role-specific
        boolean isWanderer = (session != null && session.isWanderer());
        String sub;
        if (wandererWin) {
            sub = isWanderer ? "Wanderer Victory" : "You protected the light long enough.";
        } else {
            sub = isWanderer ? "The Architect reclaimed the dark." : "Architect Victory";
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 22));
        g.setColor(new Color(0xCC, 0xC4, 0xAA));
        FontMetrics sfm = g.getFontMetrics();
        g.drawString(sub, (CANVAS_WIDTH - sfm.stringWidth(sub)) / 2, CANVAS_HEIGHT / 2 + 10);

        // Footer
        String footer = "[ Close the window or restart to play again ]";
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(0x66, 0x60, 0x58));
        FontMetrics ffm = g.getFontMetrics();
        g.drawString(footer, (CANVAS_WIDTH - ffm.stringWidth(footer)) / 2, CANVAS_HEIGHT - 50);
    }

    /**
     * Renders the pause menu: current game state at reduced brightness with a centered
     * overlay panel containing pause title and buttons.
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    public void renderPauseMenu(Graphics2D g) {
        // Render current game state into a buffer so we can darken it
        BufferedImage gameBuffer = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D bg = gameBuffer.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        bg.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        // Render the underlying game state
        boolean wasPaused = paused;
        paused = false;
        switch (levelState.currentPhase) {
            case ACT1:  renderAct1(bg);  break;
            case ACT2:  renderAct2(bg);  break;
            case ACT3:  renderAct3(bg);  break;
            case BOSS:  renderBoss(bg);  break;
            default:    renderAct1(bg);  break;
        }
        paused = wasPaused;
        bg.dispose();

        // Darken with RescaleOp (scale 0.4)
        RescaleOp darken = new RescaleOp(0.4f, 0, null);
        BufferedImage darkened = darken.filter(gameBuffer, null);
        g.drawImage(darkened, 0, 0, null);

        // Centered overlay panel (400x300)
        int panelW = 400;
        int panelH = 300;
        int panelX = (CANVAS_WIDTH - panelW) / 2;
        int panelY = (CANVAS_HEIGHT - panelH) / 2;

        // Panel background
        g.setColor(PANEL_BG);
        g.fill(new Rectangle2D.Double(panelX, panelY, panelW, panelH));

        // Panel border
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(1));
        g.draw(new Rectangle2D.Double(panelX, panelY, panelW, panelH));

        // "PAUSED" title
        g.setFont(PAUSE_TITLE);
        g.setColor(GOLD);
        FontMetrics fm = g.getFontMetrics();
        String pauseText = "PAUSED";
        int textX = panelX + (panelW - fm.stringWidth(pauseText)) / 2;
        g.drawString(pauseText, textX, panelY + 50);

        // "Press ESC to resume" subtitle
        g.setFont(PAUSE_BTN);
        g.setColor(new Color(0x88, 0x80, 0x70));
        fm = g.getFontMetrics();
        String resumeHint = "Press ESC to resume";
        int hintX = panelX + (panelW - fm.stringWidth(resumeHint)) / 2;
        g.drawString(resumeHint, hintX, panelY + 78);

        // "Press F to view fragments" subtitle
        String fragHint = "Press F to view fragments";
        int fragHintX = panelX + (panelW - fm.stringWidth(fragHint)) / 2;
        g.drawString(fragHint, fragHintX, panelY + 98);

        // Real JButtons are overlaid by GameFrame at MODAL_LAYER — no painted buttons here.

        // Fragment library overlay (drawn on top of the pause menu when F is pressed)
        if (showFragmentLibrary) {
            renderFragmentLibrary(g);
        }
    }

    /**
     * Renders the fragment library as a full-screen overlay showing collected fragment
     * entries. Delegates to {@link story.FragmentLibrary#renderArchive(Graphics2D)} if
     * available, otherwise renders a placeholder.
     */
    private void renderFragmentLibrary(Graphics2D g) {
        // Dark overlay
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
        g.setColor(new Color(0x0A, 0x0A, 0x0F));
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        // Title
        g.setFont(PAUSE_TITLE);
        g.setColor(GOLD);
        FontMetrics fm = g.getFontMetrics();
        String title = "FRAGMENT LIBRARY";
        g.drawString(title, (CANVAS_WIDTH - fm.stringWidth(title)) / 2, 60);

        // Subtitle
        g.setFont(SUBTITLE_FONT);
        g.setColor(SUBTITLE_CLR);
        fm = g.getFontMetrics();
        String hint = "Press F again to close";
        g.drawString(hint, (CANVAS_WIDTH - fm.stringWidth(hint)) / 2, 85);

        // Render collected fragments from the elements list
        if (elements != null) {
            int yPos = 120;
            g.setFont(new Font("Serif", Font.PLAIN, 14));
            g.setColor(new Color(0xCC, 0xCC, 0xCC));
            for (GameElement el : elements) {
                if (el instanceof LoreFragment) {
                    LoreFragment frag = (LoreFragment) el;
                    if (frag.isCollected()) {
                        g.setColor(GOLD);
                        g.drawString("[" + frag.getFragmentID() + "]", 80, yPos);
                        g.setColor(new Color(0xCC, 0xCC, 0xCC));
                        // Wrap body text to fit
                        String body = frag.getBodyText();
                        if (body != null && body.length() > 100) {
                            body = body.substring(0, 100) + "...";
                        }
                        g.drawString(body != null ? body : "", 180, yPos);
                        yPos += 28;
                    }
                }
            }
            if (yPos == 120) {
                g.setColor(SUBTITLE_CLR);
                g.drawString("No fragments collected yet.", (CANVAS_WIDTH - g.getFontMetrics().stringWidth("No fragments collected yet.")) / 2, 200);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Shared render helpers
    // -------------------------------------------------------------------------

    /**
     * Renders all active game elements by calling their render methods.
     */
    private void renderElements(Graphics2D g) {
        if (elements == null) return;
        for (GameElement elem : elements) {
            if (elem.isActive()
                    || (elem instanceof Platform && !((Platform) elem).fullyGone)) {
                elem.render(g);
            }
        }
    }

    /**
     * Renders the local Wanderer by delegating to {@link Player#render(Graphics2D)},
     * which handles sprite selection, frame indexing, and horizontal flip.
     */
    private void renderPlayer(Graphics2D g) {
        if (player == null) return;
        player.render(g);

        // Collect flash — white oval overlay that fades over 400 ms
        if (player.isCollectFlashActive()) {
            long flashElapsed = System.currentTimeMillis() - player.getCollectFlashStart();
            float flashAlpha = Math.max(0f, 1.0f - (flashElapsed / 400f));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flashAlpha * 0.6f));
            g.setColor(Color.WHITE);
            int cx = player.getX() + 32;
            int cy = player.getY() + 48;
            g.fill(new Ellipse2D.Float(cx - 28, cy - 40, 56, 80));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }
    }

    /**
     * Renders the HUD: health bar (top-left) and optional budget (top-right).
     *
     * @param showBudget whether to show the Apprentice's block budget
     */
    private void renderHUD(Graphics2D g, boolean showBudget) {
        // --- Wanderer-only HUD elements ---
        if (session.isWanderer()) {
            // HP bar — top-left; the 0-100 health bar is the unified survivability meter
            int hpBarY = 12;
            int hpLabelY = 32;
            if (player != null) {
                int maxHp = player.getMaxHealth();
                int curHp = player.getHealth();
                int hpBarW = 100;
                int hpBarH = 6;
                float hpFrac = (maxHp > 0) ? Math.max(0f, Math.min(1f, (float) curHp / maxHp)) : 0f;
                int hpFillW = Math.round(hpBarW * hpFrac);
                Color hpFill = (hpFrac > 0.50f) ? new Color(0x44, 0xBB, 0x55)
                             : (hpFrac > 0.25f) ? new Color(0xAA, 0x88, 0x22)
                                                : new Color(0xBB, 0x22, 0x22);
                g.setColor(new Color(0x1A, 0x1A, 0x20));
                g.fillRect(8, hpBarY, hpBarW, hpBarH);
                g.setColor(hpFill);
                if (hpFillW > 0) g.fillRect(8, hpBarY, hpFillW, hpBarH);
                g.setColor(new Color(0x3A, 0x3A, 0x48));
                g.drawRect(8, hpBarY, hpBarW, hpBarH);
                g.setFont(new Font("Monospaced", Font.PLAIN, 9));
                g.setColor(new Color(0x88, 0x80, 0x70));
                g.drawString("HP " + curHp + "/" + maxHp, 8, hpLabelY);
            }

            // P8.7 — Faithful meter: 5 pips next to HP (only shown in BOSS phase)
            if (player != null && levelState.currentPhase == LevelState.GamePhase.BOSS) {
                int faithful = player.getFaithful();
                int faithMax = Player.FAITHFUL_MAX;
                int pipW = 10;
                int pipH = 10;
                int pipGap = 3;
                int faithStartX = 116; // right of the HP bar (bar ends at 8+100=108)
                int faithY = hpBarY;
                for (int i = 0; i < faithMax; i++) {
                    Color pipFill = (i < faithful)
                            ? new Color(0xE8, 0xD0, 0x60)   // gold — earned pip
                            : new Color(0x1A, 0x1A, 0x20);  // dark — empty pip
                    g.setColor(pipFill);
                    g.fillOval(faithStartX + i * (pipW + pipGap), faithY, pipW, pipH);
                    g.setColor(new Color(0x3A, 0x3A, 0x48));
                    g.drawOval(faithStartX + i * (pipW + pipGap), faithY, pipW, pipH);
                }
                g.setFont(new Font("Monospaced", Font.PLAIN, 9));
                g.setColor(new Color(0x88, 0x80, 0x70));
                g.drawString("FAITH", faithStartX, hpLabelY);
            }

            // Ability bar — bottom-left: 5 icon slots
            if (player != null) {
                String[] labels = {"M", "P", "D", "C", "S"};
                boolean[] unlocked = {
                    player.isHasMelee(), player.isHasProjectile(), player.isHasDodge(),
                    player.isHasWallCling(), player.isHasShadowDash()
                };
                for (int i = 0; i < 5; i++) {
                    int ax = 8 + i * (28 + 6);
                    Color fill = unlocked[i] ? new Color(0x1A, 0x1A, 0x20) : new Color(0x0A, 0x0A, 0x0F);
                    Color stroke = unlocked[i] ? new Color(0x2A, 0x2A, 0x35) : new Color(0x1A, 0x1A, 0x1A);
                    g.setColor(fill);
                    g.fill(new RoundRectangle2D.Float(ax, 736, 28, 24, 3, 3));
                    g.setColor(stroke);
                    g.draw(new RoundRectangle2D.Float(ax, 736, 28, 24, 3, 3));
                    g.setFont(new Font("Monospaced", Font.PLAIN, 9));
                    g.setColor(new Color(0x88, 0x80, 0x70));
                    FontMetrics fma = g.getFontMetrics();
                    int lx = ax + (28 - fma.stringWidth(labels[i])) / 2;
                    g.drawString(labels[i], lx, 736 + 24 + fma.getAscent());
                }
            }
        }

        // --- Apprentice-only HUD elements ---
        if (session.isApprentice()) {
            GameSession gs = GameSession.getInstance();

            // Budget bar — top-right: 12 segments
            if (showBudget) {
                int totalSegs = 12;
                int segW = 13;
                int segH = 8;
                int segGap = 2;
                int rightEdge = 1008;
                int barW = totalSegs * segW + (totalSegs - 1) * segGap;
                int barStartX = rightEdge - barW;
                int budget = gs.getBlockBudget();
                int filledSegs = Math.min(totalSegs,
                        (int) Math.ceil(budget * totalSegs / 12.0));
                g.setFont(new Font("Monospaced", Font.PLAIN, 10));
                g.setColor(new Color(0x88, 0x80, 0x70));
                FontMetrics fmb = g.getFontMetrics();
                g.drawString("BLOCKS", rightEdge - fmb.stringWidth("BLOCKS"), 78);
                for (int i = 0; i < totalSegs; i++) {
                    int sx = barStartX + i * (segW + segGap);
                    Color fill = (i < filledSegs) ? new Color(0x5A, 0x8A, 0x9A) : new Color(0x1A, 0x1A, 0x20);
                    g.setColor(fill);
                    g.fillRect(sx, 46, segW, segH);
                    g.setColor(new Color(0x2A, 0x4A, 0x5A));
                    g.drawRect(sx, 46, segW, segH);
                }
            }

            // Block type indicator — tile sprite preview + label
            String blockType = gs.getCurrentBlockType();
            String previewPath = "resources/sprites/tiles/tile_" + blockType.toLowerCase() + ".png";
            java.awt.image.BufferedImage preview = SpriteLoader.getInstance().load(previewPath);
            if (preview != null) {
                g.drawImage(preview, 20, getHeight() - 70, 40, 40, null);
            }
            g.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 14));
            g.setColor(new java.awt.Color(255, 220, 100));
            g.drawString(blockType, 68, getHeight() - 44);
            g.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
            g.setColor(new java.awt.Color(150, 220, 255));
            g.drawString("BUDGET: " + gs.getBlockBudget(), 20, getHeight() - 24);

            // Battery bar — bottom-right (ACT2/ACT3 only)
            {
                String act = session.getCurrentAct();
                boolean isLightAct = act != null
                    && (act.equalsIgnoreCase("ACT2") || act.equalsIgnoreCase("ACT3"));
                if (isLightAct) {
                    renderBatteryBar(g, gs);
                }
            }
        }
    }

    /**
     * Draws the Apprentice's battery bar at the bottom-right corner.
     * Called only when the current act uses the light system (ACT2/ACT3).
     */
    private void renderBatteryBar(Graphics2D g, GameSession gs) {
        float batt = gs.getLightBattery();
        boolean lightOn = MouseApprentice.getInstance().isLightActive() && batt > 0;
        int barH = 80;
        int fillH = (int)(barH * batt / 100f);
        int bx = CANVAS_WIDTH - 28;
        int by = CANVAS_HEIGHT - 100;
        Color battFill = batt > 50f ? new Color(0xC9, 0xA8, 0x4C)
                       : batt > 25f ? new Color(0xE0, 0x80, 0x20)
                       :              new Color(0xE0, 0x30, 0x30);
        g.setColor(new Color(0x1A, 0x1A, 0x20));
        g.fillRoundRect(bx, by, 14, barH, 3, 3);
        g.setColor(lightOn ? battFill : new Color(0x44, 0x44, 0x44));
        g.fillRoundRect(bx, by + barH - fillH, 14, fillH, 3, 3);
        g.setColor(new Color(0x3A, 0x3A, 0x48));
        g.drawRoundRect(bx, by, 14, barH, 3, 3);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 9));
        g.setColor(new Color(0x88, 0x80, 0x60));
        g.drawString("PWR", bx - 2, by + barH + 12);
        g.setColor(lightOn ? new Color(0xFF, 0xE0, 0x60) : new Color(0x66, 0x66, 0x66));
        g.drawString(lightOn ? "ON" : "OFF", bx - 1, by - 4);
    }

    /**
     * Renders a semi-transparent ghost block at the Apprentice's gesture position
     * to preview platform placement.
     */
    private void renderGhostBlock(Graphics2D g) {
        if (gesturePosition == null) return;

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        g.setColor(GOLD);
        g.fill(new Rectangle2D.Double(gesturePosition.x - 16, gesturePosition.y - 16,
                32, 32));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }

    /**
     * Renders the breadcrumb trail as fading gold circles for Act 2 navigation.
     */
    private void renderBreadcrumbs(Graphics2D g) {
        if (breadcrumbs == null || breadcrumbAlphas == null) return;

        g.setColor(GOLD);
        for (int i = 0; i < breadcrumbs.length; i++) {
            if (breadcrumbs[i] == null || breadcrumbAlphas[i] <= 0.0f) continue;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    breadcrumbAlphas[i]));
            g.fill(new Ellipse2D.Float(breadcrumbs[i].x - 3, breadcrumbs[i].y - 3, 6, 6));
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }

    // -------------------------------------------------------------------------
    // P8.6 — altar overlay renderer
    // -------------------------------------------------------------------------

    /**
     * Draws the pre-boss altar choice panel centred on screen. Shows two
     * options: [1] POWER_SURGE (max HP +10) and [2] SIGHT_RESTRICTION
     * (lives +1, boss light radius −25%). Rendered on top of the active game
     * scene so the Wanderer can read it without losing world context.
     */
    private void renderAltarOverlay(Graphics2D g) {
        int panelW = 460;
        int panelH = 210;
        int ox = (CANVAS_WIDTH  - panelW) / 2;
        int oy = (CANVAS_HEIGHT - panelH) / 2;

        // Dark semi-transparent background
        Composite prev = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.88f));
        g.setColor(new Color(0x08, 0x05, 0x14));
        g.fillRoundRect(ox, oy, panelW, panelH, 14, 14);
        g.setComposite(prev);

        // Border
        g.setColor(new Color(0x66, 0x33, 0x99));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(ox, oy, panelW, panelH, 14, 14);

        // Title
        g.setFont(new Font("Monospaced", Font.BOLD, 17));
        g.setColor(new Color(0xCC, 0x99, 0xFF));
        String title = "THE ALTAR";
        FontMetrics fmT = g.getFontMetrics();
        g.drawString(title, CANVAS_WIDTH / 2 - fmT.stringWidth(title) / 2, oy + 32);

        // Divider
        g.setColor(new Color(0x44, 0x22, 0x66));
        g.drawLine(ox + 16, oy + 42, ox + panelW - 16, oy + 42);

        // Options
        g.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g.setColor(new Color(0xDD, 0xCC, 0xFF));
        g.drawString("[1]  Offer a Fragment", ox + 20, oy + 70);
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g.setColor(new Color(0x99, 0x88, 0xAA));
        g.drawString("      Max HP +10", ox + 20, oy + 88);

        g.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g.setColor(new Color(0xDD, 0xCC, 0xFF));
        g.drawString("[2]  Accept Sight Restriction", ox + 20, oy + 118);
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g.setColor(new Color(0x99, 0x88, 0xAA));
        g.drawString("      Lives +1, Boss light radius -25%", ox + 20, oy + 136);

        // Hint
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(0x55, 0x44, 0x66));
        String hint = "Press 1 or 2 to choose";
        FontMetrics fmH = g.getFontMetrics();
        g.drawString(hint, CANVAS_WIDTH / 2 - fmH.stringWidth(hint) / 2, oy + panelH - 16);

        g.setStroke(new BasicStroke(1f)); // restore default stroke
    }

    /**
     * P8.8 — renders the active {@link StunMinigame} overlay. Delegates to
     * {@link StunMinigame#render(Graphics2D, int, int)} which short-circuits
     * when no opportunity window is open. Wanderer-only visually; the
     * Apprentice sees nothing because their session never opens the window
     * (see {@code Protocol.STUN_OPPORTUNITY} handler in {@link GameStarter}).
     */
    private void renderStunOverlay(Graphics2D g) {
        if (stunMinigame == null) return;
        stunMinigame.render(g, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    /**
     * P8.8 — renders a centred "ARCHITECT STUNNED" banner while
     * {@link #architectStunnedUntilMs} is in the future. Both roles see this
     * banner so the Apprentice gets visual feedback that their attacks are
     * suppressed; the countdown bar doubles as a ticking clock until attacks
     * resume.
     */
    private void renderArchitectStunnedBanner(Graphics2D g) {
        long now = System.currentTimeMillis();
        long remainingMs = architectStunnedUntilMs - now;
        if (remainingMs <= 0L) return;

        int bannerW = 360;
        int bannerH = 56;
        int ox = (CANVAS_WIDTH - bannerW) / 2;
        int oy = 96; // above the notifications stack (yOff = 110 in renderNotifications)

        Composite prevC = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.92f));
        g.setColor(new Color(0x10, 0x05, 0x20));
        g.fillRoundRect(ox, oy, bannerW, bannerH, 10, 10);
        g.setComposite(prevC);

        Stroke prevS = g.getStroke();
        g.setColor(new Color(0xAA, 0x66, 0xFF));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(ox, oy, bannerW, bannerH, 10, 10);
        g.setStroke(prevS);

        g.setFont(new Font("Monospaced", Font.BOLD, 16));
        g.setColor(new Color(0xEE, 0xDD, 0xFF));
        String title = "ARCHITECT STUNNED";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, ox + (bannerW - fm.stringWidth(title)) / 2, oy + 26);

        // Countdown bar
        float frac = remainingMs / (float) StunMinigame.STUN_DURATION_MS;
        frac = Math.max(0f, Math.min(1f, frac));
        int cbX = ox + 16;
        int cbY = oy + 36;
        int cbW = bannerW - 32;
        int cbH = 6;
        g.setColor(new Color(0x1A, 0x10, 0x30));
        g.fillRect(cbX, cbY, cbW, cbH);
        g.setColor(new Color(0xBB, 0x88, 0xFF));
        g.fillRect(cbX, cbY, (int) (cbW * frac), cbH);
        g.setColor(new Color(0x44, 0x22, 0x66));
        g.drawRect(cbX, cbY, cbW, cbH);
    }

    /**
     * Renders active notifications as fading gold text near the top of the screen.
     * Each notification lasts 3 seconds with a 1-second fade-out at the end.
     */
    private void renderNotifications(Graphics2D g) {
        long now = System.currentTimeMillis();
        int yOff = 110;
        java.util.Iterator<String[]> it = notifications.iterator();
        while (it.hasNext()) {
            String[] entry = it.next();
            long start = Long.parseLong(entry[1]);
            long elapsed = now - start;
            if (elapsed > 3000L) {
                notifications.remove(entry);
                continue;
            }
            float alpha = (elapsed > 2000L)
                    ? 1.0f - ((elapsed - 2000L) / 1000f)
                    : 1.0f;
            alpha = Math.max(0f, Math.min(1f, alpha));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.setFont(new Font("Serif", Font.ITALIC, 16));
            g.setColor(GOLD);
            FontMetrics fm = g.getFontMetrics();
            int tx = (CANVAS_WIDTH - fm.stringWidth(entry[0])) / 2;
            g.drawString(entry[0], tx, yOff);
            yOff += 22;
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }

    /**
     * Renders a semi-transparent full-screen overlay with a centered status message.
     * Used to communicate network connection problems to the player without hiding the
     * game world beneath.
     *
     * @param g   the {@link Graphics2D} context; must not be {@code null}
     * @param msg the message text to display; must not be {@code null}
     */
    private void renderConnectionOverlay(Graphics2D g, String msg) {
        // Dark translucent backdrop
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.65f));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        // Centered message
        Font overlayFont = new Font("SansSerif", Font.BOLD, 22);
        g.setFont(overlayFont);
        g.setColor(new Color(0xFF, 0xD7, 0x00)); // gold
        FontMetrics fm = g.getFontMetrics();
        int textX = (CANVAS_WIDTH  - fm.stringWidth(msg)) / 2;
        int textY = (CANVAS_HEIGHT / 2) + fm.getAscent() / 2;
        g.drawString(msg, textX, textY);
    }

    // =========================================================================
    // Gesture debug overlay stub
    // =========================================================================

}
