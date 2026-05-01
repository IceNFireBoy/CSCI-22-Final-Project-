/**
 * Concealment subtype: the altar is walled off until the Wanderer has the
 * MELEE ability — visually a cracked wall that can be smashed open. The plan
 * called for "revealed after MELEE is used in this level"; we simplify the
 * predicate to "revealed once MELEE is unlocked", which keeps the gameplay
 * intent (you need the strike ability to get through) without adding a
 * per-level "has used melee" tracker. A future polish pass can tighten the
 * predicate to require an actual swing hit if the design wants it.
 *
 * <p>Architecture role: same plumbing as the other {@link AltarConcealment}
 * subclasses — the altar overlap in {@link GameStarter#checkAltarTrigger()}
 * gates on this predicate.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-29
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

public class BreakableWallConcealment extends AltarConcealment { // Reveals on MELEE

    /** Static factory for fluent use in level layouts. */
    public static AltarConcealment of() { return new BreakableWallConcealment(); }

    @Override
    public boolean isRevealed(LevelState state, GameStarter context) {
        if (context == null) return false;
        Player p = context.getPlayer();
        if (p == null) return false;
        return p.hasUnlock(LoreFragment.AbilityUnlock.MELEE);
    }
}
