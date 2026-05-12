











































import java.awt.event.*;
import javax.swing.*;
import java.util.*;
public class KeyBindings {





    public static final int MOVE_LEFT = KeyEvent.VK_A;
    public static final int MOVE_LEFT_ALT = KeyEvent.VK_LEFT;
    public static final int MOVE_RIGHT = KeyEvent.VK_D;
    public static final int MOVE_RIGHT_ALT = KeyEvent.VK_RIGHT;
    public static final int JUMP = KeyEvent.VK_SPACE;
    public static final int JUMP_ALT = KeyEvent.VK_W;
    public static final int ATTACK = KeyEvent.VK_J;
    public static final int CHARGE_ATTACK = KeyEvent.VK_K;
    public static final int DODGE = KeyEvent.VK_L;
    public static final int PULSE = KeyEvent.VK_E;
    public static final int SHADOW_DASH = KeyEvent.VK_SHIFT;
    public static final int PAUSE = KeyEvent.VK_ESCAPE;
    public static final int FRAGMENTS = KeyEvent.VK_F;
    public static final int TOGGLE_CAMERA = KeyEvent.VK_C;





    private final PlayerInputState inputState;
    private final Set<String> pressedKeys;





    public KeyBindings() {
        this.inputState  = new PlayerInputState();
        this.pressedKeys = new HashSet<>();
    }





    public void registerBindings(JComponent component) {
        InputMap  im = component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = component.getActionMap();








        bindKey(im, KeyEvent.VK_A,      0, "moveLeft");
        bindKey(im, KeyEvent.VK_LEFT,   0, "moveLeft");
        bindKey(im, KeyEvent.VK_D,      0, "moveRight");
        bindKey(im, KeyEvent.VK_RIGHT,  0, "moveRight");
        bindKey(im, KeyEvent.VK_SPACE,  0, "jump");
        bindKey(im, KeyEvent.VK_W,      0, "jump");
        bindKey(im, KeyEvent.VK_J,      0, "attack");
        bindKey(im, KeyEvent.VK_K,      0, "charge");
        bindKey(im, KeyEvent.VK_L,      0, "dodge");
        bindKey(im, KeyEvent.VK_E,      0, "pulse");
        bindKey(im, KeyEvent.VK_ESCAPE, 0, "pause");
        bindKey(im, KeyEvent.VK_F,      0, "fragments");
        bindKey(im, KeyEvent.VK_C,      0, "cameraToggle");



        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT,
                                      InputEvent.SHIFT_DOWN_MASK, false),
               "dash_pressed");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, 0, true),
               "dash_released");




        am.put("moveLeft_pressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                inputState.moveLeft = true;
            }
        });
        am.put("moveLeft_released", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                inputState.moveLeft = false;
            }
        });




        am.put("moveRight_pressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                inputState.moveRight = true;
            }
        });
        am.put("moveRight_released", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                inputState.moveRight = false;
            }
        });







        am.put("jump_pressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pressedKeys.add("jump")) {
                    inputState.jumpPressed = true;
                }
            }
        });
        am.put("jump_released", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.remove("jump");
            }
        });




        am.put("attack_pressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pressedKeys.add("attack")) {
                    inputState.attackPressed = true;
                }
            }
        });
        am.put("attack_released", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.remove("attack");
            }
        });








        am.put("charge_pressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!inputState.chargeHeld) {
                    inputState.chargeHeld      = true;
                    inputState.chargeStartTime = System.currentTimeMillis();
                }
            }
        });
        am.put("charge_released", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                inputState.chargeHeld = false;



            }
        });




        am.put("dodge_pressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pressedKeys.add("dodge")) {
                    inputState.dodgePressed = true;
                }
            }
        });
        am.put("dodge_released", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.remove("dodge");
            }
        });




        am.put("pulse_pressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pressedKeys.add("pulse")) {
                    inputState.pulsePressed = true;
                }
            }
        });
        am.put("pulse_released", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.remove("pulse");
            }
        });








        am.put("dash_pressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                inputState.shiftHeld = true;
                if (pressedKeys.add("dash")) {
                    inputState.dashPressed = true;
                }
            }
        });
        am.put("dash_released", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                inputState.shiftHeld = false;
                pressedKeys.remove("dash");
            }
        });




        am.put("pause_pressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pressedKeys.add("pause")) {
                    inputState.pausePressed = true;
                }
            }
        });
        am.put("pause_released", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.remove("pause");
            }
        });




        am.put("fragments_pressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pressedKeys.add("fragments")) {
                    inputState.fragmentsPressed = true;
                }
            }
        });
        am.put("fragments_released", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.remove("fragments");
            }
        });


        am.put("cameraToggle_pressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pressedKeys.add("cameraToggle")) {
                    inputState.cameraTogglePressed = true;
                }
            }
        });
        am.put("cameraToggle_released", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.remove("cameraToggle");
            }
        });


        bindKey(im, KeyEvent.VK_R, 0, "recapture");
        am.put("recapture_pressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.add("recapture");
                System.out.println("[KeyBindings] R pressed (recapture no-op)");
            }
        });
        am.put("recapture_released", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.remove("recapture");
            }
        });


        bindKey(im, KeyEvent.VK_TAB, 0, "tab");
        am.put("tab_pressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pressedKeys.add("tab")) {
                    if (GameSession.getInstance().isApprentice()) {
                        GameSession.getInstance().cycleBlockType();
                    }
                }
            }
        });
        am.put("tab_released", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.remove("tab");
            }
        });
    }





    public PlayerInputState getInputState() {
        return inputState;
    }





    private void bindKey(InputMap im, int keyCode, int modifiers, String name) {
        im.put(KeyStroke.getKeyStroke(keyCode, modifiers, false), name + "_pressed");
        im.put(KeyStroke.getKeyStroke(keyCode, modifiers, true),  name + "_released");
    }












    public static class PlayerInputState {

        public static final long CHARGE_THRESHOLD_MS = 1000L;



        public volatile boolean moveLeft;
        public volatile boolean moveRight;
        public volatile boolean chargeHeld;
        public volatile long chargeStartTime;



        public volatile boolean jumpPressed;
        public volatile boolean attackPressed;
        public volatile boolean dodgePressed;
        public volatile boolean pulsePressed;
        public volatile boolean dashPressed;
        public volatile boolean shiftHeld;
        public volatile boolean pausePressed;
        public volatile boolean fragmentsPressed;
        public volatile boolean cameraTogglePressed;



        public PlayerInputState() {
            moveLeft       = false;
            moveRight      = false;
            jumpPressed    = false;
            attackPressed  = false;
            chargeHeld     = false;
            chargeStartTime = 0L;
            dodgePressed   = false;
            pulsePressed   = false;
            dashPressed    = false;
            shiftHeld      = false;
            pausePressed        = false;
            fragmentsPressed    = false;
            cameraTogglePressed = false;
        }



        public void consumeEdgeFlags() {
            jumpPressed   = false;
            attackPressed = false;
            dodgePressed  = false;
            pulsePressed  = false;
            dashPressed          = false;
            pausePressed         = false;
            fragmentsPressed     = false;
            cameraTogglePressed  = false;


        }
    }
}
