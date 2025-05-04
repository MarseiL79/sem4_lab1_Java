import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;

public class SimulationFrame extends JFrame implements ActionListener, KeyListener {

    private Habitat habitat;
    private Timer timer;
    private long startTime;
    private boolean running;
    private boolean showTime;         // Показывать/скрывать время симуляции
    private boolean showInfoEnabled;  // Показывать диалог статистики

    // UI
    private SimulationPanel simPanel;
    private JPanel controlPanel;

    // Кнопки симуляции
    private JButton startButton;
    private JButton stopButton;
    private JButton currentObjectsButton;

    // Элементы управления информацией и временем
    private JCheckBox showInfoCheckBox;
    private JRadioButton showTimeRadio;
    private JRadioButton hideTimeRadio;

    // Текстовые поля для периодов генерации
    private JTextField adultPeriodField;
    private JTextField chickPeriodField;
    // текстовые поля для времени жизни
    private JTextField adultLifetimeField;
    private JTextField chickLifetimeField;

    // задание вероятности генерации
    private JComboBox<String> probabilityComboBox;
    private JList<String> probabilityList;

    // Меню и панель инструментов
    private JMenuBar menuBar;
    private JToolBar toolBar;

    //  поля для потоков
    private AdultBirdAI adultAI;
    private ChickAI chickAI;

    // константы
    public static final int FRAME_WIDTH = 1200;
    public static final int FRAME_HEIGHT = 800;
    public static final int SIM_PANEL_WIDTH = 800;
    public static final int SIM_PANEL_HEIGHT = 800;
    public static final int UPDATE_INTERVAL = 50;  // мс

    private static final int DEFAULT_ADULT_PERIOD = 2000;
    private static final int DEFAULT_CHICK_PERIOD = 3000;
    private static final double DEFAULT_ADULT_PROB = 0.5;
    private static final int DEFAULT_K_PERCENT = 30;
    private static final long DEFAULT_ADULT_LIFETIME = 10000; // мс (10 секунд)
    private static final long DEFAULT_CHICK_LIFETIME = 5000;  // мс (5 секунд)

    // паттерн SINGLETON
    public static class BirdManager {
        private static BirdManager instance;
        private java.util.List<Bird> birds;
        private BirdManager() {
            birds = new java.util.ArrayList<>();
        }
        public static BirdManager getInstance() {
            if (instance == null) {
                instance = new BirdManager();
            }
            return instance;
        }
        public void clear() {
            birds.clear();
        }
    }

