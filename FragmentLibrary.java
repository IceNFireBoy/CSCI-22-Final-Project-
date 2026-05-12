/**
 * Maintains the authoritative collection of {@link LoreFragment} instances
 * that the Wanderer has gathered during the current session and provides a full-screen
 * archive rendering method to display the lore log on screen. Uses a {@link java.util.Set}
 * of fragment ID strings alongside the list to ensure duplicate collection events from
 * network lag are silently ignored without requiring a full list scan.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */



// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 1d "Inner Classes"  - holds the AbilityUnlock enum reference and
//                               maintains a Set<String> of collected IDs.
// Module 1a "Modifiers"      - private static singleton instance accessed
//                               via getInstance(); public methods for
//                               queries.
// Module 3a "Graphics"       - the library overlay renders via Graphics2D
//                               primitives (fillRect for backdrop,
//                               drawString for entries, drawImage for
//                               fragment glyphs).
// =========================================================================
import java.awt.*;
import java.util.*;
import java.util.List;
public class FragmentLibrary {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final int CANVAS_W = 1024; // Width of the game canvas in pixels; used to centre the title and clip the list
    private static final int CANVAS_H = 768;  // Height of the game canvas in pixels; used for the clipping region bottom boundary

    private static final Color BG_COLOR       = new Color(0x08, 0x08, 0x0F); // Near-black deep-space background; keeps the lore log atmospheric
    private static final Color GOLD_COLOR     = new Color(0xD4, 0xAF, 0x37); // Warm gold for title, ID labels, and badge text; consistent with the game's gold palette
    private static final Color WHITE_COLOR    = Color.WHITE;                  // Pure white for collected fragment body text; maximum readability against the dark background
    private static final Color DIM_GRAY_COLOR = new Color(0x55, 0x55, 0x55); // Dim gray for "[FRAGMENT LOCKED]" text; visually subordinate to collected entries

    private static final Font TITLE_FONT     = new Font("Serif", Font.BOLD,   28); // Large bold serif for "FRAGMENT ARCHIVE" heading
    private static final Font ID_LABEL_FONT  = new Font("Serif", Font.BOLD,   16); // Medium bold serif for "[ A1-INTRO ]" labels; stands out from body text
    private static final Font BODY_TEXT_FONT = new Font("Serif", Font.ITALIC, 14); // Small italic serif for lore body text; matches the literary tone
    private static final Font LOCKED_FONT    = new Font("Serif", Font.ITALIC, 14); // Same size as body text; italic dim for "[FRAGMENT LOCKED]" placeholder
    private static final Font BADGE_FONT     = new Font("Serif", Font.BOLD,   11); // Compact bold for combat-unlock badge text; fits beside the ID label

    private static final int TITLE_Y      = 50;  // Y coordinate of the title baseline from the top of the canvas
    private static final int LIST_START_Y = 90;  // Y coordinate where the scrollable fragment list begins
    private static final int LEFT_MARGIN  = 60;  // Horizontal padding from the left edge for all list content
    private static final int RIGHT_MARGIN = 60;  // Horizontal padding from the right edge; determines the text wrap width
    private static final int ENTRY_SPACING = 12; // Vertical gap (px) added between the body text and the separator line, and between the separator and the next entry

    /**
     * The canonical ordered list of all known fragment IDs that can appear in the
     * archive. Fragments not in this list are still tracked if collected, but this
     * list drives the archive display order.
     */
    private static final String[] ALL_KNOWN_IDS = {
        "A1-INTRO",       // Act 1 — narrative-only (NONE unlock); first fragment the Wanderer encounters
        "A2-WALL_CLING",  // Act 2 — grants WALL_CLING ability unlock
        "A3-SHADOW_DASH", // Act 3 — grants SHADOW_DASH ability unlock (requires WALL_CLING prerequisite)
        "A3-IRON"         // Act 3 — grants IRON ability unlock
    };

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * An ordered list of every {@link LoreFragment} collected by the Wanderer in this
     * session, preserved in collection order.
     */
    private List<LoreFragment> collected;  // ArrayList: supports index access and iteration; order = collection order

