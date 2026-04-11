/**
 * Defines the contract for any game entity whose state can be serialized and
 * transmitted over a network connection. Implementing classes are responsible for
 * writing and reading their own state using Java object streams.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public interface Networkable {

    /**
     * Serializes and sends the current state of this entity through the provided
     * output stream to a remote peer.
     *
     * @param out the {@link ObjectOutputStream} to write state data to; must not be
     *            {@code null}
     * @throws IOException if an I/O error occurs while writing to the stream
     */
    void sendState(ObjectOutputStream out) throws IOException;

    /**
     * Reads and applies the state of this entity received from a remote peer via the
     * provided input stream.
     *
     * @param in the {@link ObjectInputStream} to read state data from; must not be
     *           {@code null}
     * @throws IOException if an I/O error occurs while reading from the stream
     */
    void receiveState(ObjectInputStream in) throws IOException;
}
