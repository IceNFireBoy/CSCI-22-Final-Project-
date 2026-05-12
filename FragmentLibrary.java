/**
 * Stores collected lore fragments and resolves which abilities they unlock.
 *
 * @author Marxus Antonio L. Magisa (253602) & Antonio Sebastian B. Pasia (254505)
 * @version May 12, 2026
 *
 * I have not discussed the Java language code in my program
 * with anyone other than my instructor or the teaching assistants
 * assigned to this course.
 *
 * I have not used Java language code obtained from another student,
 * or any other unauthorized source, either modified or unmodified.
 * If any Java language code or documentation used in my program
 * was obtained from another source, such as a textbook or website,
 * that has been clearly noted with a proper citation in the comments
 * of my program.
 */
import java.awt.*;
import java.util.*;
import java.util.List;
public class FragmentLibrary {

    private static final int CANVAS_W = 1024;
    private static final int CANVAS_H = 768;

    private static final Color BG_COLOR       = new Color(0x08, 0x08, 0x0F);
    private static final Color GOLD_COLOR     = new Color(0xD4, 0xAF, 0x37);
    private static final Color WHITE_COLOR    = Color.WHITE;
    private static final Color DIM_GRAY_COLOR = new Color(0x55, 0x55, 0x55);

    private static final Font TITLE_FONT     = new Font("Serif", Font.BOLD,   28);
    private static final Font ID_LABEL_FONT  = new Font("Serif", Font.BOLD,   16);
    private static final Font BODY_TEXT_FONT = new Font("Serif", Font.ITALIC, 14);
    private static final Font LOCKED_FONT    = new Font("Serif", Font.ITALIC, 14);
    private static final Font BADGE_FONT     = new Font("Serif", Font.BOLD,   11);

    private static final int TITLE_Y      = 50;
    private static final int LIST_START_Y = 90;
    private static final int LEFT_MARGIN  = 60;
    private static final int RIGHT_MARGIN = 60;
    private static final int ENTRY_SPACING = 12;

    private static final String[] ALL_KNOWN_IDS = {
        "A1-INTRO",
        "A2-WALL_CLING",
        "A3-SHADOW_DASH",
        "A3-IRON"
    };

    private List<LoreFragment> collected;

    private Set<String> collectedIDs;

    private Map<String, LoreFragment> collectedByID;

    private int scrollOffset;

    private Player player;

    private CutsceneRenderer cutsceneRenderer = CutsceneRenderer.get();

