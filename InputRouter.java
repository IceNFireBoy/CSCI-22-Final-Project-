/**
 Routes all keyboard and mouse input to game subsystems based on phase and client
 role (Wanderer/Apprentice). Single dispatcher bridging Swing events to Physics,
 MouseApprentice, ability fires, lobby selection, and cutscene advancement.

 Implements MouseListener/MouseMotionListener/MouseWheelListener on GameCanvas.
 Keyboard routing via routeKeyEvent() called per tick. Cutscene and Apprentice
 bindings installed via registerCutsceneBindings/registerApprenticeKeyBindings.
 */

// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 2a "Event Handling"  - implements MouseListener,
//                               MouseMotionListener, and
//                               MouseWheelListener simultaneously,
//                               registering itself with GameCanvas
//                               via addMouseListener et al. Direct
//                               application of the listener registration
//                               pattern from 2a.
// Module 1b "Interfaces"      - this class is a "implements three
//                               interfaces at once" example - exactly
//                               the multi-interface inheritance teaching
//                               from 1b.
// Module 4b "Key Bindings"    - registerCutsceneBindings and
//                               registerApprenticeKeyBindings install
//                               additional InputMap/ActionMap entries
//                               via the same pattern KeyBindings.java
//                               uses - the canonical Key Bindings
//                               approach.
// Module 1d "Inner Classes"   - each AbstractAction registered in
//                               registerApprenticeKeyBindings is an
//                               anonymous inner class.
// =========================================================================

import java.awt.event.*;          // MouseEvent, MouseWheelEvent, ActionEvent, KeyEvent, InputEvent — all AWT event types used here
import javax.swing.AbstractAction; // Base class for InputMap-backed key actions registered in registerApprenticeKeyBindings / registerCutsceneBindings
import javax.swing.ActionMap;      // Holds the AbstractAction instances looked up by the logical names stored in InputMap
import javax.swing.InputMap;       // Maps KeyStroke instances to logical action name strings used in ActionMap
import javax.swing.JComponent;     // The Swing component onto which InputMap/ActionMap entries are installed
import javax.swing.KeyStroke;      // Encapsulates a key + modifier combination as the key in InputMap

public class InputRouter implements MouseListener, MouseMotionListener, MouseWheelListener {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private GameCanvas canvas;   // Render surface; source of mouse events and coordinate queries
    private GameSession session; // Singleton session; provides role, server comms, block/ability methods
    private StunMinigame stunMinigame; // P8.8: minigame controller; injected by GameStarter

    private long lastBlockRainTime = 0; // Timestamp of last Block Rain attack; enforces cooldown
    private long lastShieldTime = 0;    // Timestamp of last Shield activation; enforces cooldown
    private long lastSpikeTime = 0;     // Timestamp of last Spike Array; enforces cooldown
    private static final long BLOCK_RAIN_COOLDOWN = 8000; // Block Rain cooldown (ms)
    private static final long SHIELD_COOLDOWN = 8000;     // Shield cooldown (ms)
    private static final long SPIKE_COOLDOWN = 6000;      // Spike Array cooldown (ms)

    // -------------------------------------------------------------------------
    // Lobby interaction state
    // -------------------------------------------------------------------------

    private String lastLobbyHoverRole = "NONE"; // Lobby hover debounce
    private String localSelectedRole = "NONE";  // Lobby selection tracking; re-click sends CANCEL
    private static InputRouter instance;        // Singleton reference for legacy callers

    // =========================================================================
    // Singleton accessor
    // =========================================================================

    public static InputRouter getInstance() { return instance; } // Legacy singleton accessor

    // =========================================================================
    // Constructors
    // =========================================================================

    public InputRouter() { // Legacy no-arg constructor; canvas/session wired via setters
        instance = this; // Publish to singleton immediately so getInstance() works before setGameCanvas() is called
    }

    public InputRouter(GameCanvas canvas, GameSession session) { // Full constructor; registers mouse listeners immediately
        this.canvas  = canvas;                       // Store canvas reference; used for coordinate queries and listener registration
        this.session = session;                      // Store session reference; used for role checks and server communication
        instance = this;                             // Publish singleton reference for legacy callers
        canvas.addMouseListener(this);               // Register for mousePressed / mouseReleased / mouseClicked / mouseEntered / mouseExited
        canvas.addMouseMotionListener(this);         // Register for mouseMoved / mouseDragged
        canvas.addMouseWheelListener(this);          // Register for mouseWheelMoved (Apprentice radius adjustment)
        canvas.setFocusable(true);                   // Ensure canvas can receive keyboard focus required by InputMap bindings
    }

