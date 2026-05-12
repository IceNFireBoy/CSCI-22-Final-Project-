/**
 * Renders and advances dialogue-based cutscene sequences frame by frame.
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
import java.awt.image.*;
public class CutsceneRenderer {

    private static final Color BG_COLOR   = new Color(0x00, 0x00, 0x00);
    private static final Color TEXT_COLOR = new Color(0xEE, 0xE4, 0xC8);
    private static final Color FOOTER_CLR = new Color(0x88, 0x80, 0x70);

    private static final Font  PANEL_FONT  = new Font("Serif", Font.ITALIC, 22);
    private static final Font  FOOTER_FONT = new Font("SansSerif", Font.PLAIN, 12);

    private static final long FADE_MS = 600L;

    private static final CutsceneRenderer INSTANCE = new CutsceneRenderer();

    public static CutsceneRenderer get() { return INSTANCE; }

    private CutsceneRenderer() {
        this.script = new CutsceneScript();
    }

    private volatile CutsceneID active;

    private volatile int panelIndex;

    private volatile long startMs;

    private volatile LevelState.GamePhase returnPhase;

    private final CutsceneScript script;

    public void play(CutsceneID id) {
        if (id == null) return;
        this.active     = id;
        this.panelIndex = 0;
        this.startMs    = System.currentTimeMillis();

        LevelState ls = sharedLevelState();
        if (ls != null) {
            if (ls.currentPhase != LevelState.GamePhase.CUTSCENE) {
                this.returnPhase = ls.currentPhase;
            }
            ls.currentPhase = LevelState.GamePhase.CUTSCENE;
        }
    }

    public boolean advance() {
        if (active == null) return false;
        String[] lines = script.getLines(active);
        if (panelIndex + 1 >= lines.length) {
            return true;
        }
        panelIndex++;
        startMs = System.currentTimeMillis();
        return false;
    }

    public void stop() {
        this.active     = null;
        this.panelIndex = 0;
        LevelState ls = sharedLevelState();
        if (ls != null && returnPhase != null
                && ls.currentPhase == LevelState.GamePhase.CUTSCENE) {
            ls.currentPhase = returnPhase;
        }
        this.returnPhase = null;
    }

    public boolean isPlaying() { return active != null; }

    public CutsceneID getActive() { return active; }

    public void render(Graphics2D g, int w, int h) {
        if (active == null) return;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(BG_COLOR);
        g.fillRect(0, 0, w, h);

        String spritePath = "resources/cutscenes/"
                + active.name().toLowerCase() + "_" + panelIndex + ".png";
        BufferedImage art = SpriteLoader.getInstance().tryLoad(spritePath);
        if (art != null) {
            int aw = art.getWidth();
            int ah = art.getHeight();
            int ax = (w - aw) / 2;
            int ay = Math.max(40, (h - ah) / 2 - 60);
            g.drawImage(art, ax, ay, null);
        }

        float alpha = Math.min(1.0f,
                (System.currentTimeMillis() - startMs) / (float) FADE_MS);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        String[] lines = script.getLines(active);
        String text = (lines.length == 0)
                ? "[" + active.name() + "]"
                : lines[Math.min(panelIndex, lines.length - 1)];

        g.setFont(PANEL_FONT);
        g.setColor(TEXT_COLOR);
        FontMetrics fm = g.getFontMetrics();
        int textY = h - 160;
        int maxW  = (int) (w * 0.8f);
        int x0    = (w - maxW) / 2;
        int cursorY = textY;

        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(candidate) > maxW && line.length() > 0) {
                g.drawString(line.toString(), x0, cursorY);
                cursorY += fm.getHeight();
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            g.drawString(line.toString(), x0, cursorY);
        }

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        g.setFont(FOOTER_FONT);
        g.setColor(FOOTER_CLR);
        String footer = (panelIndex + 1 >= lines.length)
                ? "[ Press SPACE to continue ]"
                : "[ Press SPACE to advance ]";
        FontMetrics ffm = g.getFontMetrics();
        g.drawString(footer, (w - ffm.stringWidth(footer)) / 2, h - 50);

        if (lines.length > 1) {
            String counter = (panelIndex + 1) + " / " + lines.length;
            g.drawString(counter, w - ffm.stringWidth(counter) - 24, h - 50);
        }
    }

    private volatile LevelState sharedLevelState;

    public void setLevelState(LevelState ls) {
        this.sharedLevelState = ls;
    }

    private LevelState sharedLevelState() { return sharedLevelState; }
}
