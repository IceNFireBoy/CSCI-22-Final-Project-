










































public class LightBall {











    public static final float BALL_MAX_SPEED = 4.0f;











    public static final float BALL_TURN_SPEED = 0.08f;


    private static final int ARENA_W = GameServer.ARENA_W;


    private static final int ARENA_H = GameServer.ARENA_H;






    private float x;


    private float y;


    private float vx;


    private float vy;


    private float targetX;


    private float targetY;





















    public LightBall(float initialX, float initialY) {
        this.x = clampX(initialX);
        this.y = clampY(initialY);
        this.vx = 0f;
        this.vy = 0f;
        this.targetX = this.x;
        this.targetY = this.y;
    }



















    public void setTarget(float tx, float ty) {
        this.targetX = clampX(tx);
        this.targetY = clampY(ty);
    }






















    public void step() {
        float dx = targetX - x;
        float dy = targetY - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        float desiredVx;
        float desiredVy;
        if (dist > 0.0001f) {
            float invDist = 1f / dist;
            desiredVx = dx * invDist * BALL_MAX_SPEED;
            desiredVy = dy * invDist * BALL_MAX_SPEED;
        } else {
            desiredVx = 0f;
            desiredVy = 0f;
        }

        vx += (desiredVx - vx) * BALL_TURN_SPEED;
        vy += (desiredVy - vy) * BALL_TURN_SPEED;

        x += vx;
        y += vy;

        x = clampX(x);
        y = clampY(y);
    }






    public float getX() { return x; }


    public float getY() { return y; }


    public float getTargetX() { return targetX; }


    public float getTargetY() { return targetY; }


    public float getVelocityX() { return vx; }


    public float getVelocityY() { return vy; }















    public void snapTo(float newX, float newY) {
        this.x = clampX(newX);
        this.y = clampY(newY);
        this.vx = 0f;
        this.vy = 0f;
        this.targetX = this.x;
        this.targetY = this.y;
    }











    private static float clampX(float v) {
        if (v < 0f) return 0f;
        if (v > ARENA_W) return ARENA_W;
        return v;
    }







    private static float clampY(float v) {
        if (v < 0f) return 0f;
        if (v > ARENA_H) return ARENA_H;
        return v;
    }
}
