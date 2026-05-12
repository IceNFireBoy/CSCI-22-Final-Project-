


































import java.awt.event.*;
import javax.swing.*;
public class InputRouter implements MouseListener, MouseMotionListener, MouseWheelListener {





    private GameCanvas canvas;
    private GameSession session;
    private StunMinigame stunMinigame;

    private long lastBlockRainTime = 0;
    private long lastShieldTime = 0;
    private long lastSpikeTime = 0;
    private boolean prevChargeHeld = false;
    private static final long BLOCK_RAIN_COOLDOWN = 8000;
    private static final long SHIELD_COOLDOWN = 8000;
    private static final long SPIKE_COOLDOWN = 6000;





    private String lastLobbyHoverRole = "NONE";
    private String localSelectedRole = "NONE";
    private static InputRouter instance;





    public static InputRouter getInstance() { return instance; }





    public InputRouter() {
        instance = this;
    }

    public InputRouter(GameCanvas canvas, GameSession session) {
        this.canvas  = canvas;
        this.session = session;
        instance = this;
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);
        canvas.addMouseWheelListener(this);
        canvas.setFocusable(true);
    }





    public void setStunMinigame(StunMinigame m) {
        this.stunMinigame = m;
    }

    public void setGameCanvas(GameCanvas canvas) {
        if (this.canvas != null) {
            this.canvas.removeMouseListener(this);
            this.canvas.removeMouseMotionListener(this);
            this.canvas.removeMouseWheelListener(this);
        }
        this.canvas  = canvas;
        this.session = GameSession.getInstance();
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);
        canvas.addMouseWheelListener(this);
        canvas.setFocusable(true);
    }





    public void routeKeyEvent(KeyBindings.PlayerInputState input, Player player, Physics physicsEngine) {
        if (input == null || player == null || physicsEngine == null) return;


        if (CutsceneRenderer.get().isPlaying()) {
            input.consumeEdgeFlags();
            physicsEngine.stopWalking(player);
            return;
        }


        if (input.moveLeft)  physicsEngine.walk(player, -1);
        if (input.moveRight) physicsEngine.walk(player,  1);


        if (!input.moveLeft && !input.moveRight) physicsEngine.stopWalking(player);


        if (input.jumpPressed) { physicsEngine.jump(player); input.jumpPressed = false; }


        if (input.attackPressed) { player.executeMelee(); input.attackPressed = false; }


        if (input.dodgePressed) { physicsEngine.dodgeRoll(player); input.dodgePressed = false; }


        if (input.chargeHeld && !prevChargeHeld) {
            player.startCharge();
        } else if (!input.chargeHeld && prevChargeHeld) {
            Projectile fired = player.releaseProjectile();
            if (fired != null) {
                GameStarter starter = GameStarter.getInstance();
                if (starter != null) starter.getElements().add(fired);
            }
        }
        prevChargeHeld = input.chargeHeld;











        String act = getAct();
        if ("BOSS".equals(act)) {
            if (player.isRadiantCollapseUnlocked()) {
                player.updateRadiantFsm(System.currentTimeMillis(), input.shiftHeld);
            }


            input.dashPressed = false;
        } else if (input.dashPressed) {
            physicsEngine.shadowDash(player);
            input.dashPressed = false;
        }
    }





    private String getAct() {
        if (session == null) session = GameSession.getInstance();
        LevelState ls = (canvas != null) ? canvas.getLevelStatePublic() : null;
        if (ls == null) return "ACT1";
        switch (ls.currentPhase) {
            case LOBBY: return "LOBBY";
            case ACT1:  return "ACT1";
            case ACT2:  return "ACT2";
            case ACT3:  return "ACT3";
            case BOSS:  return "BOSS";
            default:    return "ACT1";
        }
    }





    public void mousePressed(MouseEvent e) {
        if (CutsceneRenderer.get().isPlaying()) return;
        if (session == null) session = GameSession.getInstance();
        int x = e.getX(), y = e.getY();
        boolean left   = e.getButton() == MouseEvent.BUTTON1;
        boolean right  = e.getButton() == MouseEvent.BUTTON3;
        boolean middle = e.getButton() == MouseEvent.BUTTON2;
        String act = getAct();


        if (act.equals("LOBBY")) {
            if (left && canvas != null) {
                String clicked = canvas.getLobbyRoleAtPoint(x, y);
                if (!clicked.equals("NONE")) {


                    LobbyState ls = canvas.getLobbyState();
                    if (ls != null && ls.isReconnectSession) {
                        if (!clicked.equals(ls.vacantRole)) {
                            return;
                        }
                    }
                    if (clicked.equals(localSelectedRole)) {

                        session.sendToServer(Protocol.LOBBY_CANCEL + "|" + clicked);
                        localSelectedRole = "NONE";
                    } else {

                        session.sendToServer(Protocol.LOBBY_SELECT + "|" + clicked);
                        localSelectedRole = clicked;
                    }
                }
            }
            return;
        }

        if (session.isApprentice()) {
            if (act.equals("ACT1") || act.equals("ACT3")) {
                if (left) {
                    int snappedX = (x / 32) * 32;
                    int snappedY = (y / 16) * 16;
                    if (session.getBlockBudget() > 0) {
                        session.placeBlock(session.getCurrentBlockType(), snappedX, snappedY);
                        session.decrementBlockBudget();
                    }
                }
                if (right) {
                    session.removeNearestBlock(x, y, 40);
                }
            }
            if (act.equals("ACT2") || act.equals("ACT3")) {
                MouseApprentice.getInstance().setLeftPressed(left);
                MouseApprentice.getInstance().setRightPressed(right);
            }
            if (session.isArchitectOverride()) {
                long now = System.currentTimeMillis();


                int aimX = x;
                int aimY = y;
                if (act.equals("BOSS")) {
                    aimX = Camera.getInstance().screenToWorldX(x);
                    aimY = Camera.getInstance().screenToWorldY(y);
                }
                if (left)  session.fireSearingBeam(aimX, aimY);
                if (right && now - lastBlockRainTime >= BLOCK_RAIN_COOLDOWN) {
                    session.fireBlockRain();
                    lastBlockRainTime = now;
                }
                if (middle) session.fireCrusherBlock(aimX, aimY);
            }
        }
    }





    public void mouseMoved(MouseEvent e) {
        if (CutsceneRenderer.get().isPlaying()) return;
        if (session == null) session = GameSession.getInstance();
        String act = getAct();


        if (act.equals("LOBBY") && canvas != null) {
            String hovered = canvas.getLobbyRoleAtPoint(e.getX(), e.getY());
            if (!hovered.equals(lastLobbyHoverRole)) {
                lastLobbyHoverRole = hovered;
                session.sendToServer(Protocol.LOBBY_HOVER + "|" + hovered);
            }
            return;
        }

        if (session.isApprentice()) {
            int mx = e.getX(), my = e.getY();
            if (act.equals("BOSS")) {
                mx = Camera.getInstance().screenToWorldX(mx);
                my = Camera.getInstance().screenToWorldY(my);
            }
            MouseApprentice.getInstance().updatePosition(mx, my);
        }
    }

    public void mouseDragged(MouseEvent e) {
        if (CutsceneRenderer.get().isPlaying()) return;
        if (session == null) session = GameSession.getInstance();
        String act = getAct();


        if (act.equals("LOBBY") && canvas != null) {
            String hovered = canvas.getLobbyRoleAtPoint(e.getX(), e.getY());
            if (!hovered.equals(lastLobbyHoverRole)) {
                lastLobbyHoverRole = hovered;
                session.sendToServer(Protocol.LOBBY_HOVER + "|" + hovered);
            }
            return;
        }

        if (session.isApprentice()) {
            int mx = e.getX(), my = e.getY();
            if (act.equals("BOSS")) {
                mx = Camera.getInstance().screenToWorldX(mx);
                my = Camera.getInstance().screenToWorldY(my);
            }
            MouseApprentice.getInstance().updatePosition(mx, my);
        }
    }





    public void mouseWheelMoved(MouseWheelEvent e) {
        if (CutsceneRenderer.get().isPlaying()) return;
        if (session == null) session = GameSession.getInstance();
        if (session.isApprentice()) {
            int delta = (int) e.getWheelRotation() * -10;
            MouseApprentice.getInstance().adjustRadius(delta);
        }
    }





    public void registerApprenticeKeyBindings(JComponent comp) {
        InputMap im = comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = comp.getActionMap();


        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0, false), "apprenticeLight_pressed");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0, true),  "apprenticeLight_released");
        am.put("apprenticeLight_pressed", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (CutsceneRenderer.get().isPlaying()) return;
                if (session == null) session = GameSession.getInstance();
                MouseApprentice ma = MouseApprentice.getInstance();
                if (ma.isLightActive()) {
                    ma.setLightActive(false);
                } else if (session.getLightBattery() > 5f) {
                    ma.setLightActive(true);
                    ma.setLightForcedOff(false);
                }
            }
        });


        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, 0, false), "apprenticeE_pressed");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, 0, true),  "apprenticeE_released");
        am.put("apprenticeE_pressed", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (CutsceneRenderer.get().isPlaying()) return;
                if (session == null) session = GameSession.getInstance();
                long now = System.currentTimeMillis();
                session.signalLevelReady();
                if (session.isArchitectOverride() && now - lastSpikeTime >= SPIKE_COOLDOWN) {
                    session.fireSpikeArray();
                    lastSpikeTime = now;
                }
            }
        });


        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0, false), "apprenticeQ_pressed");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0, true),  "apprenticeQ_released");
        am.put("apprenticeQ_pressed", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (CutsceneRenderer.get().isPlaying()) return;
                if (session == null) session = GameSession.getInstance();
                long now = System.currentTimeMillis();
                MouseApprentice.getInstance().adjustRadius(30);
                if (session.isArchitectOverride() && now - lastShieldTime >= SHIELD_COOLDOWN) {
                    session.fireShield();
                    lastShieldTime = now;
                }
            }
        });
    }





    public void registerCutsceneBindings(JComponent comp) {
        InputMap im = comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = comp.getActionMap();



        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0, false), "debugCutsceneSignal");
        am.put("debugCutsceneSignal", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (session == null) session = GameSession.getInstance();


                String trigger = "CUT_TRIGGER|" + CutsceneID.SIGNAL.name();
                if (session != null) session.sendToServer(trigger);
                if (!CutsceneRenderer.get().isPlaying()) {
                    CutsceneRenderer.get().play(CutsceneID.SIGNAL);
                }
            }
        });







        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "cutsceneAdvance");
        am.put("cutsceneAdvance", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                CutsceneRenderer cr = CutsceneRenderer.get();
                if (cr.isPlaying()) {
                    if (cr.advance()) {

                        CutsceneID id = cr.getActive();
                        if (session == null) session = GameSession.getInstance();
                        if (session != null && id != null) {
                            session.sendToServer(Protocol.CUTSCENE_ACK + "|" + id.name());
                        }
                    }
                    return;
                }




                if (stunMinigame != null && stunMinigame.isActive()) {
                    stunMinigame.onSpacePressed();
                    return;
                }


                javax.swing.Action jumpAct = comp.getActionMap().get("jump_pressed");
                if (jumpAct != null) jumpAct.actionPerformed(e);
            }
        });
    }





    public void mouseReleased(MouseEvent e) {
        MouseApprentice.getInstance().setLeftPressed(false);
        MouseApprentice.getInstance().setRightPressed(false);
    }

    public void mouseClicked(MouseEvent e) {}

    public void mouseEntered(MouseEvent e) {}

    public void mouseExited(MouseEvent e)  {}
}
