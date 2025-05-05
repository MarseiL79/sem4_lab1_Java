import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.HashMap;
import java.util.Properties;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimulationFrame extends JFrame implements ActionListener, KeyListener {
    public static final int FRAME_WIDTH = 1200, FRAME_HEIGHT = 800;
    public static final int SIM_PANEL_WIDTH = 800, SIM_PANEL_HEIGHT = 800;
    public static final int UPDATE_INTERVAL = 50;  // мс

    private static final String CONFIG_FILE = "config.txt";
    private static final int DEFAULT_ADULT_PERIOD = 2000;
    private static final int DEFAULT_CHICK_PERIOD = 3000;
    private static final double DEFAULT_ADULT_PROB = 0.5;
    private static final int DEFAULT_K_PERCENT = 30;
    private static final long DEFAULT_ADULT_LIFETIME = 10000;
    private static final long DEFAULT_CHICK_LIFETIME = 5000;

    private Habitat habitat;
    private Timer timer;
    private long startTime;
    private boolean running, showTime = true, showInfoEnabled = true;

    private SimulationPanel simPanel;
    private JPanel controlPanel;
    private JButton startButton, stopButton, currentObjectsButton;
    private JCheckBox showInfoCheckBox;
    private JRadioButton showTimeRadio, hideTimeRadio;
    private JTextField adultPeriodField, chickPeriodField;
    private JTextField adultLifetimeField, chickLifetimeField;
    private JComboBox<String> probabilityComboBox;
    private JList<String> probabilityList;

    private AdultBirdAI adultAI;
    private ChickAI chickAI;

    // Консоль
    private JDialog consoleDialog;
    private JTextArea consoleArea;
    private PipedWriter consoleWriter;
    private ExecutorService consoleExecutor = Executors.newSingleThreadExecutor();

    public SimulationFrame() {
        super("Симуляция птиц");
        setSize(FRAME_WIDTH, FRAME_HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setResizable(false);

        loadConfig();

        simPanel = new SimulationPanel();
        simPanel.setPreferredSize(new Dimension(SIM_PANEL_WIDTH, SIM_PANEL_HEIGHT));
        simPanel.setBackground(Color.WHITE);
        add(simPanel, BorderLayout.CENTER);

        controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        add(controlPanel, BorderLayout.EAST);

        initControlPanel();
        createMenuAndToolBar();
        initConsole();

        addKeyListener(this);
        setFocusable(true);
        requestFocusInWindow();

        timer = new Timer(UPDATE_INTERVAL, this);
        running = false;

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveConfig();
                consoleExecutor.shutdownNow();
            }
        });
    }

    private void initControlPanel() {
        JPanel bp = new JPanel();
        startButton = new JButton("Старт");
        stopButton = new JButton("Стоп");
        startButton.addActionListener(e -> startSimulation());
        stopButton.addActionListener(e -> stopSimulation());
        bp.add(startButton); bp.add(stopButton);
        controlPanel.add(bp);
        controlPanel.add(Box.createVerticalStrut(10));

        showInfoCheckBox = new JCheckBox("Показывать информацию", true);
        showInfoCheckBox.addActionListener(e -> showInfoEnabled = showInfoCheckBox.isSelected());
        controlPanel.add(showInfoCheckBox);
        controlPanel.add(Box.createVerticalStrut(10));

        showTimeRadio = new JRadioButton("Показывать время", true);
        hideTimeRadio = new JRadioButton("Скрывать время", false);
        ButtonGroup tg = new ButtonGroup();
        tg.add(showTimeRadio); tg.add(hideTimeRadio);
        showTimeRadio.addActionListener(e -> { showTime = true; simPanel.repaint(); });
        hideTimeRadio.addActionListener(e -> { showTime = false; simPanel.repaint(); });
        controlPanel.add(showTimeRadio);
        controlPanel.add(hideTimeRadio);
        controlPanel.add(Box.createVerticalStrut(10));

        controlPanel.add(new JLabel("Период рождения взрослых (мс):"));
        adultPeriodField = new JTextField(String.valueOf(habitat.n1), 10);
        controlPanel.add(adultPeriodField);
        controlPanel.add(new JLabel("Период рождения птенцов (мс):"));
        chickPeriodField = new JTextField(String.valueOf(habitat.n2), 10);
        controlPanel.add(chickPeriodField);
        controlPanel.add(Box.createVerticalStrut(10));

        controlPanel.add(new JLabel("Время жизни взрослых (мс):"));
        adultLifetimeField = new JTextField(String.valueOf(habitat.adultLifetime), 10);
        controlPanel.add(adultLifetimeField);
        controlPanel.add(new JLabel("Время жизни птенцов (мс):"));
        chickLifetimeField = new JTextField(String.valueOf(habitat.chickLifetime), 10);
        controlPanel.add(chickLifetimeField);
        controlPanel.add(Box.createVerticalStrut(10));

        String[] probs = {"10%","20%","30%","40%","50%","60%","70%","80%","90%","100%"};
        controlPanel.add(new JLabel("Вероятность рождения взрослых:"));
        probabilityComboBox = new JComboBox<>(probs);
        probabilityComboBox.setSelectedItem((int)(habitat.p1*100)+"%");
        controlPanel.add(probabilityComboBox);
        controlPanel.add(Box.createVerticalStrut(10));

        controlPanel.add(new JLabel("Вероятность (список):"));
        probabilityList = new JList<>(probs);
        probabilityList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        probabilityList.setSelectedValue((int)(habitat.p1*100)+"%", true);
        JScrollPane sp = new JScrollPane(probabilityList);
        sp.setPreferredSize(new Dimension(100,80));
        controlPanel.add(sp);
        controlPanel.add(Box.createVerticalStrut(10));

        currentObjectsButton = new JButton("Текущие объекты");
        currentObjectsButton.addActionListener(e -> showCurrentObjects());
        controlPanel.add(currentObjectsButton);
        controlPanel.add(Box.createVerticalStrut(10));

        // панель для управления потоками
        JPanel aiControlPanel = new JPanel(new GridLayout(2, 2, 5, 5));  // 2 строки, 2 столбца
        JButton pauseAdultAIButton = new JButton("Пауза взрослых");
        JButton resumeAdultAIButton = new JButton("Возобновить взрослых");
        JButton pauseChickAIButton = new JButton("Пауза птенцов");
        JButton resumeChickAIButton = new JButton("Возобновить птенцов");
        aiControlPanel.add(pauseAdultAIButton);
        aiControlPanel.add(resumeAdultAIButton);
        aiControlPanel.add(pauseChickAIButton);
        aiControlPanel.add(resumeChickAIButton);

        pauseAdultAIButton.addActionListener(e -> {
            if (adultAI != null) { adultAI.pause(); habitat.setGlobalAdultActive(false); }
        });
        resumeAdultAIButton.addActionListener(e -> {
            if (adultAI != null) { adultAI.resumeThread(); habitat.setGlobalAdultActive(true); }
        });
        pauseChickAIButton.addActionListener(e -> {
            if (chickAI != null) { chickAI.pause(); habitat.setGlobalChickActive(false); }
        });
        resumeChickAIButton.addActionListener(e -> {
            if (chickAI != null) { chickAI.resumeThread(); habitat.setGlobalChickActive(true); }
        });

        controlPanel.add(aiControlPanel);
    }

    private void createMenuAndToolBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("Файл");
        JMenuItem loadSim = new JMenuItem("Загрузить");
        JMenuItem saveSim = new JMenuItem("Сохранить");
        JMenuItem loadCfg = new JMenuItem("Загрузить конфиг");
        JMenuItem saveCfg = new JMenuItem("Сохранить конфиг");
        fileMenu.add(loadCfg);
        fileMenu.add(saveCfg);
        fileMenu.addSeparator();
        fileMenu.add(loadSim);
        fileMenu.add(saveSim);
        menuBar.add(fileMenu);

        JMenu simMenu = new JMenu("Симуляция");
        JMenuItem startItem = new JMenuItem("Старт");
        JMenuItem stopItem = new JMenuItem("Стоп");
        JMenuItem toggleTimeItem = new JMenuItem("Переключить время");
        simMenu.add(startItem); simMenu.add(stopItem); simMenu.add(toggleTimeItem);
        menuBar.add(simMenu);

        JMenu extraMenu = new JMenu("Дополнительно");
        JMenuItem consoleItem = new JMenuItem("Консоль");
        extraMenu.add(consoleItem);
        menuBar.add(extraMenu);

        setJMenuBar(menuBar);

        JToolBar toolBar = new JToolBar();
        JButton tStart = new JButton("Старт");
        JButton tStop  = new JButton("Стоп");
        JButton tToggle= new JButton("Переключить время");
        toolBar.add(tStart); toolBar.add(tStop); toolBar.add(tToggle);
        add(toolBar, BorderLayout.NORTH);

        loadCfg.addActionListener(e -> loadConfig());
        saveCfg.addActionListener(e -> saveConfig());
        loadSim.addActionListener(e -> loadSimulation());
        saveSim.addActionListener(e -> saveSimulation());
        startItem.addActionListener(e -> startSimulation());
        stopItem.addActionListener(e -> stopSimulation());
        toggleTimeItem.addActionListener(e -> toggleTime());
        tStart.addActionListener(e -> startSimulation());
        tStop.addActionListener(e -> stopSimulation());
        tToggle.addActionListener(e -> toggleTime());
        consoleItem.addActionListener(e -> consoleDialog.setVisible(true));
    }

    private void initConsole() {
        consoleDialog = new JDialog(this, "Консоль", false);
        consoleArea = new JTextArea(20, 40);
        consoleArea.setLineWrap(true);
        consoleArea.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            int start = consoleArea.getLineStartOffset(consoleArea.getLineCount() - 1);
                            int end = consoleArea.getLineEndOffset(consoleArea.getLineCount() - 1);
                            String cmd = consoleArea.getText().substring(start, end).trim();
                            consoleWriter.write(cmd + "\n");
                            consoleWriter.flush();
                        } catch (Exception ex) {}
                    });
                }
            }
        });
        consoleDialog.add(new JScrollPane(consoleArea));
        consoleDialog.pack();

        try {
            PipedReader pr = new PipedReader();
            consoleWriter = new PipedWriter(pr);
            consoleExecutor.submit(() -> {
                BufferedReader br = new BufferedReader(pr);
                String line;
                while (true) {
                    try {
                        if (!((line = br.readLine()) != null)) break;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    processConsoleCommand(line);
                }
            });
        } catch (IOException ex) {}
    }

    private void processConsoleCommand(String cmd) {
        SwingUtilities.invokeLater(() -> {
            if (cmd.startsWith("Изменить процент птенцов")) {
                try {
                    int v = Integer.parseInt(cmd.replaceAll("\\D+", ""));
                    habitat.kPercent = v;
                    consoleArea.append("\nПроцент птенцов установлен: " + v + "%\n");
                } catch (Exception ex) {
                    consoleArea.append("\nОшибка команды.\n");
                }
            } else {
                consoleArea.append("\nНеизвестная команда.\n");
            }
        });
    }

    private void toggleTime() {
        showTime = !showTime;
        showTimeRadio.setSelected(showTime);
        hideTimeRadio.setSelected(!showTime);
        simPanel.repaint();
    }

    private void startSimulation() {
        if (running) return;
        try {
            int ap = Integer.parseInt(adultPeriodField.getText());
            int cp = Integer.parseInt(chickPeriodField.getText());
            double pr = Double.parseDouble(((String) probabilityComboBox.getSelectedItem()).replace("%", "")) / 100.0;
            long al = Long.parseLong(adultLifetimeField.getText());
            long cl = Long.parseLong(chickLifetimeField.getText());
            habitat = new Habitat(new Dimension(SIM_PANEL_WIDTH, SIM_PANEL_HEIGHT), ap, pr, cp, habitat.kPercent, al, cl);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Неверный формат ввода. Используются значения по умолчанию.",
                    "Ошибка ввода", JOptionPane.ERROR_MESSAGE);
        }
        running = true;
        startTime = System.currentTimeMillis();
        timer.start();
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        adultAI = new AdultBirdAI(habitat);
        chickAI = new ChickAI(habitat);
        adultAI.start();
        chickAI.start();
    }

    private void stopSimulation() {
        if (!running) return;
        timer.stop();
        if (adultAI != null) adultAI.stopAI();
        if (chickAI != null) chickAI.stopAI();
        if (showInfoEnabled) showInfoDialog();
        running = false;
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        habitat.clear();
        simPanel.repaint();
    }

    private void showInfoDialog() {
        JDialog dlg = new JDialog(this, "Статистика симуляции", true);
        JTextArea ta = new JTextArea(habitat.getStatistics());
        ta.setEditable(false);
        dlg.add(new JScrollPane(ta), BorderLayout.CENTER);
        JPanel bp = new JPanel();
        JButton ok = new JButton("ОК");
        ok.addActionListener(e -> dlg.dispose());
        bp.add(ok);
        dlg.add(bp, BorderLayout.SOUTH);
        dlg.setSize(400, 300);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void loadSimulation() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            // Если что-то уже шло — остановим
            stopSimulation();

            try (ObjectInputStream ois = new ObjectInputStream(
                    new FileInputStream(fc.getSelectedFile()))) {

                // Восстанавливаем habitat из файла
                habitat = (Habitat) ois.readObject();

                // Если вы хотите сразу увидеть загруженный снимок:
                simPanel.repaint();

                // --- И (опционально) запустить симуляцию дальше ---
                // Запомним, сколько уже было “прошло”:
                long loadedSimTime = habitat.getSimulationTime();
                // Выставляем стартовое смещение так,
                // чтобы при следующем обновлении elapsed = текущее-время - startTime = loadedSimTime
                startTime = System.currentTimeMillis() - loadedSimTime;

                // Запускаем таймер и AI-потоки заново
                running = true;
                timer.start();
                startButton.setEnabled(false);
                stopButton.setEnabled(true);

                adultAI = new AdultBirdAI(habitat);
                chickAI = new ChickAI(habitat);
                adultAI.start();
                chickAI.start();

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Ошибка загрузки: " + ex.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }


    private void saveSimulation() {
        JFileChooser fc = new JFileChooser();
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(fc.getSelectedFile()))) {
                oos.writeObject(habitat);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Ошибка сохранения: " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadConfig() {
        Properties p = new Properties();
        try (FileReader r = new FileReader(CONFIG_FILE)) { p.load(r); } catch (IOException ignored) {}
        int ap = Integer.parseInt(p.getProperty("adultPeriod", String.valueOf(DEFAULT_ADULT_PERIOD)));
        int cp = Integer.parseInt(p.getProperty("chickPeriod", String.valueOf(DEFAULT_CHICK_PERIOD)));
        double pr = Double.parseDouble(p.getProperty("adultProb", String.valueOf(DEFAULT_ADULT_PROB)));
        int kp = Integer.parseInt(p.getProperty("kPercent", String.valueOf(DEFAULT_K_PERCENT)));
        long al = Long.parseLong(p.getProperty("adultLifetime", String.valueOf(DEFAULT_ADULT_LIFETIME)));
        long cl = Long.parseLong(p.getProperty("chickLifetime", String.valueOf(DEFAULT_CHICK_LIFETIME)));
        habitat = new Habitat(new Dimension(SIM_PANEL_WIDTH, SIM_PANEL_HEIGHT), ap, pr, cp, kp, al, cl);
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("adultPeriod", String.valueOf(habitat.n1));
        p.setProperty("chickPeriod", String.valueOf(habitat.n2));
        p.setProperty("adultProb", String.valueOf(habitat.p1));
        p.setProperty("kPercent", String.valueOf(habitat.kPercent));
        p.setProperty("adultLifetime", String.valueOf(habitat.adultLifetime));
        p.setProperty("chickLifetime", String.valueOf(habitat.chickLifetime));
        try (FileWriter w = new FileWriter(CONFIG_FILE)) { p.store(w, "Simulation config"); } catch (IOException ignored) {}
    }

    @Override public void actionPerformed(ActionEvent e) {
        long elapsed = System.currentTimeMillis() - startTime;
        habitat.update(elapsed);
        simPanel.repaint();
    }

    @Override public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_B: startSimulation(); break;
            case KeyEvent.VK_E: stopSimulation(); break;
            case KeyEvent.VK_T: toggleTime(); break;
        }
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}

    class SimulationPanel extends JPanel {
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            habitat.draw(g, showTime);
        }
    }

    private void showCurrentObjects() {
        HashMap<Integer, Long> map = habitat.getBirthTimeMap();
        StringBuilder sb = new StringBuilder("ID : время рожд. (с)\n");
        map.forEach((id, t) -> sb.append(id).append(" : ").append(t/1000.0).append("\n"));
        JDialog d = new JDialog(this, "Текущие объекты", true);
        JTextArea ta = new JTextArea(sb.toString());
        ta.setEditable(false);
        d.add(new JScrollPane(ta), BorderLayout.CENTER);
        JButton ok = new JButton("ОК"); ok.addActionListener(e -> d.dispose());
        d.add(ok, BorderLayout.SOUTH);
        d.setSize(300, 400);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SimulationFrame().setVisible(true));
    }
}