    // конструктор SimulationFrame
    public SimulationFrame() {
        super("Симуляция птиц");
        setSize(FRAME_WIDTH, FRAME_HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setLayout(new BorderLayout());

        //  объект Habitat с параметрами (включая время жизни)
        habitat = new Habitat(new Dimension(SIM_PANEL_WIDTH, SIM_PANEL_HEIGHT),
                DEFAULT_ADULT_PERIOD, DEFAULT_ADULT_PROB, DEFAULT_CHICK_PERIOD,
                DEFAULT_K_PERCENT, DEFAULT_ADULT_LIFETIME, DEFAULT_CHICK_LIFETIME);

        //  панель для рисования (центр)
        simPanel = new SimulationPanel();
        simPanel.setPreferredSize(new Dimension(SIM_PANEL_WIDTH, SIM_PANEL_HEIGHT));
        simPanel.setBackground(Color.WHITE);
        add(simPanel, BorderLayout.CENTER);

        //  панель управления (справа)
        controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        add(controlPanel, BorderLayout.EAST);

        // панель для кнопок "Старт" и "Стоп"
        JPanel buttonPanel = new JPanel();
        startButton = new JButton("Старт");
        stopButton = new JButton("Стоп");
        startButton.addActionListener(e -> startSimulation());
        stopButton.addActionListener(e -> stopSimulation());
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        controlPanel.add(buttonPanel);
        controlPanel.add(Box.createVerticalStrut(10));

        // переключатель Показывать информацию
        showInfoCheckBox = new JCheckBox("Показывать информацию", true);
        showInfoEnabled = true;
        showInfoCheckBox.addActionListener(e -> showInfoEnabled = showInfoCheckBox.isSelected());
        controlPanel.add(showInfoCheckBox);
        controlPanel.add(Box.createVerticalStrut(10));

        // группа радиокнопок для управления отображением времени
        showTimeRadio = new JRadioButton("Показывать время", true);
        hideTimeRadio = new JRadioButton("Скрывать время", false);
        ButtonGroup timeGroup = new ButtonGroup();
        timeGroup.add(showTimeRadio);
        timeGroup.add(hideTimeRadio);
        showTimeRadio.addActionListener(e -> { showTime = true; simPanel.repaint(); });
        hideTimeRadio.addActionListener(e -> { showTime = false; simPanel.repaint(); });
        controlPanel.add(showTimeRadio);
        controlPanel.add(hideTimeRadio);
        controlPanel.add(Box.createVerticalStrut(10));
        showTime = true;

        // текстовые поля для ввода периодов генерации
        controlPanel.add(new JLabel("Период рождения взрослых (мс):"));
        adultPeriodField = new JTextField(String.valueOf(DEFAULT_ADULT_PERIOD), 10);
        controlPanel.add(adultPeriodField);
        controlPanel.add(new JLabel("Период рождения птенцов (мс):"));
        chickPeriodField = new JTextField(String.valueOf(DEFAULT_CHICK_PERIOD), 10);
        controlPanel.add(chickPeriodField);
        controlPanel.add(Box.createVerticalStrut(10));

        // текстовые поля для времени жизни объектов
        controlPanel.add(new JLabel("Время жизни взрослых (мс):"));
        adultLifetimeField = new JTextField(String.valueOf(DEFAULT_ADULT_LIFETIME), 10);
        controlPanel.add(adultLifetimeField);
        controlPanel.add(new JLabel("Время жизни птенцов (мс):"));
        chickLifetimeField = new JTextField(String.valueOf(DEFAULT_CHICK_LIFETIME), 10);
        controlPanel.add(chickLifetimeField);
        controlPanel.add(Box.createVerticalStrut(10));

        // вероятности генерации
        String[] probOptions = {"10%", "20%", "30%", "40%", "50%", "60%", "70%", "80%", "90%", "100%"};
        controlPanel.add(new JLabel("Вероятность рождения взрослых:"));
        probabilityComboBox = new JComboBox<>(probOptions);
        probabilityComboBox.setSelectedItem("50%");
        controlPanel.add(probabilityComboBox);
        controlPanel.add(Box.createVerticalStrut(10));

        controlPanel.add(new JLabel("Вероятность (список):"));
        probabilityList = new JList<>(probOptions);
        probabilityList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        probabilityList.setSelectedIndex(4);
        JScrollPane listScrollPane = new JScrollPane(probabilityList);
        listScrollPane.setPreferredSize(new Dimension(100, 80));
        controlPanel.add(listScrollPane);
        controlPanel.add(Box.createVerticalStrut(10));

        // кнопка Текущие объекты
        currentObjectsButton = new JButton("Текущие объекты");
        currentObjectsButton.addActionListener(e -> showCurrentObjects());
        controlPanel.add(currentObjectsButton);
        controlPanel.add(Box.createVerticalGlue());

        // панель для управления потоками
        JPanel aiControlPanel = new JPanel(new GridLayout(2, 2, 5, 5));  // 2 строки, 2 столбца
        JButton pauseAdultAIButton = new JButton("Пауза взрослых");
        JButton resumeAdultAIButton = new JButton("Возобновить взрослых");
        JButton pauseChickAIButton = new JButton("Пауза птенцов");
        JButton resumeChickAIButton = new JButton("Возобновить птенцов");

        pauseAdultAIButton.addActionListener(e -> {
            if (adultAI != null) {
                adultAI.pause();
                habitat.setGlobalAdultActive(false);
            }
        });
        resumeAdultAIButton.addActionListener(e -> {
            if (adultAI != null) {
                adultAI.resumeThread();
                habitat.setGlobalAdultActive(true);
            }
        });
        pauseChickAIButton.addActionListener(e -> {
            if (chickAI != null) {
                chickAI.pause();
                habitat.setGlobalChickActive(false);
            }
        });
        resumeChickAIButton.addActionListener(e -> {
            if (chickAI != null) {
                chickAI.resumeThread();
                habitat.setGlobalChickActive(true);
            }
        });

        aiControlPanel.add(pauseAdultAIButton);
        aiControlPanel.add(resumeAdultAIButton);
        aiControlPanel.add(pauseChickAIButton);
        aiControlPanel.add(resumeChickAIButton);
        controlPanel.add(aiControlPanel);
        controlPanel.add(Box.createVerticalStrut(10));

        // Панель для установки приоритетов потоков
        String[] priorities = {"MIN", "NORM", "MAX"};
        JComboBox<String> adultPriorityBox = new JComboBox<>(priorities);
        JComboBox<String> chickPriorityBox = new JComboBox<>(priorities);
        adultPriorityBox.setSelectedItem("NORM");
        chickPriorityBox.setSelectedItem("NORM");
        adultPriorityBox.addActionListener(e -> {
            String sel = (String) adultPriorityBox.getSelectedItem();
            if (adultAI != null) {
                if ("MIN".equals(sel)) adultAI.setPriority(Thread.MIN_PRIORITY);
                else if ("MAX".equals(sel)) adultAI.setPriority(Thread.MAX_PRIORITY);
                else adultAI.setPriority(Thread.NORM_PRIORITY);
            }
        });
        chickPriorityBox.addActionListener(e -> {
            String sel = (String) chickPriorityBox.getSelectedItem();
            if (chickAI != null) {
                if ("MIN".equals(sel)) chickAI.setPriority(Thread.MIN_PRIORITY);
                else if ("MAX".equals(sel)) chickAI.setPriority(Thread.MAX_PRIORITY);
                else chickAI.setPriority(Thread.NORM_PRIORITY);
            }
        });
        controlPanel.add(new JLabel("Приоритет взрослых:"));
        controlPanel.add(adultPriorityBox);
        controlPanel.add(new JLabel("Приоритет птенцов:"));
        controlPanel.add(chickPriorityBox);

        //  меню и панель инструментов
        createMenuAndToolBar();

        // Обработка клавиатуры: B (Старт), E (Стоп), T (Переключить время)
        addKeyListener(this);
        setFocusable(true);
        requestFocusInWindow();

        // Инициализируем таймер симуляции
        timer = new Timer(UPDATE_INTERVAL, this);
        running = false;
    }

    //создание меню
    private void createMenuAndToolBar() {
        menuBar = new JMenuBar();
        JMenu simulationMenu = new JMenu("Симуляция");
        JMenuItem startItem = new JMenuItem("Старт");
        JMenuItem stopItem = new JMenuItem("Стоп");
        JMenuItem toggleTimeItem = new JMenuItem("Переключить время");
        simulationMenu.add(startItem);
        simulationMenu.add(stopItem);
        simulationMenu.add(toggleTimeItem);
        menuBar.add(simulationMenu);
        setJMenuBar(menuBar);

        toolBar = new JToolBar();
        JButton startToolButton = new JButton("Старт");
        JButton stopToolButton = new JButton("Стоп");
        JButton toggleTimeToolButton = new JButton("Переключить время");
        toolBar.add(startToolButton);
        toolBar.add(stopToolButton);
        toolBar.add(toggleTimeToolButton);
        add(toolBar, BorderLayout.NORTH);

        startItem.addActionListener(e -> startSimulation());
        stopItem.addActionListener(e -> stopSimulation());
        toggleTimeItem.addActionListener(e -> {
            showTime = !showTime;
            showTimeRadio.setSelected(showTime);
            hideTimeRadio.setSelected(!showTime);
            simPanel.repaint();
        });
        startToolButton.addActionListener(e -> startSimulation());
        stopToolButton.addActionListener(e -> stopSimulation());
        toggleTimeToolButton.addActionListener(e -> {
            showTime = !showTime;
            showTimeRadio.setSelected(showTime);
            hideTimeRadio.setSelected(!showTime);
            simPanel.repaint();
        });
    }

    // запуск симуляции
    private void startSimulation() {
        if (running) return;

        try {
            int adultPeriod = Integer.parseInt(adultPeriodField.getText());
            int chickPeriod = Integer.parseInt(chickPeriodField.getText());
            String probStr = (String) probabilityComboBox.getSelectedItem();
            double adultProb = Double.parseDouble(probStr.replace("%", "")) / 100.0;
            long adultLifetime = Long.parseLong(adultLifetimeField.getText());
            long chickLifetime = Long.parseLong(chickLifetimeField.getText());

            habitat = new Habitat(new Dimension(SIM_PANEL_WIDTH, SIM_PANEL_HEIGHT),
                    adultPeriod, adultProb, chickPeriod, DEFAULT_K_PERCENT,
                    adultLifetime, chickLifetime);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Неверный формат ввода. Используются значения по умолчанию.",
                    "Ошибка ввода", JOptionPane.ERROR_MESSAGE);
            adultPeriodField.setText(String.valueOf(DEFAULT_ADULT_PERIOD));
            chickPeriodField.setText(String.valueOf(DEFAULT_CHICK_PERIOD));
            adultLifetimeField.setText(String.valueOf(DEFAULT_ADULT_LIFETIME));
            chickLifetimeField.setText(String.valueOf(DEFAULT_CHICK_LIFETIME));
        }

        running = true;
        startTime = System.currentTimeMillis();
        timer.start();
        startButton.setEnabled(false);
        stopButton.setEnabled(true);

        //  Запуск потоков
        adultAI = new AdultBirdAI(habitat);
        chickAI = new ChickAI(habitat);
        adultAI.start();
        chickAI.start();
    }

