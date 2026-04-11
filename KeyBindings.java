/**
 * Centralises all keyboard constant definitions for the Wanderer's control scheme and
 * exposes an inner {@link PlayerInputState} snapshot class that the game loop reads
 * each tick to determine which actions are currently held or freshly pressed. Keeping
 * bindings in one place makes remapping straightforward and avoids scattering
 * {@code KeyEvent} constants throughout the codebase.
 *
 * <p>Bindings are registered on a {@link javax.swing.JComponent} via Swing's
 * {@link javax.swing.InputMap}/{@link javax.swing.ActionMap} pipeline so no
 * {@code KeyListener} is needed. {@code WHEN_IN_FOCUSED_WINDOW} scope is used so the
 * Wanderer responds to input as long as the game window is focused regardless of which
 * sub-component holds keyboard focus.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

public class KeyBindings {

    // =========================================================================
    // Primary key constants
    // =========================================================================

    /** Primary key for moving the Wanderer left. */
    public static final int MOVE_LEFT = KeyEvent.VK_A;

    /** Alternate key for moving the Wanderer left. */
    public static final int MOVE_LEFT_ALT = KeyEvent.VK_LEFT;

    /** Primary key for moving the Wanderer right. */
    public static final int MOVE_RIGHT = KeyEvent.VK_D;

    /** Alternate key for moving the Wanderer right. */
    public static final int MOVE_RIGHT_ALT = KeyEvent.VK_RIGHT;

    /** Primary key for making the Wanderer jump. */
    public static final int JUMP = KeyEvent.VK_SPACE;

    /** Alternate key for making the Wanderer jump. */
    public static final int JUMP_ALT = KeyEvent.VK_W;

    /** Key for the Wanderer's basic melee attack. */
    public static final int ATTACK = KeyEvent.VK_J;

    /**
     * Key that, when held, charges the Wanderer's projectile attack. The charge fires
     * on release only if the key was held for at least {@value PlayerInputState#CHARGE_THRESHOLD_MS}
     * milliseconds.
     */
    public static final int CHARGE_ATTACK = KeyEvent.VK_K;

    /** Key for the Wanderer's dodge roll. */
    public static final int DODGE = KeyEvent.VK_L;

    /**
     * Key for the Wanderer's position-pulse ability, which briefly illuminates nearby
     * areas and stuns DarkCrawlers.
     */
    public static final int PULSE = KeyEvent.VK_E;

    /**
     * Key for the Wanderer's shadow-dash ability. Uses {@code SHIFT} because the dash
     * is an instantaneous burst action that benefits from a dedicated modifier key.
     */
    public static final int SHADOW_DASH = KeyEvent.VK_SHIFT;

    /** Key for toggling the pause menu. */
    public static final int PAUSE = KeyEvent.VK_ESCAPE;

    /** Key for viewing the fragment library while paused. */
    public static final int FRAGMENTS = KeyEvent.VK_F;

    /** Key for toggling the Apprentice's in-game camera feed overlay. Ignored on the Wanderer client. */
    public static final int TOGGLE_CAMERA = KeyEvent.VK_C;

    // =========================================================================
    // Fields
    // =========================================================================

    /**
     * The mutable snapshot of all Wanderer input states for the current tick.
     * Updated on the Event Dispatch Thread by the registered Actions; read on the
     * physics thread by {@link InputRouter} each tick.
     */
    private final PlayerInputState inputState;

    /**
     * Tracks which logical action names (e.g., {@code "jump"}) currently have their
     * key physically held. Used to suppress auto-repeat for edge-triggered actions so
     * a continuous key hold does not generate multiple presses per tick.
     * Only accessed on the EDT.
     */
    private final Set<String> pressedKeys;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Constructs a {@code KeyBindings} instance with a fresh {@link PlayerInputState}
     * in which all action flags are {@code false} and the charge timer is zero.
     */
    public KeyBindings() {
        this.inputState  = new PlayerInputState();
        this.pressedKeys = new HashSet<>();
    }

    // =========================================================================
    // Registration
    // =========================================================================

    /**
     * Installs all Wanderer key bindings onto the provided component using Swing's
     * {@link InputMap}/{@link ActionMap} mechanism. Two {@link KeyStroke} entries are
     * registered per logical action: one for key-pressed (fires on first press only,
     * auto-repeat suppressed for edge-triggered actions) and one for key-released.
     * The component should already be focusable; call
     * {@link JComponent#requestFocusInWindow()} before the first game tick.
     *
     * @param component the {@link JComponent} (typically {@link client.GameCanvas})
     *                  to attach the bindings to; must not be {@code null}
     */
    public void registerBindings(JComponent component) {
        InputMap  im = component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = component.getActionMap();

        // -----------------------------------------------------------------
        // Register KeyStrokes → action name strings
        // Normal keys: modifiers = 0 for both pressed and released.
        // SHIFT pressed carries SHIFT_DOWN_MASK in the event's extended modifiers;
        // SHIFT released carries 0.
        // -----------------------------------------------------------------

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

        // SHIFT must be registered separately because the pressed event has
        // SHIFT_DOWN_MASK set while the released event has modifiers = 0.
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT,
                                      InputEvent.SHIFT_DOWN_MASK, false),
               "dash_pressed");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, 0, true),
               "dash_released");

        // -----------------------------------------------------------------
        // Move Left — level-triggered (flag stays true while key is held)
        // -----------------------------------------------------------------
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

        // -----------------------------------------------------------------
        // Move Right — level-triggered
        // -----------------------------------------------------------------
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

        // -----------------------------------------------------------------
        // Jump — edge-triggered: fires once per physical key press.
        // Auto-repeat is suppressed via pressedKeys; the "jump" entry is
        // added on first press and removed on release so the next press is
        // treated as a new edge.
        // -----------------------------------------------------------------
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

        // -----------------------------------------------------------------
        // Melee Attack — edge-triggered
        // -----------------------------------------------------------------
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

        // -----------------------------------------------------------------
        // Charge Projectile — hold-triggered.
        // Start time is recorded on first press; InputRouter fires the shot
        // on release if the hold duration meets the threshold.
        // chargeStartTime is intentionally NOT reset on release so InputRouter
        // can read the elapsed duration before clearing it.
        // -----------------------------------------------------------------
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
                // chargeStartTime preserved — InputRouter reads duration then clears it
            }
        });

        // -----------------------------------------------------------------
        // Dodge Roll — edge-triggered
        // -----------------------------------------------------------------
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

        // -----------------------------------------------------------------
        // Position Pulse — edge-triggered
        // -----------------------------------------------------------------
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

        // -----------------------------------------------------------------
        // Shadow Dash (SHIFT) — edge-triggered
        // -----------------------------------------------------------------
        am.put("dash_pressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pressedKeys.add("dash")) {
                    inputState.dashPressed = true;
                }
            }
        });
        am.put("dash_released", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.remove("dash");
            }
        });

        // -----------------------------------------------------------------
        // Pause Toggle (ESC) — edge-triggered
        // -----------------------------------------------------------------
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

        // -----------------------------------------------------------------
        // Fragment Library (F) — edge-triggered
        // -----------------------------------------------------------------
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

        // TOGGLE_CAMERA — C key for Apprentice in-game camera overlay
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

        // R key — no-op (gesture system removed)
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

        // C2 — TAB key for cycling block type in offline mode
        bindKey(im, KeyEvent.VK_TAB, 0, "tab");
        am.put("tab_pressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pressedKeys.add("tab")) {
                    if (GameSession.getInstance().isOffline) {
                        InputRouter router = InputRouter.getInstance();
                        if (router != null) {
                            router.routeGesture("POINTER", new java.awt.Point(0, 0));
                        }
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

    // =========================================================================
    // Accessor
    // =========================================================================

    /**
     * Returns the current {@link PlayerInputState} snapshot. The game loop reads this
     * once per tick after all key events for that frame have been processed.
     *
     * @return the active input state; never {@code null}
     */
    public PlayerInputState getInputState() {
        return inputState;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Registers a pressed/released {@link KeyStroke} pair in the given
     * {@link InputMap}, both using the specified modifiers mask. The two entries map
     * to {@code "<name>_pressed"} and {@code "<name>_released"} respectively, which
     * must have corresponding entries in the component's {@link ActionMap}.
     *
     * @param im        the {@link InputMap} to install into
     * @param keyCode   the {@link KeyEvent} VK constant for the physical key
     * @param modifiers the modifier mask (0 for unmodified keys)
     * @param name      the logical action name; {@code "_pressed"}/{@code "_released"}
     *                  suffixes are appended automatically
     */
    private void bindKey(InputMap im, int keyCode, int modifiers, String name) {
        im.put(KeyStroke.getKeyStroke(keyCode, modifiers, false), name + "_pressed");
        im.put(KeyStroke.getKeyStroke(keyCode, modifiers, true),  name + "_released");
    }

    // =========================================================================
    // Inner class — PlayerInputState
    // =========================================================================

    /**
     * A mutable snapshot of all Wanderer input actions for a single game tick.
     * Fields marked {@code volatile} so that writes on the Swing Event Dispatch Thread
     * are immediately visible to the physics-loop thread that reads them each tick.
     *
     * <p>Level-triggered flags ({@link #moveLeft}, {@link #moveRight},
     * {@link #chargeHeld}) remain set for as long as the key is held and are cleared
     * only on key release. Edge-triggered flags ({@link #jumpPressed},
     * {@link #attackPressed}, {@link #dodgePressed}, {@link #pulsePressed},
     * {@link #dashPressed}, {@link #pausePressed}) are set once on first press and
     * reset to {@code false} by {@link #consumeEdgeFlags()} after the game loop
     * processes them.
     *
     * @author [YOUR NAME]
     * @id [YOUR ID]
     * @date 2026-03-21
     * @certification I certify that this code is my own work and has not been copied
     *                from any other source, in whole or in part.
     */
    public static class PlayerInputState {

        /**
         * Minimum hold duration in milliseconds for a charge-attack release to fire.
         * Compared against {@code (currentTimeMillis - chargeStartTime)} in
         * {@link InputRouter}.
         */
        public static final long CHARGE_THRESHOLD_MS = 1000L;

        // -- Level-triggered flags --------------------------------------------

        /** {@code true} while the {@link KeyBindings#MOVE_LEFT} key is held. */
        public volatile boolean moveLeft;

        /** {@code true} while the {@link KeyBindings#MOVE_RIGHT} key is held. */
        public volatile boolean moveRight;

        /**
         * {@code true} while the {@link KeyBindings#CHARGE_ATTACK} key is held.
         * Used together with {@link #chargeStartTime} to calculate charge duration.
         */
        public volatile boolean chargeHeld;

        /**
         * The absolute system-clock time in milliseconds at which the charge key was
         * first pressed in the current hold sequence. {@code 0} when not charging.
         * Intentionally preserved on release so {@link InputRouter} can compute the
         * held duration; {@link InputRouter} resets it to {@code 0} after consuming.
         */
        public volatile long chargeStartTime;

        // -- Edge-triggered flags ---------------------------------------------

        /**
         * {@code true} on the first tick after the {@link KeyBindings#JUMP} key is
         * pressed; reset by {@link #consumeEdgeFlags()}.
         */
        public volatile boolean jumpPressed;

        /**
         * {@code true} on the first tick after the {@link KeyBindings#ATTACK} key is
         * pressed; reset by {@link #consumeEdgeFlags()}.
         */
        public volatile boolean attackPressed;

        /**
         * {@code true} on the first tick after the {@link KeyBindings#DODGE} key is
         * pressed; reset by {@link #consumeEdgeFlags()}.
         */
        public volatile boolean dodgePressed;

        /**
         * {@code true} on the first tick after the {@link KeyBindings#PULSE} key is
         * pressed; reset by {@link #consumeEdgeFlags()}.
         */
        public volatile boolean pulsePressed;

        /**
         * {@code true} on the first tick after the {@link KeyBindings#SHADOW_DASH} key
         * is pressed; reset by {@link #consumeEdgeFlags()}.
         */
        public volatile boolean dashPressed;

        /**
         * {@code true} on the first tick after the {@link KeyBindings#PAUSE} key is
         * pressed; reset by {@link #consumeEdgeFlags()}.
         */
        public volatile boolean pausePressed;

        /**
         * {@code true} on the first tick after the {@link KeyBindings#FRAGMENTS} key
         * is pressed; reset by {@link #consumeEdgeFlags()}.
         */
        public volatile boolean fragmentsPressed;

        /**
         * {@code true} on the first tick after the {@link KeyBindings#TOGGLE_CAMERA}
         * key is pressed; reset by {@link #consumeEdgeFlags()}. Only acted upon by the
         * Apprentice client — the Wanderer client ignores it.
         */
        public volatile boolean cameraTogglePressed;

        // -- Constructor ------------------------------------------------------

        /**
         * Constructs a {@code PlayerInputState} with all flags {@code false} and the
         * charge timer initialised to zero.
         */
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
            pausePressed        = false;
            fragmentsPressed    = false;
            cameraTogglePressed = false;
        }

        // -- Consumption ------------------------------------------------------

        /**
         * Resets all edge-triggered flags to {@code false} after the game loop has
         * consumed them for the current tick. Hold-state flags ({@link #moveLeft},
         * {@link #moveRight}, {@link #chargeHeld}) are left unchanged so they remain
         * accurate across ticks.
         *
         * <p>{@link #chargeStartTime} is managed separately by {@link InputRouter}
         * and is not cleared here.
         */
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