    // =========================================================================
    // Injection setters
    // =========================================================================

    public void setStunMinigame(StunMinigame m) { // P8.8: injects minigame controller for SPACE handling
        this.stunMinigame = m; // Assign injected controller; checked for null before use in registerCutsceneBindings SPACE handler
    }

    public void setGameCanvas(GameCanvas canvas) { // Re-wires listeners to new canvas; removes from old if exists
        if (this.canvas != null) {                           // Only attempt removal if a previous canvas was registered
            this.canvas.removeMouseListener(this);           // Remove from old canvas to prevent double-dispatch after swap
            this.canvas.removeMouseMotionListener(this);     // Remove motion listener from old canvas
            this.canvas.removeMouseWheelListener(this);      // Remove wheel listener from old canvas
        }
        this.canvas  = canvas;                               // Store the new canvas reference
        this.session = GameSession.getInstance();            // Refresh session reference; may have changed on reconnect
        canvas.addMouseListener(this);                       // Register mouse events on new canvas
        canvas.addMouseMotionListener(this);                 // Register motion events on new canvas
        canvas.addMouseWheelListener(this);                  // Register wheel events on new canvas
        canvas.setFocusable(true);                           // Ensure new canvas is focusable so InputMap bindings fire
    }

    // =========================================================================
    // Key routing (called per tick by game loop)
    // =========================================================================

    public void routeKeyEvent(KeyBindings.PlayerInputState input, Player player, Physics physicsEngine) { // Routes key input each tick; suppressed during cutscenes
        if (input == null || player == null || physicsEngine == null) return; // Null-guard: any missing dependency means no routing can occur safely

        // Cutscene lock: suppress all Wanderer input while a story sequence plays
        if (CutsceneRenderer.get().isPlaying()) {
            input.consumeEdgeFlags();               // Discard all pending edge flags so they don't queue behind the cutscene
            physicsEngine.stopWalking(player);      // Force-stop horizontal movement; Wanderer freezes in place during cutscenes
            return;                                 // Skip all further input processing for this tick
        }

        // Horizontal movement — level-triggered (flag stays true while key is held)
        if (input.moveLeft)  physicsEngine.walk(player, -1); // Walk left at normal speed (-1 direction) while A or Arrow-left is held
        if (input.moveRight) physicsEngine.walk(player,  1); // Walk right at normal speed (+1 direction) while D or Arrow-right is held

        // If neither direction is held, stop applying walk velocity so the Wanderer decelerates
        if (!input.moveLeft && !input.moveRight) physicsEngine.stopWalking(player); // Neither key held: clear horizontal walk intent

        // Jump — edge-triggered (only fires on the tick the key was first pressed)
        if (input.jumpPressed) { physicsEngine.jump(player); input.jumpPressed = false; } // Apply upward impulse; clear flag so jump fires once per press

        // Melee attack — edge-triggered
        if (input.attackPressed) { player.executeMelee(); input.attackPressed = false; } // Trigger melee swing animation; clear flag

        // Dodge roll — edge-triggered
        if (input.dodgePressed) { physicsEngine.dodgeRoll(player); input.dodgePressed = false; } // Initiate dodge roll; clear flag

        // P8.9 — SHIFT routing splits on current phase:
        //   BOSS + Radiant unlocked → drive the Radiant Collapse FSM; SHIFT
        //       does NOT trigger the shadow dash here, the ability is swapped
        //       for the duration of the boss arena.
        //   BOSS + Radiant locked   → SHIFT is a no-op. The plan explicitly
        //       forbids shadow-dash during the boss even when the fragment is
        //       still missing, so the ability remains unavailable until the
        //       hidden shard is found.
        //   Any other phase         → fall through to the pre-P8.9 shadow
        //       dash behaviour so non-boss traversal is unchanged.
        String act = getAct(); // Determine current act/phase once and reuse below to avoid multiple LevelState reads
        if ("BOSS".equals(act)) {
            if (player.isRadiantCollapseUnlocked()) {                                    // Only drive the FSM if the hidden fragment has been found
                player.updateRadiantFsm(System.currentTimeMillis(), input.shiftHeld);    // Advance Radiant FSM: CHARGING when shiftHeld, ACTIVE on release
            }
            // Consume the edge flag either way so it does not leak back into
            // the shadow-dash path on the next non-BOSS tick.
            input.dashPressed = false; // Discard dash edge: in BOSS phase, SHIFT never triggers shadow-dash regardless of Radiant status
        } else if (input.dashPressed) {
            physicsEngine.shadowDash(player);  // Non-BOSS phase: SHIFT triggers shadow-dash with brief damage immunity
            input.dashPressed = false;         // Clear edge flag so the dash fires exactly once per press
        }
    }

