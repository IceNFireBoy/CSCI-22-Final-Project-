/**
 Process-wide singleton holding session-level state that survives level reloads. Stores the
 role assignment the server assigns at connection time (Wanderer or Apprentice) and exposes
 boolean flags so any class can quickly determine the local role without passing strings
 through constructors.

 GameSession stores session-persistent state: role, block budget, light battery, Radiant
 Collapse unlock flag, and the network send callback. Distinct from LevelState, which holds
 frame-by-frame mutable state (timers, remote positions). The light-battery simulation runs
 on the Apprentice client only, draining at a radius-dependent rate and recharging when light
 is off. When battery empties, MouseApprentice disables light until F is pressed.
 */

public class GameSession { // Process-wide singleton; survives level reloads; owns role, block budget, battery, and the send callback

    // =========================================================================
    // Singleton
    // =========================================================================

    private static GameSession instance; // Lazy singleton field; null until first getInstance() call

    public static GameSession getInstance() { // Lazy factory; creates singleton on first call, returns same object on all subsequent calls
        if (instance == null) {
            instance = new GameSession();
        }
        return instance;
    }

    // =========================================================================
    // Fields
    // =========================================================================

    public String role; // Role string from server during lobby handshake (WANDERER or APPRENTICE); set via setRole()
    private boolean isWandererFlag; // Cached boolean true when role equals WANDERER; avoids repeated string comparisons
    private boolean isApprenticeFlag; // Cached boolean true when role equals APPRENTICE; set together with isWandererFlag

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Private constructor — prevents direct instantiation outside of
     * {@link #getInstance()}, enforcing the singleton pattern.
     */
    private GameSession() {} // Singleton enforcement: private constructor; all construction goes through getInstance()

    // =========================================================================
    // Role assignment
    // =========================================================================

    /**
     * Sets the client's role from the server-assigned string and caches the
     * derived boolean flags. Called once by {@link GameStarter} after the server
     * sends the {@link Protocol#ROLE} message during the lobby handshake.
     *
     * <p>Interaction: After this call, {@link #isWanderer()} returns {@code true}
     * when the local machine is the Wanderer, and {@link #isApprentice()} returns
     * {@code true} when it is the Apprentice. Both flags will be {@code false}
     * before this method is first called (at the start of the session before the
     * server assigns a role).</p>
     *
     * @param role the role string from the server; must be either {@code "WANDERER"}
     *             or {@code "APPRENTICE"}; any other value leaves both flags false
     */
    public void setRole(String role) {                         // Called by GameStarter on ROLE message; sets role string and derived flags simultaneously
        this.role             = role;                          // Store the raw role string; retained for legacy comparisons and logging
        this.isWandererFlag   = "WANDERER".equals(role);       // Cache flag: true only if role exactly equals "WANDERER"; avoids repeated string.equals() calls
        this.isApprenticeFlag = "APPRENTICE".equals(role);     // Cache flag: true only if role exactly equals "APPRENTICE"; symmetric with isWandererFlag
    }

    /**
     * Returns whether this client is running as the Wanderer (the keyboard-controlled
     * platformer character).
     *
     * @return {@code true} if role is {@code "WANDERER"}; {@code false} otherwise
     */
    public boolean isWanderer()   { return isWandererFlag;   } // Read accessor for the Wanderer flag; used by GameCanvas, InputRouter, FragmentLibrary etc.

    /**
     * Returns whether this client is running as the Apprentice (the mouse-controlled
     * light and boss controller).
     *
     * @return {@code true} if role is {@code "APPRENTICE"}; {@code false} otherwise
     */
    public boolean isApprentice() { return isApprenticeFlag; } // Read accessor for the Apprentice flag; symmetric with isWanderer()

    // =========================================================================
    // Network send callback
    // =========================================================================

    /**
     * Callback injected by {@link GameStarter} once the socket is open. Accepts a
     * pipe-delimited {@link Protocol} message string and writes it to the server via
     * the {@link NetworkProtocol.StringPacket} output stream. Stored here so any
     * subsystem can call {@link #sendToServer(String)} without holding a reference
     * to {@link GameStarter} or the socket directly.
     */
    private java.util.function.Consumer<String> sendCallback; // Lambda injected by GameStarter; wraps the ObjectOutputStream write call

    /**
     * Sets the network send callback. Called once by {@link GameStarter} after the
     * socket connection to the server is established. Before this is called,
     * {@link #sendToServer(String)} is a no-op (null guard).
     *
     * <p>Interaction: The callback lambda created in {@link GameStarter} closes
     * over the {@code ObjectOutputStream} so this class never needs a direct
     * reference to the network layer.</p>
     *
     * @param cb the consumer that writes a message string to the server socket;
     *           must not be {@code null} (though no exception is thrown if it is)
     */
    public void setSendCallback(java.util.function.Consumer<String> cb) { // Called once by GameStarter after socket open; wires the outbound message channel
        this.sendCallback = cb;                                            // Store the lambda; subsequent sendToServer() calls will invoke it
    }

    /**
     * Sends a pipe-delimited {@link Protocol} message to the server. No-op if the
     * connection has not yet been established (i.e. {@link #setSendCallback} has not
     * been called yet).
     *
     * <p>Architecture role: Central send gateway used by all subsystems that need
     * to communicate with the server. Callers build the message string using
     * {@link Protocol} constants (e.g. {@code Protocol.PLACE_BLOCK + "|BRICK|100|200"})
     * and pass it here without needing to know about serialisation details.</p>
     *
     * @param msg the protocol message to send; must not be {@code null}
     */
    public void sendToServer(String msg) {                          // Single outbound message gateway; null-safe before connection is established
        if (sendCallback != null) sendCallback.accept(msg);         // If the callback was injected, delegate the actual write to it; otherwise silently drop
    }

    // =========================================================================
    // Block budget management
    // =========================================================================

    /** Starting and default block budget for each level. */
    private int blockBudget = 12; // Default 12 placement tokens; reset each level by resetBlockBudget()

    /** Currently selected block type for placement (default BRICK). */
    private String currentBlockType = "BRICK"; // Active block type string; matches Platform.PlatformType names; shown in the Apprentice HUD

    /** Index into {@link #BLOCK_CYCLE} tracking which type is currently selected. */
    private int blockCycleIndex = 0; // 0-based index into BLOCK_CYCLE; advanced by cycleBlockType()

    /** Ordered array of block types the Apprentice can cycle through. */
    private static final String[] BLOCK_CYCLE = {"BRICK", "SLIDE", "SPRING", "WALL", "CRUMBLE"}; // Five cyclic block types; cycleBlockType() wraps around from index 4 back to 0

    /**
     * Whether the Architect has activated the P8.3 override mode that relaxes
     * block-budget enforcement. Set by the server on altar choice
     * {@link Protocol#ALTAR_CHOICE} with type {@code "POWER_SURGE"}.
     */
    private boolean architectOverride = false; // P8.3 flag: true = block budget is ignored; set when Apprentice selects POWER_SURGE at an altar

    /**
     * Returns the current block-placement budget.
     *
     * @return number of placement tokens remaining; always ≥ 0
     */
    public int getBlockBudget() { return blockBudget; } // Read accessor; displayed in Apprentice HUD and checked before each placement

    /**
     * Directly sets the block budget (used by snapshot restore and altar rewards).
     *
     * @param b the new budget value; clamped to 0 if negative
     */
    public void setBlockBudget(int b) { blockBudget = Math.max(0, b); } // Mutator with floor-0 clamp; called by snapshot restore and POWER_SURGE altar reward

    /**
     * Decrements the budget by one if any tokens remain. Called after each
     * successful block placement to ensure the Apprentice cannot exceed the cap.
     */
    public void decrementBlockBudget() { if (blockBudget > 0) blockBudget--; } // Safe decrement: no-op if already at zero; called by GameStarter on successful PLACE_BLOCK

    /**
     * Returns the currently selected block type string.
     *
     * @return one of {@code "BRICK"}, {@code "SLIDE"}, {@code "SPRING"},
     *         {@code "WALL"}, {@code "CRUMBLE"}
     */
    public String getCurrentBlockType() { return currentBlockType; } // Read accessor; used by GameStarter.placeBlock() and Apprentice HUD display

    /**
     * Sets the active block type directly (used by snapshot restore).
     *
     * @param t the block type string; must match a {@link Platform.PlatformType} name
     */
    public void setCurrentBlockType(String t) { currentBlockType = t; } // Direct mutator; called by snapshot restore to reinstate the Apprentice's previous selection

    /**
     * Returns whether Architect Override mode is active.
     *
     * @return {@code true} if block budget is currently being bypassed
     */
    public boolean isArchitectOverride() { return architectOverride; } // Read accessor; checked by GameStarter before enforcing budget limits on block placement

    /**
     * Sets the Architect Override flag.
     *
     * @param b {@code true} to bypass budget limits; {@code false} to restore normal enforcement
     */
    public void setArchitectOverride(boolean b) { architectOverride = b; } // Mutator; called when the server confirms a POWER_SURGE altar choice

    /**
     * Advances to the next block type in the placement palette and wraps around.
     *
     * <p>Interaction: Called by {@link InputRouter} when the Apprentice presses the
     * block-cycle key. The updated {@link #currentBlockType} is immediately reflected
     * in the HUD on the next frame.</p>
     */
    public void cycleBlockType() {                                               // Advances blockCycleIndex by 1 with wrap-around; updates currentBlockType from BLOCK_CYCLE
        blockCycleIndex = (blockCycleIndex + 1) % BLOCK_CYCLE.length;           // Increment and wrap: 0→1→2→3→4→0; modulo prevents out-of-bounds
        currentBlockType = BLOCK_CYCLE[blockCycleIndex];                         // Set currentBlockType to the new cycle position
    }

    /**
     * Resets the block budget and block-type selection to their defaults for a new
     * level. Called by {@link GameStarter#loadLevel(int)} at the start of each level.
     */
    public void resetBlockBudget() {                // Called by GameStarter.loadLevel() to restore fresh Apprentice state at level start
        blockBudget = 12;                           // Reset to the default 12-token budget; per-level overrides would go here
        blockCycleIndex = 0;                        // Reset cycle index to 0 (BRICK) so the Apprentice always starts with the same selection
        currentBlockType = "BRICK";                 // Reset selected type string to match reset cycle index
    }

    // =========================================================================
    // Action methods — send real protocol messages to the server
    // =========================================================================

    /**
     * Sends a {@link Protocol#PLACE_BLOCK} message to the server requesting placement
     * of the given block type at the specified world position.
     *
     * <p>Architecture role: Called by {@link InputRouter} when the Apprentice
     * right-clicks on the canvas. The server validates the request (budget check,
     * position validity) and broadcasts the placement back to both clients if approved.</p>
     *
     * @param type the block type string (e.g. {@code "BRICK"})
     * @param x    the world-space x of the block's left edge
     * @param y    the world-space y of the block's top edge
     */
    public void placeBlock(String type, int x, int y) {                    // Builds and sends a PLACE_BLOCK wire message; server validates and echoes back
        sendToServer(Protocol.PLACE_BLOCK + "|" + type + "|" + x + "|" + y); // Pipe-delimited format: "PLACE_BLOCK|BRICK|100|200"
    }

    /**
     * Reference to the placed-blocks list so {@link #removeNearestBlock} can do a
     * proximity check before sending a {@link Protocol#REMOVE_BLOCK} message. Set
     * by {@link GameStarter#setPlacedBlocksRef(List)}.
     */
    private java.util.List<Platform> placedBlocksRef; // Live reference to GameStarter's placed-block list; used for pre-send proximity validation

    /**
     * Sets the reference to the live placed-blocks list used by
     * {@link #removeNearestBlock(int, int, int)}.
     *
     * @param blocks the Apprentice's placed-block list; must not be {@code null}
     */
    public void setPlacedBlocks(java.util.List<Platform> blocks) { // Called by GameStarter after level load to wire the placed-block list reference
        placedBlocksRef = blocks;                                   // Store the reference; mutated by the game loop via add/remove on the same object
    }

    /**
     * Sends a {@link Protocol#REMOVE_BLOCK} message for the nearest block within the
     * given radius of (x, y), or does nothing if no block is close enough.
     *
     * <p>Interaction: Called by {@link InputRouter} when the Apprentice presses the
     * remove key and clicks. The radius check prevents accidental removals when the
     * cursor is far from any placed block. The server performs its own authoritative
     * proximity check and ignores the request if the radius criterion is not met.</p>
     *
     * @param x      the world-space x of the cursor
     * @param y      the world-space y of the cursor
     * @param radius the maximum pixel distance to the block centre; blocks farther
     *               than this are not candidates for removal
     */
    public void removeNearestBlock(int x, int y, int radius) {                                  // Client-side proximity pre-check before sending REMOVE_BLOCK to server
        if (placedBlocksRef != null) {                                                           // Only run proximity check if a placed-block list reference is available
            double best = Double.MAX_VALUE;                                                      // Track the closest distance found; initialise to worst-possible value
            for (Platform b : placedBlocksRef) {                                                 // Iterate every placed block to find the closest one to (x,y)
                java.awt.Rectangle bounds = b.getBounds();                                       // Get the block's AABB for centre-point calculation
                double d = Math.hypot(bounds.x + bounds.width / 2.0 - x,                        // Euclidean distance from block centre to cursor; hypot avoids manual sqrt
                                      bounds.y + bounds.height / 2.0 - y);
                if (d < best) best = d;                                                          // Track the smallest distance seen so far
            }
            if (best > radius) return;                                                           // If no block is within radius, skip the server message entirely
        }
        sendToServer(Protocol.REMOVE_BLOCK + "|" + x + "|" + y);                               // Send removal request: "REMOVE_BLOCK|x|y"; server performs authoritative resolution
    }

    /**
     * Sends a {@link Protocol#ATTACK} message requesting a Searing Beam aimed at
     * the given world position.
     *
     * @param x the beam target world-space x
     * @param y the beam target world-space y
     */
    public void fireSearingBeam(int x, int y) {                              // Called by InputRouter when Apprentice selects and fires the SEARING_BEAM attack
        sendToServer(Protocol.ATTACK + "|SEARING_BEAM|" + x + "|" + y);     // Wire format: "ATTACK|SEARING_BEAM|targetX|targetY"
    }

    /**
     * Sends a {@link Protocol#ATTACK} message requesting a Block Rain attack.
     */
    public void fireBlockRain() {                                            // Called by InputRouter when Apprentice selects BLOCK_RAIN; no target coordinates needed
        sendToServer(Protocol.ATTACK + "|BLOCK_RAIN|0|0");                  // Wire format: "ATTACK|BLOCK_RAIN|0|0"; x/y unused, random positioning handled server-side
    }

    /**
     * Sends a {@link Protocol#ATTACK} message requesting a Crusher Block aimed at
     * the given world position.
     *
     * @param x the crusher target world-space x
     * @param y the crusher target world-space y
     */
    public void fireCrusherBlock(int x, int y) {                            // Called by InputRouter when Apprentice fires CRUSHER attack at cursor world position
        sendToServer(Protocol.ATTACK + "|CRUSHER|" + x + "|" + y);         // Wire format: "ATTACK|CRUSHER|targetX|targetY"
    }

    /**
     * Sends a {@link Protocol#ATTACK} message requesting a Spike Array attack.
     */
    public void fireSpikeArray() {                                          // Called by InputRouter when Apprentice selects SPIKE_ARRAY; spawns at Wanderer's position server-side
        sendToServer(Protocol.ATTACK + "|SPIKE_ARRAY|0|0");                // Wire format: "ATTACK|SPIKE_ARRAY|0|0"; server determines spike position from Wanderer state
    }

    /**
     * Sends a {@link Protocol#ATTACK} message requesting a Shield attack.
     */
    public void fireShield() {                                             // Called by InputRouter when Apprentice selects SHIELD; fixed-position attack, no coordinates
        sendToServer(Protocol.ATTACK + "|SHIELD|0|0");                    // Wire format: "ATTACK|SHIELD|0|0"; Shield appears at hardcoded (717,364) per BossArenaGenerator
    }

    /**
     * Sends a {@link Protocol#LEVEL_READY} message to the server when the Apprentice
     * client has finished loading the level, carrying the current block budget so the
     * server can confirm both clients are synchronised.
     */
    public void signalLevelReady() {                                        // Called by GameStarter after level load completes; synchronises server before starting the timer
        sendToServer(Protocol.LEVEL_READY + "|" + blockBudget);            // Wire format: "LEVEL_READY|budgetValue"; server waits for both clients before resuming
    }

    // =========================================================================
    // Light battery
    // =========================================================================

    /** Maximum battery level (100%). */
    private static final float MAX_BATTERY    = 100f; // Full battery; lightBattery is initialised to this value and recharged toward it

    /** Drain rate per tick when light radius exceeds 140 px: depletes in ~10 s @60 fps. */
    private static final float DRAIN_HIGH     = 0.1667f; // ~10 s lifetime at 60fps; radius > 140 = widest, most expensive light setting

    /** Drain rate per tick for radii 100–140 px: depletes in ~20 s. */
    private static final float DRAIN_MED      = 0.0833f; // ~20 s lifetime at 60fps; medium radius setting

    /** Drain rate per tick for radii 60–100 px: depletes in ~40 s. */
    private static final float DRAIN_LOW      = 0.0417f; // ~40 s lifetime at 60fps; smaller radius, less drain

    /** Drain rate per tick for radii ≤ 60 px: depletes in ~60 s. */
    private static final float DRAIN_MIN      = 0.0278f; // ~60 s lifetime at 60fps; smallest radius setting, slowest drain

    /** Recharge rate per tick when light is off: half of DRAIN_MIN. */
    private static final float REGEN_PER_TICK = 0.0139f; // Recharges at half the minimum drain rate when light is toggled off

    /** Current battery level in the range [0, 100]. Starts full. */
    private float lightBattery = MAX_BATTERY; // Apprentice light energy; drained each tick while light is on; recharged when off

    // =========================================================================
    // P8.9 — Radiant Collapse persistence
    // =========================================================================

    /**
     * P8.9 — Whether the Wanderer has collected the hidden Radiant Collapse fragment.
     * Stored here (rather than on {@link Player}) because {@link Player} instances are
     * destroyed and recreated on level reload, while {@code GameSession} is a
     * process-wide singleton whose lifetime matches the run. Read by
     * {@link Player#isRadiantCollapseUnlocked()} so callers can query through the
     * usual Player accessor.
     */
    private boolean radiantCollapseUnlocked = false; // P8.9 flag: true = Radiant Collapse ability is available this run; survives level reloads

    /**
     * Returns whether the Radiant Collapse fragment has been collected this run.
     *
     * @return {@code true} if the fragment was collected; {@code false} otherwise
     */
    public boolean isRadiantCollapseUnlocked() { return radiantCollapseUnlocked; } // Read accessor; checked by Player.isRadiantCollapseUnlocked() and GameCanvas HUD

    /**
     * Sets the Radiant Collapse unlock flag. Called by {@link FragmentLibrary} when
     * the corresponding LoreFragment is collected.
     *
     * @param v {@code true} to unlock; {@code false} to revoke (rare)
     */
    public void setRadiantCollapseUnlocked(boolean v) { radiantCollapseUnlocked = v; } // Mutator; called by FragmentLibrary when the RADIANT_COLLAPSE fragment is collected

    // =========================================================================
    // Current act string (used by HUD gate)
    // =========================================================================

    /**
     * String name of the current act, e.g. {@code "ACT1"}, {@code "ACT2"},
     * {@code "ACT3"}, {@code "BOSS"}. Set by {@link GameStarter#loadLevel(int)} each
     * time a level loads. Defaults to {@code "ACT1"} before any level is loaded.
     *
     * <p>Interaction: Read by {@link GameCanvas} to gate which HUD elements are
     * shown (e.g. the boss-phase Core health pips only appear in {@code "BOSS"}).
     * Also transmitted in {@link SessionSnapshot#currentAct} for reconnect restore.</p>
     */
    private String currentAct = "ACT1"; // Default to ACT1; updated by GameStarter.loadLevel() on every level transition

    /**
     * Returns the current act string, or {@code "ACT1"} if not yet set.
     *
     * @return one of {@code "ACT1"}, {@code "ACT2"}, {@code "ACT3"}, {@code "BOSS"},
     *         {@code "FINAL_CORRIDOR"}
     */
    public String getCurrentAct() { return currentAct; } // Read accessor; used by GameCanvas HUD gating and SessionSnapshot capture

    /**
     * Called by {@link GameStarter} when a level loads to keep the act string in sync.
     * A {@code null} input is normalised to {@code "ACT1"}.
     *
     * @param act the new act name; {@code null} is safe and resets to {@code "ACT1"}
     */
    public void setCurrentAct(String act) { currentAct = (act != null) ? act : "ACT1"; } // Mutator; null-safe; called by GameStarter.loadLevel()

    // =========================================================================
    // Light battery — accessors and simulation
    // =========================================================================

    /**
     * Returns the current battery level as a percentage.
     *
     * @return battery percentage in [0, 100]; 100 = full, 0 = empty
     */
    public float getLightBattery() { return lightBattery; } // Read accessor; displayed in Apprentice HUD; also checked to gate light toggle

    /**
     * Directly sets the battery level. Used by snapshot restore to reinstate the
     * battery value from {@link SessionSnapshot#lightBattery}.
     *
     * @param b the new battery level; clamped to [0, MAX_BATTERY]
     */
    public void setLightBattery(float b) { lightBattery = Math.max(0f, Math.min(MAX_BATTERY, b)); } // Mutator with [0,100] clamp; called by snapshot restore

    /**
     * Returns the drain rate per tick for the given radius.
     *
     * <p>The rate is tier-based: four fixed drain rates correspond to four radius
     * bands. Larger radius = faster drain, creating a resource-management decision
     * for the Apprentice between visibility and battery longevity.</p>
     *
     * @param radius the current light radius in pixels
     * @return the per-tick drain rate corresponding to the radius band
     */
    private float getDrainRate(int radius) {                                 // Maps radius to a tier-based drain rate; called by updateBattery() each tick
        if (radius > 140) return DRAIN_HIGH;                                 // Widest light (>140 px): fastest drain, ~10 s lifetime
        if (radius > 100) return DRAIN_MED;                                  // Medium-wide (100–140 px): medium drain, ~20 s lifetime
        if (radius > 60)  return DRAIN_LOW;                                  // Medium-small (60–100 px): slow drain, ~40 s lifetime
        return DRAIN_MIN;                                                     // Smallest (≤60 px): slowest drain, ~60 s lifetime
    }

    /**
     * Called once per game tick on the Apprentice client only. When the light is
     * active, drains the battery at the radius-dependent rate. When the light is off,
     * recharges at {@link #REGEN_PER_TICK}. When the battery reaches zero, forces the
     * light off via {@link MouseApprentice#setLightForcedOff(boolean)}.
     *
     * <p>Architecture role: Called by the game loop in {@link GameStarter} on every
     * tick while the local role is Apprentice. The battery value is transmitted in
     * each {@link Protocol#LIGHT_BATTERY} message so the Wanderer client's HUD can
     * display it. The forced-off interlock prevents the Apprentice from immediately
     * re-toggling after the battery empties by requiring them to charge back up to a
     * threshold before pressing F again.</p>
     *
     * @param lightActive whether the Apprentice's light toggle is currently on
     * @param lightRadius the current radius in pixels; controls the drain rate
     */
    public void updateBattery(boolean lightActive, int lightRadius) {                    // Called each tick by GameStarter (Apprentice only); drives drain/regen and forced-off interlock
        if (!lightActive) {                                                               // Light is off — recharge mode
            lightBattery = Math.min(MAX_BATTERY, lightBattery + REGEN_PER_TICK);         // Recharge at REGEN_PER_TICK; cap at MAX_BATTERY to avoid overflow
            return;                                                                       // No further processing needed when light is off
        }
        float drain = getDrainRate(lightRadius);                                          // Determine the correct drain rate based on the current radius tier
        lightBattery = Math.max(0f, lightBattery - drain);                               // Drain battery by the tier rate; floor at 0 to prevent negative values
        if (lightBattery <= 0f) {                                                         // Battery just hit empty
            lightBattery = 0f;                                                            // Explicitly set to zero; avoids floating-point rounding producing -epsilon values
            // Force light off to prevent oscillation — only the F key can turn it back on
            MouseApprentice.getInstance().setLightForcedOff(true);                       // Interlock: set forcedOff so the light stays off until the Apprentice presses F again
        }
    }

    /**
     * Returns the effective brightness factor for the light circle given the current
     * radius. Larger radius = higher brightness (less dark overlay inside the circle).
     *
     * <p>The formula maps radius [20, 180] to brightness [0.15, 1.0], where 0.15 is
     * the dimness at minimum radius (barely illuminated) and 1.0 is full brightness
     * at maximum radius.</p>
     *
     * <p>Interaction: Called by {@link LightRenderer} to scale the inner alpha of the
     * darkness overlay mask. A brightness of 1.0 means the lit area is fully clear
     * of darkness; 0.15 means it is only dimly illuminated.</p>
     *
     * @param radius the current light radius in pixels; expected range 20–180
     * @return the brightness factor in [0.15, 1.0]
     */
    public float getLightBrightness(int radius) {                               // Maps radius to a brightness factor; used by LightRenderer to scale inner-circle darkness
        float normalized = (float)(radius - 20) / (float)(180 - 20);           // Normalise radius to [0.0, 1.0]: (r - min) / (max - min)
        return 0.15f + (normalized * 0.85f);                                    // Scale to [0.15, 1.0]: 0.15 at min radius, 1.0 at max radius
    }
}
