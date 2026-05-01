/**
 * Singleton that tracks the Apprentice player's mouse position on the game canvas
 * and manages the state of their light source (radius, on/off toggle, and forced-off
 * flag used when the battery runs out).
 *
 * <p>Architecture role: {@code MouseApprentice} is the Apprentice's analogue to
 * {@link KeyBindings.PlayerInputState} for the Wanderer. It acts as a shared data bus
 * between three subsystems: {@link InputRouter} writes mouse coordinates and button
 * states into it; the game loop inside {@link GameStarter} reads them each tick to
 * broadcast {@link Protocol#LIGHT_UPDATE} to the server; and {@link GameCanvas} reads
 * them for HUD rendering (battery bar, light radius indicator). Keeping this state
 * in a singleton means the three subsystems can read/write it without passing object
 * references through constructors.</p>
 *
 * <p>The forcedOff flag is separate from lightActive so that {@link GameSession}
 * can cut the light when the battery is empty without permanently toggling lightActive;
 * once the battery recharges (if that mechanic is enabled), lightActive can be checked
 * independently to decide whether to resume.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */
public class MouseApprentice { // Singleton mouse-state bus for the Apprentice role; no physics or rendering logic

    private static MouseApprentice instance; // The single instance; lazily created and stored here

    private int mouseX, mouseY;              // Current canvas-space (or world-space during BOSS) mouse coordinates; updated by InputRouter.mouseMoved/Dragged
    private boolean leftPressed, rightPressed; // Mouse button state flags; set by InputRouter on press/release events; read by the game loop for block placement / attack routing

    private int lightRadius = 120;           // Current light-source radius in pixels; default 120 px; clamped to [MIN_RADIUS, MAX_RADIUS] by adjustRadius
    private boolean lightActive = true;      // Whether the Apprentice's light toggle is currently ON; true by default at session start
    private boolean forcedOff = false;       // Set to true by GameSession when the battery reaches zero; prevents the toggle from re-lighting until battery recovers

    private static final int MIN_RADIUS = 20;  // Smallest allowed light radius (20 px) — ensures the Apprentice always has a tiny circle visible even at minimum scroll
    private static final int MAX_RADIUS = 180; // Largest allowed light radius (180 px) — matches the initial full-battery radius set in GameCanvas

    /**
     * Returns the singleton {@code MouseApprentice} instance, creating it on the first
     * call. Synchronised to prevent duplicate instances if the EDT and game loop thread
     * both call this simultaneously at startup.
     *
     * <p>Architecture role: Called by {@link InputRouter} (to update position/buttons),
     * {@link GameStarter} (to read light state for broadcasting), and {@link GameCanvas}
     * (to read light state for HUD rendering). The singleton ensures all three see the
     * same object and the same state at all times.</p>
     *
     * @return the shared {@code MouseApprentice}; never {@code null}
     */
    public static synchronized MouseApprentice getInstance() { // Thread-safe lazy initialiser — synchronised because InputRouter (EDT) and GameStarter (GameLoop thread) both call this
        if (instance == null) instance = new MouseApprentice(); // Create the single instance on the very first call
        return instance;                                         // Return the cached singleton on all subsequent calls
    }

    private MouseApprentice() {} // Private constructor — enforces singleton; external callers must use getInstance()

    /**
     * Updates the Apprentice's canvas-space mouse coordinates. Called by
     * {@link InputRouter#mouseMoved} and {@link InputRouter#mouseDragged} on every
     * mouse movement event.
     *
     * <p>During the BOSS phase {@link InputRouter} transforms the raw screen coordinates
     * through {@link Camera#screenToWorldX(int)} / {@link Camera#screenToWorldY(int)}
     * before calling this method, so {@code x} and {@code y} are world-space during
     * BOSS and screen-space during Act 1–3.</p>
     *
     * <p>Interaction: Coordinates stored here are read by {@link GameStarter}'s Apprentice
     * network send block to build {@link Protocol#LIGHT_UPDATE} / {@link Protocol#LIGHT_TARGET}
     * messages. They are also read by {@link InputRouter#mousePressed} for block-placement
     * snap calculations.</p>
     *
     * @param x the new x coordinate (canvas-space normally; world-space during BOSS)
     * @param y the new y coordinate (canvas-space normally; world-space during BOSS)
     */
    public void updatePosition(int x, int y) { // Store new mouse coordinates; called on every mouse-moved or mouse-dragged event from InputRouter
        mouseX = x; // Overwrite stored x — no averaging or smoothing; latest value wins
        mouseY = y; // Overwrite stored y — same rationale
    }

