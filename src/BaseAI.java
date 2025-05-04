// BaseAI.java
public abstract class BaseAI implements Runnable {
    private Thread thread;
    protected boolean running;      // Флаг, указывающий, работает ли поток
    protected boolean paused;       // Флаг, указывающий, поставлен ли поток в режим ожидания
    protected final Object pauseLock = new Object();  // Объект для синхронизации

    public BaseAI() {
        running = false;
        paused = false;
        thread = new Thread(this);
    }

    // Запуск потока интеллекта
    public void start() {
        running = true;
        thread.start();
    }

    // Метод паузы: поток засыпает
    public void pause() {
        paused = true;
    }

    // Метод для возобновления работы потока
    public void resumeThread() {
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll(); //пробуждаем поток
        }
    }

    // Изменение приоритета потока
    public void setPriority(int priority) {
        thread.setPriority(priority);
    }

    // Главный цикл выполнения потока
    @Override
    public void run() {
        while (running) {
            // Если поток поставлен на паузу, ждём
            synchronized (pauseLock) {
                while (paused) {
                    try {
                        pauseLock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); //безопасно завершаем работу потока
                    }
                }
            }
            // изменить направление движения
            updateAI();

            try {
                Thread.sleep(getDirectionChangePeriod());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // метод для реализации логики интеллекта, который изменяет направление
    protected abstract void updateAI();

    // метод для получения периода переключения направления
    protected abstract long getDirectionChangePeriod();
}