    // =========================================================================
    // Phase helper
    // =========================================================================

    private String getAct() { // Return current phase string (LOBBY, ACT1-3, BOSS) for input routing; default to ACT1
        if (session == null) session = GameSession.getInstance(); // Lazy init: session may not have been injected yet at startup
        LevelState ls = (canvas != null) ? canvas.getLevelStatePublic() : null; // Read LevelState from canvas; null if canvas not yet set
        if (ls == null) return "ACT1"; // Default: treat missing state as ACT1 so movement input works even before level loads
        switch (ls.currentPhase) {
            case LOBBY: return "LOBBY"; // Lobby phase: only role-selection mouse events are valid
            case ACT1:  return "ACT1";  // Act 1: platforming + block placement
            case ACT2:  return "ACT2";  // Act 2: light-ball mechanics + hazard suite
            case ACT3:  return "ACT3";  // Act 3: combined platforming + light mechanics
            case BOSS:  return "BOSS";  // Boss phase: arena combat, Radiant FSM, architect override attacks
            default:    return "ACT1";  // Unknown phase: fall back to ACT1 for safety
        }
    }

    // =========================================================================
    // MouseListener
    // =========================================================================

    public void mousePressed(MouseEvent e) { // Dispatch lobby/block placement/boss attack based on phase and role
        if (CutsceneRenderer.get().isPlaying()) return; // Suppress all mouse clicks while a cutscene is showing
        if (session == null) session = GameSession.getInstance(); // Lazy-init session if not yet injected
        int x = e.getX(), y = e.getY();                          // Screen-space coordinates of the click
        boolean left   = e.getButton() == MouseEvent.BUTTON1;    // Left button: primary action (select, place block, fire beam)
        boolean right  = e.getButton() == MouseEvent.BUTTON3;    // Right button: secondary action (remove block, fire block rain)
        boolean middle = e.getButton() == MouseEvent.BUTTON2;    // Middle button: tertiary action (fire crusher block)
        String act = getAct();                                    // Determine current phase once; used by all branches below

        // ---- LOBBY phase: role-card clicks for all clients ----
        if (act.equals("LOBBY")) {
            if (left && canvas != null) {                                        // Only left-click selects a role card in the lobby
                String clicked = canvas.getLobbyRoleAtPoint(x, y);              // Ask canvas which role card (if any) is under the click
                if (!clicked.equals("NONE")) {                                   // "NONE" means the click missed all role cards
                    // In a reconnect session, only the vacant role card is clickable;
                    // ignore clicks on the occupied (still-active) partner's role.
                    LobbyState ls = canvas.getLobbyState();                      // Read current lobby state to check for reconnect context
                    if (ls != null && ls.isReconnectSession) {                   // Reconnect mode: only the vacant slot can be taken
                        if (!clicked.equals(ls.vacantRole)) {
                            return; // occupied role — ignore: cannot steal an active player's role in reconnect
                        }
                    }
                    if (clicked.equals(localSelectedRole)) {
                        // Second click on already-selected card → cancel
                        session.sendToServer(Protocol.LOBBY_CANCEL + "|" + clicked); // Inform server this client deselected the role
                        localSelectedRole = "NONE";                                  // Reset local selection tracker
                    } else {
                        // First click on a card → select
                        session.sendToServer(Protocol.LOBBY_SELECT + "|" + clicked); // Inform server this client wants the role
                        localSelectedRole = clicked;                                  // Track which card is now selected locally
                    }
                }
            }
            return; // No other mouse actions valid during lobby — exit handler early
        }

        if (session.isApprentice()) {                                            // All following code is Apprentice-only
            if (act.equals("ACT1") || act.equals("ACT3")) {                     // Block placement is available in ACT1 and ACT3
                if (left) {
                    int snappedX = (x / 32) * 32;                               // Snap click to 32-px horizontal grid for clean block alignment
                    int snappedY = (y / 16) * 16;                               // Snap click to 16-px vertical grid matching tile height
                    if (session.getBlockBudget() > 0) {                         // Only place if the Apprentice has budget remaining
                        session.placeBlock(session.getCurrentBlockType(), snappedX, snappedY); // Tell session to create a block at snapped position
                        session.decrementBlockBudget();                         // Consume one block from the budget
                    }
                }
                if (right) {
                    session.removeNearestBlock(x, y, 40); // Remove the nearest placed block within 40 px of the click
                }
            }
            if (act.equals("ACT2") || act.equals("ACT3")) {                    // Light-ball mouse drag is active in ACT2 and ACT3
                MouseApprentice.getInstance().setLeftPressed(left);             // Inform MouseApprentice of left-button state for drag calculation
                MouseApprentice.getInstance().setRightPressed(right);           // Inform MouseApprentice of right-button state for drag calculation
            }
            if (session.isArchitectOverride()) {                                // Architect override: boss attack keys are enabled for this client
                long now = System.currentTimeMillis();                          // Snapshot time once for all cooldown comparisons below
                // During BOSS the arena is world-space; transform screen click
                // through the shared camera before dispatching to the server.
                int aimX = x;  // Default to screen coords; overridden for BOSS phase
                int aimY = y;  // Default to screen coords; overridden for BOSS phase
                if (act.equals("BOSS")) {
                    aimX = Camera.getInstance().screenToWorldX(x); // Convert screen X to world X using camera scroll offset
                    aimY = Camera.getInstance().screenToWorldY(y); // Convert screen Y to world Y using camera scroll offset
                }
                if (left)  session.fireSearingBeam(aimX, aimY);                              // Left-click: fire a permanent Searing Beam toward aim point
                if (right && now - lastBlockRainTime >= BLOCK_RAIN_COOLDOWN) {               // Right-click: fire Block Rain if cooldown has elapsed
                    session.fireBlockRain();                                                   // Tell session to broadcast the Block Rain attack
                    lastBlockRainTime = now;                                                   // Record timestamp to restart cooldown window
                }
                if (middle) session.fireCrusherBlock(aimX, aimY);                            // Middle-click: spawn a Crusher Block at aim point
            }
        }
    }