    /**
     * A set of fragment ID strings corresponding to every entry in {@link #collected}.
     * Used for O(1) duplicate checking.
     */
    private Set<String> collectedIDs;  // HashSet: O(1) contains() check; prevents network-lag duplicate collections

    /**
     * Maps collected fragment IDs to their LoreFragment instances for quick lookup
     * during archive rendering.
     */
    private Map<String, LoreFragment> collectedByID; // LinkedHashMap: preserves insertion order; render() uses this for quick ID→fragment lookup

    /** The vertical scroll offset for the archive view, in pixels. */
    private int scrollOffset; // Pixels scrolled down from the top of the list; clamped to [0, maxScroll] in render()

    /** Reference to the Wanderer; set via {@link #setPlayer(Player)}. */
    private Player player; // Injected by GameStarter; used in collect() to call player.unlockAbility() on combat fragments

    /**
     * Reference to the cutscene renderer. Kept as an explicit field (rather than
     * only calling {@link CutsceneRenderer#get()} inline) so tests and headless
     * setups can substitute a different renderer via
     * {@link #setCutsceneRenderer(CutsceneRenderer)}.
     */
    private CutsceneRenderer cutsceneRenderer = CutsceneRenderer.get(); // Default to the singleton; overridable for testing

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs an empty {@code FragmentLibrary} with no fragments collected.
     *
     * <p>Architecture role: Called once by {@link GameStarter} at session start.
     * Dependencies (player, audioManager) are injected separately via setters because
     * they may not be available at construction time.</p>
     */
    public FragmentLibrary() {
        this.collected     = new ArrayList<>();        // Empty ordered list; grows as the Wanderer finds fragments
        this.collectedIDs  = new HashSet<>();          // Empty ID set; grows in parallel with collected list
        this.collectedByID = new LinkedHashMap<>();    // Empty ID→fragment map; enables O(1) render-time lookup
        this.scrollOffset  = 0;                        // Archive starts scrolled to the top
    }

    // -------------------------------------------------------------------------
    // Dependency injection
    // -------------------------------------------------------------------------

    /**
     * Sets the {@link Player} reference used to apply ability unlocks on fragment
     * collection.
     *
     * <p>Architecture role: Called by {@link GameStarter} after the Player is
     * instantiated. Without this reference, ability unlocks collected by
     * {@link #collect(LoreFragment)} are silently skipped.</p>
     *
     * @param player the Wanderer instance; must not be {@code null}
     */
    public void setPlayer(Player player) {
        this.player = player; // Store Wanderer reference; called by collect() to invoke player.unlockAbility()
    }

    /**
     * Sets the cutscene-renderer reference used to trigger ability-unlock cutscenes.
     * Call sites normally rely on the default {@link CutsceneRenderer#get()}
     * singleton assigned in the field initialiser; this setter exists for tests
     * or alternate renderer implementations.
     *
     * <p>Architecture role: Allows unit tests to inject a mock renderer so
     * cutscene playback can be verified without a real Swing rendering surface.</p>
     *
     * @param cutsceneRenderer the cutscene renderer instance; must not be {@code null}
     */
    public void setCutsceneRenderer(CutsceneRenderer cutsceneRenderer) {
        this.cutsceneRenderer = cutsceneRenderer; // Override the default singleton; used by tests to capture cutscene triggers
    }

