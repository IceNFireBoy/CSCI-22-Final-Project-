/**
 * Abstract base class for all boss-phase attack patterns in Lumen Architect. Each
 * concrete subclass defines a specific attack with its own duration, rendering, and
 * hit-detection logic. The base class manages the common lifetime countdown.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.awt.Graphics2D;

public abstract class BossAttack extends GameElement {

    /** The total duration of this attack in milliseconds. */
    protected long durationMs;

    /** Elapsed time since this attack began, in milliseconds. */
    protected long elapsed;

    /** Whether this attack has expired and should be removed. */
    protected boolean expired;

    /**
     * Constructs a new {@code BossAttack} at the given position with the specified
     * duration.
     *
     * @param x          the x-coordinate in world space
     * @param y          the y-coordinate in world space
     * @param width      the width of the attack area
     * @param height     the height of the attack area
     * @param durationMs the total lifetime of this attack in milliseconds
     */
    public BossAttack(int x, int y, int width, int height, long durationMs) {
        super(x, y, width, height);
        this.durationMs = durationMs;
        this.elapsed = 0L;
        this.expired = false;
    }

    /**
     * Increments the elapsed timer and marks the attack as expired when the
     * duration is exceeded. Subclasses should call {@code super.update(deltaMs)}
     * at the start of their own update methods.
     *
     * @param deltaMs the time elapsed since the last tick, in milliseconds
     */
    @Override
    public void update(long deltaMs) {
        elapsed += deltaMs;
        if (elapsed >= durationMs) {
            expired = true;
            setActive(false);
        }
    }

    /**
     * Returns whether this attack has expired.
     *
     * @return {@code true} if the attack's duration has been exceeded
     */
    public boolean isExpired() {
        return expired;
    }

    /**
     * Returns the elapsed time since this attack began.
     *
     * @return elapsed time in milliseconds
     */
    public long getElapsed() {
        return elapsed;
    }
}
