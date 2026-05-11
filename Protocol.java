/**
 * Defines the complete set of string-based message tokens used in the pipe-delimited
 * text protocol exchanged between Lumen Architect clients through the server relay.
 * Each public constant is the first {@code '|'}-separated field in a message line;
 * the remaining fields carry type-specific payload data.
 *
 * <p>Architecture role: {@code Protocol} is the single authoritative source of truth
 * for all message-type identifiers. Both {@link GameStarter} (client-side dispatch)
 * and {@link GameServer} (server-side relay and validation) compare incoming message
 * tokens against these constants. Keeping all tokens here avoids magic-string bugs
 * when protocol messages are parsed or constructed elsewhere in the codebase.</p>
 *
 * <p>Message transport: Every string message is wrapped in a
 * {@link NetworkProtocol.StringPacket} before being written to the
 * {@link java.io.ObjectOutputStream}; the server relays every such packet to both
 * connected clients without inspecting its content.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-11
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 4c "Networking in Java" - object-stream serialization across the
//                                   Socket - the typed-stream variant of
//                                   the DataInputStream / DataOutputStream
//                                   pattern from 4c.
// Module 1d "Inner Classes"      - packet types are public static nested
//                                   classes - the static-nested-class
//                                   pattern shown in 1d.
// Module 1a "Modifiers"          - public final fields on packet classes;
//                                   classes implement Serializable; private
//                                   static final serialVersionUID constants.
// =========================================================================
public class Protocol {

    /**
     * Private constructor prevents instantiation. {@code Protocol} is a pure constants
     * namespace; all fields are accessed statically.
     */
    private Protocol() {} // Namespace class: never instantiated; all access is static

    // =========================================================================
    // Core game-state messages
    // =========================================================================

    /**
     * Wanderer → server (relayed to Apprentice): periodic position/state update.
     * Format: {@code POS|WANDERER|x|y|vx|vy|animState}
     * Fields: x/y are world-space integers; vx/vy are float velocities (encoded as
     * strings); animState is the active clip key (e.g. "idle", "run").
     */
    public static final String PLAYER_POS    = "POS"; // Position update sent ~60 times/second from the Wanderer client

    /**
     * Apprentice → server: request to place a block at the specified world position.
     * Format: {@code PLACE|type|x|y}
     * The server validates budget and relays a {@link #BLOCK_ADDED} confirmation.
     */
    public static final String PLACE_BLOCK   = "PLACE"; // Apprentice block-placement request; server validates budget before confirming

    /**
     * Apprentice → server: request to remove the block at the specified world position.
     * Format: {@code REMOVE|x|y}
     * The server relays a {@link #BLOCK_REMOVED} confirmation after removing the entity.
     */
    public static final String REMOVE_BLOCK  = "REMOVE"; // Apprentice block-removal request; server confirms removal to both clients

    /**
     * Apprentice → server (relayed to both): light-source position teleport update.
     * Format: {@code LIGHT|x|y|radius}
     * Used in ACT1/ACT2/ACT3 where the Apprentice directly controls light position.
     * Superseded by {@link #LIGHT_TARGET} in the BOSS phase where inertia physics apply.
     */
    public static final String LIGHT_UPDATE  = "LIGHT"; // Teleport-style light update for non-boss phases; replaced by LT in BOSS

    /**
     * Apprentice → server: signals the level setup is complete and the Wanderer may enter.
     * Format: {@code READY}
     * No payload. The server flips {@link NetworkProtocol.ServerStatePacket#levelReady}
     * to {@code true} in its next broadcast so the Wanderer client un-pauses.
     */
    public static final String LEVEL_READY   = "READY"; // Apprentice readiness signal; gates the Wanderer's entry into a newly loaded level

    /**
     * Apprentice → server: boss-phase attack activation request.
     * Format: {@code ATTACK|type|x|y}
     * The server validates the architect-override gate and per-type cooldown before
     * broadcasting {@link #BOSS_ATK} to both clients.
     */
    public static final String ATTACK        = "ATTACK"; // Apprentice attack-spawn request; server cooldown check before relaying as BATK

    /**
     * Generic entity state update relayed between clients.
     * Format: {@code STATE|entityId|key|value}
     * Used for ad-hoc entity property synchronisation that doesn't warrant a dedicated
     * packet type.
     */
    public static final String STATE_UPDATE  = "STATE"; // Ad-hoc entity property sync; used for properties without a dedicated packet type

    /**
     * Server → both clients: confirms that a block has been successfully placed.
     * Format: {@code BLOCK_ADD|type|x|y}
     * Both clients instantiate a new {@link Platform} at the given position.
     */
    public static final String BLOCK_ADDED   = "BLOCK_ADD"; // Server confirmation of block placement; both clients add the Platform entity

    /**
     * Server → both clients: confirms that a block has been successfully removed.
     * Format: {@code BLOCK_REM|x|y}
     * Both clients deactivate the matching {@link Platform} entity.
     */
    public static final String BLOCK_REMOVED = "BLOCK_REM"; // Server confirmation of block removal; both clients deactivate the matching entity

    /**
     * Server → both clients: re-syncs the light-source position.
     * Format: {@code LIGHT_SYNC|x|y|radius}
     * Sent periodically to correct any drift between the Apprentice's local light state
     * and the server's authoritative value.
     */
    public static final String LIGHT_SYNC    = "LIGHT_SYNC"; // Periodic light-source resync to correct client drift

    /**
     * Server → both clients: instructs clients to unload the current level and load a new one.
     * Format: {@code LEVEL|levelIndex}
     * Clients call {@link LevelLoader#loadLevel(int)} with the provided index.
     */
    public static final String LEVEL_CHANGE  = "LEVEL"; // Level-transition command; both clients reload their entity lists from the new level file

    /**
     * Server → both clients: instructs clients to deactivate all Apprentice-placed blocks.
     * Format: {@code CLEAR_BLOCKS}
     * No payload. Sent at level transitions to prevent carry-over of placed platforms.
     */
    public static final String CLEAR_BLOCKS  = "CLEAR_BLOCKS"; // Sweep-clear of all placed blocks between levels

    // P9.3' — CRAWLER_UPDATE removed. DarkCrawler was deleted; the new hazard
    // suite (CorruptedSpike / CorruptedWall / PhantomBlock / LightLockedMover)
    // is locally driven on each client and needs no AI position relay over
    // the wire.

    /**
     * Client → server: acknowledgement that the local user has reached the final panel
     * of the active cutscene and this side is ready to resume gameplay.
     * Format: {@code CUT_ACK|<cutsceneId>}
     * The server holds the session in {@link LevelState.GamePhase#CUTSCENE} until both
     * slots have sent {@code CUT_ACK} for the same cutscene ID, or a 30 s auto-advance
     * guard expires, before broadcasting the end-of-cutscene resume.
     */
    public static final String CUTSCENE_ACK = "CUT_ACK"; // Lock-step cutscene sync: server awaits both CUT_ACKs before resuming gameplay

    // =========================================================================
    // Boss-arena transition protocol (P8.2)
    // =========================================================================

    /**
     * Wanderer → server: the Wanderer has stepped into the portal on the final regular
     * level and the session should transition into the boss arena.
     * Format: {@code BOSS_ENTER}
     * On receipt the server generates a deterministic arena seed, broadcasts
     * {@link #BOSS_ARENA} to both clients, then triggers the opening cutscene via the
     * P8.0 lock-step path before gameplay resumes in the generated arena.
     */
    public static final String BOSS_ENTER = "BOSS_ENTER"; // Wanderer portal-touch trigger; server responds with BOSS_ARENA seed broadcast

    /**
     * Server → both clients: authoritative arena-layout seed for the boss phase.
     * Format: {@code BOSS_ARENA|<seed>}
     * Both clients feed the seed into {@link BossArenaGenerator#generate(long)} to
     * produce an identical list of entities (Cores, throne, platforms) without the
     * server needing to serialise the entire element list over the wire.
     */
    public static final String BOSS_ARENA = "BOSS_ARENA"; // Seed broadcast for deterministic arena generation on both clients simultaneously

    /**
     * Apprentice → server: throttled LightBall target-position update during the BOSS phase.
     * Format: {@code LT|x|y}
     * Coordinates are world-space boss-arena values pre-transformed through
     * {@link Camera#screenToWorldX(int)}. Sent at ~20 Hz (every third client tick) to
     * avoid saturating the relay; the LightBall's inertia smooths motion between samples.
     * In ACT1/ACT2/ACT3 the legacy {@link #LIGHT_UPDATE} teleport path is used instead.
     */
    public static final String LIGHT_TARGET = "LT"; // Physics-based light targeting for BOSS phase; lower frequency than LIGHT_UPDATE

    // =========================================================================
    // Boss attack dispatcher protocol (P8.5)
    // =========================================================================

    /**
     * Server → both clients: authoritative boss-attack spawn broadcast.
     * Format: {@code BATK|<type>|<x>|<y>|<spawnMs>}
     * Emitted after the server validates an Apprentice {@link #ATTACK} request through
     * the architect-override gate and per-type cooldown check. Both clients instantiate
     * the matching {@link BossAttack} subclass locally so the attack renders and,
     * on the Wanderer side, applies damage. Valid type tokens:
     * {@code SEARING_BEAM}, {@code BLOCK_RAIN}, {@code CRUSHER}, {@code SPIKE_ARRAY},
     * {@code SHIELD}.
     */
    public static final String BOSS_ATK = "BATK"; // Server-validated attack spawn; both clients mirror the entity instantiation

    /**
     * Server → both clients: notification that a Core has absorbed damage.
     * Format: {@code CDMG|<coreIdx>}
     * Sent alongside a {@link NetworkProtocol.CoreStatePacket} carrying authoritative
     * new health values. Clients use this to trigger HUD flashes and hit-confirm SFX.
     * When the damage destroys a Core, the server also triggers the corresponding
     * {@link CutsceneID#CORE_1_DESTROYED} etc. cutscene via the lock-step path.
     */
    public static final String CORE_DAMAGED = "CDMG"; // Per-hit Core damage notification; drives HUD flash and SFX independent of health update

    // =========================================================================
    // Lobby protocol
    // =========================================================================

    /**
     * Client → server: the cursor is hovering over a role card in the lobby UI.
     * Format: {@code LOBBY_HOVER|role}
     * The server includes this hover state in the next {@link #LOBBY_STATE} broadcast
     * so other clients can render the hover highlight.
     */
    public static final String LOBBY_HOVER   = "LOBBY_HOVER"; // Hover hint for lobby UI; purely cosmetic, drives the role-card highlight animation

    /**
     * Client → server: the client is requesting the specified role.
     * Format: {@code LOBBY_SELECT|role}
     * The server confirms or rejects based on availability and sends {@link #LOBBY_START}
     * on success.
     */
    public static final String LOBBY_SELECT  = "LOBBY_SELECT"; // Role selection request; server validates availability and assigns the role

    /**
     * Server → both clients: current snapshot of lobby selection state.
     * Format: {@code LOBBY_STATE|wandererTaken|apprenticeTaken|wandererHover|apprenticeHover}
     * Broadcast whenever a client hovers or selects a card so both UIs stay in sync.
     */
    public static final String LOBBY_STATE   = "LOBBY_STATE"; // Full lobby-state broadcast; both clients refresh their UI on receipt

    /**
     * Server → client: confirms which role the receiving client has been assigned.
     * Format: {@code LOBBY_START|role}
     * The client calls {@link GameSession#setRole(String)} and transitions to the game.
     */
    public static final String LOBBY_START   = "LOBBY_START"; // Role assignment confirmation; client begins its role-specific startup sequence

    /**
     * Client → server: cancels the client's current role selection.
     * Format: {@code LOBBY_CANCEL|role}
     * The server marks the role vacant again and broadcasts a new {@link #LOBBY_STATE}.
     */
    public static final String LOBBY_CANCEL  = "LOBBY_CANCEL"; // Role de-selection; allows the client to choose a different role before the game starts

    // =========================================================================
    // Session persistence / reconnect protocol
    // =========================================================================

    /**
     * Server → surviving client: their partner has disconnected.
     * Format: {@code PARTNER_DC|role}
     * The surviving client enters a waiting state and displays a reconnect countdown.
     */
    public static final String PARTNER_DISCONNECTED = "PARTNER_DC"; // Disconnect notification; surviving client shows reconnect UI

    /**
     * Server → surviving client: their partner has rejoined.
     * Format: {@code PARTNER_RC|role}
     * The surviving client dismisses the reconnect UI and resumes normal gameplay.
     */
    public static final String PARTNER_RECONNECTED  = "PARTNER_RC"; // Reconnect confirmation; both clients resume gameplay after snapshot restore

    /**
     * Server → surviving client: the 5-minute reconnect window has expired.
     * Format: {@code SESSION_EXP}
     * The surviving client is returned to the main menu; the session is torn down.
     */
    public static final String SESSION_EXPIRED      = "SESSION_EXP"; // Reconnect timeout notification; game session is terminated for both sides

    /**
     * Server → surviving client: remaining reconnect window time.
     * Format: {@code RC_TIMER|secondsRemaining}
     * Sent every second while a partner is disconnected so the client can show a
     * countdown timer in its UI.
     */
    public static final String RECONNECT_TIMER      = "RC_TIMER"; // Periodic countdown broadcast; client displays remaining reconnect time

    /**
     * Server → reconnecting client: full scalar game state snapshot header.
     * Format: {@code SNAP_BEGIN|level|act|wandX|wandY|wandHealth|wandLives|lightBattery
     *          |lightX|lightY|lightRadius|lightActive|blockBudget|blockType}
     * Followed by one {@link #SNAPSHOT_BLOCK} message per placed block, then
     * {@link #SNAPSHOT_END}. The reconnecting client restores its entire game state
     * from this sequence without needing the server to re-simulate.
     */
    public static final String SNAPSHOT_BEGIN       = "SNAP_BEGIN"; // Snapshot header carrying all scalar state for the reconnecting client

    /**
     * Server → reconnecting client: one placed block in the session snapshot.
     * Format: {@code SNAP_BLOCK|type|x|y}
     * Sent once per placed block between {@link #SNAPSHOT_BEGIN} and
     * {@link #SNAPSHOT_END}.
     */
    public static final String SNAPSHOT_BLOCK       = "SNAP_BLOCK"; // Per-block snapshot entry; client adds a Platform entity for each received message

    /**
     * Server → reconnecting client: signals the snapshot sequence is complete.
     * Format: {@code SNAP_END}
     * The client finalises its restored state and sends a ready signal.
     */
    public static final String SNAPSHOT_END         = "SNAP_END"; // End-of-snapshot sentinel; client begins normal game loop after receiving this

    /**
     * Server → reconnecting lobby client: which role slot is vacant and available.
     * Format: {@code LOBBY_VACANT|role}
     * Sent when a new client connects to a paused-and-waiting session so it knows
     * which role to claim.
     */
    public static final String LOBBY_VACANT         = "LOBBY_VACANT"; // Vacant-role advertisement to guide a fresh reconnect through role selection

    /**
     * Server → both clients: instructs both clients to pause all gameplay updates.
     * Format: {@code GAME_PAUSE}
     * Both clients freeze their game loops and display a reconnect waiting overlay.
     */
    public static final String GAME_PAUSE           = "GAME_PAUSE"; // Gameplay pause command; both clients suspend their update loops

    /**
     * Server → both clients: instructs both clients to resume normal gameplay.
     * Format: {@code GAME_RESUME}
     * Sent once both clients are confirmed connected and state has been restored.
     */
    public static final String GAME_RESUME          = "GAME_RESUME"; // Gameplay resume command; both clients restart their update loops

    // =========================================================================
    // Pre-boss altar protocol (P8.6)
    // =========================================================================

    /**
     * Server → both clients: an altar trigger on level 10 is awaiting the Wanderer's choice.
     * Format: {@code ALTAR_OPEN|<altarId>}
     * Both clients display the altar-selection overlay; only the Wanderer's UI has
     * clickable options.
     */
    public static final String ALTAR_OPEN   = "ALTAR_OPEN"; // Altar overlay trigger; both clients enter the altar UI state

    /**
     * Wanderer → server: the Wanderer has made a pre-boss altar selection.
     * Format: {@code ALTAR_CHOICE|<altarId>|<choice>}
     * {@code choice} is either {@code POWER_SURGE} (max HP +10) or
     * {@code SIGHT_RESTRICTION} (lives +1, boss light radius −25%). The server validates
     * and echoes back as {@link #ALTAR_RESULT}.
     */
    public static final String ALTAR_CHOICE = "ALTAR_CHOICE"; // Wanderer altar selection; server validates choice before broadcasting the result

    /**
     * Server → both clients: authoritative altar outcome applied to both clients.
     * Format: {@code ALTAR_RESULT|<altarId>|<choice>}
     * Both clients apply the mechanical effect; the Wanderer client also dismisses the
     * overlay. Carried as a {@link NetworkProtocol.AltarChoicePacket} for robustness.
     */
    public static final String ALTAR_RESULT = "ALTAR_RESULT"; // Confirmed altar result; both clients apply the mechanical buff on receipt

    // =========================================================================
    // Stun minigame protocol (P8.8)
    // =========================================================================

    /**
     * Server → both clients: a stun opportunity window has opened.
     * Format: {@code STUN_OPP|<durationMs>|<faithful>}
     * Probability of the roll passing is {@code 0.1 + 0.08 × faithful}, checked once
     * per {@link #BOSS_ATK} dispatch. The Wanderer client opens the
     * {@link StunMinigame} overlay; the Apprentice client ignores the payload.
     * The window auto-closes after {@code durationMs} regardless of player input.
     */
    public static final String STUN_OPPORTUNITY = "STUN_OPP"; // Stun window open notification; Wanderer shows the sinusoidal timing minigame

    /**
     * Wanderer → server (relayed to both): stun minigame outcome.
     * Format: {@code STUN_RES|<success>}
     * {@code success} is {@code 1} if three consecutive hits landed within the window,
     * {@code 0} otherwise. On success the server suppresses {@link #BOSS_ATK}
     * dispatches for 5000 ms; both clients display a "ARCHITECT STUNNED" banner.
     */
    public static final String STUN_RESULT = "STUN_RES"; // Stun outcome from Wanderer; server suppresses attacks on success for STUN_DURATION_MS

    // =========================================================================
    // Radiant Collapse broadcast (P8.9)
    // =========================================================================

    /**
     * Wanderer → server (relayed to both): Radiant Collapse ability activation.
     * Format: {@code RADIANT_ACTIVE|<activeUntilMs>}
     * Emitted once when the Wanderer's Radiant FSM transitions from CHARGING to ACTIVE.
     * The payload is an absolute {@link System#currentTimeMillis()} timestamp marking
     * the end of the 3-second active window. Both clients stamp this onto
     * {@link Player#setRadiantActiveUntilMs(long)} so the full-arena light-reveal
     * override renders identically on both screens.
     */
    public static final String RADIANT_ACTIVE = "RADIANT_ACTIVE"; // Radiant Collapse activation broadcast; both clients enable the full-arena reveal mask

    /**
     * First packet every client sends immediately after opening a socket to identify
     * itself to the server. Two valid forms:
     * <ul>
     *   <li>{@code RC_HELLO|FRESH} — a brand-new client from the main menu CONNECT
     *       button. Valid during a fresh lobby or as a replacement for a vacant role
     *       in a paused session.</li>
     *   <li>{@code RC_HELLO|RECONNECT|<role>} — an existing JVM attempting to reclaim
     *       its prior role via {@link GameStarter#handleDisconnect}. Valid only when
     *       the server's {@code vacantRole} matches the provided role.</li>
     * </ul>
     * Any socket that fails to send a valid hello within three seconds is closed,
     * preventing ghost connections from consuming a player slot.
     */
    public static final String RECONNECT_HELLO = "RC_HELLO"; // Mandatory handshake; server closes the socket if no valid hello arrives within 3 s
}
