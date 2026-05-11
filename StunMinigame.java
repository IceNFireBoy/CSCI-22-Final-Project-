/**
 * Per-session timing minigame used to stun the Architect during the BOSS phase (P8.8).
 * While an opportunity window is open, a sinusoidal marker oscillates across a horizontal
 * track and the Wanderer presses {@code SPACE} to land the marker inside a central target
 * zone. Three consecutive successful hits within the same window count as a
 * <em>stun success</em> — the server then suppresses boss-attack dispatches for
 * {@value #STUN_DURATION_MS} milliseconds.
 *
 * <p>Difficulty scales inversely with the Wanderer's faithful meter:
 * <ul>
 *   <li>{@code faithful = 0} → oscillation at {@value #FREQ_BASE_HZ} Hz (hardest; fast marker)</li>
 *   <li>{@code faithful = 5} → oscillation at {@value #FREQ_MIN_HZ} Hz (easiest; slow marker)</li>
 * </ul>
 * The target zone width is a fixed fraction of the track, so a lower frequency gives
 * the Wanderer a wider effective hit window in real time.</p>
 *
 * <p>Architecture role: A POJO with no direct network or rendering side-effects.
 * {@link GameCanvas} calls {@link #render(java.awt.Graphics2D, int, int)} each paint;
 * {@link GameStarter}'s loop calls {@link #tick(long)} once per client tick; and
 * {@link InputRouter} calls {@link #onSpacePressed()} when the Wanderer presses
 * {@code SPACE} while the window is active. The pending result is consumed by
 * {@link GameStarter} via {@link #consumePendingResult()}, which sends a
 * {@link Protocol#STUN_RESULT} message to the server.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-19
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */


// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 4b "Key Bindings"   - the stun mini-game registers keystroke
//                               listeners during its active window via
//                               the InputMap / ActionMap pattern from 4b.
// Module 3a "Graphics"       - the gauge and target indicator are drawn
//                               with Graphics2D fill / draw primitives.
// Module 1a "Modifiers"      - private mutable state fields; public
//                               accessors expose result and isActive.
// Module 4a "Threads"        - timing uses System.currentTimeMillis()
//                               compared in the game-loop thread.
// =========================================================================
import java.awt.AlphaComposite; // Used in render() to make the panel backdrop translucent (alpha=0.88)
import java.awt.BasicStroke;    // Used in render() to set stroke widths for the track border and target zone outline
import java.awt.Color;          // AWT colour for all panel elements: backdrop, track, zone, marker, pips, bar
import java.awt.Composite;      // Interface type; saved and restored around the alpha-composite backdrop draw
import java.awt.Font;           // Font for the panel title, difficulty hint, and key-hint labels
import java.awt.FontMetrics;    // Measures string widths for right-aligned text placement
import java.awt.Graphics2D;     // 2D rendering context passed by GameCanvas for the overlay draw
import java.awt.Stroke;         // Interface type; saved before and restored after setting stroke widths

public class StunMinigame { // POJO: no extends; interacts with GameStarter, GameCanvas, InputRouter

    // -------------------------------------------------------------------------
    // Tunables
    // -------------------------------------------------------------------------

    /**
     * Duration of each stun opportunity window in milliseconds. The window starts when
     * the server broadcasts {@link Protocol#STUN_OPPORTUNITY} and auto-closes after this
     * interval regardless of player input.
     */
    public static final long OPPORTUNITY_DURATION_MS = 3_500L; // 3.5 s window; short enough to feel urgent, long enough for three presses

    /**
     * Duration of the Architect-stunned attack suppression in milliseconds. When the
     * Wanderer lands three consecutive hits, the server suppresses {@link Protocol#BOSS_ATK}
     * dispatches for this long. Exposed as public so HUD code can read the expected value.
     */
    public static final long STUN_DURATION_MS = 5_000L; // 5 s stun; meaningful respite from attacks but not overpowered

    /**
     * Number of consecutive in-zone hits required within one window to count as a
     * stun success. Missing once resets the consecutive counter, so the Wanderer must
     * be accurate rather than just fast.
     */
    public static final int HITS_REQUIRED = 3; // Three consecutive hits; resets on miss to prevent button-mashing strategies

    /**
     * Oscillation frequency in Hz at the hardest difficulty (faithful = 0).
     * At this frequency the marker crosses the target zone rapidly.
     */
    public static final float FREQ_BASE_HZ = 2.0f; // 2 Hz = two full oscillations per second; challenging but not impossible

