// Chick.java
import java.awt.Image;
import javax.swing.ImageIcon;

public class Chick extends Bird {
    private static final long serialVersionUID = 1L;
    private static final String IMAGE_PATH = "src/ChickImage.png";

    public Chick(int x, int y, long birthTime, long lifetime, int id) {
        super(x, y, 20, 20,
                new ImageIcon(IMAGE_PATH).getImage(),
                birthTime, lifetime, id);
    }

    @Override
    protected void loadImage() {
        this.image = new ImageIcon(IMAGE_PATH).getImage();
    }

    @Override
    public void update(long elapsedTime) {
        x += (int)(Math.random() * 3 - 1);
        y += (int)(Math.random() * 3 - 1);
    }
}
