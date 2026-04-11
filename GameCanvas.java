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

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.AlphaComposite;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.util.List;
import java.util.Random;


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

    // -------------------------------------------------------------------------
    // Debug flag — set to false before submission
    // -------------------------------------------------------------------------

    /**
     * When {@code true} a gesture debug overlay is drawn in every scene so the
     * Apprentice developer can verify GestureInput output in real time.
     * <b>Set to {@code false} before final submission.</b>
     */
    private static final boolean DEBUG_GESTURE = true;

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

    // ---- Light battery system (FIX 6) ----
    private float lightPower = 1.0f;
    private static final float MAX_DRAIN_RATE = 0.0008f;
    private static final float MIN_DRAIN_RATE = 0.0002f;
    private static final float REGEN_RATE = 0.0004f;

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

    /**
     * Screen-space x-coordinate of the remote Wanderer, received from the server and
     * used by the Apprentice client to render a ghost of the Wanderer's position.
     * {@code -1} means no data has arrived yet.
     */
    private volatile int remotePlayerX = -1;

    /** Screen-space y-coordinate of the remote Wanderer for ghost rendering. */
    private volatile int remotePlayerY = -1;

    /** Health of the remote Wanderer, used to tint the ghost sprite. */
    private volatile int remotePlayerHealth = 0;

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

        // C1 — Mouse tracking for offline fallback
        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { mousePosition = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) { mousePosition = e.getPoint(); }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!session.isOffline) return;
                // FIX 3 — sync currentPhase from levelState before checking
                if (levelState != null) currentPhase = levelState.currentPhase;
                System.out.println("Mouse pressed: button=" + e.getButton()
                        + " pos=" + e.getPoint() + " phase=" + currentPhase);
                if (InputRouter.getInstance() == null) {
                    System.out.println("ERROR: InputRouter.getInstance() is null");
                    return;
                }
                if (currentPhase == LevelState.GamePhase.ACT1 || currentPhase == LevelState.GamePhase.ACT3) {
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        if (InputRouter.getInstance() != null)
                            InputRouter.getInstance().routeGesture("OPEN_PALM", mousePosition);
                    } else if (e.getButton() == MouseEvent.BUTTON3) {
                        if (InputRouter.getInstance() != null)
                            InputRouter.getInstance().routeGesture("CLOSED_FIST", mousePosition);
                    }
                }
            }
        });
        System.out.println("GameCanvas: mouse listeners registered");

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

    /** Returns the current light power (0.0 to 1.0). */
    public float getLightPower() {
        return lightPower;
    }

    /** Sets the light power (0.0 to 1.0). */
    public void setLightPower(float power) {
        this.lightPower = power;
    }

    /**
     * Updates the light battery: drains when light is active, regens when off.
     * Called each game tick during ACT2/ACT3.
     */
    public void updateLightBattery(boolean lightActive, int currentRadius) {
        if (lightActive && lightPower > 0) {
            float drainRate = MIN_DRAIN_RATE + (currentRadius / 200f) * (MAX_DRAIN_RATE - MIN_DRAIN_RATE);
            lightPower = Math.max(0f, lightPower - drainRate);
        } else if (!lightActive) {
            lightPower = Math.min(1.0f, lightPower + REGEN_RATE);
        }
    }

    /** Sets the player reference used for rendering. */
    public void setPlayer(Player player) {
        this.player = player;
    }

    /** Sets the list of active game elements. */
    public void setElements(List<GameElement> elements) {
        this.elements = elements;
    }

    /** Sets the gesture input reference (no-op: gesture system removed). */
    public void setGestureInput(Object gestureInput) {
        // no-op: gesture system removed
    }

    /** Sets the current light source position and radius. */
    public void setLightSource(Point source, int radius, float velocityFactor) {
        this.lightSource = source;
        this.lightSourcePosition = source;
        this.lightRadius = radius;
        this.lightVelocityFactor = velocityFactor;
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
     * Updates the light-source position from a server-relayed Apprentice gesture without
     * changing the radius or velocity factor. Called by the WANDERER client when it
     * receives a {@link server.NetworkProtocol.ServerStatePacket} whose
     * {@code latestGesture} field carries the Apprentice's current pointer coordinates.
     *
     * @param p the new light-source position in canvas space; must not be {@code null}
     */
    public void setLightPosition(Point p) {
        this.lightSource = p;
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
    public void setRemotePlayer(int x, int y, int health) {
        this.remotePlayerX      = x;
        this.remotePlayerY      = y;
        this.remotePlayerHealth = health;
    }

    /** Returns null: cutscene renderer removed. */
    public Object getCutsceneRenderer() {
        return null;
    }

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
                case CALIBRATION:
                    renderCalibration(g2d);
                    break;
                default:
                    renderMainMenu(g2d);
                    break;
            }

            // Remote Wanderer ghost (Apprentice client only, when data has arrived)
            if (remotePlayerX >= 0) {
                renderRemoteGhost(g2d);
            }
        }

        // Gesture debug overlay — all phases.
        // Remove by setting DEBUG_GESTURE = false before submission.
        if (DEBUG_GESTURE) {
            drawGestureDebugOverlay(g2d);
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

        // DEBUG: hitbox outlines
        if (DEBUG_GESTURE) {
            g.setColor(new Color(255, 0, 0, 80));
            if (elements != null) {
                for (GameElement el : elements) {
                    if (el.isActive()) {
                        Rectangle b = el.getBounds();
                        g.drawRect(b.x, b.y, b.width, b.height);
                    }
                }
            }
            if (player != null) {
                g.setColor(new Color(0, 255, 0, 80));
                Rectangle pb = player.getBounds();
                g.drawRect(pb.x, pb.y, pb.width, pb.height);
                g.setColor(new Color(0, 100, 255, 80));
                Rectangle hb = player.getHorizontalBounds();
                g.drawRect(hb.x, hb.y, hb.width, hb.height);
            }
        }

        // Draw player
        renderPlayer(g);

        // Ghost block preview
        renderGhostBlock(g);

        // HUD: health bar top-left, budget top-right, timer top-center
        renderHUD(g, true, true);

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

        // Always use mouse position for light source
        if (mousePosition == null) mousePosition = new Point(512, 384);
        lightSourcePosition = mousePosition;
        if (lightSourcePosition == null) lightSourcePosition = new Point(512, 384);

        // Light mask using lightSourcePosition with battery-adjusted radius
        int effectiveRadius = (int)(lightRadius * lightPower);
        if (effectiveRadius > 0) {
            lightRenderer.renderLightMask(g, lightSourcePosition, effectiveRadius);
        } else {
            // Full darkness — cover entire screen
            g.setColor(new Color(0, 0, 0, 240));
            g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        }

        // Battery HUD
        renderBatteryHUD(g);

        // HUD: health bar and timer only (no budget in Act 2)
        renderHUD(g, false, true);

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

        // Always use mouse position for light source (Act 3)
        if (mousePosition == null) mousePosition = new Point(512, 384);
        lightSourcePosition = mousePosition;
        if (lightSourcePosition == null) lightSourcePosition = new Point(512, 384);

        // Light mask using lightSourcePosition with battery-adjusted radius
        int effectiveRadius3 = (int)(lightRadius * lightPower);
        if (effectiveRadius3 > 0) {
            lightRenderer.renderLightMask(g, lightSourcePosition, effectiveRadius3);
        } else {
            // Full darkness — cover entire screen
            g.setColor(new Color(0, 0, 0, 240));
            g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        }

        // Battery HUD
        renderBatteryHUD(g);

        // HUD: health bar, budget, and timer
        renderHUD(g, true, true);

        // Light radius indicator (small text near HUD)
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
        g.setFont(BUTTON_FONT);
        g.setColor(GOLD);
        g.drawString("Light: " + effectiveRadius3 + "px", 10, CANVAS_HEIGHT - 20);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

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
        // Background
        g.setColor(BG_BOSS);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        // Draw arena elements in game world (left 70%)
        renderElements(g);

        // Draw player
        renderPlayer(g);

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

        // Light mask over game world
        lightRenderer.renderLightMask(g, lightSource, lightRadius, lightVelocityFactor);

        // HUD: health bar and timer (no budget in boss)
        renderHUD(g, false, true);

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
     * Renders the player using AnimationController to get the correct animation frame.
     */
    private void renderPlayer(Graphics2D g) {
        if (player == null) return;

        // While dead, render death animation at death position
        if (player.isDead()) {
            int dx = player.getDeathX();
            int dy = player.getDeathY();
            long dt = player.getDeathTimer();

            // Fade out over 1.2 seconds
            float alpha = Math.max(0f, 1.0f - (dt / 1200f));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            BufferedImage deathFrame = SpriteLoader.getInstance().load("resources/sprites/wanderer/wanderer_death.png");
            if (deathFrame != null) {
                g.drawImage(deathFrame, dx, dy, null);
            } else {
                // Fallback: draw a fading red silhouette
                g.setColor(new Color(200, 50, 50));
                g.fillRect(dx + 20, dy + 8, 24, 80);
            }
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            return;
        }

        String animName = getPlayerAnimationName();
        BufferedImage frame = SpriteLoader.getInstance().load("resources/sprites/wanderer/" + animName + ".png");

        if (frame != null) {
            int drawX = player.getX();
            int drawY = player.getY();

            // Flip horizontally if facing left
            if (player.getFacingDirection() < 0) {
                g.drawImage(frame, drawX + frame.getWidth(), drawY,
                        -frame.getWidth(), frame.getHeight(), null);
            } else {
                g.drawImage(frame, drawX, drawY, null);
            }
        }

        // Collect flash — white oval overlay that fades over 400ms
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
     * Determines the current animation name based on the player's state.
     */
    private String getPlayerAnimationName() {
        if (player == null) return "wanderer_idle";

        if (!player.isAlive()) return "wanderer_death";
        if (player.isDodging()) return "wanderer_dodge";
        if (player.isGrounded()) {
            if (Math.abs(player.getVelX()) > 0.1f) return "wanderer_run";
            return "wanderer_idle";
        }
        if (player.getVelY() < -2.0f) return "wanderer_jump";
        if (player.getVelY() > 2.0f) return "wanderer_fall";

        return "wanderer_idle";
    }

    /**
     * Renders the HUD: health bar (top-left), optional budget (top-right),
     * and timer (top-center).
     *
     * @param showBudget whether to show the Apprentice's block budget
     * @param showTimer  whether to show the countdown timer
     */
    /** Renders the light battery bar on the left side (Act 2/3 only). */
    private void renderBatteryHUD(Graphics2D g) {
        // Battery outline
        g.setColor(new Color(0x3a, 0x3a, 0x48));
        g.fillRoundRect(8, 200, 14, 120, 4, 4);
        g.setColor(new Color(0xc9, 0xa8, 0x4c, 100));
        g.drawRoundRect(8, 200, 14, 120, 4, 4);
        // Battery fill — color shifts gold → orange → red as power depletes
        int fillHeight = (int)(lightPower * 116);
        Color battColor = lightPower > 0.5f ? new Color(0xc9, 0xa8, 0x4c) :
                          lightPower > 0.25f ? new Color(0xe0, 0x80, 0x20) :
                          new Color(0xe0, 0x30, 0x30);
        g.setColor(battColor);
        g.fillRoundRect(9, 200 + (120 - fillHeight), 12, fillHeight, 3, 3);
        // Battery label
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 9));
        g.setColor(new Color(0x88, 0x80, 0x60));
        g.drawString("PWR", 6, 330);
    }

    private void renderHUD(Graphics2D g, boolean showBudget, boolean showTimer) {
        // --- Lives bar — top-left: 3 pips as small rounded rects ---
        int maxLives = 3;
        int lives = (player != null) ? player.getLives() : 3;
        for (int i = 0; i < maxLives; i++) {
            int px = 8 + i * (18 + 4);
            Color fill = (i < lives) ? new Color(0xC9, 0xA8, 0x4C) : new Color(0x1A, 0x1A, 0x20);
            g.setColor(fill);
            g.fill(new RoundRectangle2D.Float(px, 8, 18, 14, 3, 3));
            g.setColor(new Color(0x3A, 0x3A, 0x48));
            g.draw(new RoundRectangle2D.Float(px, 8, 18, 14, 3, 3));
        }
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g.setColor(new Color(0x88, 0x80, 0x70));
        g.drawString("LIVES", 8, 36);

        // Attempts remaining
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        g.setColor(new Color(0x88, 0x70, 0x50));
        g.drawString("ATTEMPTS: " + (player != null ? player.getTotalAttempts() : 3), 8, 50);

        // --- Timer — top-center: text display with thin arc beneath ---
        if (showTimer) {
            long secs = levelState.timeRemainingMs / 1000;
            long mins = secs / 60;
            secs = secs % 60;
            String timeText = String.format("%d:%02d", mins, secs);
            Font timerFont = new Font("Serif", Font.PLAIN, 16);
            g.setFont(timerFont);
            g.setColor(new Color(0xC9, 0xA8, 0x4C));
            FontMetrics fm = g.getFontMetrics();
            int textW = fm.stringWidth(timeText);
            g.drawString(timeText, 512 - textW / 2, 22);

            // Thin line beneath the text, opacity decreasing as time runs out
            float timeRatio = Math.max(0f, Math.min(1f, levelState.timeRemainingMs / 300000f));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, timeRatio));
            g.setStroke(new BasicStroke(1.5f));
            g.setColor(new Color(0xC9, 0xA8, 0x4C));
            int lineHalfW = (int) (40 * timeRatio);
            g.draw(new Line2D.Float(512 - lineHalfW, 28, 512 + lineHalfW, 28));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g.setStroke(new BasicStroke(1.0f));
        }

        // --- Budget bar — top-right: 12 segments, right edge at x=1008 ---
        if (showBudget) {
            int totalSegs = 12;
            int segW = 13;
            int segH = 8;
            int segGap = 2;
            int rightEdge = 1008;
            int barW = totalSegs * segW + (totalSegs - 1) * segGap; // 12*13 + 11*2 = 178
            int barStartX = rightEdge - barW; // 1008 - 178 = 830
            int filledSegs = Math.min(totalSegs,
                    (int) Math.ceil(levelState.blockBudget * totalSegs / 20.0));
            // Label "BLOCKS" right-aligned at y=78
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

        // --- Ability bar — bottom-left: 5 icon slots ---
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
                // Label beneath slot
                g.setFont(new Font("Monospaced", Font.PLAIN, 9));
                g.setColor(new Color(0x88, 0x80, 0x70));
                FontMetrics fma = g.getFontMetrics();
                int lx = ax + (28 - fma.stringWidth(labels[i])) / 2;
                g.drawString(labels[i], lx, 736 + 24 + fma.getAscent());
            }
        }
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

    // -------------------------------------------------------------------------
    // Calibration screen (B3) — stub (gesture system removed)
    // -------------------------------------------------------------------------

    private void renderCalibration(Graphics2D g2d) {
        g2d.setColor(BG_MENU);
        g2d.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        g2d.setFont(TITLE_FONT);
        g2d.setColor(GOLD);
        g2d.drawString("CALIBRATION", 300, 384);
    }

    private void renderRemoteGhost(Graphics2D g) {
        float hpFraction = Math.max(0f, Math.min(1f, remotePlayerHealth / 5f));
        // Interpolate amber→red based on health
        int r = 220;
        int gVal = (int) (160 * hpFraction);
        int b   = 20;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.40f));
        g.setColor(new Color(r, gVal, b));
        g.fill(new Rectangle2D.Double(remotePlayerX, remotePlayerY, 24, 36));
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

    /** Stub: gesture debug overlay (gesture system removed). */
    private void drawGestureDebugOverlay(Graphics2D g2d) {
        if (session != null && session.isOffline) {
            g2d.setColor(new Color(0xc9, 0xa8, 0x4c, 120));
            g2d.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
            g2d.drawString("OFFLINE: LEFT=place  RIGHT=remove  TAB=cycle  MOVE=light", 8, 758);
        }
    }

    // =========================================================================
    // Inner class — GestureListener
    // =========================================================================

    /** Stub inner class (gesture system removed). */
    public class GestureListener extends java.awt.event.MouseAdapter {
        // no-op: gesture system removed
    }
}