    // =========================================================================
    // MouseMotionListener
    // =========================================================================

    public void mouseMoved(MouseEvent e) { // Update Apprentice cursor position; send lobby hover on card change
        if (CutsceneRenderer.get().isPlaying()) return; // Suppress position updates while a cutscene is showing
        if (session == null) session = GameSession.getInstance(); // Lazy-init session
        String act = getAct(); // Determine phase once

        // Lobby hover detection — debounced so LOBBY_HOVER is only sent on change
        if (act.equals("LOBBY") && canvas != null) {
            String hovered = canvas.getLobbyRoleAtPoint(e.getX(), e.getY()); // Determine which card is under the cursor
            if (!hovered.equals(lastLobbyHoverRole)) {                        // Only send if the hovered card has changed
                lastLobbyHoverRole = hovered;                                  // Update debounce tracker
                session.sendToServer(Protocol.LOBBY_HOVER + "|" + hovered);  // Notify server so both clients can show hover highlight
            }
            return; // Skip Apprentice-only position tracking during lobby
        }

        if (session.isApprentice()) {
            int mx = e.getX(), my = e.getY();         // Screen coordinates of the cursor
            if (act.equals("BOSS")) {
                mx = Camera.getInstance().screenToWorldX(mx); // Convert to world X for boss-arena targeting
                my = Camera.getInstance().screenToWorldY(my); // Convert to world Y for boss-arena targeting
            }
            MouseApprentice.getInstance().updatePosition(mx, my); // Update the authoritative cursor position in MouseApprentice
        }
    }

