










































import java.awt.*;
import java.util.*;
public class Trigger extends GameElement implements SpriteOverridable {











    private String type;








    private Map<String, Object> params;






    private boolean fired;







    private String spritePath;
























    public Trigger(String type, int x, int y, Map<String, Object> params) {
        super(x, y, 32, 32);
        this.type = type;
        this.params = (params != null) ? params : new HashMap<>();
        this.fired = false;
        this.spritePath = null;
    }

















    @Override
    public void update(long deltaMs) {


    }















    @Override
    public void render(Graphics2D g) {


        SpriteOverridable.tryDrawSprite(g, this, x, y, width, height);
    }






















    public void fire() {
        this.fired = true;
    }
















    public String getType() {
        return type;
    }













    public Map<String, Object> getParams() {
        return params;
    }













    public boolean isFired() {
        return fired;
    }














    @Override
    public void setSpritePath(String path) {
        this.spritePath = path;
    }







    @Override
    public String getSpritePath() {
        return spritePath;
    }
}