    /**
     * Dispatches a cutscene by ID to the renderer. Every collecting client calls
     * into here when the relayed {@link NetworkProtocol.FragmentCollectedPacket}
     * unlocks a new ability, so both sides begin playback together — the server
     * already relays that packet in lock-step via its single-threaded game loop.
     *
     * <p>Architecture role: Private helper called at the end of
     * {@link #collect(LoreFragment)} when the fragment's unlock has an associated
     * cutscene. The null guard handles both "no cutscene for this unlock" and
     * "renderer not injected yet" cases without requiring the caller to check.</p>
     *
     * @param id the cutscene to play; {@code null} is ignored silently
     */
    private void triggerCutscene(CutsceneID id) {
        if (id == null || cutsceneRenderer == null) return; // Null guards: no cutscene for this unlock, or renderer not yet injected
        cutsceneRenderer.play(id);                          // Initiate cutscene playback; locks Wanderer input until both clients ACK
    }

    /**
     * Maps a combat-ability unlock to the cutscene that should play when it
     * is first collected, or {@code null} for unlocks with no cutscene.
     *
     * <p>Architecture role: Centralises the unlock→cutscene mapping so
     * {@link #collect(LoreFragment)} remains clean and the mapping is easy to
     * update when new cutscenes are added for future ability unlocks.</p>
     *
     * @param unlock the ability unlock constant from the collected fragment
     * @return the {@link CutsceneID} to play, or {@code null} if no cutscene applies
     */
    private static CutsceneID cutsceneForUnlock(LoreFragment.AbilityUnlock unlock) {
        if (unlock == null) return null; // Null guard: treat missing unlock as NONE
        switch (unlock) {
            case DODGE:       return CutsceneID.FIRST_LIGHT;          // DODGE unlock → First Light cutscene (first act transition moment)
            case WALL_CLING:  return CutsceneID.EDGE_OF_LIGHT;        // WALL_CLING → Edge of Light cutscene (scaling the first vertical surface)
            case SHADOW_DASH: return CutsceneID.LAST_COOPERATIVE_ACT; // SHADOW_DASH → Last Cooperative Act cutscene (penultimate story beat)
            default:          return null;                             // P9.1' — VEIL/ECHO/TETHER/SHADOW_STEP also fall through here; no dedicated cutscene yet (NONE, MELEE, PROJECTILE, EMBER, IRON, RADIANT_COLLAPSE share this branch)
        }
    }

    // -------------------------------------------------------------------------
    // Collection
    // -------------------------------------------------------------------------

    /**
     * Adds the given {@link LoreFragment} to the library if it has not already been
     * collected. Handles the Shadow Dash prerequisite gate, applies ability unlocks
     * to the Wanderer, plays the appropriate sound effect, and triggers cutscenes
     * for combat ability unlocks.
     *
     * <p>Shadow Dash gate: SHADOW_DASH can only be collected after both the DODGE
     * and WALL_CLING fragments are already in the library. If the prerequisites are
     * missing the fragment is marked gated and the method returns early.</p>
     *
     * <p>Architecture role: Called by {@link CollisionDetector} when the Wanderer
     * overlaps an uncollected, ungated fragment. Also called by
     * {@link GameSession} when a {@link NetworkProtocol.FragmentCollectedPacket}
     * arrives from the server to keep both clients in sync.</p>
     *
     * @param f the fragment to collect; must not be {@code null}
     */
    public void collect(LoreFragment f) {
        // Skip duplicates
        if (collectedIDs.contains(f.getFragmentID())) {
            return; // O(1) duplicate check: network lag can deliver the same collection event twice; silently ignore
        }

        LoreFragment.AbilityUnlock unlock = f.getUnlock(); // Cache the unlock constant once; used multiple times below

        // Shadow Dash gate: require both DODGE and WALL_CLING to be collected first
        if (unlock == LoreFragment.AbilityUnlock.SHADOW_DASH) {  // Only SHADOW_DASH has prerequisites; check before adding
            boolean hasDodgeFragment     = false;                 // Will be set true if a collected fragment grants DODGE
            boolean hasWallClingFragment = false;                 // Will be set true if a collected fragment grants WALL_CLING
            for (LoreFragment cf : collected) {                   // Scan all already-collected fragments for the prerequisites
                if (cf.getUnlock() == LoreFragment.AbilityUnlock.DODGE)      hasDodgeFragment     = true; // Found DODGE prerequisite
                if (cf.getUnlock() == LoreFragment.AbilityUnlock.WALL_CLING) hasWallClingFragment = true; // Found WALL_CLING prerequisite
            }
            if (!hasDodgeFragment || !hasWallClingFragment) {  // One or both prerequisites are missing
                f.setGated(true);                               // Mark the fragment gated so CollisionDetector skips it until prerequisites are met
                return;                                         // Abort collection; Wanderer cannot pick up this fragment yet
            }
        }

        // Add to collection
        collected.add(f);                              // Append to ordered list; collection order is preserved for future reference
        collectedIDs.add(f.getFragmentID());           // Add ID to duplicate-check set; subsequent collect() calls are now filtered
        collectedByID.put(f.getFragmentID(), f);       // Map ID → fragment for O(1) archive render lookup
        f.collect();                                   // Mark fragment entity as collected and deactivate it so it stops rendering

        // Apply ability unlock to the Wanderer
        if (unlock != LoreFragment.AbilityUnlock.NONE && player != null) { // Only unlock if this fragment grants an ability and the player is injected
            player.unlockAbility(unlock);                                    // Activate the ability in the Player's ability set (melee, dodge, etc.)
        }

        // Trigger the ability-unlock cutscene if this fragment unlocked a combat ability.
        triggerCutscene(cutsceneForUnlock(unlock)); // Look up the cutscene for this unlock; null unlocks and non-cutscene unlocks are no-ops
        System.out.println("[FragmentLibrary] Fragment collected: " + f.getFragmentID()); // Debug log: confirms collection and ID for server reconciliation
    }

