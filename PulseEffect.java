/**
 * Visual expanding-ring effect played on ability activations and impacts.
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
import java.awt.*;
import java.awt.geom.*;
public class PulseEffect extends GameElement {

    private int radius;

    private int maxRadius;

    private float alpha;

    public PulseEffect(int centreX, int centreY) {
        super(centreX, centreY, 0, 0);
        this.radius = 0;
        this.maxRadius = 200;
        this.alpha = 0.9f;
    }

    @Override
    public void update(long deltaMs) {
        radius += 8;
        alpha -= 0.045f;
        if (alpha <= 0f) {
            alpha = 0f;
            setActive(false);
        }
    }

    @Override
    public void render(Graphics2D g) {
        if (!active || alpha <= 0f) {
            return;
        }

        Composite originalComposite = g.getComposite();
        Stroke originalStroke = g.getStroke();

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setColor(new Color(0xc9, 0xa8, 0x4c));
        g.setStroke(new BasicStroke(1.5f));

        Ellipse2D.Double ring = new Ellipse2D.Double(
                x - radius, y - radius, radius * 2, radius * 2);
        g.draw(ring);

        g.setComposite(originalComposite);
        g.setStroke(originalStroke);
    }

    public int getRadius() {
        return radius;
    }

    public float getAlpha() {
        return alpha;
    }
}