    //  остановка симуляции
    private void stopSimulation() {
        if (running) {
            timer.stop();
            // Останавливаем потоки
            if (adultAI != null) adultAI.stopAI();
            if (chickAI != null) chickAI.stopAI();

            if (showInfoEnabled) {
                JDialog infoDialog = new JDialog(this, "Статистика симуляции", true);
                infoDialog.setLayout(new BorderLayout());
                JTextArea statsArea = new JTextArea(habitat.getStatistics());
                statsArea.setEditable(false);
                infoDialog.add(new JScrollPane(statsArea), BorderLayout.CENTER);
                JPanel buttonPanel = new JPanel();
                JButton okButton = new JButton("ОК");
                JButton cancelButton = new JButton("Отмена");
                buttonPanel.add(okButton);
                buttonPanel.add(cancelButton);
                infoDialog.add(buttonPanel, BorderLayout.SOUTH);
                infoDialog.setSize(400, 300);
                infoDialog.setLocationRelativeTo(this);

                okButton.addActionListener(e -> {
                    infoDialog.dispose();
                    running = false;
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    BirdManager.getInstance().clear();
                    habitat.clear();
                    simPanel.repaint();
                });
                cancelButton.addActionListener(e -> {
                    infoDialog.dispose();
                    timer.start();
                });
                infoDialog.setVisible(true);
            } else {
                running = false;
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                BirdManager.getInstance().clear();
                habitat.clear();
                simPanel.repaint();
            }
        }
    }

