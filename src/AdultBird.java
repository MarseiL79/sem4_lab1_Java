// AdultBird.java
import java.awt.Image;
import javax.swing.ImageIcon;

public class AdultBird extends Bird {
    private static final long serialVersionUID = 1L;
    private static final String IMAGE_PATH = "src/AdultBird.png";

    public AdultBird(int x, int y, long birthTime, long lifetime, int id) {
        super(x, y, 40, 40,
                new ImageIcon(IMAGE_PATH).getImage(),
                birthTime, lifetime, id);
    }

    @Override
    protected void loadImage() {
        // восстанавливаем картинку после десериализации
        this.image = new ImageIcon(IMAGE_PATH).getImage();
    }

    @Override
    public void update(long elapsedTime) {
        x += (int)(Math.random() * 5 - 2);
        y += (int)(Math.random() * 5 - 2);
    }
}
