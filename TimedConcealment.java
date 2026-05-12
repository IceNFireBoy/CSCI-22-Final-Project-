





























public class TimedConcealment extends AltarConcealment {


    private final long durationMs;


    private long firstPolledAtMs = -1L;






    public TimedConcealment(long durationMs) {
        this.durationMs = Math.max(0L, durationMs);
    }


    public static AltarConcealment of(long durationMs) {
        return new TimedConcealment(durationMs);
    }

    @Override
    public boolean isRevealed(LevelState state, GameStarter context) {
        long now = System.currentTimeMillis();
        if (firstPolledAtMs < 0L) firstPolledAtMs = now;
        return (now - firstPolledAtMs) < durationMs;
    }
}
