/**
 Centralizes Wanderer control scheme key constants and exposes PlayerInputState
 snapshot class. Game loop reads state each tick to determine held/pressed actions.
 Bindings registered via Swing InputMap/ActionMap; WHEN_IN_FOCUSED_WINDOW scope.
 */

import javax.swing.AbstractAction;  // Base class for all InputMap-backed key actions; supplies actionPerformed contract
import javax.swing.ActionMap;        // Maps logical action name strings (e.g. "jump_pressed") to AbstractAction instances
import javax.swing.InputMap;         // Maps KeyStroke instances to logical action name strings
import javax.swing.JComponent;       // The Swing component onto which InputMap/ActionMap bindings are installed
import javax.swing.KeyStroke;        // Encapsulates a key + modifier combination used as a map key in InputMap
import java.awt.event.ActionEvent;   // Event object delivered to AbstractAction#actionPerformed when a binding fires
import java.awt.event.InputEvent;    // Provides modifier-mask constants (SHIFT_DOWN_MASK) for SHIFT key registration
import java.awt.event.KeyEvent;      // Provides VK_ virtual-key constants referenced by the public binding constants
import java.util.HashSet;            // Backing set for pressedKeys: O(1) add/remove/contains for auto-repeat suppression
import java.util.Set;                // Interface type for pressedKeys; keeps field declaration independent of impl

public class KeyBindings {

    // =========================================================================
    // Primary key constants
    // =========================================================================

    public static final int MOVE_LEFT = KeyEvent.VK_A;        // Move left (WASD)
    public static final int MOVE_LEFT_ALT = KeyEvent.VK_LEFT;  // Move left alternate
    public static final int MOVE_RIGHT = KeyEvent.VK_D;        // Move right (WASD)
    public static final int MOVE_RIGHT_ALT = KeyEvent.VK_RIGHT; // Move right alternate
    public static final int JUMP = KeyEvent.VK_SPACE;          // Jump
    public static final int JUMP_ALT = KeyEvent.VK_W;          // Jump alternate (WASD)
    public static final int ATTACK = KeyEvent.VK_J;            // Melee attack
    public static final int CHARGE_ATTACK = KeyEvent.VK_K;     // Charge projectile (hold to charge, release to fire)
    public static final int DODGE = KeyEvent.VK_L;             // Dodge roll
    public static final int PULSE = KeyEvent.VK_E;             // Position-pulse ability
    public static final int SHADOW_DASH = KeyEvent.VK_SHIFT;   // Shadow-dash ability
    public static final int PAUSE = KeyEvent.VK_ESCAPE;        // Toggle pause menu
    public static final int FRAGMENTS = KeyEvent.VK_F;         // Fragment library
    public static final int TOGGLE_CAMERA = KeyEvent.VK_C;     // Apprentice camera overlay toggle

    // =========================================================================
    // Fields
    // =========================================================================

    private final PlayerInputState inputState;  // Shared input snapshot; updated by EDT, read by game loop
    private final Set<String> pressedKeys;      // Tracks held keys to suppress auto-repeat for edge-triggered actions

    // =========================================================================
    // Constructor
    // =========================================================================

    public KeyBindings() { // Creates fresh PlayerInputState with all flags cleared
        this.inputState  = new PlayerInputState(); // Create the shared input snapshot with all flags cleared
        this.pressedKeys = new HashSet<>();         // Start with no keys held; populated by pressed Actions, cleared by released Actions
    }

    // =========================================================================
    // Registration
    // =========================================================================

    public void registerBindings(JComponent component) { // Installs all key bindings via InputMap/ActionMap
        InputMap  im = component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW); // Window-scope map: fires regardless of focus sub-component
        ActionMap am = component.getActionMap();                                   // Action map: looked up by the string keys stored in im

        // -----------------------------------------------------------------
        // Register KeyStrokes → action name strings
        // Normal keys: modifiers = 0 for both pressed and released.
        // SHIFT pressed carries SHIFT_DOWN_MASK in the event's extended modifiers;
        // SHIFT released carries 0.
        // -----------------------------------------------------------------

