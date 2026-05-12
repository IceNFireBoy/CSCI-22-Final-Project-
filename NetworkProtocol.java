/**
 * Defines serializable message types exchanged over the network.
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
import java.io.*;
public class NetworkProtocol {

    private NetworkProtocol() {}

    public static class PlayerStatePacket implements Serializable {

        private static final long serialVersionUID = 14L;

        public int x;

        public int y;

        public int health;

        public String animState;

        public int faithful;

        public PlayerStatePacket(int x, int y, int health, String animState, int faithful) {
            this.x         = x;
            this.y         = y;
            this.health    = health;
            this.animState = animState;
            this.faithful  = faithful;
        }
    }

    public static class CoreStatePacket implements Serializable {

        private static final long serialVersionUID = 3L;

        public int[] health;

        public CoreStatePacket(int[] health) {
            this.health = health;
        }
    }

    public static class VictoryPacket implements Serializable {

        private static final long serialVersionUID = 4L;

        public GameServer.VictoryState result;

        public VictoryPacket(GameServer.VictoryState result) {
            this.result = result;
        }
    }

    public static class FragmentCollectedPacket implements Serializable {

        private static final long serialVersionUID = 6L;

        public String fragmentID;

        public FragmentCollectedPacket(String fragmentID) {
            this.fragmentID = fragmentID;
        }
    }

    public static class StringPacket implements java.io.Serializable {

        private static final long serialVersionUID = 10L;

        public String message;

        public StringPacket(String message) {
            this.message = message;
        }
    }

    public static class CutscenePacket implements Serializable {

        private static final long serialVersionUID = 11L;

        public String cutsceneId;

        public boolean start;

        public CutscenePacket(String cutsceneId, boolean start) {
            this.cutsceneId = cutsceneId;
            this.start      = start;
        }
    }

    public static class CoreHitPacket implements Serializable {

        private static final long serialVersionUID = 9L;

        public int coreIndex;

        public CoreHitPacket(int coreIndex) {
            this.coreIndex = coreIndex;
        }
    }

    public static class RoleAssignmentPacket implements Serializable {

        private static final long serialVersionUID = 7L;

        public String role;

        public RoleAssignmentPacket(String role) {
            this.role = role;
        }
    }

    public static class ServerStatePacket implements Serializable {

        private static final long serialVersionUID = 15L;

        public PlayerStatePacket playerState;

        public CoreStatePacket coreState;

        public GameServer.VictoryState victoryState;

        public boolean architectOverride;

        public boolean levelReady;

        public boolean bothConnected;

        public float lightX;

        public float lightY;

        public ServerStatePacket(PlayerStatePacket playerState,
                                 CoreStatePacket coreState,
                                 GameServer.VictoryState victoryState,
                                 boolean architectOverride,
                                 boolean levelReady,
                                 boolean bothConnected,
                                 float lightX,
                                 float lightY) {
            this.playerState      = playerState;
            this.coreState        = coreState;
            this.victoryState     = victoryState;
            this.architectOverride = architectOverride;
            this.levelReady       = levelReady;
            this.bothConnected    = bothConnected;
            this.lightX           = lightX;
            this.lightY           = lightY;
        }
    }

    public static class AltarChoicePacket implements Serializable {

        private static final long serialVersionUID = 13L;

        public int altarId;

        public String choice;

        public AltarChoicePacket(int altarId, String choice) {
            this.altarId = altarId;
            this.choice  = choice;
        }
    }
}
