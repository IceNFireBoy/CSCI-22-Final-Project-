/**
 * Maintains the authoritative collection of {@link entities.LoreFragment} instances
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


import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FragmentLibrary {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final int CANVAS_W = 1024;
    private static final int CANVAS_H = 768;

    private static final Color BG_COLOR       = new Color(0x08, 0x08, 0x0F);
    private static final Color GOLD_COLOR     = new Color(0xD4, 0xAF, 0x37);
    private static final Color WHITE_COLOR    = Color.WHITE;
    private static final Color DIM_GRAY_COLOR = new Color(0x55, 0x55, 0x55);

    private static final Font TITLE_FONT       = new Font("Serif", Font.BOLD, 28);
    private static final Font ID_LABEL_FONT    = new Font("Serif", Font.BOLD, 16);
    private static final Font BODY_TEXT_FONT   = new Font("Serif", Font.ITALIC, 14);
    private static final Font LOCKED_FONT      = new Font("Serif", Font.ITALIC, 14);
    private static final Font BADGE_FONT       = new Font("Serif", Font.BOLD, 11);

    private static final int TITLE_Y       = 50;
    private static final int LIST_START_Y  = 90;
    private static final int LEFT_MARGIN   = 60;
    private static final int RIGHT_MARGIN  = 60;
    private static final int ENTRY_SPACING = 12;

    private static final String SFX_FRAGMENT_COLLECT = "sfx_fragment_collect.wav";
    private static final String SFX_COMBAT_UNLOCK    = "sfx_combat_unlock.wav";

    /**
     * The canonical ordered list of all known fragment IDs that can appear in the
     * archive. Fragments not in this list are still tracked if collected, but this
     * list drives the archive display order.
     */
    private static final String[] ALL_KNOWN_IDS = {
        "01-A", "01-B",
        "02-A", "02-B",
        "03-A", "03-B",
        "04-A", "04-B",
        "05-A", "05-B",
        "06-A",
        "07-A", "07-B",
        "08-A", "08-B",
        "09-A", "09-B",
        "BOSS-A", "BOSS-B"
    };

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * An ordered list of every {@link LoreFragment} collected by the Wanderer in this
     * session, preserved in collection order.
     */
    private List<LoreFragment> collected;

    /**
     * A set of fragment ID strings corresponding to every entry in {@link #collected}.
     * Used for O(1) duplicate checking.
     */
    private Set<String> collectedIDs;

    /**
     * Maps collected fragment IDs to their LoreFragment instances for quick lookup
     * during archive rendering.
     */
    private Map<String, LoreFragment> collectedByID;

    /** The vertical scroll offset for the archive view, in pixels. */
    private int scrollOffset;

    /** Reference to the Wanderer; set via {@link #setPlayer(Player)}. */
    private Player player;

    /** Reference to the audio manager; set via {@link #setAudioManager(AudioManager)}. */
    private AudioManager audioManager;

    /** Reference to the cutscene renderer; set via {@link #setCutsceneRenderer(CutsceneRenderer)}. */
    private Object cutsceneRenderer;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs an empty {@code FragmentLibrary} with no fragments collected.
     */
    public FragmentLibrary() {
        this.collected     = new ArrayList<>();
        this.collectedIDs  = new HashSet<>();
        this.collectedByID = new LinkedHashMap<>();
        this.scrollOffset  = 0;
    }

    // -------------------------------------------------------------------------
    // Dependency injection
    // -------------------------------------------------------------------------

    /**
     * Sets the {@link Player} reference used to apply ability unlocks on fragment
     * collection.
     *
     * @param player the Wanderer instance; must not be {@code null}
     */
    public void setPlayer(Player player) {
        this.player = player;
    }

    /**
     * Sets the {@link AudioManager} reference used to play collection sound effects.
     *
     * @param audioManager the audio manager instance; must not be {@code null}
     */
    public void setAudioManager(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    /**
     * Sets the {@link CutsceneRenderer} reference used to trigger ability-unlock
     * cutscenes.
     *
     * @param cutsceneRenderer the cutscene renderer instance; must not be {@code null}
     */
    public void setCutsceneRenderer(Object cutsceneRenderer) {
        this.cutsceneRenderer = cutsceneRenderer;
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
     * @param f the fragment to collect; must not be {@code null}
     */
    public void collect(LoreFragment f) {
        // Skip duplicates
        if (collectedIDs.contains(f.getFragmentID())) {
            return;
        }

        LoreFragment.AbilityUnlock unlock = f.getUnlock();

        // Shadow Dash gate: require both DODGE and WALL_CLING to be collected first
        if (unlock == LoreFragment.AbilityUnlock.SHADOW_DASH) {
            boolean hasDodgeFragment = false;
            boolean hasWallClingFragment = false;
            for (LoreFragment cf : collected) {
                if (cf.getUnlock() == LoreFragment.AbilityUnlock.DODGE)     hasDodgeFragment = true;
                if (cf.getUnlock() == LoreFragment.AbilityUnlock.WALL_CLING) hasWallClingFragment = true;
            }
            if (!hasDodgeFragment || !hasWallClingFragment) {
                f.setGated(true);
                return;
            }
        }

        // Add to collection
        collected.add(f);
        collectedIDs.add(f.getFragmentID());
        collectedByID.put(f.getFragmentID(), f);
        f.collect();

        // Apply ability unlock to the Wanderer
        if (unlock != LoreFragment.AbilityUnlock.NONE && player != null) {
            player.unlockAbility(unlock);
        }

        // Play sound effect
        if (audioManager != null) {
            if (unlock != LoreFragment.AbilityUnlock.NONE) {
                audioManager.playSFX(SFX_COMBAT_UNLOCK);
            } else {
                audioManager.playSFX(SFX_FRAGMENT_COLLECT);
            }
        }

        // Trigger cutscenes for specific combat unlocks (no-op: CutsceneRenderer removed)
        System.out.println("[FragmentLibrary] Fragment collected: " + f.getFragmentID());
    }

    // -------------------------------------------------------------------------
    // Scroll control
    // -------------------------------------------------------------------------

    /**
     * Scrolls the archive view up by the given number of pixels. The offset is
     * clamped so it does not go below zero.
     *
     * @param amount the number of pixels to scroll up
     */
    public void scrollUp(int amount) {
        scrollOffset = Math.max(0, scrollOffset - amount);
    }

    /**
     * Scrolls the archive view down by the given number of pixels. The offset is
     * clamped to the maximum scrollable extent in {@link #render(Graphics2D)}.
     *
     * @param amount the number of pixels to scroll down
     */
    public void scrollDown(int amount) {
        scrollOffset += amount;
    }

    /**
     * Returns the current scroll offset, allowing input handlers to read the state.
     *
     * @return the current vertical scroll offset in pixels
     */
    public int getScrollOffset() {
        return scrollOffset;
    }

    /**
     * Sets the scroll offset directly.
     *
     * @param offset the new scroll offset in pixels; will be clamped during rendering
     */
    public void setScrollOffset(int offset) {
        this.scrollOffset = Math.max(0, offset);
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
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    public void render(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Dark background
        g.setColor(BG_COLOR);
        g.fillRect(0, 0, CANVAS_W, CANVAS_H);

        // Title: "FRAGMENT ARCHIVE" in gold serif
        g.setFont(TITLE_FONT);
        g.setColor(GOLD_COLOR);
        FontMetrics titleFm = g.getFontMetrics();
        String title = "FRAGMENT ARCHIVE";
        int titleWidth = titleFm.stringWidth(title);
        g.drawString(title, (CANVAS_W - titleWidth) / 2, TITLE_Y);

        // Decorative line under title
        int lineY = TITLE_Y + 8;
        int lineHalfW = titleWidth / 2 + 30;
        g.drawLine(CANVAS_W / 2 - lineHalfW, lineY, CANVAS_W / 2 + lineHalfW, lineY);

        // Set up clipping region for the scrollable list area
        int clipTop = LIST_START_Y;
        int clipBottom = CANVAS_H - 30;
        g.setClip(0, clipTop, CANVAS_W, clipBottom - clipTop);

        // Compute total content height to clamp scroll
        int totalHeight = computeTotalContentHeight(g);
        int maxScroll = Math.max(0, totalHeight - (clipBottom - clipTop));
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }

        // Draw each fragment entry
        int curY = LIST_START_Y - scrollOffset;
        int textAreaWidth = CANVAS_W - LEFT_MARGIN - RIGHT_MARGIN;

        for (String fragID : ALL_KNOWN_IDS) {
            LoreFragment frag = collectedByID.get(fragID);
            boolean isCollected = (frag != null);

            // Fragment ID label in gold
            g.setFont(ID_LABEL_FONT);
            FontMetrics idFm = g.getFontMetrics();
            g.setColor(GOLD_COLOR);
            String idLabel = "[ " + fragID + " ]";
            g.drawString(idLabel, LEFT_MARGIN, curY + idFm.getAscent());

            // Combat unlock badge beside the ID
            if (isCollected && frag.getUnlock() != LoreFragment.AbilityUnlock.NONE) {
                g.setFont(BADGE_FONT);
                FontMetrics badgeFm = g.getFontMetrics();
                String badge = "UNLOCKED: " + frag.getUnlock().name();
                int badgeX = LEFT_MARGIN + idFm.stringWidth(idLabel) + 12;
                int badgeY = curY + idFm.getAscent();

                // Badge background
                int badgeW = badgeFm.stringWidth(badge) + 10;
                int badgeH = badgeFm.getHeight() + 2;
                g.setColor(new Color(0xD4, 0xAF, 0x37, 0x33));
                g.fillRoundRect(badgeX - 5, badgeY - badgeFm.getAscent() - 1,
                        badgeW, badgeH, 4, 4);
                g.setColor(GOLD_COLOR);
                g.drawRoundRect(badgeX - 5, badgeY - badgeFm.getAscent() - 1,
                        badgeW, badgeH, 4, 4);
                g.drawString(badge, badgeX, badgeY);
            }

            curY += idFm.getHeight() + 4;

            // Body text or locked placeholder
            if (isCollected) {
                g.setFont(BODY_TEXT_FONT);
                g.setColor(WHITE_COLOR);
                curY = drawWrappedText(g, frag.getBodyText(), LEFT_MARGIN + 16,
                        curY, textAreaWidth - 16);
            } else {
                g.setFont(LOCKED_FONT);
                g.setColor(DIM_GRAY_COLOR);
                g.drawString("[FRAGMENT LOCKED]", LEFT_MARGIN + 16,
                        curY + g.getFontMetrics().getAscent());
                curY += g.getFontMetrics().getHeight();
            }

            curY += ENTRY_SPACING;

            // Separator line
            g.setColor(new Color(0x22, 0x22, 0x2A));
            g.drawLine(LEFT_MARGIN, curY, CANVAS_W - RIGHT_MARGIN, curY);
            curY += ENTRY_SPACING;
        }

        // Remove clipping
        g.setClip(null);

        // Scroll indicator arrows
        if (scrollOffset > 0) {
            g.setColor(GOLD_COLOR);
            g.setFont(BADGE_FONT);
            g.drawString("\u25B2  Scroll Up", CANVAS_W / 2 - 35, clipTop + 14);
        }
        if (scrollOffset < maxScroll) {
            g.setColor(GOLD_COLOR);
            g.setFont(BADGE_FONT);
            g.drawString("\u25BC  Scroll Down", CANVAS_W / 2 - 40, clipBottom - 4);
        }
    }

    // -------------------------------------------------------------------------
    // Rendering helpers
    // -------------------------------------------------------------------------

    /**
     * Draws text wrapped to the given width, returning the y position after the
     * last line.
     */
    private int drawWrappedText(Graphics2D g, String text, int x, int y, int maxWidth) {
        FontMetrics fm = g.getFontMetrics();
        int lineHeight = fm.getHeight();

        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        int curY = y;

        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(candidate) > maxWidth && line.length() > 0) {
                g.drawString(line.toString(), x, curY + fm.getAscent());
                curY += lineHeight;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            g.drawString(line.toString(), x, curY + fm.getAscent());
            curY += lineHeight;
        }

        return curY;
    }

    /**
     * Computes the total content height of the archive list in pixels, used to
     * determine the maximum scroll offset.
     */
    private int computeTotalContentHeight(Graphics2D g) {
        int height = 0;

        g.setFont(ID_LABEL_FONT);
        int idLineHeight = g.getFontMetrics().getHeight() + 4;
        int textAreaWidth = CANVAS_W - LEFT_MARGIN - RIGHT_MARGIN - 16;

        for (String fragID : ALL_KNOWN_IDS) {
            LoreFragment frag = collectedByID.get(fragID);

            // ID label line
            height += idLineHeight;

            if (frag != null) {
                // Body text (estimate wrapped lines)
                g.setFont(BODY_TEXT_FONT);
                FontMetrics fm = g.getFontMetrics();
                height += estimateWrappedHeight(fm, frag.getBodyText(), textAreaWidth);
            } else {
                g.setFont(LOCKED_FONT);
                height += g.getFontMetrics().getHeight();
            }

            // Entry spacing + separator + spacing
            height += ENTRY_SPACING * 2;
        }

        return height;
    }

    /**
     * Estimates the pixel height of text when word-wrapped to the given width.
     */
    private int estimateWrappedHeight(FontMetrics fm, String text, int maxWidth) {
        if (text == null || text.isEmpty()) return fm.getHeight();

        String[] words = text.split("\\s+");
        int lines = 1;
        StringBuilder line = new StringBuilder();

        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(candidate) > maxWidth && line.length() > 0) {
                lines++;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }

        return lines * fm.getHeight();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the ordered list of all collected lore fragments.
     *
     * @return an unmodifiable view of the collected fragments; never {@code null}
     */
    public List<LoreFragment> getCollected() {
        return Collections.unmodifiableList(collected);
    }

    /**
     * Returns the set of fragment IDs that have already been collected.
     *
     * @return an unmodifiable view of the collected ID set; never {@code null}
     */
    public Set<String> getCollectedIDs() {
        return Collections.unmodifiableSet(collectedIDs);
    }
}
