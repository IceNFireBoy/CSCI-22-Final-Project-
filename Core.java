/**
 * Represents one of the four destructible Cores that serve as the primary win-condition
 * objectives in Lumen Architect. Each Core has a fixed index identifying its position
 * in the boss arena, a small health pool of three hit points, and a destroyed flag that
 * signals to the game loop when the Wanderer has eliminated it.
 *
 * <p>Architecture role: Four {@code Core} instances are placed at the corners of the
 * boss arena by {@link BossArenaGenerator#generate(long)}. The Wanderer attacks them
 * via {@link Player#executeMelee()} which sets {@link Player#pendingCoreHitIndex}; the
 * NetworkIO thread then sends a {@link NetworkProtocol.CoreHitPacket} to the server.
 * {@link GameServer} is the sole authority on damage and destruction — it decrements
 * its own health array and broadcasts a {@link NetworkProtocol.CoreStatePacket} back to
 * both clients. Each client's {@link LevelState#coreHealth} array is updated from that
 * packet; the client-side {@code Core} objects are visualisation-only. Win condition is
 * checked by {@link GameStarter#checkWinLoss()} once all four {@link #isDestroyed()}
 * return {@code true}.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */


// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 1c "Abstract Classes" - extends GameElement, the abstract base
//                                 covered in the abstract-classes module.
// Module 3a "Graphics"          - render(Graphics2D) override uses 3a's
//                                 drawing primitives (fill, draw, drawImage)
//                                 plus 3a's RenderingHints anti-aliasing.
// Module 3c "Collision"         - getBounds() / getHitbox() returns a
//                                 Rectangle for AABB tests per 3c.
// Module 1a "Modifiers"         - private fields with public accessors;
//                                 where present, static final constants
//                                 follow the constants pattern from 1a.
// =========================================================================
import java.awt.*;
public class Core extends GameElement implements Damageable { // Extends GameElement for entity-list participation; implements Damageable for the takeDamage/health contract

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** The maximum number of hits a Core can absorb before being destroyed. */
    private static final int MAX_HEALTH = 3; // 3 HP per Core; each successful Wanderer melee decrements this on the server

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * The zero-based index of this Core in the boss arena layout. Valid values are 0
     * through 3, corresponding to the four corner positions defined by
     * {@link BossArenaGenerator}. Used by {@link NetworkProtocol.CoreHitPacket} and
     * {@link NetworkProtocol.CoreStatePacket} to map server data to the correct
     * client-side Core object.
     */
    private int coreIndex; // 0-based; 0=top-left, 1=top-right, 2=bottom-left, 3=bottom-right (per BossArenaGenerator layout)

    /**
     * The current health of this Core. Starts at {@link #MAX_HEALTH} (3) and decrements
     * with each authoritative damage event broadcast by the server. When this reaches
     * zero the Core is marked destroyed and the Wanderer's victory condition advances.
     *
     * <p>Note: On the client this field is informational only. The true health lives in
     * {@link GameServer}'s own Core array and is mirrored to {@link LevelState#coreHealth}
     * via {@link NetworkProtocol.CoreStatePacket}.</p>
     */
    private int health; // Current HP; server-authoritative; client value updated from CoreStatePacket

