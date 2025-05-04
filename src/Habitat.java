import java.awt.Graphics;
import java.awt.Dimension;
import java.util.LinkedList;
import java.util.TreeSet;
import java.util.HashMap;
import java.util.Iterator;

public class Habitat {
    // Коллекция для хранения объектов (используем LinkedList)
    public static Dimension areaSize;

    // Параметры генерации для создания новых птиц
    private int n1;         // период для взрослых птиц (мс)
    private double p1;      // вероятность генерации взрослой птицы
    private int n2;         // период для птенцов (мс)
    private int kPercent;   // процентное соотношение птенцов к взрослым

    // Параметры времени жизни для объектов
    private long adultLifetime;
    private long chickLifetime;

    // Время симуляции (мс)
    private long simulationTime;

    // Для управления генерацией по времени
    private long lastAdultTime;
    private long lastChickTime;

    // Вспомогательные коллекции:
    private LinkedList<Bird> birds;
    private TreeSet<Integer> idSet;            // хранит уникальные идентификаторы
    private HashMap<Integer, Long> birthTimeMap; // хранит соответствие id -> время рождения

    // Флаги, используется ли глобальное движение (стаевое)
    private boolean globalAdultActive = true;
    private boolean globalChickActive = true;
    // глобальные углы движения (в радианах) для взрослых и птенцов соответственно
    private double globalAdultAngle = Math.PI / 4; // начальное значение – 45 градусов
    private double globalChickAngle = Math.PI / 4;

    // конструктор
    public Habitat(Dimension areaSize, int n1, double p1, int n2, int kPercent,
                   long adultLifetime, long chickLifetime) {
        this.areaSize = areaSize;
        this.n1 = n1;
        this.p1 = p1;
        this.n2 = n2;
        this.kPercent = kPercent;
        this.adultLifetime = adultLifetime;
        this.chickLifetime = chickLifetime;
        this.birds = new LinkedList<>();
        this.simulationTime = 0;
        this.lastAdultTime = 0;
        this.lastChickTime = 0;
        this.idSet = new TreeSet<>();
        this.birthTimeMap = new HashMap<>();
    }


    public void setGlobalAdultActive(boolean active) {
        globalAdultActive = active;
    }

    public void setGlobalChickActive(boolean active) {
        globalChickActive = active;
    }

    public boolean isGlobalAdultActive() {
        return globalAdultActive;
    }

    public boolean isGlobalChickActive() {
        return globalChickActive;
    }

    public double getGlobalAdultAngle() {
        return globalAdultAngle;
    }

    public double getGlobalChickAngle() {
        return globalChickAngle;
    }

    // обновление глобальных углов движения
    public void updateGlobalAngles() {
        double newAngle = Math.random() * 2 * Math.PI;
        globalAdultAngle = newAngle;
        globalChickAngle = newAngle;
    }

    // генерация уникального идентификатора
    private int generateUniqueId() {
        int id;
        do {
            id = 1000 + (int)(Math.random() * 9000);
        } while (idSet.contains(id));
        return id;
    }

    // обновление симуляции
    public void update(long elapsedTime) {
        simulationTime = elapsedTime;

        // Генерация взрослой птицы
        if (simulationTime - lastAdultTime >= n1) {
            if (Math.random() < p1) {
                int id = generateUniqueId();
                // Создаем взрослую птицу с текущим временем рождения и заданным временем жизни
                AdultBird adult = new AdultBird(randomX(), randomY(), simulationTime, adultLifetime, id);
                birds.add(adult);
                idSet.add(id);
                birthTimeMap.put(id, simulationTime);
            }
            lastAdultTime = simulationTime;
        }

        // Генерация птенца
        if (simulationTime - lastChickTime >= n2) {
            int adultCount = (int) birds.stream().filter(b -> b instanceof AdultBird).count();
            int chickCount = (int) birds.stream().filter(b -> b instanceof Chick).count();
            if (adultCount > 0 && chickCount * 100 < adultCount * kPercent) {
                int id = generateUniqueId();
                Chick chick = new Chick(randomX(), randomY(), simulationTime, chickLifetime, id);
                birds.add(chick);
                idSet.add(id);
                birthTimeMap.put(id, simulationTime);
            }
            lastChickTime = simulationTime;
        }

        // Удаление объектов, время жизни которых истекло
        Iterator<Bird> iter = birds.iterator();
        while (iter.hasNext()) {
            Bird bird = iter.next();
            if (simulationTime - bird.birthTime >= bird.lifetime) {
                iter.remove();
                idSet.remove(bird.id);
                birthTimeMap.remove(bird.id);
            }
        }

        // Обновление движения птиц:
        // Если глобальное движение активно, то применяется глобальный угол,
        // иначе вызывается индивидуальное обновление (метод update())
        for (Bird bird : birds) {
            if (bird instanceof AdultBird && globalAdultActive) {
                // Движение по глобальному углу
                bird.x += (int)(5 * Math.cos(globalAdultAngle));
                bird.y += (int)(5 * Math.sin(globalAdultAngle));
            } else if (bird instanceof Chick && globalChickActive) {
                // Движение по глобальному углу
                bird.x += (int)(3 * Math.cos(globalChickAngle));
                bird.y += (int)(3 * Math.sin(globalChickAngle));
            } else {
                // (хаотичное движение)
                bird.update(elapsedTime);
            }
        }
    }


    public void draw(Graphics g, boolean showTime) {
        for (Bird bird : birds) {
            bird.draw(g);
        }
        if (showTime) {
            g.drawString("Время симуляции: " + simulationTime / 1000.0 + " сек.", 10, 20);
        }
    }


    public void clear() {
        birds.clear();
        idSet.clear();
        birthTimeMap.clear();
    }


    public String getStatistics() {
        int adultCount = (int) birds.stream().filter(b -> b instanceof AdultBird).count();
        int chickCount = (int) birds.stream().filter(b -> b instanceof Chick).count();
        return "Взрослых птиц: " + adultCount + ", птенцов: " + chickCount +
                ", время симуляции: " + simulationTime / 1000.0 + " сек.";
    }


    private int randomX() {
        return (int)(Math.random() * areaSize.width);
    }

    private int randomY() {
        return (int)(Math.random() * areaSize.height);
    }


    public HashMap<Integer, Long> getBirthTimeMap() {
        return birthTimeMap;
    }
}