    /**
     * Sets whether the primary (left) mouse button is currently held down.
     * Used by the game loop to determine continuous block-placement or camera drag.
     *
     * @param b {@code true} when the left button is pressed; {@code false} when released
     */
    public void setLeftPressed(boolean b) { leftPressed = b; } // Update left-button held state; called by InputRouter.mousePressed/Released

    /**
     * Sets whether the secondary (right) mouse button is currently held down.
     * Used by the game loop for block removal or secondary attack targeting.
     *
     * @param b {@code true} when the right button is pressed; {@code false} when released
     */
    public void setRightPressed(boolean b) { rightPressed = b; } // Update right-button held state; called by InputRouter.mousePressed/Released

    /**
     * Adjusts the light radius by {@code delta} pixels, clamping the result to
     * [{@link #MIN_RADIUS}, {@link #MAX_RADIUS}]. Called by the mouse-wheel handler
     * ({@link InputRouter#mouseWheelMoved}) and by the Q-key binding in
     * {@link InputRouter#registerApprenticeKeyBindings}.
     *
     * <p>Interaction: The updated radius is read by {@link GameStarter}'s Apprentice
     * send block every tick, packed into the {@link Protocol#LIGHT_UPDATE} broadcast,
     * and applied on the Wanderer client to size the darkness mask rendered by
     * {@link LightRenderer#renderLightMask}.</p>
     *
     * @param delta the signed pixel change to apply; positive = larger radius; negative = smaller
     */
    public void adjustRadius(int delta) {                                             // Apply a relative change to lightRadius and clamp to valid range
        lightRadius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, lightRadius + delta)); // Clamp to [MIN_RADIUS, MAX_RADIUS]: Max(...) guards the lower bound, Min(...) guards the upper
    }

    /**
     * Directly toggles the light between on and off, applying a battery guard on
     * the on-path. If the light is currently on, turns it off. If it is off, turns
     * it on only when the battery level exceeds 5%, preventing the player from
     * switching the light on with an almost-empty battery and immediately triggering
     * another forced-off.
     *
     * <p>Architecture role: Called by the F-key binding in
     * {@link InputRouter#registerApprenticeKeyBindings} as the primary toggle action.
     * The battery guard ({@link GameSession#getLightBattery() > 5f}) prevents the
     * common edge case of toggling on at 1% battery and immediately having it forced
     * off again, which would feel like the key is broken.</p>
     *
     * <p>Interaction: {@link GameSession#getLightBattery()} is read here to gate the
     * on-toggle. {@link GameSession#updateBattery(boolean, int)} is called by the
     * game loop which checks {@link #isLightActive()} to determine whether to drain
     * the battery.</p>
     */
    public void toggleLight() {                                       // Flip lightActive; on-path is battery-gated to avoid toggling on at near-zero charge
        if (lightActive) {                                            // Light is currently on — toggling it off is always permitted
            lightActive = false;                                      // Turn the light off; battery drain stops on the next tick because GameStarter checks isLightActive()
        } else {                                                      // Light is currently off — only re-enable if there is enough battery to make it worthwhile
            if (GameSession.getInstance().getLightBattery() > 5f) {  // Guard: require > 5% battery before turning on to prevent immediate forced-off on low charge
                lightActive = true;                                   // Enable the toggle flag — light will be ON next time isLightActive() is checked
                forcedOff = false;                                    // Clear the forced-off flag so the battery-empty interlock does not immediately override the toggle
            }
        }
    }

    /**
     * Directly sets whether the light toggle is on or off. Used by
     * {@link InputRouter#registerApprenticeKeyBindings}'s F-key action for fine-grained
     * control (e.g. separately handling key-pressed and key-released events).
     *
     * <p>When turning on ({@code b == true}), also clears {@link #forcedOff} so the
     * battery-empty interlock is released, allowing the light to actually shine.
     * This lets the player recover the light after a forced-off event without having
     * to toggle twice.</p>
     *
     * @param b {@code true} to enable the light; {@code false} to disable it
     */
    public void setLightActive(boolean b) {         // Direct setter — used by InputRouter for separate press/release handling
        lightActive = b;                            // Apply the requested state directly without the battery guard used by toggleLight()
        if (b) forcedOff = false;                   // Turning on always clears forcedOff so the interlock doesn't override an explicit player action
    }

    /**
     * Sets the forced-off flag that overrides the {@link #lightActive} toggle.
     * Called by {@link GameSession} when the battery reaches zero to force the light
     * off regardless of the player's toggle state, preventing the light from burning
     * battery that no longer exists.
     *
     * <p>Architecture role: Decoupling forced-off from lightActive means the player's
     * toggle state is preserved; if battery recharges, lightActive still remembers
     * whether the player wanted the light on and the system can restore it cleanly.</p>
     *
     * @param b {@code true} to force the light off; {@code false} to release the
     *          battery-empty interlock
     */
    public void setLightForcedOff(boolean b) { forcedOff = b; } // Battery-empty interlock: when true, isLightActive() returns false regardless of lightActive flag

    /**
     * Returns whether the light is effectively active for rendering and battery-drain
     * purposes. {@code true} only when the player toggle is on AND the battery has
     * not been forced off.
     *
     * <p>This is the single authoritative check used by the game loop to decide
     * whether to drain the battery and by {@link GameCanvas} to decide whether to
     * render the light mask. Never test {@code lightActive} directly — always call
     * this method so the forced-off guard is automatically applied.</p>
     *
     * <p>Architecture role: Read by {@link GameStarter}'s Apprentice battery block
     * ({@code GameSession.updateBattery(lightOn, radius)}) and by {@link GameCanvas}
     * methods {@code isLightEffectivelyOn()} and {@code renderBatteryBar()}.</p>
     *
     * @return {@code true} when the light is on and not battery-forced off;
     *         {@code false} when either the toggle is off or the forced-off flag is set
     */
    public boolean isLightActive() { return lightActive && !forcedOff; } // Effective light state: both the player toggle AND the battery interlock must allow it

    /** Returns the current canvas-space (or world-space during BOSS) mouse x coordinate. */
    public int getX() { return mouseX; } // Read accessor for x; called by GameStarter Apprentice send block and by InputRouter block-placement logic

    /** Returns the current canvas-space (or world-space during BOSS) mouse y coordinate. */
    public int getY() { return mouseY; } // Read accessor for y; symmetric with getX()

    /** Returns whether the primary (left) mouse button is currently held down. */
    public boolean isLeftPressed() { return leftPressed; } // Read accessor for left-button state; used by game loop for continuous block-placement

    /** Returns whether the secondary (right) mouse button is currently held down. */
    public boolean isRightPressed() { return rightPressed; } // Read accessor for right-button state; used by game loop for block removal

    /** Returns the current light-source radius in pixels, clamped to [MIN_RADIUS, MAX_RADIUS]. */
    public int getLightRadius() { return lightRadius; } // Read accessor for radius; packed into LIGHT_UPDATE by GameStarter and read by LightRenderer for mask size

    /**
     * Returns the current mouse position as a {@link java.awt.Point} for use by APIs
     * that expect a Point rather than separate x/y integers.
     *
     * @return a new {@link java.awt.Point} with the current mouse coordinates; never {@code null}
     */
    public java.awt.Point getPosition() { return new java.awt.Point(mouseX, mouseY); } // Convenience wrapper; allocates a new Point each call — avoid calling 60× per tick
}
