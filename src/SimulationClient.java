import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.*;

public class SimulationClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private Consumer<List<String>> onClientList;
    private BiConsumer<String, Integer> onTransferRequest;

    public SimulationClient(String host, int port, String username) throws IOException {
        socket = new Socket(host, port);
        in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        // отправляем имя для регистрации
        out.println(username);

        // слушаем в отдельном потоке
        new Thread(this::listen).start();
    }

    private void listen() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("CLIENTS:")) {
                    List<String> names = Arrays.asList(line.substring(8).split(","));
                    if (onClientList != null) onClientList.accept(names);
                } else if (line.startsWith("TRANSFER_FROM:")) {
                    String[] p = line.split(":");
                    String from = p[1];
                    int percent = Integer.parseInt(p[2]);
                    if (onTransferRequest != null) onTransferRequest.accept(from, percent);
                }
            }
        } catch (IOException ignored) {}
    }

    /** Назначить обработчик обновления списка клиентов */
    public void setOnClientList(Consumer<List<String>> handler) {
        this.onClientList = handler;
    }

    /** Назначить обработчик входящей передачи */
    public void setOnTransferRequest(BiConsumer<String, Integer> handler) {
        this.onTransferRequest = handler;
    }

    /** Инициировать передачу percent% птиц клиенту target */
    public void transferTo(String target, int percent) {
        out.println("TRANSFER:" + target + ":" + percent);
    }

    public void close() throws IOException {
        socket.close();
    }
}