    /**
     * Oscillation frequency in Hz at the easiest difficulty (faithful = FAITHFUL_MAX).
     * At this frequency the marker moves slowly and is easier to time.
     */
    public static final float FREQ_MIN_HZ = 0.8f; // 0.8 Hz ≈ one oscillation per 1.25 s; generous timing for high-faithful Wanderers

    /**
     * Half-width of the central target zone as a fraction of the total track length
     * {@code [-1, 1]}. A candidate hit at position {@code p} succeeds when
     * {@code |p| <= TARGET_HALF_WIDTH}.
     */
    public static final float TARGET_HALF_WIDTH = 0.18f; // 18% of half-track on each side = 36% total; enough to reward timing, not luck

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Whether an opportunity window is currently open and accepting input. */
    private boolean active = false; // True while the window is open; read by GameCanvas for render() and by InputRouter

    /** Wall-clock millisecond timestamp when the current window was opened via {@link #open(int)}. */
    private long startMs = 0L; // Recorded by open(); used by markerPosition() to compute elapsed sine phase

    /** Wall-clock millisecond timestamp at which the current window automatically expires. */
    private long endMs = 0L; // startMs + OPPORTUNITY_DURATION_MS; compared against nowMs in tick() and render()

    /** Number of consecutive in-zone hits landed in the current open window. */
    private int hits = 0; // Incremented on a zone hit; reset to 0 on a miss; compared against HITS_REQUIRED

    /**
     * The Wanderer's faithful score used to compute the oscillation frequency for the
     * current window. Captured at {@link #open(int)} time so mid-window faithful changes
     * do not shift the difficulty.
     */
    private int faithful = 0; // Frozen at window-open time; used to compute frequencyHz and for the HUD hint display

    /**
     * Oscillation frequency in Hz for the current window, computed by linearly
     * interpolating between {@link #FREQ_BASE_HZ} and {@link #FREQ_MIN_HZ} based on the
     * Wanderer's faithful score.
     */
    private float frequencyHz = FREQ_BASE_HZ; // Computed in open(); drives markerPosition(); lower = easier

    /**
     * Latched terminal result for the current window:
     * {@code 1} = success (three consecutive hits), {@code 0} = failure (time expired or
     * too few hits), {@code -1} = result not yet determined (window still open or no
     * window has run). Read and cleared atomically by {@link #consumePendingResult()}.
     */
    private int pendingResult = -1; // Sentinel -1 means no result pending; set to 0 or 1 when the window resolves

