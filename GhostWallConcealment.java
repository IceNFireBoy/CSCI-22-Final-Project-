/**
 * Concealment subtype: the altar is hidden behind a ghostly veil that the
 * Wanderer can only see through after collecting the DODGE ability fragment.
 *
 * <p>Architecture role: extends {@link AltarConcealment} for use with
 * {@link Altar#setConcealment(AltarConcealment)}. Each tick the altar's
 * overlap path in {@link GameStarter#checkAltarTrigger()} calls
 * {@link #isRevealed(LevelState, GameStarter)} to decide whether the player
 * can interact with the altar.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-29
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

public class GhostWallConcealment extends AltarConcealment { // Reveals on DODGE

    /** Static factory for fluent use in level layouts: {@code lastAltar().setConcealment(GhostWallConcealment.of())}. */
    public static AltarConcealment of() { return new GhostWallConcealment(); }

    @Override
    public boolean isRevealed(LevelState state, GameStarter context) {
        if (context == null) return false;             // Defensive — render-time render-side path may pass nulls
        Player p = context.getPlayer();                  // GameStarter exposes the local Wanderer reference
        if (p == null) return false;                     // Pre-construction: not yet revealed
        return p.hasUnlock(LoreFragment.AbilityUnlock.DODGE); // Single predicate — flips true once DODGE shard is collected
    }
}
