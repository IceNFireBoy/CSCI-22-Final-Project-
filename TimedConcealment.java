/**
 * Concealment subtype: the altar is visible at level start and hides itself
 * permanently once a configured duration has elapsed. Encourages the players
 * to commit to the choice early rather than backtracking late in the level.
 *
 * <p>Implementation note: {@link LevelState#timeRemainingMs} is the level's
 * countdown timer (counts down from the per-level limit toward zero). We
 * cache the {@code timeRemainingMs} on the first call (effectively "level
 * start") and then report revealed only while less than {@code durationMs}
 * has elapsed since that snapshot. This avoids needing a per-instance "level
 * start" wall-clock — we read it lazily on the first tick the concealment is
 * polled.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-29
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

public class TimedConcealment extends AltarConcealment { // Reveals only inside a window from level start

    /** Window length in milliseconds; the altar hides after this much real time has passed. */
    private final long durationMs;

    /** Wall-clock ms snapshot taken on the first poll; -1 sentinel until snapped. */
    private long firstPolledAtMs = -1L;

    /**
     * @param durationMs how long the altar stays visible after the first time
     *                   it is polled (effectively, after the level starts and
     *                   the altar comes into render range)
     */
    public TimedConcealment(long durationMs) {
        this.durationMs = Math.max(0L, durationMs);
    }

    /** Static factory mirror for fluent use. */
    public static AltarConcealment of(long durationMs) {
        return new TimedConcealment(durationMs);
    }

    @Override
    public boolean isRevealed(LevelState state, GameStarter context) {
        long now = System.currentTimeMillis();
        if (firstPolledAtMs < 0L) firstPolledAtMs = now; // Lazy init — first poll IS our level-start snapshot
        return (now - firstPolledAtMs) < durationMs;     // True while we're inside the visibility window
    }
}