        bindKey(im, KeyEvent.VK_A,      0, "moveLeft");      // A key → "moveLeft_pressed" / "moveLeft_released"
        bindKey(im, KeyEvent.VK_LEFT,   0, "moveLeft");       // Arrow-left → same logical action as A
        bindKey(im, KeyEvent.VK_D,      0, "moveRight");      // D key → "moveRight_pressed" / "moveRight_released"
        bindKey(im, KeyEvent.VK_RIGHT,  0, "moveRight");      // Arrow-right → same logical action as D
        bindKey(im, KeyEvent.VK_SPACE,  0, "jump");           // Space bar → "jump_pressed" / "jump_released"
        bindKey(im, KeyEvent.VK_W,      0, "jump");           // W key → same logical action as Space
        bindKey(im, KeyEvent.VK_J,      0, "attack");         // J key → "attack_pressed" / "attack_released"
        bindKey(im, KeyEvent.VK_K,      0, "charge");         // K key → "charge_pressed" / "charge_released"
        bindKey(im, KeyEvent.VK_L,      0, "dodge");          // L key → "dodge_pressed" / "dodge_released"
        bindKey(im, KeyEvent.VK_E,      0, "pulse");          // E key → "pulse_pressed" / "pulse_released"
        bindKey(im, KeyEvent.VK_ESCAPE, 0, "pause");          // Escape → "pause_pressed" / "pause_released"
        bindKey(im, KeyEvent.VK_F,      0, "fragments");      // F key → "fragments_pressed" / "fragments_released"
        bindKey(im, KeyEvent.VK_C,      0, "cameraToggle");   // C key → "cameraToggle_pressed" / "cameraToggle_released"

