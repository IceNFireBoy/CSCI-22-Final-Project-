























public class LevelState {






    public enum GamePhase {

        MENU,
        LOBBY,
        ACT1,
        ACT2,
        ACT3,
        BOSS,
        FINAL_CORRIDOR,
        CUTSCENE,
        PAUSED_WAITING,
        END_SCREEN
    }





    public int currentLevel;

    public int blockBudget;

    public GamePhase currentPhase;

    public int[] coreHealth = {3, 3, 3, 3};





    public float remoteWandererX = -1;

    public float remoteWandererY = -1;

    public String remoteWandererState = "idle";

    public float remoteWandererHealth = 5;





    public int remoteLightX = 512;

    public int remoteLightY = 384;

    public int remoteLightRadius = 180;

    private boolean lightActive = true;









    public LevelState() {
        this.currentLevel = 1;
        this.blockBudget = 20;
        this.currentPhase = GamePhase.MENU;
    }






    public void advancePhase() { }






    public void setWandererPosition(float x, float y) {
        this.remoteWandererX = x;
        this.remoteWandererY = y;
    }

    public void setWandererState(String state) {
        this.remoteWandererState = (state != null) ? state : "idle";
    }

    public void setLightPosition(int x, int y) {
        this.remoteLightX = x;
        this.remoteLightY = y;
    }

    public void setLightRadius(int r) {
        this.remoteLightRadius = r;
    }

    public int getLightX() { return remoteLightX; }

    public int getLightY() { return remoteLightY; }

    public int getLightRadius() { return remoteLightRadius; }

    public void setLightActive(boolean b) { lightActive = b; }

    public boolean getLightActive() { return lightActive; }

    public void setWandererHealth(int h) { remoteWandererHealth = h; }


}
