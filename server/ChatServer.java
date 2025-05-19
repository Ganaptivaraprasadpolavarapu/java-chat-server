package server;

import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static final Set<ClientHandler> clients = new HashSet<>();
    private static final Map<String, String> users = new HashMap<>();
    private static final File userFile = new File("users.txt");

    public static void main(String[] args) {
        loadUsers();
        System.out.println("Server started on port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                new Thread(new ClientHandler(serverSocket.accept())).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadUsers() {
        if (!userFile.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(userFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] split = line.split(":", 2);
                if (split.length == 2) users.put(split[0], split[1]);
            }
        } catch (IOException e) {
            System.out.println("Error loading users.");
        }
    }

    private static synchronized void saveUser(String username, String hash) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(userFile, true))) {
            writer.println(username + ":" + hash);
        } catch (IOException e) {
            System.out.println("Could not save user.");
        }
        users.put(username, hash);
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Auth loop
                while (true) {
                    out.println("LOGIN_REGISTER"); // signal client to prompt
                    String input = in.readLine();
                    if (input == null) return;
                    String[] parts = input.split(":", 3);
                    if (parts[0].equals("register")) {
                        String user = parts[1];
                        if (users.containsKey(user)) {
                            out.println("EXISTS");
                        } else {
                            String hash = PasswordUtil.hashPassword(parts[2]);
                            saveUser(user, hash);
                            this.username = user;
                            out.println("REGISTERED");
                            break;
                        }
                    } else if (parts[0].equals("login")) {
                        String user = parts[1];
                        String hash = PasswordUtil.hashPassword(parts[2]);
                        if (hash.equals(users.get(user))) {
                            this.username = user;
                            out.println("LOGGEDIN");
                            break;
                        } else {
                            out.println("INVALID");
                        }
                    }
                }

                clients.add(this);
                broadcast(username + " joined.");

                String msg;
                while ((msg = in.readLine()) != null) {
                    if (msg.startsWith("/msg ")) {
                        String[] split = msg.split(" ", 3);
                        sendPrivate(split[1], username + " (private): " + split[2]);
                    } else {
                        broadcast(username + ": " + msg);
                    }
                }
            } catch (IOException e) {
                System.out.println("Connection closed");
            } finally {
                clients.remove(this);
                broadcast(username + " left.");
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void broadcast(String msg) {
            for (ClientHandler ch : clients) ch.out.println(msg);
        }

        private void sendPrivate(String target, String msg) {
            for (ClientHandler ch : clients) {
                if (ch.username.equalsIgnoreCase(target)) {
                    ch.out.println(msg);
                    return;
                }
            }
            out.println("User not found: " + target);
        }
    }
}
