






















































import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import javax.swing.*;
import javax.swing.Timer;
import java.util.*;
import java.util.List;

public class GameCanvas extends JComponent {






    public static final int CANVAS_WIDTH = 1024;


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
    private static final int CAM_PANEL_W = CANVAS_WIDTH - CAM_PANEL_X;
    private static final int CAM_PANEL_H = CANVAS_HEIGHT;

    private static final int PARTICLE_COUNT = 20;





    private LevelState levelState;
    private Player player;
    private List<GameElement> elements;

    private LightRenderer lightRenderer;


    private Point lightSource;

    private int lightRadius;

    private float lightVelocityFactor;













    private float bossLightWorldX = Camera.ARENA_W / 2f;





    private float bossLightWorldY = Camera.ARENA_H / 2f;








    private boolean bossLightReceived = false;












    private List<BossAttack> bossAttacks;













    private StunMinigame stunMinigame;







    private volatile long architectStunnedUntilMs = 0L;






    private boolean altarOpen = false;


    private int altarId = -1;






    private volatile String pendingAltarChoice = null;


    private Point[] breadcrumbs;

    private float[] breadcrumbAlphas;


    private boolean paused;


    private boolean showFragmentLibrary;


    private final java.util.concurrent.CopyOnWriteArrayList<String[]> notifications =
            new java.util.concurrent.CopyOnWriteArrayList<>();


    private String ipInputText;


    private Point mousePosition = new Point(512, 384);


    private Point lightSourcePosition = new Point(512, 384);


    private LevelState.GamePhase currentPhase = LevelState.GamePhase.MENU;


    private GameServer.VictoryState victoryResult;


    private LobbyState lobbyState = new LobbyState();








    private volatile String partnerDCRole = null;






    private volatile long reconnectCountdown = -1;






    private final java.awt.Rectangle wandererCardBounds   = new java.awt.Rectangle(162, 230, 280, 320);





    private final java.awt.Rectangle apprenticeCardBounds = new java.awt.Rectangle(582, 230, 280, 320);



    private Point gesturePosition;







    private volatile String connectionOverlay;







    private volatile String connectionStatus = "";







    private final GameSession session = GameSession.getInstance();


    private float[] particleX;
    private float[] particleY;
    private float[] particleSpeed;
    private Random particleRng;






    private Timer menuTimer;