    public void mouseDragged(MouseEvent e) { // Mirror mouseMoved() for drag-based cursor tracking
        if (CutsceneRenderer.get().isPlaying()) return; // Suppress drag updates during cutscenes
        if (session == null) session = GameSession.getInstance(); // Lazy-init session
        String act = getAct(); // Determine phase once

        // Lobby hover detection during drag (e.g. mouse pressed and moved across cards)
        if (act.equals("LOBBY") && canvas != null) {
            String hovered = canvas.getLobbyRoleAtPoint(e.getX(), e.getY()); // Identify card under dragged cursor
            if (!hovered.equals(lastLobbyHoverRole)) {                        // Debounce: only send on card change
                lastLobbyHoverRole = hovered;                                  // Update tracker
                session.sendToServer(Protocol.LOBBY_HOVER + "|" + hovered);  // Send hover update to server
            }
            return; // No Apprentice drag logic during lobby
        }

        if (session.isApprentice()) {
            int mx = e.getX(), my = e.getY();         // Screen coordinates during drag
            if (act.equals("BOSS")) {
                mx = Camera.getInstance().screenToWorldX(mx); // Convert to world X for boss-arena drag targeting
                my = Camera.getInstance().screenToWorldY(my); // Convert to world Y for boss-arena drag targeting
            }
            MouseApprentice.getInstance().updatePosition(mx, my); // Update cursor position so LightBall follows the dragged point
        }
    }

    // =========================================================================
    // MouseWheelListener
    // =========================================================================

    public void mouseWheelMoved(MouseWheelEvent e) { // Adjust Apprentice light radius; negative rotation = zoom in
        if (CutsceneRenderer.get().isPlaying()) return; // Suppress wheel input during cutscenes
        if (session == null) session = GameSession.getInstance(); // Lazy-init session
        if (session.isApprentice()) {
            int delta = (int) e.getWheelRotation() * -10; // Negate: scroll toward user = positive delta = radius increase
            MouseApprentice.getInstance().adjustRadius(delta); // Expand or shrink the light radius by delta pixels
        }
    }

    // =========================================================================
    // Apprentice key bindings
    // =========================================================================

    public void registerApprenticeKeyBindings(JComponent comp) { // Install Apprentice bindings: F (light toggle), E (spike/ready), Q (shield/expand)
        InputMap im = comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW); // Window-scoped map: fires even if a sub-panel holds focus
        ActionMap am = comp.getActionMap();                                  // Matching action map for the logical name strings

