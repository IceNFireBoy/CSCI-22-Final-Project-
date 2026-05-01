/**
 * Shared data class holding the current state of the pre-game role-selection lobby.
 * Updated on the client by parsing incoming {@link Protocol#LOBBY_STATE} messages and
 * read by {@link GameCanvas#renderLobby} to draw the lobby screen.
 *
 * <p>Architecture role: {@code LobbyState} is a lightweight POJO that acts as the
 * render model for the lobby screen. {@link GameStarter#handleMessage} receives a
 * {@link Protocol#LOBBY_STATE} broadcast from the server and calls
 * {@link #applyMessage(String)} to parse it into this object. {@link GameCanvas#renderLobby}
 * then reads the flags each frame to decide which cards appear "taken", "hovered", or
 * "clickable". By keeping the parse logic here, neither the canvas nor the message
 * handler has to duplicate the field-to-field mapping.</p>
 *
 * <p>The {@link #isReconnectSession} and {@link #vacantRole} fields serve the reconnect
 * lobby path (P7): when a client disconnects and a new one joins mid-session, the server
 * sends a {@link Protocol#LOBBY_VACANT} message to indicate which role is open; the
 * lobby screen then greys out the occupied role card and only allows clicking the
 * vacant one.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-15
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */
public class LobbyState { // Plain data class — no logic beyond applyMessage parsing; public fields for direct read access by GameCanvas

    /**
     * {@code true} when a client has claimed the Wanderer role by sending
     * {@link Protocol#LOBBY_SELECT}|WANDERER to the server. Set via
     * {@link #applyMessage(String)} on every {@link Protocol#LOBBY_STATE} broadcast.
     * Read by {@link GameCanvas#renderLobby} to draw the Wanderer card as "CLAIMED".
     */
    public boolean wandererTaken     = false; // Default false — no one has selected Wanderer yet at lobby entry

    /**
     * {@code true} when a client has claimed the Apprentice role. Symmetric with
     * {@link #wandererTaken}. When both this and {@link #wandererTaken} are true,
     * {@link GameCanvas#renderLobby} shows the "Both roles selected — starting…" hint
     * and {@link GameServer} sends {@link Protocol#LOBBY_START} to both clients.
     */
    public boolean apprenticeTaken   = false; // Default false — no one has selected Apprentice yet at lobby entry

    /**
     * {@code true} when at least one client is hovering the cursor over the Wanderer
     * role card. Sent by the hovering client as {@link Protocol#LOBBY_HOVER}|WANDERER
     * and broadcast to both by the server in the next {@link Protocol#LOBBY_STATE}.
     * Read by {@link GameCanvas#renderLobby} to highlight the card with a gold border.
     */
    public boolean wandererHovered   = false; // Default false — no hover at lobby entry; updated frame-by-frame as the mouse moves

    /**
     * {@code true} when at least one client is hovering over the Apprentice card.
     * Symmetric with {@link #wandererHovered}. Allows both clients to see which role
     * the other player is eyeing even before they click.
     */
    public boolean apprenticeHovered = false; // Default false — symmetric with wandererHovered

    /**
     * {@code true} when a client is joining a session that is already in progress
     * (one player disconnected, the other is waiting in the PAUSED_WAITING state).
     * The lobby screen shows a different UI in this case — only the vacant role card
     * is rendered as selectable; the occupied one shows as greyed-out ACTIVE.
     * Set in {@link GameStarter#handleMessage} on receipt of {@link Protocol#LOBBY_VACANT}.
     */
    public boolean isReconnectSession = false; // Default false — normal fresh lobby; true only when server identifies this as a reconnect-lobby scenario

    /**
     * The role name ({@code "WANDERER"} or {@code "APPRENTICE"}) of the slot that is
     * currently vacant and awaiting a replacement player. {@code null} during a fresh
     * lobby (both slots are open). Set in {@link GameStarter#handleMessage} when a
     * {@link Protocol#LOBBY_VACANT} message arrives. Read by {@link GameCanvas#renderLobby}
     * to suppress hover/click handling on the occupied role card.
     */
    public String vacantRole = null; // null = fresh lobby; "WANDERER" or "APPRENTICE" = one slot already filled by the surviving player

    /**
     * Updates all four boolean flags by parsing a {@link Protocol#LOBBY_STATE} message
     * from the server. Called by {@link GameStarter#handleMessage} on the NetworkIO
     * thread every time the server broadcasts a lobby-state change.
     *
     * <p>Message format: {@code "LOBBY_STATE|wTaken|aTaken|wHovered|aHovered"} where each
     * field is {@code "1"} (true) or {@code "0"} (false). Fields beyond index 4 are
     * silently ignored for forward-compatibility.</p>
     *
     * <p>Architecture role: Centralising the parse logic here keeps
     * {@link GameStarter#handleMessage} short and prevents field-mapping bugs from
     * scattering across both the canvas and the message handler. After this call returns,
     * the next {@link GameCanvas#renderLobby} invocation on the EDT will automatically
     * see the updated flags because the canvas holds a reference to this object.</p>
     *
     * <p>Interaction: Called by {@link GameStarter#handleMessage} (case
     * {@link Protocol#LOBBY_STATE}). The updated object is passed to
     * {@link GameCanvas#setLobbyState(LobbyState)} so the canvas uses the latest values
     * on its next repaint.</p>
     *
     * @param msg the full pipe-delimited message string, e.g.
     *            {@code "LOBBY_STATE|1|0|1|0"}; must not be {@code null}
     */
    public void applyMessage(String msg) {                    // Parse incoming LOBBY_STATE wire message and populate this object's fields
        String[] p = msg.split("\\|");                        // Split on the pipe delimiter to extract the five message tokens
        if (p.length >= 5) {                                  // Guard: only parse if the message has all five expected fields (token[0] is the command name, tokens[1–4] are the flags)
            wandererTaken     = "1".equals(p[1]);             // p[1] = Wanderer-taken flag: "1" → true (role claimed), "0" → false (open)
            apprenticeTaken   = "1".equals(p[2]);             // p[2] = Apprentice-taken flag: "1" → true (role claimed), "0" → false (open)
            wandererHovered   = "1".equals(p[3]);             // p[3] = Wanderer-hovered flag: "1" → true (a client is hovering), "0" → false (no hover)
            apprenticeHovered = "1".equals(p[4]);             // p[4] = Apprentice-hovered flag: "1" → true (a client is hovering), "0" → false (no hover)
        }
        // If the message is malformed (< 5 tokens), all fields retain their previous/default values — safe no-op
    }
}
