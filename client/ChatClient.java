package client;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.*;

public class ChatClient {
    private JFrame frame;
    private JTextArea chatArea;
    private JTextField inputField;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public ChatClient(String host, int port) {
        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            setupGUI();

            handleAuthentication();

            new Thread(new IncomingReader()).start();

            inputField.addActionListener(e -> {
                String text = inputField.getText().trim();
                if (!text.isEmpty()) {
                    out.println(text);
                    inputField.setText("");
                    logToFile("Me: " + text);
                }
            });

        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Could not connect to server.");
        }
    }

    private void setupGUI() {
        frame = new JFrame("Chat Client");
        frame.setSize(500, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        inputField = new JTextField();
        frame.add(inputField, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private void handleAuthentication() throws IOException {
        while (true) {
            if ("LOGIN_REGISTER".equals(in.readLine())) {
                String[] options = {"Login", "Register"};
                int choice = JOptionPane.showOptionDialog(null, "Choose an option", "Authentication",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);

                String username = JOptionPane.showInputDialog("Username:");
                String password = JOptionPane.showInputDialog("Password:");
                if (username == null || password == null) System.exit(0);

                if (choice == 1) {
                    out.println("register:" + username + ":" + password);
                    if ("EXISTS".equals(in.readLine())) {
                        showMessage("Username already exists.");
                    } else {
                        showMessage("Registration successful.");
                        break;
                    }
                } else {
                    out.println("login:" + username + ":" + password);
                    String resp = in.readLine();
                    if ("LOGGEDIN".equals(resp)) {
                        showMessage("Login successful.");
                        break;
                    } else {
                        showMessage("Login failed.");
                    }
                }
            }
        }
    }

    private void showMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(msg + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
        logToFile(msg);
    }

    private void logToFile(String msg) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            Files.write(Paths.get("chat_history.txt"), ("[" + timestamp + "] " + msg + "\n").getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }

    private class IncomingReader implements Runnable {
        public void run() {
            try {
                String msg;
                while ((msg = in.readLine()) != null) showMessage(msg);
            } catch (IOException e) {
                showMessage("Disconnected.");
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatClient("192.168.1.37", 12345));
    }
}
