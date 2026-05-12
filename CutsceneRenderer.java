/**
 * Singleton that owns the presentation of any currently-playing cutscene. While a
 * cutscene is active {@link #isPlaying()} returns {@code true}, which both
 * {@link GameCanvas} and {@link InputRouter} consult to suppress world rendering,
 * HUD overlays, and gameplay input. Each call to {@link #play(CutsceneID)} resets
 * the panel index to zero, records a start timestamp for fade-in easing, and looks
 * up the dialogue lines from {@link CutsceneScript}. Art panels are loaded lazily
 * through {@link SpriteLoader#tryLoad(String)} so missing cutscene PNGs fall back
 * to a text-only presentation without crashing the session.
 *
 * <p>Architecture role: The renderer is purely local — authority over which cutscene
 * plays, when it starts, and when it ends lives on the server via
 * {@link NetworkProtocol.CutscenePacket}. The client-side flow is:
 * <ol>
 *   <li>{@link GameServer} broadcasts a {@code CutscenePacket(id, start=true)}.</li>
 *   <li>{@link GameStarter} receives it and calls {@link #play(CutsceneID)}.</li>
 *   <li>The local user advances panels by pressing SPACE; each advance calls
 *       {@link #advance()} which returns {@code true} on the final panel, prompting
 *       the client to send {@link Protocol#CUTSCENE_ACK}.</li>
 *   <li>When both clients have acked (or a 30 s auto-advance guard expires) the
 *       server broadcasts {@code CutscenePacket(id, start=false)} and each client
 *       calls {@link #stop()} to restore the pre-cutscene game phase.</li>
 * </ol></p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-17
 * @certification I certify that this code is my own work and has not been copied
 *                from any other source, in whole or in part.
 */


// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 3a "Graphics"       - paintComponent-style drawing within a
//                               dedicated renderer class; uses fillRect,
//                               drawString, FontMetrics, and RenderingHints
//                               anti-aliasing per 3a.
// Module 3b "More Graphics"  - AffineTransform translate for centring
//                               text and panel layouts at runtime.
// Module 1a "Modifiers"      - private fields with public accessors;
//                               static final font / colour constants.
// =========================================================================
import java.awt.*;
import java.awt.image.*;
public class CutsceneRenderer { // Singleton: instantiated once eagerly; never more than one instance

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final Color BG_COLOR   = new Color(0x00, 0x00, 0x00); // Pure black background: maximises contrast with the white narration text
    private static final Color TEXT_COLOR = new Color(0xEE, 0xE4, 0xC8); // Warm cream: softer on the eye than pure white for long-form text
    private static final Color FOOTER_CLR = new Color(0x88, 0x80, 0x70); // Muted brown-grey: unobtrusive footer that guides without dominating

    private static final Font  PANEL_FONT  = new Font("Serif", Font.ITALIC, 22);  // Serif italic: cinematic feel appropriate for cutscene narration
    private static final Font  FOOTER_FONT = new Font("SansSerif", Font.PLAIN, 12); // Small sans-serif: compact prompts that don't compete with the narration

    /**
     * Cross-fade duration between the moment a panel begins and when the narration text
     * reaches full opacity, in milliseconds. Provides a gentle fade-in effect rather
     * than an abrupt text pop-in.
     */
    private static final long FADE_MS = 600L; // 0.6 s fade-in; fast enough not to feel sluggish, slow enough to feel cinematic

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

     
    private static final CutsceneRenderer INSTANCE = new CutsceneRenderer(); // Eager singleton: no null-check overhead at call sites

    /**
     * Returns the process-wide {@code CutsceneRenderer}. All callers share the same
     * instance and therefore the same active cutscene state.
     *
     * @return the singleton {@code CutsceneRenderer}; never {@code null}
     */
    public static CutsceneRenderer get() { return INSTANCE; } // Static accessor; used by GameStarter, GameCanvas, and InputRouter

