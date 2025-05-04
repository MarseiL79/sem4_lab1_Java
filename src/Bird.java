import java.awt.Graphics;
import java.awt.Image;

public abstract class Bird implements IBehaviour {
    protected int x, y;           // координаты объекта
    protected int width, height;  // размеры для отрисовки изображения
    protected Image image;        // изображение птицы

    // Новые поля
    protected long birthTime;     // время рождения (мс)
    protected long lifetime;      // время жизни (мс)
    protected int id;             // уникальный идентификатор

    // Изменённый конструктор: добавлены birthTime, lifetime и id
    public Bird(int x, int y, int width, int height, Image image, long birthTime, long lifetime, int id) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.image = image;
        this.birthTime = birthTime;
        this.lifetime = lifetime;
        this.id = id;
    }

    // Абстрактный метод обновления состояния (движения) объекта
    public abstract void update(long elapsedTime);

    // Отрисовка изображения
    @Override
    public void draw(Graphics g) {
        if (image != null) {
            g.drawImage(image, x, y, width, height, null);
        }
    }
}
