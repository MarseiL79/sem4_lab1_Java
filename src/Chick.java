import java.awt.Image;
import javax.swing.ImageIcon;

public class Chick extends Bird {
    private static final Image chickImage = new ImageIcon("src/ChickImage.png").getImage();

    public Chick(int x, int y, long birthTime, long lifetime, int id) {
        super(x, y, 20, 20, chickImage, birthTime, lifetime, id);
    }

    @Override
    public void update(long elapsedTime) {
        x += (int)(Math.random() * 3 - 1);
        y += (int)(Math.random() * 3 - 1);
    }
}
