














































public class Camera {







    public static final float CAMERA_LERP = 0.1f;






    public static final int ARENA_W = 3072;





    public static final int ARENA_H = 2304;

    private static final Camera INSTANCE = new Camera();











    public static Camera getInstance() { return INSTANCE; }

    private float worldX;
    private float worldY;
    private float targetX;
    private float targetY;

    private Camera() {
        reset();
    }











    public void reset() {
        this.worldX = ARENA_W / 2f;
        this.worldY = ARENA_H / 2f;
        this.targetX = worldX;
        this.targetY = worldY;
    }















    public void snapTo(float x, float y) {
        this.worldX = x;
        this.worldY = y;
        this.targetX = x;
        this.targetY = y;
        clamp();
    }













    public void follow(Player p) {
        if (p == null) return;
        this.targetX = p.getX();
        this.targetY = p.getY();
    }













    public void followPoint(float x, float y) {
        this.targetX = x;
        this.targetY = y;
    }















    public void update(float dt) {
        worldX += (targetX - worldX) * CAMERA_LERP;
        worldY += (targetY - worldY) * CAMERA_LERP;
        clamp();
    }










    private void clamp() {
        float halfW = GameCanvas.CANVAS_WIDTH / 2f;
        float halfH = GameCanvas.CANVAS_HEIGHT / 2f;
        if (worldX < halfW) worldX = halfW;
        if (worldY < halfH) worldY = halfH;
        if (worldX > ARENA_W - halfW) worldX = ARENA_W - halfW;
        if (worldY > ARENA_H - halfH) worldY = ARENA_H - halfH;
    }






    public float getWorldX() { return worldX; }






    public float getWorldY() { return worldY; }














    public float getRenderOffsetX() {
        return -worldX + GameCanvas.CANVAS_WIDTH / 2f;
    }







    public float getRenderOffsetY() {
        return -worldY + GameCanvas.CANVAS_HEIGHT / 2f;
    }











    public int screenToWorldX(int sx) {
        return Math.round(sx + worldX - GameCanvas.CANVAS_WIDTH / 2f);
    }











    public int screenToWorldY(int sy) {
        return Math.round(sy + worldY - GameCanvas.CANVAS_HEIGHT / 2f);
    }











    public int worldToScreenX(int wx) {
        return Math.round(wx - worldX + GameCanvas.CANVAS_WIDTH / 2f);
    }








    public int worldToScreenY(int wy) {
        return Math.round(wy - worldY + GameCanvas.CANVAS_HEIGHT / 2f);
    }
}