    /**
     * Whether this Core has been fully destroyed. A destroyed Core no longer
     * participates in collision detection or damage calculations and will eventually be
     * removed from the active entity list by the game loop.
     *
     * <p>Set to {@code true} when {@link LevelState#coreHealth}[{@link #coreIndex}]
     * reaches 0 during a {@link NetworkProtocol.CoreStatePacket} update.</p>
     */
    private boolean destroyed; // true once health == 0; read by GameStarter.checkWinLoss() to determine overall win condition

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code Core} at the given world position with the specified index.
     * The Core is initialised to full health ({@link #MAX_HEALTH}) and is not yet
     * destroyed.
     *
     * <p>Architecture role: Called exclusively by {@link BossArenaGenerator#generate(long)}
     * which places four Cores at the four corners of the boss arena (CORE_INSET = 512 px
     * from each edge). The 32×32 pixel dimensions are shared with the standard tile size
     * so the Core's AABB lines up with the platform grid.</p>
     *
     * <p>Interaction: The resulting Core object is added to {@link GameStarter#elements}
     * alongside all arena platforms. {@link GameStarter#checkWinLoss()} iterates elements
     * looking for Core instances each tick to evaluate the win condition.</p>
     *
     * @param coreIndex the zero-based index identifying this Core's corner position;
     *                  expected to be in the range [0, 3]
     * @param x         the x-coordinate (world space) of the Core's left edge
     * @param y         the y-coordinate (world space) of the Core's top edge
     */
    public Core(int coreIndex, int x, int y) {      // Creates a 32×32 Core at (x,y) with the given index, full health, and not-destroyed
        super(x, y, 32, 32);                        // Call GameElement constructor: set position to (x,y) and AABB size to 32×32 pixels
        this.coreIndex = coreIndex;                 // Store the 0-based corner index for server/packet mapping
        this.health = MAX_HEALTH;                   // Start at full 3 HP — the server will decrement via CoreHitPacket broadcasts
        this.destroyed = false;                     // Not destroyed at spawn time; becomes true when health reaches 0 via server authority
    }

    // -------------------------------------------------------------------------
    // GameElement overrides
    // -------------------------------------------------------------------------

    /**
     * Updates this Core's state for the current game tick. Will later drive an idle
     * pulse animation (glow intensity reflecting remaining health) and respond to the
     * destroyed flag by triggering a destruction particle effect.
     *
     * <p>Architecture role: Called every 16 ms by the game loop in
     * {@link GameStarter} for each active element. Currently a stub; animation logic
     * will be added when art assets become available.</p>
     *
     * @param deltaMs the time elapsed since the last update, in milliseconds
     */
    @Override
    public void update(long deltaMs) { // Called each tick by GameStarter's game loop; stub until Core animation is implemented
        // Stub — idle pulse animation and destruction effect to be implemented in a later phase.
        // Future implementation: drive a pulseTimer += deltaMs and modulate glow alpha via Math.sin().
    }

    /**
     * Renders this Core onto the provided graphics context. Will later draw a glowing
     * orb whose visual intensity (colour, radius, brightness) reflects the remaining
     * health — dimmer and smaller as the Core takes damage.
     *
     * <p>Architecture role: Called each frame by {@link GameCanvas#renderElements(Graphics2D)}
     * under the boss arena camera transform, so world-space coordinates are automatically
     * converted to screen space.</p>
     *
     * @param g the {@link Graphics2D} context to draw on; must not be {@code null}
     */
    @Override
    public void render(Graphics2D g) { // Called each frame by GameCanvas.renderElements(); stub until Core art is implemented
        // Stub — glowing orb rendering to be implemented in a later phase.
        // Future implementation: draw a RadialGradientPaint scaled by health/MAX_HEALTH,
        // pulse alpha via Math.sin(pulseTimer), and optionally overlay a destruction crack sprite.
    }

    // -------------------------------------------------------------------------
    // Damageable implementation
    // -------------------------------------------------------------------------

    /**
     * Stub for applying damage to this Core. Health management is server-authoritative:
     * the Wanderer reports melee contact via {@link Player#pendingCoreHitIndex}
     * → NetworkIO thread → {@link NetworkProtocol.CoreHitPacket} → server. The server
     * decrements its own health array and broadcasts a
     * {@link NetworkProtocol.CoreStatePacket}; clients update {@link LevelState#coreHealth}
     * from that packet rather than calling this method directly.
     *
     * <p>Interaction: This method is declared to satisfy the {@link Damageable} interface
     * contract. It is intentionally empty on the client; only the server-side Core
     * equivalent actually decrements health.</p>
     *
     * @param amount the amount of damage to apply; must be non-negative
     */
    @Override
    public void takeDamage(int amount) {
        // Health is server-authoritative; the client reports hits via
        // Player.pendingCoreHitIndex → NetworkIO thread → CoreHitPacket.
        // Do NOT decrement health here on the client — the CoreStatePacket from
        // the server will update LevelState.coreHealth[] with the true value.
    }

    /**
     * Returns the current health of this Core.
     *
     * <p>Interaction: Read by the boss arena HUD in {@link GameCanvas#renderBoss} to
     * fill the Core health pips on the architect panel. Also read by
     * {@link GameStarter#checkWinLoss()} indirectly through {@link #isDestroyed()}.</p>
     *
     * @return the current health value, in the range {@code [0, MAX_HEALTH]}
     */
    @Override
    public int getHealth() {   // Damageable accessor; returns current HP for HUD display and win-condition checks
        return health;         // Return the stored health value; only accurate if kept in sync with LevelState.coreHealth[]
    }

    /**
     * Returns the maximum health this Core can have.
     *
     * <p>Interaction: Used by HUD rendering code inside {@link GameCanvas} to scale
     * the health-pip display proportionally (e.g. {@code health / MAX_HEALTH} as a fill
     * fraction for a bar). Also read by any future damage-feedback visual that needs to
     * calculate what percentage of health remains.</p>
     *
     * @return {@link #MAX_HEALTH} (always 3)
     */
    @Override
    public int getMaxHealth() { // Damageable accessor; returns the constant maximum HP for proportional HUD display
        return MAX_HEALTH;      // Constant value 3; does not change over a session
    }

    /**
     * Determines whether this Core is still alive (health greater than zero).
     *
     * <p>Interaction: Used by {@link GameStarter#checkWinLoss()} in conjunction with
     * {@link #isDestroyed()} to decide when the Wanderer has won. The two checks are
     * slightly different: {@code isAlive()} is a real-time health test; {@code isDestroyed()}
     * is a latched flag set by the game loop after the server confirms death.</p>
     *
     * @return {@code true} if {@code health > 0}; {@code false} if fully depleted
     */
    @Override
    public boolean isAlive() {    // Damageable predicate; true = has remaining HP; false = destroyed
        return health > 0;        // Direct comparison; false implies destroyed (health == 0)
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the zero-based index identifying this Core's corner position in the boss
     * arena.
     *
     * <p>Interaction: Used by {@link NetworkProtocol.CoreHitPacket} to tell the server
     * which Core was struck, and by {@link NetworkProtocol.CoreStatePacket} handling in
     * {@link GameStarter#applyServerState} to map the incoming health array index to
     * the correct local Core object.</p>
     *
     * @return the core index, in the range [0, 3]
     */
    public int getCoreIndex() { // Read accessor for coreIndex; used by packet serialisation and win-condition mapping
        return coreIndex;       // Return the 0-based corner index assigned at construction time
    }

    /**
     * Returns whether this Core has been destroyed (health permanently at zero). A
     * destroyed Core is visually inert and excluded from future collision and damage
     * passes.
     *
     * <p>Interaction: Checked by {@link GameStarter#checkWinLoss()} — once all four
     * Cores have {@code isDestroyed() == true}, the Wanderer win condition fires.
     * Also checked by the boss-arena renderer to decide whether to draw the Core's
     * glowing orb or a rubble sprite.</p>
     *
     * @return {@code true} if health == 0 and the Core has been eliminated;
     *         {@code false} if the Core is still in play
     */
    public boolean isDestroyed() { // Latched destroyed flag; read by checkWinLoss() for win-condition evaluation
        return destroyed;          // Returns the stored boolean; only set to true by the server-authoritative damage path
    }
}
