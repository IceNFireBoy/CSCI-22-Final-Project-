/**
 * Concealment subtype: the altar is hidden until an Apprentice-placed block
 * sits on a designated pressure-plate location. The plate is identified by an
 * AABB rectangle at construction; the concealment polls
 * {@link GameStarter#getPlacedBlocks()} each query and reports revealed when
 * any placed block overlaps the plate.
 *
 * <p>Architecture role: extends {@link AltarConcealment}; same overlap-gate
 * plumbing as the other subclasses. The plate rectangle is private to this
 * concealment instance so each altar can have its own plate without sharing
 * geometry.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-04-29
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */


// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 1c "Abstract Classes" - extends AltarConcealment, supplying a
//                                 concrete implementation of the abstract
//                                 isRevealed predicate. Direct application
//                                 of the abstract-method-implementation
//                                 pattern from 1c.
// Module 1a "Modifiers"         - private fields with public accessors and
//                                 final constants where applicable.
// =========================================================================
import java.awt.*;
public class PressurePlateConcealment extends AltarConcealment { // Reveals while a placed block overlaps the plate

    private final Rectangle plate; // World-space pressure-plate AABB; final — never mutated after construction

    /**
     * Constructs a pressure-plate concealment. The plate rectangle is the
     * world-space AABB the Apprentice must cover with a placed block to
     * reveal the altar.
     *
     * @param plateX world-space left-edge x in pixels
     * @param plateY world-space top-edge y in pixels
     * @param plateW plate width in pixels
     * @param plateH plate height in pixels
     */
    public PressurePlateConcealment(int plateX, int plateY, int plateW, int plateH) {
        this.plate = new Rectangle(plateX, plateY, plateW, plateH);
    }

     
    public static AltarConcealment of(int x, int y, int w, int h) {
        return new PressurePlateConcealment(x, y, w, h);
    }

    @Override
    public boolean isRevealed(LevelState state, GameStarter context) {
        if (context == null) return false;
        // Any active placed block overlapping the plate reveals the altar.
        // CopyOnWriteArrayList iteration is safe even if the network IO thread
        // is mutating the list mid-tick; we accept a slightly stale view since
        // the next tick re-evaluates anyway.
        for (Platform pb : context.getPlacedBlocks()) {
            if (pb == null || !pb.isActive()) continue;
            if (pb.getBounds().intersects(plate)) return true;
        }
        return false; // No block on the plate this tick — altar stays hidden
    }
}
