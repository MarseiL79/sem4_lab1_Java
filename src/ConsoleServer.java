import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ConsoleServer {
    private final int port = 12345;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final ExecutorService exec = Executors.newCachedThreadPool();

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server started on port " + port);
        while (true) {
            Socket socket = serverSocket.accept();
            exec.submit(new ClientHandler(socket));
        }
    }

    private void broadcastClientList() {
        String listMsg = "CLIENTS:" + String.join(",", clients.keySet());
        clients.values().forEach(h -> h.send(listMsg));
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private String username;
        private BufferedReader in;
        private PrintWriter out;

        ClientHandler(Socket sock) {
            this.socket = sock;
        }

        @Override
        public void run() {
            try {
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // регистрация
                username = in.readLine();
                clients.put(username, this);
                System.out.println(username + " connected");
                broadcastClientList();

                // цикл обработки
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("TRANSFER:")) {
                        // TRANSFER:target:percent
                        String[] parts = line.split(":");
                        String target = parts[1], percent = parts[2];
                        ClientHandler tgt = clients.get(target);
                        if (tgt != null) {
                            tgt.send("TRANSFER_FROM:" + username + ":" + percent);
                        }
                    }
                }
            } catch (IOException e) {
                // игнорируем
            } finally {
                clients.remove(username);
                System.out.println(username + " disconnected");
                broadcastClientList();
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        void send(String msg) {
            out.println(msg);
        }
    }

    public static void main(String[] args) throws IOException {
        new ConsoleServer().start();
    }
}