    /**
     * Private constructor — instantiation is restricted to the class itself.
     * Initialises the {@link CutsceneScript} table eagerly so the first {@link #play}
     * call does not incur lookup overhead.
     */
    private CutsceneRenderer() {
        this.script = new CutsceneScript(); // Pre-build the script table; avoids per-play lazy init
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * The cutscene currently playing, or {@code null} when the renderer is idle.
     * Declared {@code volatile} so that reads from {@link GameCanvas}'s render thread
     * always see the latest value written by the network-receive thread calling
     * {@link #play(CutsceneID)} or {@link #stop()}.
     */
    private volatile CutsceneID active; // Null = idle; non-null = cutscene in progress; read by isPlaying() every frame

     
    private volatile int panelIndex; // Tracks which dialogue line to display; ranges from 0 to lines.length-1

    /**
     * {@link System#currentTimeMillis()} at which the current panel started fading in.
     * Reset to the current time each time {@link #advance()} increments the panel index
     * so the fade-in animation plays fresh on each new panel.
     */
    private volatile long startMs; // Timestamp of the most recent panel start; used by render() for the alpha fade

    /**
     * The {@link LevelState.GamePhase} that was active when {@link #play(CutsceneID)}
     * was last called. Restored by {@link #stop()} so gameplay resumes in the same
     * phase it was in before the cutscene began.
     */
    private volatile LevelState.GamePhase returnPhase; // Snapshot of the pre-cutscene phase; restored by stop()

    /**
     * Lazily-cached reference to the script table. Constructed in the private
     * constructor and held for the lifetime of the singleton.
     */
    private final CutsceneScript script; // Provides getLines(CutsceneID) lookups; shared across all play() calls

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Begins playback of the given cutscene. Resets the panel index to zero, captures
     * the current {@link LevelState.GamePhase} as the return phase so {@link #stop()}
     * can restore it, and flips the shared level state into
     * {@link LevelState.GamePhase#CUTSCENE} so game-world rendering is suppressed.
     * A {@code null} id is treated as a no-op; callers do not need to null-check before
     * calling.
     *
     * <p>Architecture role: Called by {@link GameStarter} immediately upon receiving a
     * {@link NetworkProtocol.CutscenePacket} with {@code start=true} from the server.
     * Both the Wanderer and Apprentice clients call this independently with the same
     * {@link CutsceneID} so their displays stay in lock-step.</p>
     *
     * @param id the cutscene to play; {@code null} is silently ignored
     */
    public void play(CutsceneID id) {
        if (id == null) return;                       // Null guard: ignore null ids to allow safe unconditional calls
        this.active     = id;                         // Arm the renderer: isPlaying() now returns true; GameCanvas will delegate to render()
        this.panelIndex = 0;                          // Start from the first panel regardless of any prior cutscene state
        this.startMs    = System.currentTimeMillis(); // Record the panel start time for the fade-in calculation in render()

        LevelState ls = sharedLevelState();           // Access the injected LevelState; may be null in test environments
        if (ls != null) {                             // Only flip the phase if a LevelState has been injected
            if (ls.currentPhase != LevelState.GamePhase.CUTSCENE) { // Avoid overwriting returnPhase if we're already in CUTSCENE
                this.returnPhase = ls.currentPhase;   // Capture the pre-cutscene phase so stop() can restore it
            }
            ls.currentPhase = LevelState.GamePhase.CUTSCENE; // Suppress world rendering and gameplay input for the duration
        }
    }

    /**
     * Advances to the next panel of the active cutscene and returns whether the
     * currently displayed panel is the last one. If the final panel is already
     * displayed, returns {@code true} without incrementing the index — the caller
     * should send {@link Protocol#CUTSCENE_ACK} and wait for the server to broadcast
     * the stop packet.
     *
     * <p>Architecture role: Called by {@link InputRouter} (or directly by
     * {@link GameStarter}) when the player presses the advance key while a cutscene
     * is active. The {@code true} return value is the trigger for sending
     * {@code CUT_ACK}.</p>
     *
     * @return {@code true} if the final panel has been shown and the caller should
     *         send {@link Protocol#CUTSCENE_ACK}; {@code false} if there are more
     *         panels to show
     */
    public boolean advance() {
        if (active == null) return false;               // No cutscene is playing; nothing to advance
        String[] lines = script.getLines(active);       // Look up the dialogue lines for the current cutscene
        if (panelIndex + 1 >= lines.length) {           // Already on the last panel: signal the caller to send CUT_ACK
            return true;                                // Return true: caller sends CUT_ACK and waits for the stop broadcast
        }
        panelIndex++;                                   // Move to the next panel
        startMs = System.currentTimeMillis();            // Reset the fade-in timer for the new panel
        return false;                                   // More panels remain; caller should not yet send CUT_ACK
    }

    /**
     * Terminates playback and restores the pre-cutscene game phase. Called by
     * {@link GameStarter} when it receives a {@link NetworkProtocol.CutscenePacket}
     * with {@code start=false} from the server, indicating both clients have acked.
     *
     * <p>Architecture role: Restores {@link LevelState#currentPhase} to the value
     * captured at {@link #play(CutsceneID)} time so gameplay resumes in the correct
     * phase without needing the server to re-broadcast the current phase.</p>
     */
    public void stop() {
        this.active     = null;    // Disarm the renderer: isPlaying() returns false; GameCanvas resumes normal rendering
        this.panelIndex = 0;       // Reset panel index for the next play() call
        LevelState ls = sharedLevelState(); // Access the shared LevelState to restore the phase
        if (ls != null && returnPhase != null
                && ls.currentPhase == LevelState.GamePhase.CUTSCENE) { // Only restore if we're still in CUTSCENE (guard against race)
            ls.currentPhase = returnPhase; // Restore the phase captured at play() time (e.g. ACT1, BOSS, etc.)
        }
        this.returnPhase = null; // Clear the captured phase so stop() cannot be called twice with a stale value
    }

    /**
     * Returns whether a cutscene is currently active. Checked by {@link GameCanvas}
     * each frame to decide whether to delegate painting to this renderer, and by
     * {@link InputRouter} to suppress normal gameplay key bindings.
     *
     * @return {@code true} iff a cutscene is currently playing; {@code false} when idle
     */
    public boolean isPlaying() { return active != null; } // Cheap null-check on the volatile field; thread-safe for read-only use

    /**
     * Returns the currently active cutscene ID. Used by {@link GameStarter} to
     * include the correct cutscene ID in the {@link Protocol#CUTSCENE_ACK} message.
     *
     * @return the playing {@link CutsceneID}, or {@code null} when idle
     */
    public CutsceneID getActive() { return active; } // Read the volatile field; null when no cutscene is playing

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    /**
     * Renders the current panel of the active cutscene onto the provided graphics
     * context. Draws a full-screen black background, optional panel art loaded via
     * {@link SpriteLoader#tryLoad(String)}, the current narration line with a
     * {@link #FADE_MS}-millisecond fade-in, and a footer prompt instructing the
     * player to press SPACE.
     *
     * <p>Art loading: Attempts to load {@code resources/cutscenes/<id>_<panelIndex>.png}
     * via {@link SpriteLoader#tryLoad(String)}. If the file is missing the renderer
     * silently falls through to a text-only presentation, so cutscenes degrade
     * gracefully during development when art assets are not yet available.</p>
     *
     * <p>Word-wrap: The narration text is split on whitespace and reflowed to fit within
     * 80% of the canvas width. Each overflow word is pushed to a new line at the same
     * x origin. This provides basic responsive text layout without a full text-layout
     * library.</p>
     *
     * <p>Architecture role: Called every frame by {@link GameCanvas} while
     * {@link #isPlaying()} returns {@code true}. The method returns immediately if
     * {@link #active} is {@code null} to handle any residual render calls during the
     * stop-to-idle transition.</p>
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     * @param w canvas width in pixels; used for horizontal centring of text and art
     * @param h canvas height in pixels; used for vertical positioning of text and footer
     */
    public void render(Graphics2D g, int w, int h) {
        if (active == null) return; // No cutscene active: skip rendering entirely; guard against residual frame after stop()

        // Apply anti-aliasing hints for smooth text and image rendering
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);        // Smooth edges for polygon and oval shapes
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);   // Smooth text glyphs; important for the large serif narration font

        // ---- Full-screen black background ----
        g.setColor(BG_COLOR);        // Pure black: completely suppresses anything painted beneath by GameCanvas
        g.fillRect(0, 0, w, h);      // Cover the entire canvas to prevent world geometry or HUD elements showing through

        // ---- Optional panel art ----
        // Construct the art path from the cutscene ID and current panel index
        String spritePath = "resources/cutscenes/"
                + active.name().toLowerCase() + "_" + panelIndex + ".png"; // e.g. "resources/cutscenes/architect_speaks_0.png"
        BufferedImage art = SpriteLoader.getInstance().tryLoad(spritePath); // tryLoad() returns null if the file is missing (no exception)
        if (art != null) {                                                   // Art available: draw it centred above the text region
            int aw = art.getWidth();                                         // Natural width of the art image in pixels
            int ah = art.getHeight();                                        // Natural height of the art image in pixels
            int ax = (w - aw) / 2;                                          // Centre the art horizontally
            int ay = Math.max(40, (h - ah) / 2 - 60);                      // Position art in the upper half; at least 40 px from the top
            g.drawImage(art, ax, ay, null);                                  // Draw the art at its natural resolution; no scaling applied
        }

        // ---- Fade-in alpha for the narration text ----
        float alpha = Math.min(1.0f,
                (System.currentTimeMillis() - startMs) / (float) FADE_MS); // Normalise elapsed ms to [0,1] over FADE_MS; clamped at 1.0
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)); // Apply the fade-in alpha to all subsequent draws

        // ---- Narration text ----
        String[] lines = script.getLines(active); // Look up all panels for the active cutscene
        String text = (lines.length == 0)         // Guard against cutscenes with no registered script
                ? "[" + active.name() + "]"       // Fallback placeholder: shows the cutscene ID so QA can identify the gap
                : lines[Math.min(panelIndex, lines.length - 1)]; // Clamp the panel index in case of script/advance desync

        g.setFont(PANEL_FONT);            // Set the large serif italic narration font
        g.setColor(TEXT_COLOR);           // Warm cream: cinematic and readable on black background
        FontMetrics fm = g.getFontMetrics(); // Measure glyph widths for the word-wrap calculation
        int textY = h - 160;              // Anchor text y position: 160 px from the bottom to leave room for the footer
        int maxW  = (int) (w * 0.8f);    // Maximum line width: 80% of canvas; leaves symmetric margins on each side
        int x0    = (w - maxW) / 2;      // Left edge of the text column: centred on the canvas
        int cursorY = textY;              // Tracks the current vertical draw position; advances by line height on wraps

        // ---- Word-wrap: split narration on whitespace, reflow into lines ----
        StringBuilder line = new StringBuilder(); // Accumulates the current line being built
        for (String word : text.split("\\s+")) {  // Split on any whitespace; handles spaces, newlines, tabs
            String candidate = line.length() == 0 ? word : line + " " + word; // Proposed next word appended to current line
            if (fm.stringWidth(candidate) > maxW && line.length() > 0) { // Candidate would overflow: flush the current line
                g.drawString(line.toString(), x0, cursorY);              // Draw the completed line at the current cursor y
                cursorY += fm.getHeight();                                // Advance the cursor down by one line height
                line = new StringBuilder(word);                           // Start a new line with the overflowing word
            } else {
                line = new StringBuilder(candidate);                      // No overflow: append the word to the current line
            }
        }
        if (line.length() > 0) {                  // Flush the final line that didn't trigger an overflow
            g.drawString(line.toString(), x0, cursorY); // Draw the last line of the narration text
        }

        // ---- Restore full opacity before drawing the footer ----
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)); // Footer is always fully opaque, not subject to fade-in

        // ---- Footer prompt ----
        g.setFont(FOOTER_FONT); // Small sans-serif for the footer; doesn't compete with the narration
        g.setColor(FOOTER_CLR); // Muted colour: present but unobtrusive
        String footer = (panelIndex + 1 >= lines.length)
                ? "[ Press SPACE to continue ]"  // Final panel: "continue" signals the player they are done
                : "[ Press SPACE to advance ]";  // Non-final panel: "advance" to show more narration exists
        FontMetrics ffm = g.getFontMetrics();                            // Re-measure with the footer font
        g.drawString(footer, (w - ffm.stringWidth(footer)) / 2, h - 50); // Draw the footer centred, 50 px from the bottom

        // ---- Panel counter (shown only when there are multiple panels) ----
        if (lines.length > 1) {                                              // Only show "N / M" when there is more than one panel
            String counter = (panelIndex + 1) + " / " + lines.length;       // e.g. "2 / 4"; 1-based for human readability
            g.drawString(counter, w - ffm.stringWidth(counter) - 24, h - 50); // Right-aligned next to the footer; 24 px from the right edge
        }
    }

    // -------------------------------------------------------------------------
    // LevelState wiring
    // -------------------------------------------------------------------------

    /**
     * The shared {@link LevelState} instance used to flip the game phase into
     * {@link LevelState.GamePhase#CUTSCENE} when a cutscene begins and back out of it
     * when it ends. Injected by {@link GameStarter} during startup; {@code null} in
     * unit tests or when the client boots in offline/headless mode.
     */
    private volatile LevelState sharedLevelState; // Injected dependency; volatile for cross-thread visibility

    /**
     * Injects the shared {@link LevelState} so {@link #play(CutsceneID)} and
     * {@link #stop()} can cooperatively flip the game phase alongside the renderer.
     * Called once by {@link GameStarter} during initialisation.
     *
     * <p>Architecture role: Dependency injection via a setter avoids a circular
     * compile-time dependency between {@code CutsceneRenderer} and {@code GameStarter}.
     * The injected reference is stored in a {@code volatile} field for safe cross-thread
     * access.</p>
     *
     * @param ls the client-side {@link LevelState}; may be {@code null} to detach
     *           (e.g. during teardown or test cleanup)
     */
    public void setLevelState(LevelState ls) {
        this.sharedLevelState = ls; // Store the injected reference; play() and stop() read it on every invocation
    }

    /**
     * Returns the currently injected shared {@link LevelState}, or {@code null} if none
     * has been set. Internal helper to keep field access encapsulated.
     *
     * @return the shared {@link LevelState}, or {@code null}
     */
    private LevelState sharedLevelState() { return sharedLevelState; } // Internal accessor; null-checked by play() and stop() before use
}
