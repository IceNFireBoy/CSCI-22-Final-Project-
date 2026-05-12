






























import java.awt.*;
public class PressurePlateConcealment extends AltarConcealment {

    private final Rectangle plate;











    public PressurePlateConcealment(int plateX, int plateY, int plateW, int plateH) {
        this.plate = new Rectangle(plateX, plateY, plateW, plateH);
    }


    public static AltarConcealment of(int x, int y, int w, int h) {
        return new PressurePlateConcealment(x, y, w, h);
    }

    @Override
    public boolean isRevealed(LevelState state, GameStarter context) {
        if (context == null) return false;




        for (Platform pb : context.getPlacedBlocks()) {
            if (pb == null || !pb.isActive()) continue;
            if (pb.getBounds().intersects(plate)) return true;
        }
        return false;
    }
}