    // -------------------------------------------------------------------------
    // Scroll control
    // -------------------------------------------------------------------------

    /**
     * Scrolls the archive view up by the given number of pixels. The offset is
     * clamped so it does not go below zero.
     *
     * <p>Architecture role: Called by the keyboard/mouse handler in
     * {@link GameCanvas} when the up-arrow or scroll-wheel up event fires while the
     * archive overlay is open.</p>
     *
     * @param amount the number of pixels to scroll up
     */
    public void scrollUp(int amount) {
        scrollOffset = Math.max(0, scrollOffset - amount); // Subtract amount but never go below 0 (cannot scroll above the top)
    }

    /**
     * Scrolls the archive view down by the given number of pixels. The offset is
     * clamped to the maximum scrollable extent in {@link #render(Graphics2D)}.
     *
     * <p>Architecture role: Called by the keyboard/mouse handler when the down-arrow
     * or scroll-wheel down event fires. The upper clamp is applied inside
     * {@link #render(Graphics2D)} once {@code totalHeight} is known.</p>
     *
     * @param amount the number of pixels to scroll down
     */
    public void scrollDown(int amount) {
        scrollOffset += amount; // Increase offset; render() will clamp it to maxScroll before drawing
    }

    /**
     * Returns the current scroll offset, allowing input handlers to read the state.
     *
     * <p>Architecture role: Used by {@link GameCanvas} or UI overlays that want to
     * display a numeric position indicator or synchronise scroll across panels.</p>
     *
     * @return the current vertical scroll offset in pixels
     */
    public int getScrollOffset() {
        return scrollOffset; // Current pixels scrolled from the top of the archive list
    }