        // SHIFT must be registered separately because the pressed event has
        // SHIFT_DOWN_MASK set while the released event has modifiers = 0.
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT,
                                      InputEvent.SHIFT_DOWN_MASK, false), // false = key-pressed event (not released)
               "dash_pressed");                                             // Maps to "dash_pressed" action in the ActionMap
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, 0, true),        // true = key-released; modifiers=0 because SHIFT is released
               "dash_released");                                            // Maps to "dash_released" action in the ActionMap

        // -----------------------------------------------------------------
        // Move Left — level-triggered (flag stays true while key is held)
        // -----------------------------------------------------------------
        am.put("moveLeft_pressed", new AbstractAction() {   // Fires every time Swing delivers a key-pressed event (including auto-repeat)
            @Override
            public void actionPerformed(ActionEvent e) {
                inputState.moveLeft = true;  // Hold-flag: stays set until moveLeft_released fires on key release
            }
        });
        am.put("moveLeft_released", new AbstractAction() {  // Fires once when the A or Arrow-left key is physically released
            @Override
            public void actionPerformed(ActionEvent e) {
                inputState.moveLeft = false; // Clear hold-flag: InputRouter will stop applying leftward velocity this tick
            }
        });

        // -----------------------------------------------------------------
        // Move Right — level-triggered
        // -----------------------------------------------------------------
        am.put("moveRight_pressed", new AbstractAction() {  // Fires every key-pressed event for D or Arrow-right (including auto-repeat)
            @Override
            public void actionPerformed(ActionEvent e) {
                inputState.moveRight = true; // Hold-flag: stays set until moveRight_released clears it
            }
        });
        am.put("moveRight_released", new AbstractAction() { // Fires once when the D or Arrow-right key is released
            @Override
            public void actionPerformed(ActionEvent e) {
                inputState.moveRight = false; // Clear hold-flag: Wanderer stops moving right next tick
            }
        });

        // -----------------------------------------------------------------
        // Jump — edge-triggered: fires once per physical key press.
        // Auto-repeat is suppressed via pressedKeys; the "jump" entry is
        // added on first press and removed on release so the next press is
        // treated as a new edge.
        // -----------------------------------------------------------------
        am.put("jump_pressed", new AbstractAction() {       // Fires on every key-pressed event for Space or W
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pressedKeys.add("jump")) {              // add() returns true only on first insertion (edge), false on auto-repeat
                    inputState.jumpPressed = true;           // Set edge flag; InputRouter reads this once then consumeEdgeFlags() clears it
                }
            }
        });
        am.put("jump_released", new AbstractAction() {      // Fires once when Space or W is physically released
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.remove("jump");                  // Remove so the next physical press is treated as a fresh edge
            }
        });

        // -----------------------------------------------------------------
        // Melee Attack — edge-triggered
        // -----------------------------------------------------------------
        am.put("attack_pressed", new AbstractAction() {     // Fires on every key-pressed event for J (including auto-repeat)
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pressedKeys.add("attack")) {            // Guard: only set edge flag on the first press, not auto-repeat
                    inputState.attackPressed = true;         // Edge flag: InputRouter triggers one melee swing then consumeEdgeFlags() resets
                }
            }
        });
        am.put("attack_released", new AbstractAction() {    // Fires once when J is released
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.remove("attack");                // Re-arm so the next J press generates a new attack edge
            }
        });

        // -----------------------------------------------------------------
        // Charge Projectile — hold-triggered.
        // Start time is recorded on first press; InputRouter fires the shot
        // on release if the hold duration meets the threshold.
        // chargeStartTime is intentionally NOT reset on release so InputRouter
        // can read the elapsed duration before clearing it.
        // -----------------------------------------------------------------
        am.put("charge_pressed", new AbstractAction() {     // Fires on every key-pressed event for K
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!inputState.chargeHeld) {               // Guard: only latch start time on the first press, not auto-repeat
                    inputState.chargeHeld      = true;       // Signals to InputRouter that the charge key is currently held
                    inputState.chargeStartTime = System.currentTimeMillis(); // Snapshot wall-clock for hold-duration calculation on release
                }
            }
        });
        am.put("charge_released", new AbstractAction() {    // Fires once when K is released
            @Override
            public void actionPerformed(ActionEvent e) {
                inputState.chargeHeld = false;               // Clear hold flag so InputRouter knows key is up
                // chargeStartTime preserved — InputRouter reads duration then clears it
                // Leaving chargeStartTime intact lets InputRouter compute elapsed = now - chargeStartTime
                // before deciding whether to fire the projectile. InputRouter sets chargeStartTime=0 after consuming.
            }
        });

        // -----------------------------------------------------------------
        // Dodge Roll — edge-triggered
        // -----------------------------------------------------------------
        am.put("dodge_pressed", new AbstractAction() {      // Fires on every key-pressed event for L
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pressedKeys.add("dodge")) {             // Edge guard: dodge fires once per physical press, not on auto-repeat
                    inputState.dodgePressed = true;          // Edge flag: InputRouter initiates one dodge roll animation this tick
                }
            }
        });
        am.put("dodge_released", new AbstractAction() {     // Fires once when L is released
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.remove("dodge");                 // Re-arm for next physical L press
            }
        });

        // -----------------------------------------------------------------
        // Position Pulse — edge-triggered
        // -----------------------------------------------------------------
        am.put("pulse_pressed", new AbstractAction() {      // Fires on every key-pressed event for E
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pressedKeys.add("pulse")) {             // Edge guard: pulse emits once per physical press
                    inputState.pulsePressed = true;          // Edge flag: InputRouter triggers the light-pulse ability this tick
                }
            }
        });
        am.put("pulse_released", new AbstractAction() {     // Fires once when E is released
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.remove("pulse");                 // Re-arm for next E press
            }
        });

        // -----------------------------------------------------------------
        // Shadow Dash (SHIFT) — edge-triggered for shadow dash outside BOSS;
        // P8.9 — also mirrored as level-triggered {@code shiftHeld} so the
        // BOSS-phase Radiant Collapse FSM can distinguish "held down" from
        // "freshly pressed". Both flags are maintained in parallel so
        // non-BOSS behaviour is unchanged.
        // -----------------------------------------------------------------
        am.put("dash_pressed", new AbstractAction() {       // Fires on every key-pressed event for SHIFT (registered with SHIFT_DOWN_MASK)
            @Override
            public void actionPerformed(ActionEvent e) {
                inputState.shiftHeld = true;                 // Level-triggered: stays true while SHIFT is held; read by Radiant Collapse FSM
                if (pressedKeys.add("dash")) {              // Edge guard: dashPressed fires only on first physical press, not auto-repeat
                    inputState.dashPressed = true;           // Edge flag: InputRouter triggers shadow-dash this tick (non-BOSS) or ignores in BOSS
                }
            }
        });
        am.put("dash_released", new AbstractAction() {      // Fires once when SHIFT is physically released
            @Override
            public void actionPerformed(ActionEvent e) {
                inputState.shiftHeld = false;                // Clear hold flag: Radiant FSM transitions CHARGING→IDLE on next tick
                pressedKeys.remove("dash");                  // Re-arm for next SHIFT press so the next press creates a new dash edge
            }
        });

        // -----------------------------------------------------------------
        // Pause Toggle (ESC) — edge-triggered
        // -----------------------------------------------------------------
        am.put("pause_pressed", new AbstractAction() {      // Fires on every key-pressed event for Escape
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pressedKeys.add("pause")) {             // Edge guard: only toggle pause once per physical press
                    inputState.pausePressed = true;          // Edge flag: GameSession processes pause/resume this tick
                }
            }
        });
        am.put("pause_released", new AbstractAction() {     // Fires once when Escape is released
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.remove("pause");                 // Re-arm so the next Escape press toggles again
            }
        });

        // -----------------------------------------------------------------
        // Fragment Library (F) — edge-triggered
        // -----------------------------------------------------------------
        am.put("fragments_pressed", new AbstractAction() {  // Fires on every key-pressed event for F
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pressedKeys.add("fragments")) {         // Edge guard: open/close fragment log once per press
                    inputState.fragmentsPressed = true;      // Edge flag: InputRouter shows the FragmentLibrary overlay this tick
                }
            }
        });
        am.put("fragments_released", new AbstractAction() { // Fires once when F is released
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.remove("fragments");             // Re-arm for next F press
            }
        });

        // TOGGLE_CAMERA — C key for Apprentice in-game camera overlay
        am.put("cameraToggle_pressed", new AbstractAction() { // Fires on every key-pressed event for C
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pressedKeys.add("cameraToggle")) {      // Edge guard: toggle once per press
                    inputState.cameraTogglePressed = true;   // Edge flag: only acted upon by Apprentice client code in InputRouter
                }
            }
        });
        am.put("cameraToggle_released", new AbstractAction() { // Fires once when C is released
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.remove("cameraToggle");          // Re-arm for next C press
            }
        });

        // R key — no-op (gesture system removed)
        bindKey(im, KeyEvent.VK_R, 0, "recapture");         // Register R so it doesn't fall through to unintended actions; now a no-op
        am.put("recapture_pressed", new AbstractAction() {  // Fires on every key-pressed event for R
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.add("recapture");                // Track hold state even for no-op so released cleans up correctly
                System.out.println("[KeyBindings] R pressed (recapture no-op)"); // Debug trace; gesture system was removed but key is still captured
            }
        });
        am.put("recapture_released", new AbstractAction() { // Fires once when R is released
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.remove("recapture");             // Clean up hold tracking for no-op key
            }
        });

        // C2 — TAB key for cycling block type (Apprentice only)
        bindKey(im, KeyEvent.VK_TAB, 0, "tab");             // Register TAB so it can cycle the Apprentice's block type selection
        am.put("tab_pressed", new AbstractAction() {         // Fires on every key-pressed event for TAB
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pressedKeys.add("tab")) {               // Edge guard: cycle only once per press, not on auto-repeat
                    if (GameSession.getInstance().isApprentice()) {    // Guard: only Apprentice clients can cycle block types
                        GameSession.getInstance().cycleBlockType();    // Advance selected block type to the next in the rotation
                    }
                }
            }
        });
        am.put("tab_released", new AbstractAction() {        // Fires once when TAB is released
            @Override
            public void actionPerformed(ActionEvent e) {
                pressedKeys.remove("tab");                   // Re-arm so next TAB press triggers another cycle
            }
        });
    }

    // =========================================================================
    // Accessor
    // =========================================================================

    public PlayerInputState getInputState() { // Returns current input state snapshot
        return inputState; // Return the single shared snapshot; callers must not hold a reference across ticks
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void bindKey(InputMap im, int keyCode, int modifiers, String name) { // Helper to register pressed/released KeyStroke pair
        im.put(KeyStroke.getKeyStroke(keyCode, modifiers, false), name + "_pressed");  // false = key-pressed event; maps to "<name>_pressed" action
        im.put(KeyStroke.getKeyStroke(keyCode, modifiers, true),  name + "_released"); // true  = key-released event; maps to "<name>_released" action
    }

    // =========================================================================
    // Inner class — PlayerInputState
    // =========================================================================

    /**
     Mutable snapshot of all Wanderer input actions per tick. Volatile fields
     ensure EDT writes are immediately visible to game loop reader. Level-triggered
     flags (moveLeft, moveRight, chargeHeld) stay set while key is held. Edge-triggered
     flags (jumpPressed, attackPressed, etc.) set once on first press and cleared by
     consumeEdgeFlags() after game loop processes them.
     */
    public static class PlayerInputState {

        public static final long CHARGE_THRESHOLD_MS = 1000L; // Min charge hold time (ms)

        // -- Level-triggered flags --------------------------------------------

        public volatile boolean moveLeft;  // Level-triggered: held while key pressed
        public volatile boolean moveRight; // Level-triggered: held while key pressed
        public volatile boolean chargeHeld; // Level-triggered: held while charge key pressed
        public volatile long chargeStartTime; // Wall-clock time when charge started (0 = not charging)

        // -- Edge-triggered flags ---------------------------------------------

        public volatile boolean jumpPressed;   // Edge flag: set once per physical press
        public volatile boolean attackPressed; // Edge flag: set once per physical press
        public volatile boolean dodgePressed;  // Edge flag: set once per physical press
        public volatile boolean pulsePressed;  // Edge flag: set once per physical press
        public volatile boolean dashPressed;   // Edge flag: set once per physical press
        public volatile boolean shiftHeld;     // Level-triggered: true while SHIFT held
        public volatile boolean pausePressed;  // Edge flag: set once per physical press
        public volatile boolean fragmentsPressed; // Edge flag: set once per physical press
        public volatile boolean cameraTogglePressed; // Edge flag: set once per physical press (Apprentice only)

        // -- Constructor ------------------------------------------------------

        public PlayerInputState() { // All flags cleared; safe "no input" defaults
            moveLeft       = false;  // No left movement at startup
            moveRight      = false;  // No right movement at startup
            jumpPressed    = false;  // No jump queued at startup
            attackPressed  = false;  // No melee queued at startup
            chargeHeld     = false;  // Charge key not held at startup
            chargeStartTime = 0L;    // 0 sentinel means "not charging"; InputRouter skips charge processing when this is 0
            dodgePressed   = false;  // No dodge queued at startup
            pulsePressed   = false;  // No pulse queued at startup
            dashPressed    = false;  // No dash queued at startup
            shiftHeld      = false;  // SHIFT not held at startup; Radiant FSM stays in IDLE
            pausePressed        = false; // Pause not triggered at startup
            fragmentsPressed    = false; // Fragment log not triggered at startup
            cameraTogglePressed = false; // Camera toggle not triggered at startup
        }

        // -- Consumption ------------------------------------------------------

        public void consumeEdgeFlags() { // Clears all edge-triggered flags; called once per tick after game loop processes them
            jumpPressed   = false;  // Clear jump edge; prevents the same press from triggering a second jump next tick
            attackPressed = false;  // Clear attack edge; prevents double-swing from one press
            dodgePressed  = false;  // Clear dodge edge; prevents double-dodge from one press
            pulsePressed  = false;  // Clear pulse edge; prevents double-pulse from one press
            dashPressed          = false; // Clear dash edge; prevents double-dash from one SHIFT press
            pausePressed         = false; // Clear pause edge; prevents toggling pause twice from one ESC press
            fragmentsPressed     = false; // Clear fragment-log edge; prevents toggling twice from one F press
            cameraTogglePressed  = false; // Clear camera-toggle edge; prevents toggling twice from one C press
            // moveLeft, moveRight, chargeHeld, shiftHeld, and chargeStartTime are intentionally NOT cleared here;
            // they are maintained by key-release events, not by the consumption pass.
        }
    }
}
