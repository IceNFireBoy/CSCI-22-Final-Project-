




























public class GhostWallConcealment extends AltarConcealment {


    public static AltarConcealment of() { return new GhostWallConcealment(); }

    @Override
    public boolean isRevealed(LevelState state, GameStarter context) {
        if (context == null) return false;
        Player p = context.getPlayer();
        if (p == null) return false;
        return p.hasUnlock(LoreFragment.AbilityUnlock.DODGE);
    }
}
