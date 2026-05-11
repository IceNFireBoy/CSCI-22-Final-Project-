/**
 * Abstract base for altar concealment behaviours. A concealment hides an
 * {@link Altar} from view (and from the trigger overlap check) until a
 * subclass-specific reveal condition is met. Concrete subclasses live in
 * separate files and are wired in by P9.2'.
 *
 * <p>Architecture role: An {@link Altar} may hold an optional reference to a
 * {@code AltarConcealment}. Each frame, {@link Altar#render(java.awt.Graphics2D)}
 * calls {@link #isRevealed(LevelState, GameStarter)}; when the result is
 * {@code false} the altar paints nothing (or a faint silhouette hint at the
 * subclass's discretion) and {@link GameStarter#checkAltarTrigger()} skips the
 * overlap dispatch so the player cannot interact with a hidden altar.</p>
 *
 * <p>The contract is intentionally narrow — one boolean predicate — so new
 * concealment types (e.g. "revealed when the Apprentice has placed N blocks")
 * can be added without changing this base class.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-29
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 1c "Abstract Classes" - minimal abstract-class example: zero
//                                fields, one abstract method
//                                (isRevealed). The abstract-classes
//                                module's "use abstract when you want a
//                                shared signature without forcing a
//                                shared implementation" pattern - the
//                                four subclasses (GhostWall, Breakable,
//                                PressurePlate, Timed) provide totally
//                                different reveal logic but share this
//                                contract.
// =========================================================================

public abstract class AltarConcealment { // Abstract — never instantiated directly; subclasses provide the reveal logic

    /**
     * Returns whether the concealed altar is currently visible and interactable.
     * Called every frame by the owning {@link Altar} during render and every tick
     * by {@link GameStarter#checkAltarTrigger()} during overlap dispatch.
     *
     * <p>The two arguments give subclasses access to: the per-level mutable
     * state ({@code state}) for time-of-level reveals and shared counters; and
     * the running game session ({@code context}) for cross-entity queries such
     * as "has the player used MELEE this level?" or "does any placed block
     * overlap our paired position?". Subclasses are free to ignore either
     * argument when their reveal condition does not require it.</p>
     *
     * @param state   the active {@link LevelState}; never {@code null}
     * @param context the running game session for cross-entity queries;
     *                never {@code null}
     * @return {@code true} if the altar should render and accept overlaps;
     *         {@code false} if it should remain hidden
     */
    public abstract boolean isRevealed(LevelState state, GameStarter context); // Single-method contract; subclasses implement the reveal predicate
}
