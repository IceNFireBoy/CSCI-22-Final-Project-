/**
 Shared mutable state for the active level, including current act, block budget,
 countdown timer, and remote player state. All game-loop subsystems (GameStarter,
 GameCanvas, InputRouter, NetworkIO) read/write fields directly. Enum GamePhase
 drives renderer and input-routing selection each frame.

 Remote-state fields (remote*) are populated from incoming network packets by
 GameStarter.handleMessage() and read by GameCanvas to draw partner avatars and
 light sources.
 */


// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 1d "Inner Classes"  - GamePhase is a public nested enum used
//                               by GameCanvas, GameStarter, and
//                               LevelGenerator. Direct nested-enum
//                               example from 1d.
// Module 1a "Modifiers"      - public mutable fields used as a small
//                               data carrier; static factory helpers.
// Module 4c "Networking"     - implements Serializable / Networkable so
//                               snapshots can cross the wire.
// =========================================================================
public class LevelState { // Plain mutable state bag; public fields allow direct read/write from all subsystems without verbose getters/setters

    // =========================================================================
    // Enum — GamePhase
    // =========================================================================

    // High-level game phase; drives renderer and input routing each frame
    public enum GamePhase { // Finite set of high-level game states; drives renderer selection and input routing

        MENU,             // Startup title/lobby screen
        LOBBY,            // Role-selection lobby
        ACT1,             // First platforming act
        ACT2,             // Second platforming act
        ACT3,             // Third platforming act
        BOSS,             // Boss arena encounter
        FINAL_CORRIDOR,   // Post-boss gauntlet
        CUTSCENE,         // Narrative interlude
        PAUSED_WAITING,   // Reconnect wait state
        END_SCREEN        // Final victory/defeat screen
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    public int currentLevel; // 1-based level index; read by GameCanvas for HUD display

    public int blockBudget; // Placement token allowance; decremented by block placement, reset per level

    public long timeRemainingMs; // Countdown in milliseconds; decremented 16 ms/tick when timerActive is true

    public boolean timerActive; // Timer running flag; false during lobby, cutscenes, pause

    public GamePhase currentPhase; // Active phase; drives renderer and input dispatch every frame

    public int[] coreHealth = {3, 3, 3, 3}; // HP of each Core (index 0-3); server-authoritative, updated from network packets

    // -------------------------------------------------------------------------
    // Remote Wanderer state (Apprentice client only)
    // -------------------------------------------------------------------------

    public float remoteWandererX = -1; // Remote Wanderer x; -1 = not yet received from network

    public float remoteWandererY = -1; // Remote Wanderer y; -1 = not yet received from network

    public String remoteWandererState = "idle"; // Animation state for ghost sprite; defaults to idle until network packet arrives

    public float remoteWandererHealth = 5; // Remote health (0-5); modulates ghost tint color

    // -------------------------------------------------------------------------
    // Remote light-source state (Wanderer client only)
    // -------------------------------------------------------------------------

    public int remoteLightX = 512; // Canvas-space light x; defaults to center until network packet arrives

    public int remoteLightY = 384; // Canvas-space light y; defaults to center until network packet arrives

    public int remoteLightRadius = 180; // Light radius (px); range 20-180; defaults to max

    private boolean lightActive = true; // Light on/off toggle; accessed via setters

    // P9.3' — crawlerStates removed alongside DarkCrawler. The new hazard suite
    // (CorruptedSpike / CorruptedWall / PhantomBlock / LightLockedMover) is
    // locally driven; no per-entity sync state on the wire.

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public LevelState() {                              // Default constructor: initialise to safe pre-session values
        this.currentLevel = 1;                         // Start at level 1; LevelLoader.loadLevel(1) will be called on session start
        this.blockBudget = 20;                         // Default 20 placement tokens; reset by GameStarter.loadLevel() per-level spec
        this.timeRemainingMs = 300_000L;               // 5 minutes (300,000 ms) default timer; overridden by per-level timer specs
        this.timerActive = false;                      // Timer starts paused; GameStarter.resumeTimer() starts it after level load completes
        this.currentPhase = GamePhase.MENU;            // Start in MENU phase; transitions to LOBBY once the network connection is established
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    // Placeholder for act-to-act transition; to be implemented in later phase
    public void advancePhase() { }


    // -------------------------------------------------------------------------
    // Remote-state setters (called from GameStarter.handleMessage)
    // -------------------------------------------------------------------------

    public void setWandererPosition(float x, float y) { // Called on PLAYER_STATE packets; updates ghost position
        this.remoteWandererX = x;                        // Store remote x; read by GameCanvas to position the partner ghost sprite
        this.remoteWandererY = y;                        // Store remote y; read by GameCanvas to position the partner ghost sprite
    }

    public void setWandererState(String state) { // Called on PLAYER_STATE; null defaults to idle
        this.remoteWandererState = (state != null) ? state : "idle"; // Normalise null to "idle" so ghost renderer always has a valid non-null key
    }

    public void setLightPosition(int x, int y) { // Called on LIGHT_POS packets; updates light cursor position
        this.remoteLightX = x;                   // Store new canvas-space light x; read by LightRenderer on the next frame
        this.remoteLightY = y;                   // Store new canvas-space light y; read by LightRenderer on the next frame
    }

    public void setLightRadius(int r) { // Called on LIGHT_RADIUS packets; updates light radius
        this.remoteLightRadius = r;     // Store new radius; read by LightRenderer to compute the mask cutout circle size
    }

    public int getLightX() { return remoteLightX; } // Read light x position

    public int getLightY() { return remoteLightY; } // Read light y position

    public int getLightRadius() { return remoteLightRadius; } // Read light radius

    public void setLightActive(boolean b) { lightActive = b; } // Set light on/off

    public boolean getLightActive() { return lightActive; } // Read light active state

    public void setWandererHealth(int h) { remoteWandererHealth = h; } // Called on PLAYER_STATE; modulates ghost tint

    // P9.3' — updateCrawler / getCrawlerState removed alongside DarkCrawler.
}