    public FragmentLibrary() {
        this.collected     = new ArrayList<>();
        this.collectedIDs  = new HashSet<>();
        this.collectedByID = new LinkedHashMap<>();
        this.scrollOffset  = 0;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public void setCutsceneRenderer(CutsceneRenderer cutsceneRenderer) {
        this.cutsceneRenderer = cutsceneRenderer;
    }

    private void triggerCutscene(CutsceneID id) {
        if (id == null || cutsceneRenderer == null) return;
        cutsceneRenderer.play(id);
    }

    private static CutsceneID cutsceneForUnlock(LoreFragment.AbilityUnlock unlock) {
        if (unlock == null) return null;
        switch (unlock) {
            case DODGE:       return CutsceneID.FIRST_LIGHT;
            case WALL_CLING:  return CutsceneID.EDGE_OF_LIGHT;
            case SHADOW_DASH: return CutsceneID.LAST_COOPERATIVE_ACT;
            default:          return null;
        }
    }

    public void collect(LoreFragment f) {

        if (collectedIDs.contains(f.getFragmentID())) {
            return;
        }

        LoreFragment.AbilityUnlock unlock = f.getUnlock();

        if (unlock == LoreFragment.AbilityUnlock.SHADOW_DASH) {
            boolean hasDodgeFragment     = false;
            boolean hasWallClingFragment = false;
            for (LoreFragment cf : collected) {
                if (cf.getUnlock() == LoreFragment.AbilityUnlock.DODGE)      hasDodgeFragment     = true;
                if (cf.getUnlock() == LoreFragment.AbilityUnlock.WALL_CLING) hasWallClingFragment = true;
            }
            if (!hasDodgeFragment || !hasWallClingFragment) {
                f.setGated(true);
                return;
            }
        }

        collected.add(f);
        collectedIDs.add(f.getFragmentID());
        collectedByID.put(f.getFragmentID(), f);
        f.collect();

        if (unlock != LoreFragment.AbilityUnlock.NONE && player != null) {
            player.unlockAbility(unlock);
        }

        triggerCutscene(cutsceneForUnlock(unlock));
        System.out.println("[FragmentLibrary] Fragment collected: " + f.getFragmentID());
    }

    public void scrollUp(int amount) {
        scrollOffset = Math.max(0, scrollOffset - amount);
    }

    public void scrollDown(int amount) {
        scrollOffset += amount;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public void setScrollOffset(int offset) {
        this.scrollOffset = Math.max(0, offset);
    }

    public void render(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(BG_COLOR);
        g.fillRect(0, 0, CANVAS_W, CANVAS_H);

        g.setFont(TITLE_FONT);
        g.setColor(GOLD_COLOR);
        FontMetrics titleFm = g.getFontMetrics();
        String title = "FRAGMENT ARCHIVE";
        int titleWidth = titleFm.stringWidth(title);
        g.drawString(title, (CANVAS_W - titleWidth) / 2, TITLE_Y);

        int lineY      = TITLE_Y + 8;
        int lineHalfW  = titleWidth / 2 + 30;
        g.drawLine(CANVAS_W / 2 - lineHalfW, lineY, CANVAS_W / 2 + lineHalfW, lineY);

        int clipTop    = LIST_START_Y;
        int clipBottom = CANVAS_H - 30;
        g.setClip(0, clipTop, CANVAS_W, clipBottom - clipTop);

        int totalHeight = computeTotalContentHeight(g);
        int maxScroll   = Math.max(0, totalHeight - (clipBottom - clipTop));
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }

        int curY          = LIST_START_Y - scrollOffset;
        int textAreaWidth = CANVAS_W - LEFT_MARGIN - RIGHT_MARGIN;

        for (String fragID : ALL_KNOWN_IDS) {
            LoreFragment frag      = collectedByID.get(fragID);
            boolean      isCollected = (frag != null);

            g.setFont(ID_LABEL_FONT);
            FontMetrics idFm = g.getFontMetrics();
            g.setColor(GOLD_COLOR);
            String idLabel = "[ " + fragID + " ]";
            g.drawString(idLabel, LEFT_MARGIN, curY + idFm.getAscent());

            if (isCollected && frag.getUnlock() != LoreFragment.AbilityUnlock.NONE) {
                g.setFont(BADGE_FONT);
                FontMetrics badgeFm = g.getFontMetrics();
                String badge  = "UNLOCKED: " + frag.getUnlock().name();
                int    badgeX = LEFT_MARGIN + idFm.stringWidth(idLabel) + 12;
                int    badgeY = curY + idFm.getAscent();

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

            g.setColor(new Color(0x22, 0x22, 0x2A));
            g.drawLine(LEFT_MARGIN, curY, CANVAS_W - RIGHT_MARGIN, curY);
            curY += ENTRY_SPACING;
        }

        g.setClip(null);

        if (scrollOffset > 0) {
            g.setColor(GOLD_COLOR);
            g.setFont(BADGE_FONT);
            g.drawString("▲  Scroll Up", CANVAS_W / 2 - 35, clipTop + 14);
        }
        if (scrollOffset < maxScroll) {
            g.setColor(GOLD_COLOR);
            g.setFont(BADGE_FONT);
            g.drawString("▼  Scroll Down", CANVAS_W / 2 - 40, clipBottom - 4);
        }
    }

    private int drawWrappedText(Graphics2D g, String text, int x, int y, int maxWidth) {
        FontMetrics fm     = g.getFontMetrics();
        int         lineHeight = fm.getHeight();

        String[]      words = text.split("\\s+");
        StringBuilder line  = new StringBuilder();
        int           curY  = y;

        for (String word : words) {
            String candidate = line.length() == 0
                    ? word
                    : line + " " + word;
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

    private int computeTotalContentHeight(Graphics2D g) {
        int height = 0;

        g.setFont(ID_LABEL_FONT);
        int idLineHeight  = g.getFontMetrics().getHeight() + 4;
        int textAreaWidth = CANVAS_W - LEFT_MARGIN - RIGHT_MARGIN - 16;

        for (String fragID : ALL_KNOWN_IDS) {
            LoreFragment frag = collectedByID.get(fragID);

            height += idLineHeight;

            if (frag != null) {

                g.setFont(BODY_TEXT_FONT);
                FontMetrics fm = g.getFontMetrics();
                height += estimateWrappedHeight(fm, frag.getBodyText(), textAreaWidth);
            } else {
                g.setFont(LOCKED_FONT);
                height += g.getFontMetrics().getHeight();
            }

            height += ENTRY_SPACING * 2;
        }

        return height;
    }

    private int estimateWrappedHeight(FontMetrics fm, String text, int maxWidth) {
        if (text == null || text.isEmpty()) return fm.getHeight();

        String[]      words = text.split("\\s+");
        int           lines = 1;
        StringBuilder line  = new StringBuilder();

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

    public List<LoreFragment> getCollected() {
        return Collections.unmodifiableList(collected);
    }

    public Set<String> getCollectedIDs() {
        return Collections.unmodifiableSet(collectedIDs);
    }
}