    /**
     * Timestamp of the most recent successful zone hit. Used by render() to flash the
     * marker gold for 180 ms after each successful press to give positive feedback.
     */
    private long lastHitMs = 0L; // Set in onSpacePressed() on each zone hit; compared in render() for the flash duration

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Opens a new stun opportunity window, resetting all in-window state. Safe to call
     * while another window is still active — the previous window is discarded without
     * emitting a result (the server should avoid overlapping dispatches in practice, but
     * the client must remain consistent either way).
     *
     * <p>Architecture role: Called by {@link GameStarter} immediately upon receiving a
     * {@link Protocol#STUN_OPPORTUNITY} broadcast from the server. The faithfulScore
     * parameter is typically read from the local {@link Player#getFaithful()} value,
     * which the server also has via {@link NetworkProtocol.PlayerStatePacket#faithful}.</p>
     *
     * @param faithfulScore the current {@link Player#getFaithful()} value in [0, FAITHFUL_MAX];
     *                      clamped internally; drives the oscillation frequency for this window
     */
    public synchronized void open(int faithfulScore) {
        this.active  = true;                                         // Arm the window: tick() and onSpacePressed() are now live
        this.startMs = System.currentTimeMillis();                   // Record the window start time for markerPosition() phase calculation
        this.endMs   = startMs + OPPORTUNITY_DURATION_MS;            // Precompute the expiry timestamp for efficient per-tick comparison
        this.hits    = 0;                                            // Reset consecutive hit counter for this fresh window
        this.faithful = Math.max(0, Math.min(Player.FAITHFUL_MAX, faithfulScore)); // Clamp to valid range to prevent frequency computation errors
        // Lerp frequency: faithful=0 → FREQ_BASE_HZ (hardest); faithful=MAX → FREQ_MIN_HZ (easiest)
        float t = (Player.FAITHFUL_MAX == 0) ? 0f : (float) this.faithful / Player.FAITHFUL_MAX; // Normalise faithful to [0,1]; guard against division by zero
        this.frequencyHz = FREQ_BASE_HZ + (FREQ_MIN_HZ - FREQ_BASE_HZ) * t; // Linear interpolation: moves from 2.0 Hz toward 0.8 Hz as faithful increases
        this.pendingResult = -1;                                     // Clear any leftover result from a previous window
        this.lastHitMs = 0L;                                         // Clear the hit-flash timestamp so the marker starts un-flashed
    }

    /**
     * Immediately closes the current window without latching a pending result. Used by
     * {@link GameStarter} when the server sends a race-cancellation (e.g. the opportunity
     * was superseded by a new one before it resolved). The normal time-expiry path in
     * {@link #tick(long)} latches a proper result when the window expires naturally.
     *
     * <p>Architecture role: Provides a clean abort path so the game loop does not
     * accidentally fire a stale {@link Protocol#STUN_RESULT} for a cancelled window.</p>
     */
    public synchronized void forceClose() {
        this.active        = false; // Close the window: tick() and onSpacePressed() become no-ops
        this.hits          = 0;     // Reset hit counter so state is clean for the next window
        this.pendingResult = -1;    // Clear any in-progress result; no STUN_RES will be sent for this window
    }

    /**
     * Per-tick update called from the client game loop. Closes the window when
     * {@link #OPPORTUNITY_DURATION_MS} has elapsed and latches a terminal result based
     * on whether the Wanderer reached the required hit count.
     *
     * <p>Architecture role: Called by {@link GameStarter} once per loop iteration,
     * passing the cached {@link System#currentTimeMillis()} to avoid repeated JNI calls.
     * The latched {@code pendingResult} is later consumed by {@link #consumePendingResult()}
     * which sends the appropriate {@link Protocol#STUN_RESULT} message.</p>
     *
     * @param nowMs cached wall-clock milliseconds; passed in to avoid repeated system calls
     */
    public synchronized void tick(long nowMs) {
        if (!active) return;           // Window is not open: nothing to update
        if (nowMs >= endMs) {          // Window has expired: resolve the result
            if (pendingResult == -1) { // Only latch if not already resolved (e.g. by onSpacePressed reaching HITS_REQUIRED)
                pendingResult = (hits >= HITS_REQUIRED) ? 1 : 0; // 1 = stun success; 0 = failure; based on consecutive hit count at expiry
            }
            active = false;            // Close the window: no further input or timer processing
        }
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    /**
     * Called when the Wanderer presses {@code SPACE} while a window is open. Checks
     * whether the oscillating marker is currently inside the central target zone. A
     * zone hit increments the consecutive counter; a miss resets it to zero. On reaching
     * {@link #HITS_REQUIRED} consecutive hits, the window closes with a success result.
     *
     * <p>Architecture role: Called by {@link InputRouter} when it detects a SPACE
     * key-down event and {@link #isActive()} is {@code true}. The return value allows
     * InputRouter to suppress the normal SPACE jump binding so the stun press does not
     * also make the Wanderer jump.</p>
     *
     * @return {@code true} if this press was consumed by the minigame (caller should
     *         suppress the jump binding); {@code false} if the window is not active
     */
    public synchronized boolean onSpacePressed() {
        if (!active) return false;              // Window not open: this press is not for the minigame
        long now = System.currentTimeMillis();   // Read current time to check if we're still within the window
        if (now >= endMs) return false;          // Window just expired: tick() will clean up shortly; don't process the input
        float p = markerPosition(now);           // Compute marker position in [-1, 1] at the exact moment of the keypress
        if (Math.abs(p) <= TARGET_HALF_WIDTH) {  // Marker is inside the target zone: register a hit
            hits++;                              // Increment the consecutive hit counter
            lastHitMs = now;                     // Record the hit timestamp for the 180 ms flash feedback in render()
            if (hits >= HITS_REQUIRED) {         // Three consecutive hits reached: stun succeeds
                pendingResult = 1;               // Latch success result so consumePendingResult() fires a STUN_RES|1
                active = false;                  // Close the window immediately; success is final
            }
        } else {                                 // Marker is outside the target zone: miss
            hits = 0;                            // Reset consecutive counter — the spec requires "3 consecutive hits", not 3 total
        }
        return true; // This keypress was consumed by the minigame; caller should suppress jump
    }

    // -------------------------------------------------------------------------
    // Output
    // -------------------------------------------------------------------------

    /**
     * Returns whether a stun opportunity window is currently open. Consulted by
     * {@link GameCanvas} to decide whether to call {@link #render}, and by
     * {@link InputRouter} to decide whether to route SPACE to this minigame.
     *
     * @return {@code true} if the window is open and accepting input; {@code false} otherwise
     */
    public synchronized boolean isActive() { return active; } // Read by GameCanvas (render gate) and InputRouter (SPACE routing)

    /**
     * Returns the current in-window consecutive hit count in [0, {@link #HITS_REQUIRED}].
     * Used by the HUD pip row in {@link #render} to show progress toward the stun.
     *
     * @return number of consecutive successful hits so far in this window
     */
    public synchronized int getHits() { return hits; } // Read by render() to fill the correct number of green pips

    /**
     * Returns the faithful score that the current window was opened with. Used by
     * {@link #render} to display the difficulty hint to the Wanderer.
     *
     * @return the faithful score in [0, Player.FAITHFUL_MAX]
     */
    public synchronized int getFaithful() { return faithful; } // Read by render() for the difficulty hint display

    /**
     * Returns the latched window result and atomically clears it so the caller fires
     * exactly one {@link Protocol#STUN_RESULT} message per window resolution.
     *
     * <p>Architecture role: Called by {@link GameStarter} each tick after
     * {@link #tick(long)}. A non-negative return value triggers the STUN_RES network
     * message. The clear ensures no duplicate messages are sent even if tick() is
     * called multiple times before the result is processed.</p>
     *
     * @return {@code 1} on stun success, {@code 0} on failure, {@code -1} if no result
     *         is pending yet
     */
    public synchronized int consumePendingResult() {
        int r = pendingResult;  // Read the latched result before clearing
        pendingResult = -1;     // Clear atomically so the next call returns -1 unless a new window resolves
        return r;               // Return the result to the caller for STUN_RES dispatch
    }

    /**
     * Computes the current marker position along the track in the range {@code [-1, 1]}
     * using a sinusoidal oscillation at {@link #frequencyHz}. Uses the wall-clock time
     * so the oscillation speed is independent of the frame rate.
     *
     * <p>Formula: {@code sin(2π × frequencyHz × elapsedSeconds)}</p>
     *
     * @param nowMs cached wall-clock milliseconds; must be >= {@link #startMs}
     * @return marker position in [-1, 1]; {@code 0} = track centre, {@code ±1} = edges
     */
    private float markerPosition(long nowMs) {
        float t = (nowMs - startMs) / 1000f;          // Convert elapsed ms to seconds since window open
        double phase = 2.0 * Math.PI * frequencyHz * t; // Full sine wave phase in radians: 2π × f × t
        return (float) Math.sin(phase);                // Return the [-1, 1] sine value as the marker position
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    /**
     * Renders the stun minigame overlay as a horizontal track panel near the bottom of
     * the canvas. Draws the panel backdrop, title, oscillating marker track, central
     * target zone, consecutive-hit pip row, and a countdown progress bar. No-op when
     * the minigame is not active or the window has just expired.
     *
     * <p>Visual layout:
     * <ul>
     *   <li>Translucent near-black backdrop (alpha=0.88) with a blue border.</li>
     *   <li>Title: "STUN THE ARCHITECT — SPACE ×3" in blue-white.</li>
     *   <li>Track: a dark-filled rounded bar containing the green target zone in the
     *       centre and a gold marker that slides sinusoidally.</li>
     *   <li>Pip row: one small oval per required hit; filled green when the hit has
     *       landed, dark when pending.</li>
     *   <li>Countdown bar: full-width blue bar that depletes as the window time runs out.</li>
     *   <li>Difficulty hint: {@code "FAITH n/MAX — f Hz"} at the bottom-left; key hint
     *       {@code "SPACE"} at the bottom-right.</li>
     * </ul></p>
     *
     * <p>Architecture role: Called by {@link GameCanvas#renderBoss} (or the general
     * overlay pass) each frame while {@link #isActive()} returns {@code true}. The
     * method is {@code synchronized} on the same monitor as the input and tick methods
     * to prevent torn reads of the marker position or hit count.</p>
     *
     * @param g       the destination {@link Graphics2D} context; must not be {@code null}
     * @param canvasW width of the canvas in screen pixels; used to centre the panel
     * @param canvasH height of the canvas in screen pixels; used to anchor the panel bottom
     */
    public synchronized void render(Graphics2D g, int canvasW, int canvasH) {
        if (!active) return;                   // Window is not open: skip the overlay entirely
        long now = System.currentTimeMillis(); // Sample wall-clock time once for all render calculations this frame
        if (now >= endMs) return;             // Window just expired but tick() hasn't processed it yet: skip to avoid a flash

        // ---- Panel geometry: centred horizontally, anchored near the bottom ----
        int panelW = 520;                          // Panel width in screen pixels; wide enough for track + pip row
        int panelH = 96;                           // Panel height in screen pixels; compact enough not to obscure the arena
        int ox = (canvasW - panelW) / 2;           // Horizontal offset to centre the panel on the canvas
        int oy = canvasH - panelH - 96;            // Vertical offset: 96 px above the bottom, leaving room for HUD notifications

        // ---- Translucent backdrop ----
        Composite prev = g.getComposite();                                           // Save caller's composite to restore after the semi-transparent draw
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.88f)); // 88% opacity: visible but lets a hint of the arena show through
        g.setColor(new Color(0x05, 0x05, 0x10));                                    // Near-black with a blue tinge; matches the dark boss-arena atmosphere
        g.fillRoundRect(ox, oy, panelW, panelH, 12, 12);                            // Draw the rounded backdrop rectangle
        g.setComposite(prev);                                                        // Restore caller's composite to prevent alpha bleed into subsequent draws

        // ---- Border and title ----
        g.setColor(new Color(0x66, 0x99, 0xFF));                      // Bright blue border; matches the stun/electric theme
        Stroke prevStroke = g.getStroke();                             // Save caller's stroke before setting the border thickness
        g.setStroke(new BasicStroke(2f));                              // 2 px border: visible but not overpowering
        g.drawRoundRect(ox, oy, panelW, panelH, 12, 12);              // Draw the panel border
        g.setStroke(prevStroke);                                       // Restore caller's stroke
        g.setFont(new Font("Monospaced", Font.BOLD, 13));             // Monospaced bold: consistent with the game's HUD font style
        g.setColor(new Color(0xCC, 0xDD, 0xFF));                      // Light blue-white; readable against the near-black backdrop
        g.drawString("STUN THE ARCHITECT — SPACE x" + HITS_REQUIRED, // Dynamic label; shows required hit count (3) for clarity
                     ox + 16, oy + 20);                               // Left-aligned inside the panel with 16 px inset

        // ---- Track geometry ----
        int trackX = ox + 24;           // Track left edge; 24 px from panel left to leave room for pip row alignment
        int trackY = oy + 42;           // Track vertical position; below the title label
        int trackW = panelW - 48;       // Track width: full panel width minus 48 px margins on each side
        int trackH = 14;                // Track height: thick enough to be legible, thin enough to feel precise

        // ---- Track background ----
        g.setColor(new Color(0x10, 0x10, 0x20));                     // Very dark navy: the track interior colour
        g.fillRoundRect(trackX, trackY, trackW, trackH, 6, 6);       // Draw the track body

        // ---- Target zone: central band of width 2 × TARGET_HALF_WIDTH in track-fraction space ----
        int zoneW     = Math.max(4, (int) (trackW * TARGET_HALF_WIDTH)); // Zone half-width in pixels; Math.max(4) ensures a minimum visible width
        int zoneX     = trackX + trackW / 2 - zoneW;                    // Zone left edge: centre minus half-width
        int zoneFullW = zoneW * 2;                                       // Total zone pixel width: symmetric around the track centre
        g.setColor(new Color(0x33, 0x88, 0x44));                        // Dark green fill: indicates the safe landing zone
        g.fillRoundRect(zoneX, trackY, zoneFullW, trackH, 6, 6);        // Draw the filled green zone band
        g.setColor(new Color(0x55, 0xDD, 0x88));                        // Bright green outline for the zone edge; pops against the track background
        g.setStroke(new BasicStroke(1.5f));                             // Slightly thicker than the track corners for visibility
        g.drawRoundRect(zoneX, trackY, zoneFullW, trackH, 6, 6);        // Draw the green zone border
        g.setStroke(prevStroke);                                         // Restore stroke after the zone outline

        // ---- Oscillating marker ----
        float p = markerPosition(now);                                                           // Compute marker position [-1, 1] at this render timestamp
        int markerX = trackX + (int) ((trackW / 2.0f) * (1.0f + p)) - 4;                       // Convert [-1,1] → pixel x; subtract 4 (half of 8 px marker width) to centre
        boolean flash = (now - lastHitMs) < 180L;                                               // True for 180 ms after a successful hit; triggers the flash colour
        g.setColor(flash ? new Color(0xFF, 0xEE, 0x88) : new Color(0xEE, 0xCC, 0x66));         // Flash colour: brighter yellow; normal colour: warm gold
        g.fillRoundRect(markerX, trackY - 3, 8, trackH + 6, 4, 4);                             // Draw the marker: 8 px wide, 6 px taller than the track for a protruding look
        g.setColor(new Color(0x33, 0x22, 0x00));                                                // Dark brown outline for the marker; creates a visible border against the gold
        g.drawRoundRect(markerX, trackY - 3, 8, trackH + 6, 4, 4);                             // Draw the marker outline

        // ---- Consecutive-hit pip row ----
        int pipSize  = 10;                           // Pip diameter in pixels
        int pipGap   = 4;                            // Horizontal gap between pips in pixels
        int pipRowW  = HITS_REQUIRED * pipSize + (HITS_REQUIRED - 1) * pipGap; // Total width of the pip row
        int pipRowX  = ox + panelW - pipRowW - 16;  // Right-align the pip row with 16 px inset from panel edge
        int pipRowY  = oy + 18;                      // Vertically aligned with the title row
        for (int i = 0; i < HITS_REQUIRED; i++) {    // Draw one pip per required hit
            int px = pipRowX + i * (pipSize + pipGap);          // Compute the left edge of pip i
            Color fill = (i < hits)                              // Pips below the current hit count are filled green; the rest are dark
                    ? new Color(0x66, 0xFF, 0xAA)               // Bright green: hit landed for this pip slot
                    : new Color(0x1A, 0x1A, 0x24);              // Dark navy: hit not yet landed
            g.setColor(fill);                                    // Set the pip fill colour
            g.fillOval(px, pipRowY, pipSize, pipSize);           // Draw the filled pip oval
            g.setColor(new Color(0x3A, 0x3A, 0x48));            // Muted border for all pips regardless of state
            g.drawOval(px, pipRowY, pipSize, pipSize);           // Draw the pip border
        }

        // ---- Countdown bar ----
        float remainingFrac = (endMs - now) / (float) OPPORTUNITY_DURATION_MS; // Fraction of window remaining: 1.0 when just opened, 0.0 when expired
        remainingFrac = Math.max(0f, Math.min(1f, remainingFrac));              // Clamp to [0,1] to guard against floating-point rounding at boundaries
        int cbX = ox + 16;                                                      // Countdown bar left edge with 16 px inset
        int cbY = oy + panelH - 18;                                             // Countdown bar y: 18 px from the panel bottom
        int cbW = panelW - 32;                                                  // Countdown bar width: panel width minus 32 px margins
        int cbH = 6;                                                             // Countdown bar height: thin 6 px strip
        g.setColor(new Color(0x1A, 0x1A, 0x24));                               // Dark track for the unfilled portion of the countdown
        g.fillRect(cbX, cbY, cbW, cbH);                                         // Draw the empty countdown track
        g.setColor(new Color(0x77, 0xAA, 0xFF));                               // Blue fill for the remaining time; drains from right to left as time passes
        g.fillRect(cbX, cbY, (int) (cbW * remainingFrac), cbH);                // Draw the filled portion proportional to remaining time
        g.setColor(new Color(0x33, 0x44, 0x66));                               // Subtle border for the countdown bar
        g.drawRect(cbX, cbY, cbW, cbH);                                         // Draw the countdown bar border

        // ---- Difficulty hint and key prompt ----
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));                            // Smaller font for the hint text; keeps it unobtrusive
        g.setColor(new Color(0x88, 0x99, 0xCC));                                      // Muted blue-grey; secondary label colour
        FontMetrics fm = g.getFontMetrics();                                           // Measure text for right-alignment of the key hint
        String hint = String.format("FAITH %d/%d  —  %.1f Hz",                        // Show faithful score, max, and current oscillation frequency
                faithful, Player.FAITHFUL_MAX, frequencyHz);                           // Dynamic values; changes each window based on the Wanderer's faithful progress
        g.drawString(hint, ox + 16, oy + panelH - 22);                               // Draw the hint left-aligned in the bottom margin
        String keyHint = "SPACE";                                                      // Remind the Wanderer which key to press
        int khX = ox + panelW - fm.stringWidth(keyHint) - 16;                        // Right-align the key hint with 16 px inset
        g.drawString(keyHint, khX, oy + panelH - 22);                                // Draw the right-aligned key hint
    }
}