    // Обработка таймера
    @Override
    public void actionPerformed(ActionEvent e) {
        long elapsed = System.currentTimeMillis() - startTime;
        habitat.update(elapsed);
        simPanel.repaint();
    }

    // Обработка клавиатуры
    @Override
    public void keyPressed(KeyEvent e) {
        switch(e.getKeyCode()) {
            case KeyEvent.VK_B:
                startSimulation();
                break;
            case KeyEvent.VK_E:
                stopSimulation();
                break;
            case KeyEvent.VK_T:
                showTime = !showTime;
                showTimeRadio.setSelected(showTime);
                hideTimeRadio.setSelected(!showTime);
                simPanel.repaint();
                break;
        }
    }
    @Override public void keyReleased(KeyEvent e) { }
    @Override public void keyTyped(KeyEvent e) { }

    // Панель визуализации
    class SimulationPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            habitat.draw(g, showTime);
        }
    }

    // показ текущих объектов
    private void showCurrentObjects() {
        HashMap<Integer, Long> btMap = habitat.getBirthTimeMap();
        StringBuilder sb = new StringBuilder("Список текущих объектов (ID : Время рождения, сек):\n");
        for (Integer id : btMap.keySet()) {
            sb.append(id).append(" : ").append(btMap.get(id) / 1000.0).append("\n");
        }
        JDialog currentDialog = new JDialog(this, "Текущие объекты", true);
        currentDialog.setLayout(new BorderLayout());
        JTextArea textArea = new JTextArea(sb.toString());
        textArea.setEditable(false);
        currentDialog.add(new JScrollPane(textArea), BorderLayout.CENTER);
        JButton okBtn = new JButton("ОК");
        okBtn.addActionListener(e -> currentDialog.dispose());
        JPanel pan = new JPanel();
        pan.add(okBtn);
        currentDialog.add(pan, BorderLayout.SOUTH);
        currentDialog.setSize(300, 400);
        currentDialog.setLocationRelativeTo(this);
        currentDialog.setVisible(true);
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SimulationFrame frame = new SimulationFrame();
            frame.setVisible(true);
        });
    }
}
