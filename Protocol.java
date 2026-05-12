/**
 * String constants defining the client-server message protocol.
 *
 * @author Marxus Antonio L. Magisa (253602) & Antonio Sebastian B. Pasia (254505)
 * @version May 12, 2026
 *
 * I have not discussed the Java language code in my program
 * with anyone other than my instructor or the teaching assistants
 * assigned to this course.
 *
 * I have not used Java language code obtained from another student,
 * or any other unauthorized source, either modified or unmodified.
 * If any Java language code or documentation used in my program
 * was obtained from another source, such as a textbook or website,
 * that has been clearly noted with a proper citation in the comments
 * of my program.
 */
public class Protocol {

    private Protocol() {}

    public static final String PLAYER_POS    = "POS";

    public static final String PLACE_BLOCK   = "PLACE";

    public static final String REMOVE_BLOCK  = "REMOVE";

    public static final String LIGHT_UPDATE  = "LIGHT";

    public static final String LEVEL_READY   = "READY";

    public static final String ATTACK        = "ATTACK";

    public static final String STATE_UPDATE  = "STATE";

    public static final String BLOCK_ADDED   = "BLOCK_ADD";

    public static final String BLOCK_REMOVED = "BLOCK_REM";

    public static final String LIGHT_SYNC    = "LIGHT_SYNC";

    public static final String LEVEL_CHANGE  = "LEVEL";

    public static final String CLEAR_BLOCKS  = "CLEAR_BLOCKS";

    public static final String CUTSCENE_ACK = "CUT_ACK";

    public static final String BOSS_ENTER = "BOSS_ENTER";

    public static final String BOSS_ARENA = "BOSS_ARENA";

    public static final String LIGHT_TARGET = "LT";

    public static final String BOSS_ATK = "BATK";

    public static final String CORE_DAMAGED = "CDMG";

    public static final String LOBBY_HOVER   = "LOBBY_HOVER";

    public static final String LOBBY_SELECT  = "LOBBY_SELECT";

    public static final String LOBBY_STATE   = "LOBBY_STATE";

    public static final String LOBBY_START   = "LOBBY_START";

    public static final String LOBBY_CANCEL  = "LOBBY_CANCEL";

    public static final String PARTNER_DISCONNECTED = "PARTNER_DC";

    public static final String PARTNER_RECONNECTED  = "PARTNER_RC";

    public static final String SESSION_EXPIRED      = "SESSION_EXP";

    public static final String RECONNECT_TIMER      = "RC_TIMER";

    public static final String SNAPSHOT_BEGIN       = "SNAP_BEGIN";

    public static final String SNAPSHOT_BLOCK       = "SNAP_BLOCK";

    public static final String SNAPSHOT_END         = "SNAP_END";

    public static final String LOBBY_VACANT         = "LOBBY_VACANT";

    public static final String GAME_PAUSE           = "GAME_PAUSE";

    public static final String GAME_RESUME          = "GAME_RESUME";

    public static final String ALTAR_OPEN   = "ALTAR_OPEN";

    public static final String ALTAR_CHOICE = "ALTAR_CHOICE";

    public static final String ALTAR_RESULT = "ALTAR_RESULT";

    public static final String STUN_OPPORTUNITY = "STUN_OPP";

    public static final String STUN_RESULT = "STUN_RES";

    public static final String RADIANT_ACTIVE = "RADIANT_ACTIVE";

    public static final String RECONNECT_HELLO = "RC_HELLO";
}
