/**
 * Defines the contract for any game entity whose state can be serialized and
 * transmitted over a network connection. Implementing classes are responsible for
 * writing and reading their own state using Java object streams.
 *
 * <p>Architecture role: This interface is the backbone of the authoritative-server
 * design. {@link GameServer} ticks at 60 fps and calls {@code sendState} on each
 * Networkable to push updates to both clients, while each client calls
 * {@code receiveState} to apply incoming server packets to local objects such as
 * {@link Player}, {@link LevelState}, and {@link SessionSnapshot}.</p>
 *
 * <p>Implementors must keep their read/write field order strictly in sync; a
 * mismatch between sender and receiver will corrupt the stream and cause
 * deserialization errors that are hard to trace at runtime.</p>
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
// Module 1b "Interfaces"          - this file IS the canonical interface
//                                   declaration pattern: a contract of
//                                   abstract method signatures with no body
//                                   and no fields. Implementing classes
//                                   provide the bodies via the implements
//                                   keyword.
// Module 4c "Networking in Java"  - the contract revolves around
//                                   ObjectOutputStream / ObjectInputStream,
//                                   the typed-stream pattern shown in the
//                                   networking module for sending/receiving
//                                   Java objects across a Socket.
// Module 1a "Modifiers"           - all interface methods are implicitly
//                                   public, matching the modifiers module's
//                                   teaching that interface members carry
//                                   the most permissive visibility by default.
// =========================================================================

import java.io.*;
/**
 * Contract that any game object must satisfy in order to participate in the
 * client-server state-synchronisation cycle. Keeping this as a narrow two-method
 * interface (rather than forcing full {@link java.io.Serializable}) lets each
 * implementor decide exactly which fields cross the wire, avoiding accidental
 * transmission of heavyweight references like Swing components.
 *
 * <p>Architecture role: Every authoritative simulation object that needs to
 * mirror its state on the other side of the network implements this interface.
 * {@link GameServer} iterates over all Networkable entities once per 60 Hz tick,
 * calling {@code sendState} on each, and clients call {@code receiveState} to
 * overwrite local copies with server-authoritative values.</p>
 */
public interface Networkable { // Interface — no inheritance; any class can implement to participate in network sync

    /**
     * Serializes and sends the current state of this entity through the provided
     * output stream to a remote peer.
     *
     * <p>Called by {@link GameServer} once per tick for every Networkable it owns
     * (e.g. the server-side {@link Player} instance, {@link LevelState}) so that
     * both connected clients stay in sync with the authoritative simulation.
     * Implementors should write fields in a fixed, documented order so that the
     * matching {@link #receiveState(ObjectInputStream)} call on the client side reads
     * them in the same order.</p>
     *
     * <p>Interaction: {@link GameServer} calls this on all Networkable objects it
     * maintains each tick. {@link GameStarter} (client side) indirectly relies on
     * proper implementation so that {@link NetworkProtocol.ServerStatePacket} fields
     * accurately reflect the authoritative simulation.</p>
     *
     * <p>Do not flush or close {@code out} inside this method — the caller
     * ({@link GameServer}) manages stream lifecycle and flushes after all Networkables
     * have had a chance to write their data for the current tick.</p>
     *
     * @param out the {@link ObjectOutputStream} to write state data to; must not be
     *            {@code null}
     * @throws IOException if an I/O error occurs while writing to the stream (e.g.
     *         the client socket was closed mid-tick)
     */
    void sendState(ObjectOutputStream out) throws IOException; // Write this object's mutable fields to the outbound network stream so the remote peer can reconstruct the state

    /**
     * Reads and applies the state of this entity received from a remote peer via the
     * provided input stream.
     *
     * <p>Called by the client-side game loop (inside {@link GameStarter}) every time
     * the server pushes a new state packet. The method must read exactly as many bytes
     * as {@link #sendState(ObjectOutputStream)} wrote for this object in the same tick,
     * otherwise the stream position drifts and subsequent packets are misread.</p>
     *
     * <p>Interaction: {@link GameStarter} calls this on local entity mirrors after
     * receiving a {@link NetworkProtocol.ServerStatePacket}. For example,
     * {@link Player#receiveState} reads x, y, velocityX, velocityY, health, etc.
     * from the server-authored values and overwrites local copies so the client
     * render always reflects authoritative state, not locally predicted state.</p>
     *
     * @param in the {@link ObjectInputStream} to read state data from; must not be
     *           {@code null}
     * @throws IOException if an I/O error occurs while reading from the stream (e.g.
     *         the server socket was closed or the data was truncated)
     */
    void receiveState(ObjectInputStream in) throws IOException; // Read exactly the fields sendState wrote and apply them to this object's mutable state
}
