// SimulationFrame.java
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.HashMap;
import java.util.List;
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

    // Консоль из прошлой лабы
    private JDialog consoleDialog;
    private JTextArea consoleArea;
    private PipedWriter consoleWriter;
    private ExecutorService consoleExecutor = Executors.newSingleThreadExecutor();

    // TCP-клиент и список клиентов
    private SimulationClient client;
    private DefaultListModel<String> clientListModel;

    public SimulationFrame() {
        super("Симуляция птиц");
        setSize(FRAME_WIDTH, FRAME_HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setResizable(false);

        loadConfig();

        // Симуляционная панель
        simPanel = new SimulationPanel();
        simPanel.setPreferredSize(new Dimension(SIM_PANEL_WIDTH, SIM_PANEL_HEIGHT));
        simPanel.setBackground(Color.WHITE);
        add(simPanel, BorderLayout.CENTER);

        // Панель управления
        controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        add(controlPanel, BorderLayout.EAST);

        initControlPanel();
        createMenuAndToolBar();
        initConsole();
        initNetworkControls();

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
                if (client != null) {
                    try { client.close(); } catch (IOException ignored) {}
                }
            }
        });
    }

    private void initControlPanel() {
        // Старт/Стоп
        JPanel bp = new JPanel();
        startButton = new JButton("Старт");
        stopButton  = new JButton("Стоп");
        startButton.addActionListener(e -> startSimulation());
        stopButton .addActionListener(e -> stopSimulation());
        bp.add(startButton);
        bp.add(stopButton);
        controlPanel.add(bp);
        controlPanel.add(Box.createVerticalStrut(10));

        // Показывать информацию
        showInfoCheckBox = new JCheckBox("Показывать информацию", true);
        showInfoCheckBox.addActionListener(e -> showInfoEnabled = showInfoCheckBox.isSelected());
        controlPanel.add(showInfoCheckBox);
        controlPanel.add(Box.createVerticalStrut(10));

        // Радиокнопки времени
        showTimeRadio = new JRadioButton("Показывать время", true);
        hideTimeRadio = new JRadioButton("Скрывать время", false);
        ButtonGroup bg = new ButtonGroup();
        bg.add(showTimeRadio);
        bg.add(hideTimeRadio);
        showTimeRadio.addActionListener(e -> { showTime = true; simPanel.repaint(); });
        hideTimeRadio.addActionListener(e -> { showTime = false; simPanel.repaint(); });
        controlPanel.add(showTimeRadio);
        controlPanel.add(hideTimeRadio);
        controlPanel.add(Box.createVerticalStrut(10));

        // Периоды генерации
        controlPanel.add(new JLabel("Период взрослых (мс):"));
        adultPeriodField = new JTextField(String.valueOf(habitat.n1), 10);
        controlPanel.add(adultPeriodField);
        controlPanel.add(new JLabel("Период птенцов (мс):"));
        chickPeriodField = new JTextField(String.valueOf(habitat.n2), 10);
        controlPanel.add(chickPeriodField);
        controlPanel.add(Box.createVerticalStrut(10));

        // Время жизни
        controlPanel.add(new JLabel("Жизнь взрослых (мс):"));
        adultLifetimeField = new JTextField(String.valueOf(habitat.adultLifetime), 10);
        controlPanel.add(adultLifetimeField);
        controlPanel.add(new JLabel("Жизнь птенцов (мс):"));
        chickLifetimeField = new JTextField(String.valueOf(habitat.chickLifetime), 10);
        controlPanel.add(chickLifetimeField);
        controlPanel.add(Box.createVerticalStrut(10));

        // Вероятность взрослых
        String[] probs = {"10%","20%","30%","40%","50%","60%","70%","80%","90%","100%"};
        controlPanel.add(new JLabel("Вероятность взрослых:"));
        probabilityComboBox = new JComboBox<>(probs);
        probabilityComboBox.setSelectedItem((int)(habitat.p1*100)+"%");
        controlPanel.add(probabilityComboBox);
        controlPanel.add(Box.createVerticalStrut(10));

        // Список вероятностей
        controlPanel.add(new JLabel("Вероятность (список):"));
        probabilityList = new JList<>(probs);
        probabilityList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        probabilityList.setSelectedValue((int)(habitat.p1*100)+"%", true);
        JScrollPane sp = new JScrollPane(probabilityList);
        sp.setPreferredSize(new Dimension(100,80));
        controlPanel.add(sp);
        controlPanel.add(Box.createVerticalStrut(10));

        // Текущие объекты
        currentObjectsButton = new JButton("Текущие объекты");
        currentObjectsButton.addActionListener(e -> showCurrentObjects());
        controlPanel.add(currentObjectsButton);
        controlPanel.add(Box.createVerticalStrut(10));

        // Управление потоками
        JPanel aiPanel = new JPanel(new GridLayout(2,2,5,5));
        JButton pauseAdult = new JButton("Пауза взрослых");
        JButton resumeAdult = new JButton("Возобновить взрослых");
        JButton pauseChick = new JButton("Пауза птенцов");
        JButton resumeChick = new JButton("Возобновить птенцов");
        aiPanel.add(pauseAdult);
        aiPanel.add(resumeAdult);
        aiPanel.add(pauseChick);
        aiPanel.add(resumeChick);
        pauseAdult.addActionListener(e -> { if(adultAI!=null){adultAI.pause(); habitat.setGlobalAdultActive(false);} });
        resumeAdult.addActionListener(e -> { if(adultAI!=null){adultAI.resumeThread(); habitat.setGlobalAdultActive(true);} });
        pauseChick.addActionListener(e -> { if(chickAI!=null){chickAI.pause(); habitat.setGlobalChickActive(false);} });
        resumeChick.addActionListener(e -> { if(chickAI!=null){chickAI.resumeThread(); habitat.setGlobalChickActive(true);} });
        controlPanel.add(aiPanel);
    }

    private void createMenuAndToolBar() {
        JMenuBar mb = new JMenuBar();
        JMenu file = new JMenu("Файл");
        JMenuItem loadCfg = new JMenuItem("Загрузить конфиг");
        JMenuItem saveCfg = new JMenuItem("Сохранить конфиг");
        JMenuItem loadSim = new JMenuItem("Загрузить симуляцию");
        JMenuItem saveSim = new JMenuItem("Сохранить симуляцию");
        file.add(loadCfg); file.add(saveCfg); file.addSeparator(); file.add(loadSim); file.add(saveSim);
        mb.add(file);

        JMenu sim = new JMenu("Симуляция");
        JMenuItem start = new JMenuItem("Старт");
        JMenuItem stop = new JMenuItem("Стоп");
        JMenuItem toggle = new JMenuItem("Переключить время");
        sim.add(start); sim.add(stop); sim.add(toggle);
        mb.add(sim);

        JMenu extra = new JMenu("Дополнительно");
        JMenuItem consoleItem = new JMenuItem("Консоль");
        extra.add(consoleItem);
        mb.add(extra);

        setJMenuBar(mb);

        JToolBar tb = new JToolBar();
        tb.add(new JButton("Старт")  {{ addActionListener(e->startSimulation()); }});
        tb.add(new JButton("Стоп")   {{ addActionListener(e->stopSimulation()); }});
        tb.add(new JButton("Время")  {{ addActionListener(e->toggleTime()); }});
        add(tb, BorderLayout.NORTH);

        loadCfg.addActionListener(e -> loadConfig());
        saveCfg.addActionListener(e -> saveConfig());
        loadSim.addActionListener(e -> loadSimulation());
        saveSim.addActionListener(e -> saveSimulation());
        start .addActionListener(e -> startSimulation());
        stop  .addActionListener(e -> stopSimulation());
        toggle.addActionListener(e -> toggleTime());
        consoleItem.addActionListener(e -> consoleDialog.setVisible(true));
    }

    private void initConsole() {
        consoleDialog = new JDialog(this, "Консоль", false);
        consoleArea = new JTextArea(20,40);
        consoleArea.setLineWrap(true);
        consoleArea.addKeyListener(new KeyAdapter(){
            @Override
            public void keyPressed(KeyEvent e){
                if(e.getKeyCode() == KeyEvent.VK_ENTER){
                    SwingUtilities.invokeLater(() -> {
                        try {
                            // Берём весь текст, разбиваем по строкам и берём последнюю непустую
                            String full = consoleArea.getText();
                            String[] lines = full.split("\\r?\\n");
                            String last = "";
                            for (int i = lines.length-1; i >= 0; i--) {
                                if (!lines[i].trim().isEmpty()) {
                                    last = lines[i].trim();
                                    break;
                                }
                            }
                            // Записываем именно эту последнюю строку
                            consoleWriter.write(last + "\n");
                            consoleWriter.flush();
                        } catch(Exception ex){
                            ex.printStackTrace();
                        }
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
                try {
                    while ((line = br.readLine()) != null) {
                        processConsoleCommand(line);
                    }
                } catch(IOException ex) {
                    ex.printStackTrace();
                }
            });
        } catch(IOException ignored){}
    }


    private void processConsoleCommand(String cmd) {
        SwingUtilities.invokeLater(() -> {
            String line = cmd.trim();
            // Разбиваем по пробелам
            String[] parts = line.split(" ");
            // Ожидаем ровно 4 слова: Изменить, процент, птенцов, <число>
            System.out.println(parts.length);
            if (parts.length == 4
                    && parts[0].equalsIgnoreCase("Изменить")
                    && parts[1].equalsIgnoreCase("процент")
                    && parts[2].equalsIgnoreCase("птенцов")) {
                try {
                    int v = Integer.parseInt(parts[3]);
                    if (v < 0 || v > 100) {
                        consoleArea.append("\nОшибка: процент от 0 до 100.\n");
                    } else {
                        habitat.kPercent = v;
                        consoleArea.append("\nПроцент птенцов установлен: " + v + "%\n");
                    }
                } catch (NumberFormatException ex) {
                    consoleArea.append("\nОшибка: не удалось распознать число.\n");
                }
            } else {
                consoleArea.append("\nНеизвестная команда: " + line + "\n");
                consoleArea.append("Используйте: Изменить процент птенцов N\n");
            }
        });
    }


    private void initNetworkControls() {
        clientListModel = new DefaultListModel<>();
        JList<String> clientList = new JList<>(clientListModel);
        clientList.setBorder(BorderFactory.createTitledBorder("Клиенты"));
        controlPanel.add(clientList);
        controlPanel.add(Box.createVerticalStrut(10));

        JPanel np = new JPanel();
        JTextField pct = new JTextField("10",4);
        JButton tr = new JButton("Передать");
        np.add(new JLabel("Процент:")); np.add(pct); np.add(tr);
        controlPanel.add(np);
        controlPanel.add(Box.createVerticalStrut(10));

        JButton conn = new JButton("Connect");
        controlPanel.add(conn);
        controlPanel.add(Box.createVerticalStrut(10));

        conn.addActionListener(e->{
            String name = JOptionPane.showInputDialog(this,"Ваше имя:");
            if(name==null||name.isEmpty())return;
            try {
                client = new SimulationClient("localhost",12345,name);
                client.setOnClientList(list->SwingUtilities.invokeLater(()->{
                    clientListModel.clear();
                    list.forEach(clientListModel::addElement);
                }));
                client.setOnTransferRequest((from, percent) ->
                        SwingUtilities.invokeLater(() -> {
                            // Для проверки выведем в консоль до и после
                            int before = (int) habitat.getBirds().stream()
                                    .filter(b -> b instanceof Chick)
                                    .count();

                            habitat.reduceChicksBy(percent);

                            int after  = (int) habitat.getBirds().stream()
                                    .filter(b -> b instanceof Chick)
                                    .count();

                            System.out.println(
                                    String.format("TRANSFER_FROM %s: %d%% — chicks %d → %d",
                                            from, percent, before, after)
                            );

                            // Обязательно перерисовываем
                            simPanel.repaint();

                            // И уведомляем пользователя
                            JOptionPane.showMessageDialog(this,
                                    String.format("%s отнял у вас %d%% птенцов\nЧисло птенцов: %d → %d",
                                            from, percent, before, after));
                        })
                );
            } catch(IOException ex){
                JOptionPane.showMessageDialog(this,"Ошибка: "+ex.getMessage());
            }
        });

        tr.addActionListener(e->{
            String tgt = clientList.getSelectedValue();
            if(client!=null&&tgt!=null){
                int p = Integer.parseInt(pct.getText());
                client.transferTo(tgt,p);
            }
        });
    }

    private void loadConfig() {
        Properties p = new Properties();
        try(FileReader r=new FileReader(CONFIG_FILE)){p.load(r);}catch(IOException ignored){}
        int ap = Integer.parseInt(p.getProperty("adultPeriod",""+DEFAULT_ADULT_PERIOD));
        int cp = Integer.parseInt(p.getProperty("chickPeriod",""+DEFAULT_CHICK_PERIOD));
        double pr=Double.parseDouble(p.getProperty("adultProb",""+DEFAULT_ADULT_PROB));
        int kp = Integer.parseInt(p.getProperty("kPercent",""+DEFAULT_K_PERCENT));
        long al=Long.parseLong(p.getProperty("adultLifetime",""+DEFAULT_ADULT_LIFETIME));
        long cl=Long.parseLong(p.getProperty("chickLifetime",""+DEFAULT_CHICK_LIFETIME));
        habitat = new Habitat(new Dimension(SIM_PANEL_WIDTH,SIM_PANEL_HEIGHT),
                ap,pr,cp,kp,al,cl);
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("adultPeriod",""+habitat.n1);
        p.setProperty("chickPeriod",""+habitat.n2);
        p.setProperty("adultProb",""+habitat.p1);
        p.setProperty("kPercent",""+habitat.kPercent);
        p.setProperty("adultLifetime",""+habitat.adultLifetime);
        p.setProperty("chickLifetime",""+habitat.chickLifetime);
        try(FileWriter w=new FileWriter(CONFIG_FILE)){p.store(w,null);}catch(IOException ignored){}
    }

    private void startSimulation() {
        if(running)return;
        try{
            int ap=Integer.parseInt(adultPeriodField.getText());
            int cp=Integer.parseInt(chickPeriodField.getText());
            double pr=Double.parseDouble(((String)probabilityComboBox.getSelectedItem()).replace("%",""))/100.0;
            long al=Long.parseLong(adultLifetimeField.getText());
            long cl=Long.parseLong(chickLifetimeField.getText());
            habitat = new Habitat(new Dimension(SIM_PANEL_WIDTH,SIM_PANEL_HEIGHT),
                    ap,pr,cp,habitat.kPercent,al,cl);
        }catch(Exception ex){
            JOptionPane.showMessageDialog(this,"Используются дефолт значения");
        }
        running=true; startTime=System.currentTimeMillis();
        timer.start();
        startButton.setEnabled(false); stopButton.setEnabled(true);
        adultAI=new AdultBirdAI(habitat); chickAI=new ChickAI(habitat);
        adultAI.start(); chickAI.start();
    }

    private void stopSimulation() {
        if(!running)return;
        timer.stop();
        if(adultAI!=null) adultAI.stopAI();
        if(chickAI!=null) chickAI.stopAI();
        if(showInfoEnabled) showInfoDialog();
        running=false;
        startButton.setEnabled(true); stopButton.setEnabled(false);
        habitat.clear(); simPanel.repaint();
    }

    private void showInfoDialog() {
        JDialog d=new JDialog(this,"Статистика",true);
        JTextArea ta=new JTextArea(habitat.getStatistics());
        ta.setEditable(false);
        d.add(new JScrollPane(ta),BorderLayout.CENTER);
        JButton ok=new JButton("OK");
        ok.addActionListener(e->d.dispose());
        d.add(ok,BorderLayout.SOUTH);
        d.setSize(300,200); d.setLocationRelativeTo(this); d.setVisible(true);
    }

    private void loadSimulation() {
        JFileChooser fc=new JFileChooser();
        if(fc.showOpenDialog(this)==JFileChooser.APPROVE_OPTION){
            stopSimulation();
            try(ObjectInputStream ois=new ObjectInputStream(new FileInputStream(fc.getSelectedFile()))) {
                habitat=(Habitat)ois.readObject();
                simPanel.repaint();
                long lt=habitat.getSimulationTime();
                startTime=System.currentTimeMillis()-lt;
                running=true; timer.start();
                startButton.setEnabled(false); stopButton.setEnabled(true);
                adultAI=new AdultBirdAI(habitat); chickAI=new ChickAI(habitat);
                adultAI.start(); chickAI.start();
            }catch(Exception ex){
                JOptionPane.showMessageDialog(this,"Ошибка загрузки:"+ex.getMessage());
            }
        }
    }

    private void saveSimulation() {
        JFileChooser fc=new JFileChooser();
        if(fc.showSaveDialog(this)==JFileChooser.APPROVE_OPTION){
            try(ObjectOutputStream oos=new ObjectOutputStream(new FileOutputStream(fc.getSelectedFile()))){
                oos.writeObject(habitat);
            }catch(IOException ex){
                JOptionPane.showMessageDialog(this,"Ошибка сохранения:"+ex.getMessage());
            }
        }
    }

    private void toggleTime(){
        showTime=!showTime;
        showTimeRadio.setSelected(showTime);
        hideTimeRadio.setSelected(!showTime);
        simPanel.repaint();
    }

    @Override public void actionPerformed(ActionEvent e) {
        long el=System.currentTimeMillis()-startTime;
        habitat.update(el);
        simPanel.repaint();
    }

    @Override public void keyPressed(KeyEvent e){
        switch(e.getKeyCode()){
            case KeyEvent.VK_B: startSimulation(); break;
            case KeyEvent.VK_E: stopSimulation();  break;
            case KeyEvent.VK_T: toggleTime();      break;
        }
    }
    @Override public void keyReleased(KeyEvent e){}
    @Override public void keyTyped(KeyEvent e){}

    class SimulationPanel extends JPanel{
        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            habitat.draw(g, showTime);
        }
    }

    private void showCurrentObjects(){
        HashMap<Integer,Long> map=habitat.getBirthTimeMap();
        StringBuilder sb=new StringBuilder("ID:время(с)\n");
        map.forEach((i,t)->sb.append(i).append(":").append(t/1000.0).append("\n"));
        JDialog d=new JDialog(this,"Текущие объекты",true);
        JTextArea ta=new JTextArea(sb.toString());
        ta.setEditable(false);
        d.add(new JScrollPane(ta),BorderLayout.CENTER);
        JButton ok=new JButton("OK"); ok.addActionListener(e->d.dispose());
        d.add(ok,BorderLayout.SOUTH);
        d.setSize(300,400); d.setLocationRelativeTo(this); d.setVisible(true);
    }

    public static void main(String[] args){
        SwingUtilities.invokeLater(()->new SimulationFrame().setVisible(true));
    }
}