    public GameCanvas() {
        Dimension size = new Dimension(CANVAS_WIDTH, CANVAS_HEIGHT);
        setPreferredSize(size);
        setMaximumSize(size);
        setDoubleBuffered(true);
        setFocusable(true);


        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { mousePosition = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) { mousePosition = e.getPoint(); }
        });


        levelState = new LevelState();
        lightRenderer = new LightRenderer();
        lightSource = new Point(CANVAS_WIDTH / 2, CANVAS_HEIGHT / 2);
        lightRadius = 180;
        lightVelocityFactor = 0.0f;
        paused = false;
        ipInputText = "";
        gesturePosition = null;


        particleRng = new Random(42);
        particleX = new float[PARTICLE_COUNT];
        particleY = new float[PARTICLE_COUNT];
        particleSpeed = new float[PARTICLE_COUNT];
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particleX[i] = particleRng.nextFloat() * CANVAS_WIDTH;
            particleY[i] = particleRng.nextFloat() * CANVAS_HEIGHT;
            particleSpeed[i] = 0.3f + particleRng.nextFloat() * 0.7f;
        }


        menuTimer = new Timer(16, e -> repaint());
        menuTimer.start();
    }






    public void setLevelState(LevelState levelState) {
        this.levelState = levelState;
        this.currentPhase = levelState.currentPhase;


        if (levelState.currentPhase != LevelState.GamePhase.MENU && menuTimer != null && menuTimer.isRunning()) {
            menuTimer.stop();
        }
    }


    public void setPhase(LevelState.GamePhase phase) {
        this.currentPhase = phase;
        this.levelState.currentPhase = phase;
        if (phase != LevelState.GamePhase.MENU && menuTimer != null && menuTimer.isRunning()) {
            menuTimer.stop();
        }
        repaint();
    }


    public void setLightSourcePosition(Point p) {
        this.lightSourcePosition = p;
    }


    public Point getLightSourcePosition() {
        return lightSourcePosition;
    }


    public void setLightRadius(int r) {
        this.lightRadius = r;
    }


    public void setPlayer(Player player) {
        this.player = player;
    }


    public void setElements(List<GameElement> elements) {
        this.elements = elements;
    }












    public void setBossAttacks(List<BossAttack> attacks) {
        this.bossAttacks = attacks;
    }









    public void setStunMinigame(StunMinigame m) {
        this.stunMinigame = m;
    }









    public void setArchitectStunnedUntilMs(long ms) {
        this.architectStunnedUntilMs = ms;
    }












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


    public void closeAltarOverlay() {
        altarOpen = false;
        altarId   = -1;
        pendingAltarChoice = null;
        javax.swing.InputMap im = getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        im.remove(javax.swing.KeyStroke.getKeyStroke('1'));
        im.remove(javax.swing.KeyStroke.getKeyStroke('2'));
    }


    public boolean isAltarOpen() { return altarOpen; }






    public String getAndClearPendingAltarChoice() {
        String c = pendingAltarChoice;
        pendingAltarChoice = null;
        return c;
    }


    public void setLightSource(Point source, int radius, float velocityFactor) {
        this.lightSource = source;
        this.lightSourcePosition = source;
        this.lightRadius = radius;
        this.lightVelocityFactor = velocityFactor;
    }













    public void setBossLightWorldPosition(float worldX, float worldY) {
        this.bossLightWorldX = worldX;
        this.bossLightWorldY = worldY;
        this.bossLightReceived = true;
    }


    public void setVictoryResult(GameServer.VictoryState result) {
        this.victoryResult = result;
    }





    private boolean isLightEffectivelyOn() {
        if (session.isApprentice()) {
            MouseApprentice ma = MouseApprentice.getInstance();
            return ma.isLightActive() && GameSession.getInstance().getLightBattery() > 0;
        }
        return levelState == null || levelState.getLightActive();
    }


    private boolean isActWithLight() {
        if (levelState == null) return false;
        return levelState.currentPhase == LevelState.GamePhase.ACT2
            || levelState.currentPhase == LevelState.GamePhase.ACT3;
    }





    private float getLightBrightness() {
        int radius;
        if (session.isApprentice()) {
            radius = MouseApprentice.getInstance().getLightRadius();
        } else {
            radius = (levelState != null) ? levelState.getLightRadius() : 120;
        }
        return GameSession.getInstance().getLightBrightness(radius);
    }





    private void applyBrightnessOverlay(Graphics2D g, Point center, int radius) {
        float brightness = getLightBrightness();
        if (brightness >= 1.0f || center == null || radius <= 0) return;
        float insideAlpha = (1f - brightness) * 0.80f;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, insideAlpha));
        g.setColor(Color.BLACK);
        g.fillOval(center.x - radius, center.y - radius, radius * 2, radius * 2);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }







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

        mg.setColor(Color.BLACK);
        mg.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        mg.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OUT));
        float[] fractions = {0.0f, 0.75f, 1.0f};
        Color[] colors = {
            new Color(0, 0, 0, (int)(brightness * 255)),
            new Color(0, 0, 0, (int)(brightness * 255 * 0.5f)),
            new Color(0, 0, 0, 0)
        };
        RadialGradientPaint rgp = new RadialGradientPaint(
                center.x, center.y, radius, fractions, colors);
        mg.setPaint(rgp);
        mg.fillOval(center.x - radius, center.y - radius, radius * 2, radius * 2);
        mg.dispose();
        g.drawImage(mask, 0, 0, null);
    }





    private float computeBrightness(int radius) {
        float normalized = (float)(radius - 20) / 160f;
        normalized = Math.max(0f, Math.min(1f, normalized));
        return 0.02f + (normalized * 0.98f);
    }


    public void setBreadcrumbs(Point[] breadcrumbs, float[] alphas) {
        this.breadcrumbs = breadcrumbs;
        this.breadcrumbAlphas = alphas;
    }


    public void setPaused(boolean paused) {
        this.paused = paused;
    }


    public void setGesturePosition(Point pos) {
        this.gesturePosition = pos;
    }


    public void setIpInputText(String text) {
        this.ipInputText = text;
    }








    public void setConnectionOverlay(String msg) {
        this.connectionOverlay = msg;
    }







    public void setConnectionStatus(String status) {
        this.connectionStatus = (status != null) ? status : "";
    }







    public void setLobbyState(LobbyState ls) {
        if (ls != null) this.lobbyState = ls;
    }







    public LobbyState getLobbyState() { return lobbyState; }








    public void setPartnerDCRole(String role) { this.partnerDCRole = role; }







    public void setReconnectCountdown(long seconds) { this.reconnectCountdown = seconds; }











    public String getLobbyRoleAtPoint(int x, int y) {
        if (wandererCardBounds.contains(x, y))   return "WANDERER";
        if (apprenticeCardBounds.contains(x, y)) return "APPRENTICE";
        return "NONE";
    }






    public int getLightRadius() {
        return lightRadius;
    }


    public LevelState getLevelStatePublic() {
        return levelState;
    }


    public Point getMousePosition2() {
        return mousePosition;
    }










    public void setShowFragmentLibrary(boolean show) {
        this.showFragmentLibrary = show;
    }


    public boolean isShowingFragmentLibrary() {
        return showFragmentLibrary;
    }




    public void toggleCameraOverlay() {

    }






    public void showNotification(String text) {
        notifications.add(new String[]{ text, String.valueOf(System.currentTimeMillis()) });
    }














    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                             RenderingHints.VALUE_RENDER_QUALITY);


        if (levelState != null) currentPhase = levelState.currentPhase;



        if (CutsceneRenderer.get().isPlaying()) {
            CutsceneRenderer.get().render(g2d, CANVAS_WIDTH, CANVAS_HEIGHT);
            if (connectionOverlay != null) {
                renderConnectionOverlay(g2d, connectionOverlay);
            }
            return;
        }



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
                    renderAct3(g2d);
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


        }


        if (altarOpen) {
            renderAltarOverlay(g2d);
        }




        if (levelState != null && levelState.currentPhase == LevelState.GamePhase.BOSS) {
            renderStunOverlay(g2d);
            renderArchitectStunnedBanner(g2d);
        }


        if (connectionOverlay != null) {
            renderConnectionOverlay(g2d, connectionOverlay);
        }

        g2d.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        g2d.setColor(new Color(0x00, 0xFF, 0x00));
        String roleLabel = "ROLE: " + (session.role != null ? session.role : "NONE");
        g2d.drawString(roleLabel, 10, 758);
    }











    public void renderMainMenu(Graphics2D g) {

        g.setColor(BG_MENU);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);


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


        g.setFont(TITLE_FONT);
        g.setColor(GOLD);
        FontMetrics fm = g.getFontMetrics();
        String title = "LUMEN ARCHITECT";
        int titleX = (CANVAS_WIDTH - fm.stringWidth(title)) / 2;
        g.drawString(title, titleX, 280);


        g.setFont(SUBTITLE_FONT);
        g.setColor(SUBTITLE_CLR);
        fm = g.getFontMetrics();
        String subtitle = "A cooperative puzzle of light and shadow";
        int subX = (CANVAS_WIDTH - fm.stringWidth(subtitle)) / 2;
        g.drawString(subtitle, subX, 310);


        String status = connectionStatus;
        if (status != null && !status.isEmpty()) {
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            Color statusColor;
            if (status.startsWith("Connecting")) {
                statusColor = GOLD;
            } else if (status.startsWith("Connected")) {
                statusColor = new Color(0x44, 0xCC, 0x66);
            } else if (status.startsWith("Starting")) {
                statusColor = GOLD;
            } else {
                statusColor = new Color(0xCC, 0x44, 0x44);
            }
            g.setColor(statusColor);
            fm = g.getFontMetrics();
            int statusX = (CANVAS_WIDTH - fm.stringWidth(status)) / 2;
            g.drawString(status, statusX, 600);
        }

    }












    public void renderLobby(Graphics2D g) {

        g.setColor(BG_MENU);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);


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


        g.setFont(TITLE_FONT);
        g.setColor(reconnect ? new Color(0xCC, 0x88, 0x44) : GOLD);
        FontMetrics fm = g.getFontMetrics();
        String title = reconnect ? "SESSION IN PROGRESS" : "LUMEN ARCHITECT";
        g.drawString(title, (CANVAS_WIDTH - fm.stringWidth(title)) / 2, 130);


        g.setFont(SUBTITLE_FONT);
        g.setColor(SUBTITLE_CLR);
        fm = g.getFontMetrics();
        String sub = reconnect
                ? "Rejoin the vacant role to resume the game"
                : "Choose your role to begin";
        g.drawString(sub, (CANVAS_WIDTH - fm.stringWidth(sub)) / 2, 162);


        g.setColor(new Color(0x3A, 0x3A, 0x48));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(160, 178, CANVAS_WIDTH - 160, 178);



        boolean wTaken;
        boolean aTaken;
        boolean wHover;
        boolean aHover;
        if (reconnect) {

            wTaken = !"WANDERER".equals(lobbyState.vacantRole);
            aTaken = !"APPRENTICE".equals(lobbyState.vacantRole);

            wHover = !wTaken && lobbyState.wandererHovered;
            aHover = !aTaken && lobbyState.apprenticeHovered;
        } else {
            wTaken = lobbyState.wandererTaken;
            aTaken = lobbyState.apprenticeTaken;
            wHover = lobbyState.wandererHovered;
            aHover = lobbyState.apprenticeHovered;
        }


        renderRoleCard(g, wandererCardBounds, "WANDERER",
                "Traverse the darkness\nand destroy the Cores.",
                wTaken, wHover);
        renderRoleCard(g, apprenticeCardBounds, "APPRENTICE",
                "Wield the light\nand shape the path.",
                aTaken, aHover);


        g.setFont(new Font("Serif", Font.BOLD, 22));
        g.setColor(new Color(0x3A, 0x3A, 0x48));
        fm = g.getFontMetrics();
        String vs = "VS";
        g.drawString(vs, (CANVAS_WIDTH - fm.stringWidth(vs)) / 2,
                wandererCardBounds.y + wandererCardBounds.height / 2 + fm.getAscent() / 2);


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











    private void renderRoleCard(Graphics2D g, java.awt.Rectangle bounds,
            String title, String description, boolean taken, boolean hovered) {
        if (bounds == null) return;


        Color bgColor = taken  ? new Color(0x12, 0x22, 0x12)
                      : hovered ? new Color(0x14, 0x14, 0x22)
                                : new Color(0x0E, 0x0E, 0x14);
        g.setColor(bgColor);
        g.fill(new java.awt.geom.RoundRectangle2D.Float(
                bounds.x, bounds.y, bounds.width, bounds.height, 12, 12));


        Color borderColor = taken  ? new Color(0x44, 0xCC, 0x66)
                          : hovered ? GOLD
                                    : new Color(0x3A, 0x3A, 0x4A);
        float strokeW = (taken || hovered) ? 2f : 1f;
        g.setColor(borderColor);
        g.setStroke(new BasicStroke(strokeW));
        g.draw(new java.awt.geom.RoundRectangle2D.Float(
                bounds.x, bounds.y, bounds.width, bounds.height, 12, 12));
        g.setStroke(new BasicStroke(1f));


        int iconR  = 40;
        int iconCX = bounds.x + bounds.width / 2;
        int iconCY = bounds.y + 86;
        g.setColor(taken ? new Color(0x1A, 0x3A, 0x1A) : new Color(0x12, 0x18, 0x2E));
        g.fill(new Ellipse2D.Float(iconCX - iconR, iconCY - iconR, iconR * 2, iconR * 2));
        g.setColor(borderColor);
        g.setStroke(new BasicStroke(strokeW));
        g.draw(new Ellipse2D.Float(iconCX - iconR, iconCY - iconR, iconR * 2, iconR * 2));
        g.setStroke(new BasicStroke(1f));


        g.setFont(new Font("Serif", Font.BOLD, 38));
        g.setColor(taken ? new Color(0x44, 0xCC, 0x66) : GOLD);
        FontMetrics fm = g.getFontMetrics();
        String letter = String.valueOf(title.charAt(0));
        g.drawString(letter,
                iconCX - fm.stringWidth(letter) / 2,
                iconCY + fm.getAscent() / 2 - 2);


        g.setFont(new Font("Serif", Font.PLAIN, 20));
        g.setColor(taken ? new Color(0x44, 0xCC, 0x66) : GOLD);
        fm = g.getFontMetrics();
        g.drawString(title,
                bounds.x + (bounds.width - fm.stringWidth(title)) / 2,
                bounds.y + 168);


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














    public void renderPausedWaiting(Graphics2D g) {

        g.setColor(new Color(0x06, 0x06, 0x0E));
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);


        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));


        g.setFont(TITLE_FONT);
        g.setColor(new Color(0xCC, 0x44, 0x44));
        FontMetrics fm = g.getFontMetrics();
        String title = "PARTNER DISCONNECTED";
        g.drawString(title, (CANVAS_WIDTH - fm.stringWidth(title)) / 2, 260);


        g.setFont(SUBTITLE_FONT);
        g.setColor(SUBTITLE_CLR);
        fm = g.getFontMetrics();
        String role = (partnerDCRole != null) ? partnerDCRole : "YOUR PARTNER";
        String line = "The " + role + " has left the session.";
        g.drawString(line, (CANVAS_WIDTH - fm.stringWidth(line)) / 2, 300);


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

            g.setFont(new Font("Monospaced", Font.PLAIN, 16));
            g.setColor(SUBTITLE_CLR);
            fm = g.getFontMetrics();
            String hint = "Waiting for reconnection…";
            g.drawString(hint, (CANVAS_WIDTH - fm.stringWidth(hint)) / 2, 400);
        }


        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.setColor(new Color(0x66, 0x66, 0x7A));
        fm = g.getFontMetrics();
        String foot = "Session will end if no one reconnects in time.";
        g.drawString(foot, (CANVAS_WIDTH - fm.stringWidth(foot)) / 2, 600);
    }







    public void renderAct1(Graphics2D g) {

        g.setColor(BG_ACT1);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);


        renderElements(g);


        renderPlayer(g);


        renderGhostBlock(g);


        renderHUD(g, true);


        renderNotifications(g);
    }







    public void renderAct2(Graphics2D g) {

        g.setColor(BG_ACT2);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);


        if (!isLightEffectivelyOn()) {
            g.setColor(new Color(0, 0, 0, 255));
            g.fillRect(0, 0, getWidth(), getHeight());
            renderHUD(g, false);
            return;
        }


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


        renderPlayer(g);


        renderBreadcrumbs(g);

        if (lightSourcePosition == null) lightSourcePosition = new Point(512, 384);


        float batt2 = session.isApprentice() ? GameSession.getInstance().getLightBattery() : 100f;
        boolean flicker2 = batt2 < 10f && (System.currentTimeMillis() / 120) % 2 == 0;
        int effectiveRadius2 = flicker2 ? (int)(lightRadius * 0.85f) : lightRadius;

        renderDarkness(g, lightSourcePosition, effectiveRadius2);


        renderHUD(g, false);


        renderNotifications(g);
    }







    public void renderAct3(Graphics2D g) {

        g.setColor(BG_ACT3);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);


        if (!isLightEffectivelyOn()) {
            g.setColor(new Color(0, 0, 0, 255));
            g.fillRect(0, 0, getWidth(), getHeight());
            renderHUD(g, true);
            return;
        }


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


        renderPlayer(g);

        if (lightSourcePosition == null) lightSourcePosition = new Point(512, 384);


        float batt3 = session.isApprentice() ? GameSession.getInstance().getLightBattery() : 100f;
        boolean flicker3 = batt3 < 10f && (System.currentTimeMillis() / 120) % 2 == 0;
        int effectiveRadius3 = flicker3 ? (int)(lightRadius * 0.85f) : lightRadius;

        renderDarkness(g, lightSourcePosition, effectiveRadius3);


        renderHUD(g, true);


        renderNotifications(g);
    }







    public void renderBoss(Graphics2D g) {

        g.setColor(BG_BOSS);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);


        Camera cam = Camera.getInstance();
        cam.follow(player);
        cam.update(1f / 60f);



        float ox = cam.getRenderOffsetX();
        float oy = cam.getRenderOffsetY();
        g.translate(ox, oy);
        renderElements(g);
        renderPlayer(g);








        if (bossAttacks != null && !bossAttacks.isEmpty()) {
            for (BossAttack ba : bossAttacks) {
                if (ba.isActive()) ba.render(g);
            }
        }
        g.translate(-ox, -oy);


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

        if (player != null && player.isSightRestricted()) {
            bossLightRadius = Math.round(bossLightRadius * 0.75f);
        }






        boolean radiantReveal = (player != null) && player.isRadiantActive();

        lightRenderer.renderLightMask(g, bossLightScreen, bossLightRadius,
                lightVelocityFactor, radiantReveal);















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


        renderHUD(g, false);


        renderNotifications(g);
    }







    public void renderCutscene(Graphics2D g) {

        switch (levelState.currentLevel) {
            case 1:  renderAct1(g);  break;
            case 2:  renderAct2(g);  break;
            case 3:  renderAct3(g);  break;
            case 4:  renderBoss(g);  break;
            default: renderAct1(g);  break;
        }


        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.7f));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 1.0f));
        g.setFont(TITLE_FONT);
        g.setColor(GOLD);
        g.drawString("CUTSCENE", 380, 384);
    }









    private void renderVictoryScreen(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        boolean wandererWin = (victoryResult == GameServer.VictoryState.WANDERER_WIN);


        g.setColor(wandererWin ? new Color(0xFF, 0xD7, 0x00, 80) : new Color(0x66, 0x00, 0xCC, 80));
        g.fillRect(0, 0, CANVAS_WIDTH, 4);


        String headline = wandererWin ? "THE LIGHT RETURNS" : "THE DARK ENDURES";
        g.setFont(new Font("Serif", Font.BOLD, 52));
        g.setColor(wandererWin ? new Color(0xFF, 0xD7, 0x00) : new Color(0xAA, 0x88, 0xFF));
        FontMetrics hfm = g.getFontMetrics();
        g.drawString(headline, (CANVAS_WIDTH - hfm.stringWidth(headline)) / 2, CANVAS_HEIGHT / 2 - 60);


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


        String footer = "[ Close the window or restart to play again ]";
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(0x66, 0x60, 0x58));
        FontMetrics ffm = g.getFontMetrics();
        g.drawString(footer, (CANVAS_WIDTH - ffm.stringWidth(footer)) / 2, CANVAS_HEIGHT - 50);
    }







    public void renderPauseMenu(Graphics2D g) {

        BufferedImage gameBuffer = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D bg = gameBuffer.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        bg.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);


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


        RescaleOp darken = new RescaleOp(0.4f, 0, null);
        BufferedImage darkened = darken.filter(gameBuffer, null);
        g.drawImage(darkened, 0, 0, null);


        int panelW = 400;
        int panelH = 300;
        int panelX = (CANVAS_WIDTH - panelW) / 2;
        int panelY = (CANVAS_HEIGHT - panelH) / 2;


        g.setColor(PANEL_BG);
        g.fill(new Rectangle2D.Double(panelX, panelY, panelW, panelH));


        g.setColor(GOLD);
        g.setStroke(new BasicStroke(1));
        g.draw(new Rectangle2D.Double(panelX, panelY, panelW, panelH));


        g.setFont(PAUSE_TITLE);
        g.setColor(GOLD);
        FontMetrics fm = g.getFontMetrics();
        String pauseText = "PAUSED";
        int textX = panelX + (panelW - fm.stringWidth(pauseText)) / 2;
        g.drawString(pauseText, textX, panelY + 50);


        g.setFont(PAUSE_BTN);
        g.setColor(new Color(0x88, 0x80, 0x70));
        fm = g.getFontMetrics();
        String resumeHint = "Press ESC to resume";
        int hintX = panelX + (panelW - fm.stringWidth(resumeHint)) / 2;
        g.drawString(resumeHint, hintX, panelY + 78);


        String fragHint = "Press F to view fragments";
        int fragHintX = panelX + (panelW - fm.stringWidth(fragHint)) / 2;
        g.drawString(fragHint, fragHintX, panelY + 98);




        if (showFragmentLibrary) {
            renderFragmentLibrary(g);
        }
    }






    private void renderFragmentLibrary(Graphics2D g) {

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
        g.setColor(new Color(0x0A, 0x0A, 0x0F));
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));


        g.setFont(PAUSE_TITLE);
        g.setColor(GOLD);
        FontMetrics fm = g.getFontMetrics();
        String title = "FRAGMENT LIBRARY";
        g.drawString(title, (CANVAS_WIDTH - fm.stringWidth(title)) / 2, 60);


        g.setFont(SUBTITLE_FONT);
        g.setColor(SUBTITLE_CLR);
        fm = g.getFontMetrics();
        String hint = "Press F again to close";
        g.drawString(hint, (CANVAS_WIDTH - fm.stringWidth(hint)) / 2, 85);


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








    private void renderElements(Graphics2D g) {
        if (elements == null) return;
        for (GameElement elem : elements) {
            if (elem.isActive()
                    || (elem instanceof Platform && !((Platform) elem).fullyGone)) {
                elem.render(g);
            }
        }
    }





    private void renderPlayer(Graphics2D g) {
        if (player == null) return;
        player.render(g);


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






    private void renderHUD(Graphics2D g, boolean showBudget) {

        if (session.isWanderer()) {

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


            if (player != null && levelState.currentPhase == LevelState.GamePhase.BOSS) {
                int faithful = player.getFaithful();
                int faithMax = Player.FAITHFUL_MAX;
                int pipW = 10;
                int pipH = 10;
                int pipGap = 3;
                int faithStartX = 116;
                int faithY = hpBarY;
                for (int i = 0; i < faithMax; i++) {
                    Color pipFill = (i < faithful)
                            ? new Color(0xE8, 0xD0, 0x60)
                            : new Color(0x1A, 0x1A, 0x20);
                    g.setColor(pipFill);
                    g.fillOval(faithStartX + i * (pipW + pipGap), faithY, pipW, pipH);
                    g.setColor(new Color(0x3A, 0x3A, 0x48));
                    g.drawOval(faithStartX + i * (pipW + pipGap), faithY, pipW, pipH);
                }
                g.setFont(new Font("Monospaced", Font.PLAIN, 9));
                g.setColor(new Color(0x88, 0x80, 0x70));
                g.drawString("FAITH", faithStartX, hpLabelY);
            }


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


        if (session.isApprentice()) {
            GameSession gs = GameSession.getInstance();


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





    private void renderGhostBlock(Graphics2D g) {
        if (gesturePosition == null) return;

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        g.setColor(GOLD);
        g.fill(new Rectangle2D.Double(gesturePosition.x - 16, gesturePosition.y - 16,
                32, 32));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }




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











    private void renderAltarOverlay(Graphics2D g) {
        int panelW = 460;
        int panelH = 210;
        int ox = (CANVAS_WIDTH  - panelW) / 2;
        int oy = (CANVAS_HEIGHT - panelH) / 2;


        Composite prev = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.88f));
        g.setColor(new Color(0x08, 0x05, 0x14));
        g.fillRoundRect(ox, oy, panelW, panelH, 14, 14);
        g.setComposite(prev);


        g.setColor(new Color(0x66, 0x33, 0x99));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(ox, oy, panelW, panelH, 14, 14);


        g.setFont(new Font("Monospaced", Font.BOLD, 17));
        g.setColor(new Color(0xCC, 0x99, 0xFF));
        String title = "THE ALTAR";
        FontMetrics fmT = g.getFontMetrics();
        g.drawString(title, CANVAS_WIDTH / 2 - fmT.stringWidth(title) / 2, oy + 32);


        g.setColor(new Color(0x44, 0x22, 0x66));
        g.drawLine(ox + 16, oy + 42, ox + panelW - 16, oy + 42);


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


        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(0x55, 0x44, 0x66));
        String hint = "Press 1 or 2 to choose";
        FontMetrics fmH = g.getFontMetrics();
        g.drawString(hint, CANVAS_WIDTH / 2 - fmH.stringWidth(hint) / 2, oy + panelH - 16);

        g.setStroke(new BasicStroke(1f));
    }








    private void renderStunOverlay(Graphics2D g) {
        if (stunMinigame == null) return;
        stunMinigame.render(g, CANVAS_WIDTH, CANVAS_HEIGHT);
    }








    private void renderArchitectStunnedBanner(Graphics2D g) {
        long now = System.currentTimeMillis();
        long remainingMs = architectStunnedUntilMs - now;
        if (remainingMs <= 0L) return;

        int bannerW = 360;
        int bannerH = 56;
        int ox = (CANVAS_WIDTH - bannerW) / 2;
        int oy = 96;

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









    private void renderConnectionOverlay(Graphics2D g, String msg) {

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.65f));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));


        Font overlayFont = new Font("SansSerif", Font.BOLD, 22);
        g.setFont(overlayFont);
        g.setColor(new Color(0xFF, 0xD7, 0x00));
        FontMetrics fm = g.getFontMetrics();
        int textX = (CANVAS_WIDTH  - fm.stringWidth(msg)) / 2;
        int textY = (CANVAS_HEIGHT / 2) + fm.getAscent() / 2;
        g.drawString(msg, textX, textY);
    }





}
