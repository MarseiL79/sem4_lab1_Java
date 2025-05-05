// Bird.java
import java.awt.Graphics;
import java.awt.Image;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;

public abstract class Bird implements IBehaviour, Serializable {
    private static final long serialVersionUID = 1L;

    protected int x, y;           // координаты
    protected int width, height;  // размеры для отрисовки

    // Изображение — transient, чтобы оно не шло в сериализацию
    protected transient Image image;

    // Новые поля
    protected long birthTime;     // время рождения (мс)
    protected long lifetime;      // время жизни (мс)
    protected int id;             // уникальный идентификатор

    public Bird(int x, int y, int width, int height,
                Image image, long birthTime, long lifetime, int id) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.image = image;
        this.birthTime = birthTime;
        this.lifetime = lifetime;
        this.id = id;
    }

    // Восстанавливаем image после десериализации
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        loadImage();
    }

    /** Каждый подкласс знает, из какого файла подгружать своё изображение */
    protected abstract void loadImage();

    // Абстрактный метод обновления состояния (движения)
    public abstract void update(long elapsedTime);

    // Отрисовка
    @Override
    public void draw(Graphics g) {
        if (image != null) {
            g.drawImage(image, x, y, width, height, null);
        }
    }
}
