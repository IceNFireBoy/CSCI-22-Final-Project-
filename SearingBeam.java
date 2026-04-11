/**
 * A continuous beam attack directed at the Apprentice's gesture target. Deals 1
 * damage to the Wanderer every 30 ticks when the beam endpoint is within 25 pixels
 * of the player. The beam persists until the gesture is released (duration is
 * effectively infinite while held).
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */


import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;

public class SearingBeam extends BossAttack {

    /** The coordinates the beam is aimed at, updated by the Apprentice's gesture. */
    private Point beamTarget;

    /** Whether the beam can currently deal damage. */
    private boolean damageActive;

    /** Reference to the Wanderer for proximity damage checks. */
    private Player player;

    /** Reference to the audio manager for beam sound effects. */
    private AudioManager audioManager;

    /** Tick counter used to throttle damage to once per 30 ticks. */
    private int damageTick;

    /** The fixed origin x-coordinate of the beam (camera panel left edge centre). */
    private static final int BEAM_ORIGIN_X = 717;

    /** The fixed origin y-coordinate of the beam. */
    private static final int BEAM_ORIGIN_Y = 384;

    /**
     * Constructs a new {@code SearingBeam} aimed at the specified target.
     *
     * @param beamTarget   the initial target coordinates
     * @param player       the Wanderer entity for damage checks
     * @param audioManager the audio manager for SFX
     */
    public SearingBeam(Point beamTarget, Player player, AudioManager audioManager) {
        super(BEAM_ORIGIN_X, BEAM_ORIGIN_Y, 0, 0, Long.MAX_VALUE);
        this.beamTarget = beamTarget;
        this.damageActive = true;
        this.player = player;
        this.audioManager = audioManager;
        this.damageTick = 0;
    }

    /**
     * Updates the beam's damage tick counter and applies damage when the beam
     * target is within 25 pixels of the Wanderer.
     *
     * @param deltaMs the time elapsed since the last tick, in milliseconds
     */
    @Override
    public void update(long deltaMs) {
        // Do NOT call super.update() — duration is Long.MAX_VALUE (continuous)
        if (!active) {
            return;
        }

        damageTick++;

        if (damageActive && player != null && !player.isInvincible()) {
            double dx = beamTarget.x - (player.getX() + 12);
            double dy = beamTarget.y - (player.getY() + 16);
            double dist = Math.sqrt(dx * dx + dy * dy);

            if (dist < 25 && damageTick % 30 == 0) {
                player.takeDamage(1);
                if (audioManager != null) {
                    audioManager.playSFX("sfx_beam_burn");
                }
            }
        }
    }

    /**
     * Renders the beam as a line from the origin to the target with a glow effect.
     *
     * @param g the {@link Graphics2D} context to draw on
     */
    @Override
    public void render(Graphics2D g) {
        if (!active) {
            return;
        }

        Composite originalComposite = g.getComposite();
        Stroke originalStroke = g.getStroke();

        // Glow layer (wider, semi-transparent)
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
        g.setColor(new Color(0xf8, 0xe4, 0xa0));  // #f8e4a0
        g.setStroke(new BasicStroke(8f));
        g.drawLine(BEAM_ORIGIN_X, BEAM_ORIGIN_Y, beamTarget.x, beamTarget.y);

        // Core beam
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g.setColor(new Color(0xf8, 0xe4, 0xa0));
        g.setStroke(new BasicStroke(4f));
        g.drawLine(BEAM_ORIGIN_X, BEAM_ORIGIN_Y, beamTarget.x, beamTarget.y);

        g.setComposite(originalComposite);
        g.setStroke(originalStroke);
    }

    /**
     * Updates the beam's target coordinates.
     *
     * @param target the new target point
     */
    public void setBeamTarget(Point target) {
        this.beamTarget = target;
    }

    /**
     * Sets whether the beam can deal damage.
     *
     * @param damageActive {@code true} to enable damage
     */
    public void setDamageActive(boolean damageActive) {
        this.damageActive = damageActive;
    }
}
