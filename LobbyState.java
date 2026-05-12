



































public class LobbyState {







    public boolean wandererTaken     = false;







    public boolean apprenticeTaken   = false;







    public boolean wandererHovered   = false;






    public boolean apprenticeHovered = false;








    public boolean isReconnectSession = false;








    public String vacantRole = null;
























    public void applyMessage(String msg) {
        String[] p = msg.split("\\|");
        if (p.length >= 5) {
            wandererTaken     = "1".equals(p[1]);
            apprenticeTaken   = "1".equals(p[2]);
            wandererHovered   = "1".equals(p[3]);
            apprenticeHovered = "1".equals(p[4]);
        }

    }
}
