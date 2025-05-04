import java.awt.Image;
import javax.swing.ImageIcon;

public class AdultBird extends Bird {
    private static final Image adultImage = new ImageIcon("src/AdultBird.png").getImage();

    public AdultBird(int x, int y, long birthTime, long lifetime, int id) {
        super(x, y, 40, 40, adultImage, birthTime, lifetime, id);
    }

    @Override
    public void update(long elapsedTime) {
        x += (int)(Math.random() * 5 - 2);
        y += (int)(Math.random() * 5 - 2);
    }
}