    /**
     * Sets the scroll offset directly.
     *
     * <p>Architecture role: Used to restore a saved scroll position (e.g., after
     * re-opening the archive) or to jump to a specific entry programmatically.</p>
     *
     * @param offset the new scroll offset in pixels; will be clamped to [0, ∞) here
     *               and to [0, maxScroll] during rendering
     */
    public void setScrollOffset(int offset) {
        this.scrollOffset = Math.max(0, offset); // Clamp at zero; rendering clamps the upper bound
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    /**
     * Renders the full-screen Fragment Archive overlay. Draws a dark background,
     * a gold title, and a scrollable list of all known fragment IDs. Collected
     * fragments display their full body text in white serif italic; uncollected
     * ones show "[FRAGMENT LOCKED]" in dim gray. Combat-unlock fragments show a
     * gold badge indicating the unlocked ability.
     *
     * <p>Rendering pipeline per frame:
     * <ol>
     *   <li>Enable anti-aliasing for smooth text and badge corners.</li>
     *   <li>Fill the dark background and draw the gold title + decorative line.</li>
     *   <li>Set a clipping region for the scrollable list area.</li>
     *   <li>Compute total content height; clamp scrollOffset.</li>
     *   <li>For each fragment ID in {@link #ALL_KNOWN_IDS}: draw the ID label,
     *       optional combat badge, and body text or "[FRAGMENT LOCKED]".</li>
     *   <li>Remove the clip; draw scroll-indicator arrows if scrollable.</li>
     * </ol></p>
     *
     * <p>Architecture role: Called each frame by {@link GameCanvas} while the
     * archive overlay is open (triggered by the F key via {@link InputRouter}).</p>
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    public void render(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);      // Enable shape anti-aliasing for smooth rounded badge corners
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON); // Enable text anti-aliasing for readable serif fonts at small sizes

        // Dark background
        g.setColor(BG_COLOR);                                     // Near-black background covers the game world beneath the overlay
        g.fillRect(0, 0, CANVAS_W, CANVAS_H);                    // Fill the entire canvas so no game elements show through

        // Title: "FRAGMENT ARCHIVE" in gold serif
        g.setFont(TITLE_FONT);                                    // Bold 28pt serif for the heading
        g.setColor(GOLD_COLOR);                                   // Gold colour matches the lore fragment aesthetic
        FontMetrics titleFm = g.getFontMetrics();                 // Measure title width for centring
        String title = "FRAGMENT ARCHIVE";
        int titleWidth = titleFm.stringWidth(title);              // Pixel width of the title string
        g.drawString(title, (CANVAS_W - titleWidth) / 2, TITLE_Y); // Centre the title horizontally at TITLE_Y baseline

        // Decorative line under title
        int lineY      = TITLE_Y + 8;                             // 8 px below the title baseline
        int lineHalfW  = titleWidth / 2 + 30;                    // Extend 30 px beyond each side of the title for a wider underline
        g.drawLine(CANVAS_W / 2 - lineHalfW, lineY, CANVAS_W / 2 + lineHalfW, lineY); // Draw the gold horizontal rule under the heading

        // Set up clipping region for the scrollable list area
        int clipTop    = LIST_START_Y;                            // Clip starts just below the title line
        int clipBottom = CANVAS_H - 30;                          // Clip ends 30 px from the bottom to leave room for scroll arrows
        g.setClip(0, clipTop, CANVAS_W, clipBottom - clipTop);  // Any content outside this rectangle is not drawn

        // Compute total content height to clamp scroll
        int totalHeight = computeTotalContentHeight(g);                 // Sum of all entry heights including ID, body, spacing, separators
        int maxScroll   = Math.max(0, totalHeight - (clipBottom - clipTop)); // Maximum pixels scrollable before the last entry goes out of view
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll; // Clamp: prevent scrolling past the last entry
        }

        // Draw each fragment entry
        int curY          = LIST_START_Y - scrollOffset;          // Starting Y for the first entry; scrollOffset shifts content upward
        int textAreaWidth = CANVAS_W - LEFT_MARGIN - RIGHT_MARGIN; // Available width for text content

        for (String fragID : ALL_KNOWN_IDS) {                    // Iterate in canonical order defined by ALL_KNOWN_IDS
            LoreFragment frag      = collectedByID.get(fragID);  // O(1) lookup: null if this fragment has not been collected
            boolean      isCollected = (frag != null);           // True = draw body text; false = draw locked placeholder

            // Fragment ID label in gold
            g.setFont(ID_LABEL_FONT);                            // Bold 16pt serif for the ID label
            FontMetrics idFm = g.getFontMetrics();               // Measure ID label for badge positioning
            g.setColor(GOLD_COLOR);                              // Gold colour for the ID label
            String idLabel = "[ " + fragID + " ]";              // Format: "[ A1-INTRO ]", "[ A2-WALL_CLING ]", etc.
            g.drawString(idLabel, LEFT_MARGIN, curY + idFm.getAscent()); // Draw at left margin with ascent offset so baseline is at curY

            // Combat unlock badge beside the ID
            if (isCollected && frag.getUnlock() != LoreFragment.AbilityUnlock.NONE) { // Only show badge for ability-unlocking fragments
                g.setFont(BADGE_FONT);                                                 // Compact 11pt bold for the badge text
                FontMetrics badgeFm = g.getFontMetrics();
                String badge  = "UNLOCKED: " + frag.getUnlock().name();              // e.g. "UNLOCKED: DODGE"
                int    badgeX = LEFT_MARGIN + idFm.stringWidth(idLabel) + 12;        // Position badge to the right of the ID label with 12 px gap
                int    badgeY = curY + idFm.getAscent();                              // Align badge baseline with the ID label baseline

                // Badge background
                int badgeW = badgeFm.stringWidth(badge) + 10; // Add 10 px horizontal padding inside the badge pill
                int badgeH = badgeFm.getHeight() + 2;         // Add 2 px vertical padding inside the badge pill
                g.setColor(new Color(0xD4, 0xAF, 0x37, 0x33));                     // Semi-transparent gold fill (alpha=0x33 ≈ 20%): tinted background
                g.fillRoundRect(badgeX - 5, badgeY - badgeFm.getAscent() - 1,
                        badgeW, badgeH, 4, 4);                                      // Draw filled rounded rectangle for badge background
                g.setColor(GOLD_COLOR);                                              // Solid gold for the badge border and text
                g.drawRoundRect(badgeX - 5, badgeY - badgeFm.getAscent() - 1,
                        badgeW, badgeH, 4, 4);                                      // Draw badge border with same rounded corners
                g.drawString(badge, badgeX, badgeY);                                // Draw badge text inside the rounded rectangle
            }

            curY += idFm.getHeight() + 4; // Advance Y past the ID label row with 4 px gap before body text

            // Body text or locked placeholder
            if (isCollected) {
                g.setFont(BODY_TEXT_FONT);                                  // Italic 14pt serif for lore body text
                g.setColor(WHITE_COLOR);                                    // White text for maximum readability on the dark background
                curY = drawWrappedText(g, frag.getBodyText(), LEFT_MARGIN + 16,
                        curY, textAreaWidth - 16);                          // Word-wrap body text with 16 px indent; returns Y after last line
            } else {
                g.setFont(LOCKED_FONT);                                     // Same size as body text but dim to de-emphasise
                g.setColor(DIM_GRAY_COLOR);                                 // Dim gray: clearly different from collected entries
                g.drawString("[FRAGMENT LOCKED]", LEFT_MARGIN + 16,
                        curY + g.getFontMetrics().getAscent());             // Draw locked placeholder at the same indentation as body text
                curY += g.getFontMetrics().getHeight();                     // Advance Y by one line height for the single-line placeholder
            }

            curY += ENTRY_SPACING; // Add spacing below the body text/locked text before the separator line

            // Separator line
            g.setColor(new Color(0x22, 0x22, 0x2A));                       // Very dark blue-gray separator; visible but subtle on the dark background
            g.drawLine(LEFT_MARGIN, curY, CANVAS_W - RIGHT_MARGIN, curY);  // Horizontal rule spanning the full content width
            curY += ENTRY_SPACING;                                          // Add spacing below the separator before the next entry's ID label
        }

        // Remove clipping
        g.setClip(null); // Restore full-canvas clipping so subsequent renders (e.g. pause overlay) are not constrained

        // Scroll indicator arrows
        if (scrollOffset > 0) {                                    // There is content above the visible area: show up arrow
            g.setColor(GOLD_COLOR);
            g.setFont(BADGE_FONT);
            g.drawString("▲  Scroll Up", CANVAS_W / 2 - 35, clipTop + 14); // Unicode up-triangle + label centred near the top edge
        }
        if (scrollOffset < maxScroll) {                            // There is content below the visible area: show down arrow
            g.setColor(GOLD_COLOR);
            g.setFont(BADGE_FONT);
            g.drawString("▼  Scroll Down", CANVAS_W / 2 - 40, clipBottom - 4); // Unicode down-triangle + label centred near the bottom edge
        }
    }

    // -------------------------------------------------------------------------
    // Rendering helpers
    // -------------------------------------------------------------------------

    /**
     * Draws text wrapped to the given pixel width, returning the y position
     * immediately after the last drawn line.
     *
     * <p>Architecture role: Used by {@link #render(Graphics2D)} to flow long lore
     * body text within the archive's content column. The wrapping algorithm is a
     * greedy word-wrap: words are added to the current line until the line would
     * exceed {@code maxWidth}, then a new line is started.</p>
     *
     * @param g        the graphics context with the desired font already set
     * @param text     the text to wrap and draw; may contain spaces but not explicit
     *                 newlines (those are collapsed by the split)
     * @param x        the x position for the left edge of all text lines
     * @param y        the y position of the top of the first line (not the baseline)
     * @param maxWidth the maximum pixel width of a single line before wrapping
     * @return the y position immediately below the last drawn line
     */
    private int drawWrappedText(Graphics2D g, String text, int x, int y, int maxWidth) {
        FontMetrics fm     = g.getFontMetrics();  // Read metrics from the currently set font in the graphics context
        int         lineHeight = fm.getHeight();  // Height of one line including leading; used to advance curY between lines

        String[]      words = text.split("\\s+");       // Split on any whitespace; handles spaces, tabs, and collapsed newlines
        StringBuilder line  = new StringBuilder();      // Current partial line being assembled
        int           curY  = y;                        // Current Y position; starts at the top of the first line

        for (String word : words) {                     // Iterate over every word in the body text
            String candidate = line.length() == 0      // Build the candidate string with the next word appended
                    ? word                              // First word on an empty line: no leading space
                    : line + " " + word;               // Subsequent words: prepend a space separator
            if (fm.stringWidth(candidate) > maxWidth && line.length() > 0) { // Candidate would overflow and we have something to flush
                g.drawString(line.toString(), x, curY + fm.getAscent());     // Draw the completed line at the current baseline
                curY += lineHeight;                                            // Advance to the next line position
                line = new StringBuilder(word);                               // Start the new line with the overflowing word
            } else {
                line = new StringBuilder(candidate);   // Word fits: accept the candidate and continue building
            }
        }
        if (line.length() > 0) {                                             // Flush the last partial line that didn't trigger a wrap
            g.drawString(line.toString(), x, curY + fm.getAscent());        // Draw the remaining text
            curY += lineHeight;                                               // Advance past the last line
        }

        return curY; // Return Y position after the last line so the caller can continue placing content below
    }

    /**
     * Computes the total content height of the archive list in pixels, used to
     * determine the maximum scroll offset.
     *
     * <p>Architecture role: Called once at the start of each {@link #render(Graphics2D)}
     * call to set the scroll clamp. Temporarily changes the font in the graphics
     * context to match each entry type; the caller is responsible for setting the
     * desired font again after this method returns.</p>
     *
     * @param g the graphics context; font is changed internally for measurement
     * @return total pixel height of all archive entries combined
     */
    private int computeTotalContentHeight(Graphics2D g) {
        int height = 0; // Accumulator for the total content height in pixels

        g.setFont(ID_LABEL_FONT);                                      // Set font to ID label for consistent idLineHeight measurement
        int idLineHeight  = g.getFontMetrics().getHeight() + 4;        // Height of one ID label row + 4 px gap before body text
        int textAreaWidth = CANVAS_W - LEFT_MARGIN - RIGHT_MARGIN - 16; // Available width for body text (same as in render(), with 16 px indent)

        for (String fragID : ALL_KNOWN_IDS) {                          // Iterate all known IDs to count every entry regardless of collection status
            LoreFragment frag = collectedByID.get(fragID);             // Null = uncollected; affects body text vs locked placeholder height

            // ID label line
            height += idLineHeight; // Every entry has one ID label row

            if (frag != null) {
                // Body text (estimate wrapped lines)
                g.setFont(BODY_TEXT_FONT);                             // Switch to body text font for accurate wrapping metrics
                FontMetrics fm = g.getFontMetrics();
                height += estimateWrappedHeight(fm, frag.getBodyText(), textAreaWidth); // Add estimated word-wrapped body text height
            } else {
                g.setFont(LOCKED_FONT);                                // Switch to locked font for accurate single-line height
                height += g.getFontMetrics().getHeight();              // Single-line placeholder height for locked entries
            }

            // Entry spacing + separator + spacing
            height += ENTRY_SPACING * 2; // ENTRY_SPACING above the separator line + ENTRY_SPACING below the separator line
        }

        return height; // Total height in pixels; caller uses this to compute maxScroll = max(0, height - visibleHeight)
    }

    /**
     * Estimates the pixel height of text when word-wrapped to the given width.
     *
     * <p>Architecture role: Used by {@link #computeTotalContentHeight(Graphics2D)}
     * to estimate body-text heights without actually drawing. Uses the same greedy
     * word-wrap algorithm as {@link #drawWrappedText} so the estimate matches the
     * rendered output.</p>
     *
     * @param fm       the font metrics for the body text font
     * @param text     the text to measure; {@code null} or empty returns one line height
     * @param maxWidth the maximum pixel width before wrapping
     * @return estimated total height in pixels of the wrapped text
     */
    private int estimateWrappedHeight(FontMetrics fm, String text, int maxWidth) {
        if (text == null || text.isEmpty()) return fm.getHeight(); // Edge case: empty text still occupies one line height

        String[]      words = text.split("\\s+");  // Same split as drawWrappedText; ensures estimate matches rendered line count
        int           lines = 1;                   // At least one line even if all words fit without wrapping
        StringBuilder line  = new StringBuilder(); // Current line being assembled for width measurement

        for (String word : words) {               // Greedy word-wrap loop; mirrors the logic in drawWrappedText exactly
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(candidate) > maxWidth && line.length() > 0) {
                lines++;                          // Overflow: this word starts a new line
                line = new StringBuilder(word);   // Start new line with the overflowing word
            } else {
                line = new StringBuilder(candidate); // Word fits: extend current line
            }
        }

        return lines * fm.getHeight(); // Multiply line count by single-line height to get total pixel height
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the ordered list of all collected lore fragments.
     *
     * <p>Architecture role: Used by {@link GameSession} and
     * {@link NetworkProtocol.ServerStatePacket} serialisation to persist collected
     * fragment state across level transitions and reconnects.</p>
     *
     * @return an unmodifiable view of the collected fragments; never {@code null}
     */
    public List<LoreFragment> getCollected() {
        return Collections.unmodifiableList(collected); // Unmodifiable wrapper: prevents external code from bypassing collect() checks
    }

    /**
     * Returns the set of fragment IDs that have already been collected.
     *
     * <p>Architecture role: Used by {@link CollisionDetector} to quickly check
     * whether a fragment is already in the library before calling
     * {@link #collect(LoreFragment)}, and by the server to broadcast which fragments
     * are collected in a {@link NetworkProtocol.ServerStatePacket}.</p>
     *
     * @return an unmodifiable view of the collected ID set; never {@code null}
     */
    public Set<String> getCollectedIDs() {
        return Collections.unmodifiableSet(collectedIDs); // Unmodifiable wrapper: ID set should only grow through collect()
    }
}