        // F key — light toggle (overrides KeyBindings "fragments" mapping for Apprentice)
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0, false), "apprenticeLight_pressed");  // Map F-press to apprenticeLight_pressed action
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0, true),  "apprenticeLight_released"); // Map F-release to apprenticeLight_released action
        am.put("apprenticeLight_pressed", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (CutsceneRenderer.get().isPlaying()) return;              // Ignore input during cutscene lock
                if (session == null) session = GameSession.getInstance();    // Lazy-init session
                MouseApprentice ma = MouseApprentice.getInstance();          // Get the singleton Apprentice controller
                if (ma.isLightActive()) {
                    ma.setLightActive(false);                                // Light is on → turn it off
                } else if (session.getLightBattery() > 5f) {                // Battery check: require more than 5% to prevent flicker-on
                    ma.setLightActive(true);                                 // Light is off and battery is sufficient → turn it on
                    ma.setLightForcedOff(false);                             // Clear forced-off flag set by battery-drain system
                }
            }
        });

        // E key — signal level ready (+ spike array if architect override)
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, 0, false), "apprenticeE_pressed");  // Map E-press
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, 0, true),  "apprenticeE_released"); // Map E-release (no action needed on release)
        am.put("apprenticeE_pressed", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (CutsceneRenderer.get().isPlaying()) return;                              // Ignore during cutscenes
                if (session == null) session = GameSession.getInstance();                    // Lazy-init session
                long now = System.currentTimeMillis();                                       // Snapshot time once for cooldown check
                session.signalLevelReady();                                                   // Tell the server this client is ready to advance the level
                if (session.isArchitectOverride() && now - lastSpikeTime >= SPIKE_COOLDOWN) { // Spike Array: only if architect and cooldown elapsed
                    session.fireSpikeArray();                                                 // Broadcast Spike Array attack to server
                    lastSpikeTime = now;                                                      // Reset cooldown timer
                }
            }
        });

        // Q key — expand light radius (+ shield if architect override)
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0, false), "apprenticeQ_pressed");  // Map Q-press
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0, true),  "apprenticeQ_released"); // Map Q-release (no action needed on release)
        am.put("apprenticeQ_pressed", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (CutsceneRenderer.get().isPlaying()) return;                               // Ignore during cutscenes
                if (session == null) session = GameSession.getInstance();                     // Lazy-init session
                long now = System.currentTimeMillis();                                        // Snapshot time for cooldown check
                MouseApprentice.getInstance().adjustRadius(30);                               // Expand light radius by 30 px; gives Wanderer more visibility
                if (session.isArchitectOverride() && now - lastShieldTime >= SHIELD_COOLDOWN) { // Shield: only if architect and cooldown elapsed
                    session.fireShield();                                                      // Broadcast Shield activation to server
                    lastShieldTime = now;                                                      // Reset cooldown timer
                }
            }
        });
    }

    // =========================================================================
    // Cutscene key bindings
    // =========================================================================

    public void registerCutsceneBindings(JComponent comp) { // Install cutscene bindings: F9 (debug SIGNAL), SPACE (advance/minigame/jump precedence)
        InputMap im = comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW); // Window-scoped map: fires regardless of focus sub-component
        ActionMap am = comp.getActionMap();                                  // Matching action map

        // F9 — debug trigger: play SIGNAL locally (server-authoritative path is
        // still preferred in production; this is for P8.0 verification).
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0, false), "debugCutsceneSignal"); // Map F9-press to debug action
        am.put("debugCutsceneSignal", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (session == null) session = GameSession.getInstance(); // Lazy-init session
                // Ask the server to broadcast so both clients lock-step.
                // Fall back to local play if offline.
                String trigger = "CUT_TRIGGER|" + CutsceneID.SIGNAL.name();  // Build trigger string with the SIGNAL cutscene name
                if (session != null) session.sendToServer(trigger);            // Send to server so both clients begin the cutscene together
                if (!CutsceneRenderer.get().isPlaying()) {
                    CutsceneRenderer.get().play(CutsceneID.SIGNAL);            // Also play locally in case server path hasn't responded yet
                }
            }
        });

        // SPACE — cutscene advance (highest priority), then stun minigame
        // hit input (P8.8), then the normal jump fallback. The precedence
        // order matters: cutscenes freeze the world entirely and must swallow
        // SPACE first; during a stun opportunity SPACE is the minigame input
        // and must not also trigger a jump; outside those windows the
        // existing jump binding runs as before.
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "cutsceneAdvance"); // Override SPACE: this action runs before jump_pressed
        am.put("cutsceneAdvance", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                CutsceneRenderer cr = CutsceneRenderer.get(); // Get the singleton cutscene renderer
                if (cr.isPlaying()) {
                    if (cr.advance()) {                          // Advance to the next panel; returns true when the final panel was just shown
                        // Reached the final panel — ack the server.
                        CutsceneID id = cr.getActive();                          // Get the ID of the cutscene that just ended
                        if (session == null) session = GameSession.getInstance(); // Lazy-init session
                        if (session != null && id != null) {
                            session.sendToServer(Protocol.CUTSCENE_ACK + "|" + id.name()); // Send CUT_ACK so server can resume both clients
                        }
                    }
                    return; // Cutscene consumed SPACE; do not fall through to jump or minigame
                }
                // P8.8 — if a stun opportunity is active, consume SPACE here
                // so the minigame counts the hit and the jump binding does
                // NOT fire. The minigame itself reports back to the server
                // via Protocol.STUN_RESULT once a terminal result latches.
                if (stunMinigame != null && stunMinigame.isActive()) {
                    stunMinigame.onSpacePressed(); // Register the SPACE press as a hit attempt in the stun minigame
                    return;                        // Minigame consumed SPACE; do not fall through to jump
                }
                // Not in a cutscene or minigame — let the existing jump binding
                // take it by re-invoking the action map under its jump name.
                javax.swing.Action jumpAct = comp.getActionMap().get("jump_pressed"); // Look up the pre-registered jump action
                if (jumpAct != null) jumpAct.actionPerformed(e);                      // Delegate to jump_pressed as if SPACE was pressed normally
            }
        });
    }

    // =========================================================================
    // Remaining MouseListener stubs
    // =========================================================================

    public void mouseReleased(MouseEvent e) { // Clear Apprentice button flags so LightBall drag stops on release
        MouseApprentice.getInstance().setLeftPressed(false);  // Clear left-button flag so LightBall drag stops on button release
        MouseApprentice.getInstance().setRightPressed(false); // Clear right-button flag
    }

    public void mouseClicked(MouseEvent e) {} // No-op; all logic is in mousePressed()

    public void mouseEntered(MouseEvent e) {} // No-op; not used

    public void mouseExited(MouseEvent e)  {} // No-op; not used
}
