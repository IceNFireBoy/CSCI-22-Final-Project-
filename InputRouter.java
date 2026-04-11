import java.awt.event.*;

public class InputRouter implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {

    private GameCanvas canvas;
    private GameSession session;
    private String currentBlockType = "BRICK";
    private static final String[] BLOCK_CYCLE = {"BRICK","SLIDE","SPRING","WALL","CRUMBLE"};
    private int blockCycleIndex = 0;
    private long lastBlockRainTime = 0;
    private long lastShieldTime = 0;
    private long lastSpikeTime = 0;
    private static final long BLOCK_RAIN_COOLDOWN = 8000;
    private static final long SHIELD_COOLDOWN = 8000;
    private static final long SPIKE_COOLDOWN = 6000;

    // Singleton reference for legacy code that calls InputRouter.getInstance()
    private static InputRouter instance;

    public static InputRouter getInstance() { return instance; }

    // Legacy constructor used by old GameStarter code — GameCanvas/session can be set later
    public InputRouter() {
        instance = this;
    }

    public InputRouter(GameCanvas canvas, GameSession session) {
        this.canvas = canvas;
        this.session = session;
        instance = this;
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);
        canvas.addMouseWheelListener(this);
        canvas.addKeyListener(this);
        canvas.setFocusable(true);
    }

    // Called by GameStarter after canvas is created
    public void setGameCanvas(GameCanvas canvas) {
        if (this.canvas != null) {
            this.canvas.removeMouseListener(this);
            this.canvas.removeMouseMotionListener(this);
            this.canvas.removeMouseWheelListener(this);
            this.canvas.removeKeyListener(this);
        }
        this.canvas = canvas;
        this.session = GameSession.getInstance();
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);
        canvas.addMouseWheelListener(this);
        canvas.addKeyListener(this);
        canvas.setFocusable(true);
    }

    // Legacy method called by old InputRouter usage
    public void routeKeyEvent(KeyBindings.PlayerInputState input, Player player, PhysicsEngine physicsEngine) {
        if (input == null || player == null || physicsEngine == null) return;
        if (input.moveLeft)  physicsEngine.walk(player, -1);
        if (input.moveRight) physicsEngine.walk(player, 1);
        if (!input.moveLeft && !input.moveRight) physicsEngine.stopWalking(player);
        if (input.jumpPressed) { physicsEngine.jump(player); input.jumpPressed = false; }
        if (input.attackPressed) { player.executeMelee(); input.attackPressed = false; }
        if (input.dodgePressed) { physicsEngine.dodgeRoll(player); input.dodgePressed = false; }
        if (input.dashPressed) { physicsEngine.shadowDash(player); input.dashPressed = false; }
    }

    // Legacy routeGesture stub (was used for GestureInput.GestureID routing)
    public void routeGesture(Object gestureID, java.awt.Point pos) {
        // No-op: gesture system removed. Mouse events handled via MouseListener.
        System.out.println("[InputRouter] routeGesture called (no-op): " + gestureID);
    }

    // Legacy setArchitectOverride stub
    public void setArchitectOverride(boolean override) {
        // No-op: architectOverride is managed by GameSession/GameStarter
    }

    // Legacy stubs for methods called by old GameStarter/InputRouter code
    public void setLevelState(LevelState levelState) {}
    public void setPlayer(Player player) {}
    public void setActiveEntities(java.util.List<GameElement> elements) {}
    public void setAudioManager(AudioManager audioManager) {}

    private String getAct() {
        if (session == null) session = GameSession.getInstance();
        LevelState ls = (canvas != null) ? canvas.getLevelStatePublic() : null;
        if (ls == null) return "ACT1";
        switch (ls.currentPhase) {
            case ACT1: return "ACT1";
            case ACT2: return "ACT2";
            case ACT3: return "ACT3";
            case BOSS: return "BOSS";
            default:   return "ACT1";
        }
    }

    public void mousePressed(MouseEvent e) {
        int x = e.getX(), y = e.getY();
        boolean left   = e.getButton() == MouseEvent.BUTTON1;
        boolean right  = e.getButton() == MouseEvent.BUTTON3;
        boolean middle = e.getButton() == MouseEvent.BUTTON2;
        String act = getAct();

        if (act.equals("ACT1") || act.equals("ACT3")) {
            if (left) {
                int snappedX = (x / 32) * 32;
                int snappedY = (y / 16) * 16;
                GameSession gs = GameSession.getInstance();
                if (gs.getBlockBudget() > 0) {
                    gs.placeBlock(gs.getCurrentBlockType(), snappedX, snappedY);
                    gs.decrementBlockBudget();
                }
            }
            if (right) {
                GameSession.getInstance().removeNearestBlock(x, y, 40);
            }
        }

        if (act.equals("ACT2") || act.equals("ACT3")) {
            MouseApprentice.getInstance().setLeftPressed(left);
            MouseApprentice.getInstance().setRightPressed(right);
        }

        if (GameSession.getInstance().isArchitectOverride()) {
            long now = System.currentTimeMillis();
            if (left)   GameSession.getInstance().fireSearingBeam(x, y);
            if (right  && now - lastBlockRainTime >= BLOCK_RAIN_COOLDOWN) {
                GameSession.getInstance().fireBlockRain();
                lastBlockRainTime = now;
            }
            if (middle) GameSession.getInstance().fireCrusherBlock(x, y);
        }
    }

    public void mouseMoved(MouseEvent e) {
        MouseApprentice.getInstance().updatePosition(e.getX(), e.getY());
    }

    public void mouseDragged(MouseEvent e) {
        MouseApprentice.getInstance().updatePosition(e.getX(), e.getY());
    }

    public void mouseWheelMoved(MouseWheelEvent e) {
        int delta = (int) e.getWheelRotation() * -10;
        MouseApprentice.getInstance().adjustRadius(delta);
    }

    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        long now = System.currentTimeMillis();

        if (code == KeyEvent.VK_TAB) {
            blockCycleIndex = (blockCycleIndex + 1) % BLOCK_CYCLE.length;
            currentBlockType = BLOCK_CYCLE[blockCycleIndex];
            GameSession.getInstance().setCurrentBlockType(currentBlockType);
        }
        if (code == KeyEvent.VK_E) {
            GameSession.getInstance().signalLevelReady();
            if (GameSession.getInstance().isArchitectOverride() && now - lastSpikeTime >= SPIKE_COOLDOWN) {
                GameSession.getInstance().fireSpikeArray();
                lastSpikeTime = now;
            }
        }
        if (code == KeyEvent.VK_Q) {
            MouseApprentice.getInstance().adjustRadius(30);
            if (GameSession.getInstance().isArchitectOverride() && now - lastShieldTime >= SHIELD_COOLDOWN) {
                GameSession.getInstance().fireShield();
                lastShieldTime = now;
            }
        }
    }

    public void mouseReleased(MouseEvent e) {
        MouseApprentice.getInstance().setLeftPressed(false);
        MouseApprentice.getInstance().setRightPressed(false);
    }

    public void mouseClicked(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e)  {}
    public void keyReleased(KeyEvent e)    {}
    public void keyTyped(KeyEvent e)       {}
}
