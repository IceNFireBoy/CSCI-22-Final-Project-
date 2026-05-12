












































import java.awt.*;
public class StunMinigame {










    public static final long OPPORTUNITY_DURATION_MS = 3_500L;






    public static final long STUN_DURATION_MS = 5_000L;






    public static final int HITS_REQUIRED = 3;





    public static final float FREQ_BASE_HZ = 2.0f;





    public static final float FREQ_MIN_HZ = 0.8f;






    public static final float TARGET_HALF_WIDTH = 0.18f;






    private boolean active = false;


    private long startMs = 0L;


    private long endMs = 0L;


    private int hits = 0;






    private int faithful = 0;






    private float frequencyHz = FREQ_BASE_HZ;







    private int pendingResult = -1;





    private long lastHitMs = 0L;



















    public synchronized void open(int faithfulScore) {
        this.active  = true;
        this.startMs = System.currentTimeMillis();
        this.endMs   = startMs + OPPORTUNITY_DURATION_MS;
        this.hits    = 0;
        this.faithful = Math.max(0, Math.min(Player.FAITHFUL_MAX, faithfulScore));

        float t = (Player.FAITHFUL_MAX == 0) ? 0f : (float) this.faithful / Player.FAITHFUL_MAX;
        this.frequencyHz = FREQ_BASE_HZ + (FREQ_MIN_HZ - FREQ_BASE_HZ) * t;
        this.pendingResult = -1;
        this.lastHitMs = 0L;
    }










    public synchronized void forceClose() {
        this.active        = false;
        this.hits          = 0;
        this.pendingResult = -1;
    }













    public synchronized void tick(long nowMs) {
        if (!active) return;
        if (nowMs >= endMs) {
            if (pendingResult == -1) {
                pendingResult = (hits >= HITS_REQUIRED) ? 1 : 0;
            }
            active = false;
        }
    }



















    public synchronized boolean onSpacePressed() {
        if (!active) return false;
        long now = System.currentTimeMillis();
        if (now >= endMs) return false;
        float p = markerPosition(now);
        if (Math.abs(p) <= TARGET_HALF_WIDTH) {
            hits++;
            lastHitMs = now;
            if (hits >= HITS_REQUIRED) {
                pendingResult = 1;
                active = false;
            }
        } else {
            hits = 0;
        }
        return true;
    }












    public synchronized boolean isActive() { return active; }







    public synchronized int getHits() { return hits; }







    public synchronized int getFaithful() { return faithful; }













    public synchronized int consumePendingResult() {
        int r = pendingResult;
        pendingResult = -1;
        return r;
    }











    private float markerPosition(long nowMs) {
        float t = (nowMs - startMs) / 1000f;
        double phase = 2.0 * Math.PI * frequencyHz * t;
        return (float) Math.sin(phase);
    }

































    public synchronized void render(Graphics2D g, int canvasW, int canvasH) {
        if (!active) return;
        long now = System.currentTimeMillis();
        if (now >= endMs) return;


        int panelW = 520;
        int panelH = 96;
        int ox = (canvasW - panelW) / 2;
        int oy = canvasH - panelH - 96;


        Composite prev = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.88f));
        g.setColor(new Color(0x05, 0x05, 0x10));
        g.fillRoundRect(ox, oy, panelW, panelH, 12, 12);
        g.setComposite(prev);


        g.setColor(new Color(0x66, 0x99, 0xFF));
        Stroke prevStroke = g.getStroke();
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(ox, oy, panelW, panelH, 12, 12);
        g.setStroke(prevStroke);
        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        g.setColor(new Color(0xCC, 0xDD, 0xFF));
        g.drawString("STUN THE ARCHITECT — SPACE x" + HITS_REQUIRED,
                     ox + 16, oy + 20);


        int trackX = ox + 24;
        int trackY = oy + 42;
        int trackW = panelW - 48;
        int trackH = 14;


        g.setColor(new Color(0x10, 0x10, 0x20));
        g.fillRoundRect(trackX, trackY, trackW, trackH, 6, 6);


        int zoneW     = Math.max(4, (int) (trackW * TARGET_HALF_WIDTH));
        int zoneX     = trackX + trackW / 2 - zoneW;
        int zoneFullW = zoneW * 2;
        g.setColor(new Color(0x33, 0x88, 0x44));
        g.fillRoundRect(zoneX, trackY, zoneFullW, trackH, 6, 6);
        g.setColor(new Color(0x55, 0xDD, 0x88));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(zoneX, trackY, zoneFullW, trackH, 6, 6);
        g.setStroke(prevStroke);


        float p = markerPosition(now);
        int markerX = trackX + (int) ((trackW / 2.0f) * (1.0f + p)) - 4;
        boolean flash = (now - lastHitMs) < 180L;
        g.setColor(flash ? new Color(0xFF, 0xEE, 0x88) : new Color(0xEE, 0xCC, 0x66));
        g.fillRoundRect(markerX, trackY - 3, 8, trackH + 6, 4, 4);
        g.setColor(new Color(0x33, 0x22, 0x00));
        g.drawRoundRect(markerX, trackY - 3, 8, trackH + 6, 4, 4);


        int pipSize  = 10;
        int pipGap   = 4;
        int pipRowW  = HITS_REQUIRED * pipSize + (HITS_REQUIRED - 1) * pipGap;
        int pipRowX  = ox + panelW - pipRowW - 16;
        int pipRowY  = oy + 18;
        for (int i = 0; i < HITS_REQUIRED; i++) {
            int px = pipRowX + i * (pipSize + pipGap);
            Color fill = (i < hits)
                    ? new Color(0x66, 0xFF, 0xAA)
                    : new Color(0x1A, 0x1A, 0x24);
            g.setColor(fill);
            g.fillOval(px, pipRowY, pipSize, pipSize);
            g.setColor(new Color(0x3A, 0x3A, 0x48));
            g.drawOval(px, pipRowY, pipSize, pipSize);
        }


        float remainingFrac = (endMs - now) / (float) OPPORTUNITY_DURATION_MS;
        remainingFrac = Math.max(0f, Math.min(1f, remainingFrac));
        int cbX = ox + 16;
        int cbY = oy + panelH - 18;
        int cbW = panelW - 32;
        int cbH = 6;
        g.setColor(new Color(0x1A, 0x1A, 0x24));
        g.fillRect(cbX, cbY, cbW, cbH);
        g.setColor(new Color(0x77, 0xAA, 0xFF));
        g.fillRect(cbX, cbY, (int) (cbW * remainingFrac), cbH);
        g.setColor(new Color(0x33, 0x44, 0x66));
        g.drawRect(cbX, cbY, cbW, cbH);


        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(0x88, 0x99, 0xCC));
        FontMetrics fm = g.getFontMetrics();
        String hint = String.format("FAITH %d/%d  —  %.1f Hz",
                faithful, Player.FAITHFUL_MAX, frequencyHz);
        g.drawString(hint, ox + 16, oy + panelH - 22);
        String keyHint = "SPACE";
        int khX = ox + panelW - fm.stringWidth(keyHint) - 16;
        g.drawString(keyHint, khX, oy + panelH - 22);
    }
}
