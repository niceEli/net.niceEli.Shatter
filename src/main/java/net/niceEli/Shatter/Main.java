package net.niceEli.Shatter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Main {
    private static List<ClientHandler> clients = new ArrayList<>();
    private static List<String> banList;

    public static void main(String[] args) {
        // Initialize the ban list
        banList = loadBanList();

        if (args.length < 2) {
            System.out.println("Usage: Shatter [--server] <host> <port>");
            return;
        }

        if (args[0].equals("--server")) {
            // Run as a server
            int port = Integer.parseInt(args[1]);
            runServer(port);
            // Server command input thread
            startServerCommandInput();
        } else {
            // Run as a client
            String host = args[0];
            int port = Integer.parseInt(args[1]);
            runClient(host, port);
        }
    }

    private static class ClientHandler {
        private UUID uuid;
        private PrintWriter writer;
        private Socket clientSocket;

        public ClientHandler(UUID uuid, PrintWriter writer, Socket clientSocket) {
            this.uuid = uuid;
            this.writer = writer;
            this.clientSocket = clientSocket;
        }

        public UUID getUUID() {
            return uuid;
        }

        public PrintWriter getWriter() {
            return writer;
        }

        public Socket getClientSocket() {
            return clientSocket;
        }
    }

    private static void runServer(int port) {
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server is listening on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected from " + clientSocket.getInetAddress());

                UUID clientUUID = UUID.randomUUID();
                PrintWriter clientWriter = new PrintWriter(clientSocket.getOutputStream(), true);
                ClientHandler clientHandler = new ClientHandler(clientUUID, clientWriter, clientSocket);
                clients.add(clientHandler);

                // Send the short 7-letter UUID to the client when they connect
                String shortUUID = clientUUID.toString().substring(0, 7);
                clientWriter.println("Your UUID: " + shortUUID);

                // Create a new thread to handle each client connection
                Thread clientThread = new Thread(() -> {
                    try {
                        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                        String message;
                        while ((message = in.readLine()) != null) {
                            // Broadcast the message to all connected clients
                            sendMessageToAll(message, shortUUID);
                        }

                        // Remove the client when they disconnect
                        clients.remove(clientHandler);
                        clientSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                clientThread.start();
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void runClient(String host, int port) {
        try {
            Socket socket = new Socket(host, port);
            System.out.println("Connected to " + host + " on port " + port);

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // Read and display the short 7-letter UUID when they connect
            String shortUUID = in.readLine();
            System.out.println(shortUUID);

            Thread readThread = new Thread(() -> {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        System.out.println(message);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            readThread.start();

            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
            String userInputMessage;
            while ((userInputMessage = userInput.readLine()) != null) {
                // Ensure client does not send commands to the server
                if (!userInputMessage.startsWith("/")) {
                    out.println(userInputMessage);
                }
            }

            socket.close();
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    // Method to send a message to all clients
    private static void sendMessageToAll(String message, String senderUUID) {
        for (ClientHandler handler : clients) {
            String sender = handler.getUUID().equals(senderUUID) ? "You" : senderUUID;
            String fullMessage = sender + ": " + message;
            handler.getWriter().println(fullMessage);
            handler.getWriter().flush();
            // Log the message to the server console
            System.out.println(fullMessage);
        }
    }

    // Method to load the banlist from a JSON file
    private static List<String> loadBanList() {
        List<String> loadedBanList = new ArrayList<>();
        try {
            Gson gson = new Gson();
            JsonArray banArray = gson.fromJson(new FileReader("banlist.json"), JsonArray.class);
            if (banArray != null) {
                for (JsonElement element : banArray) {
                    loadedBanList.add(element.getAsString());
                }
            }
        } catch (FileNotFoundException e) {
            // If the banlist.json file does not exist, create an empty list
            return new ArrayList<>();
        }
        return loadedBanList;
    }

    // Method to save the banlist to a JSON file
    private static void saveBanList() {
        try {
            Gson gson = new Gson();
            JsonArray banArray = new JsonArray();
            for (String ban : banList) {
                banArray.add(ban);
            }
            String json = gson.toJson(banArray);
            FileWriter writer = new FileWriter("banlist.json");
            writer.write(json);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to start server command input thread
    private static void startServerCommandInput() {
        Thread commandThread = new Thread(() -> {
            BufferedReader serverInput = new BufferedReader(new InputStreamReader(System.in));
            try {
                while (true) {
                    String serverCommand = serverInput.readLine();
                    if (serverCommand != null && !serverCommand.isEmpty()) {
                        // Handle server commands here
                        handleServerCommand(serverCommand);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        commandThread.start();
    }

    // Method to handle server commands
    private static void handleServerCommand(String command) {
        // Implement your server-side command handling logic here
        // For example, you can handle "/kick," "/ban," and "/say" commands
        if (command.startsWith("/kick") && command.length() > 6) {
            String targetUUIDStr = command.substring(6);
            kickClient(targetUUIDStr);
        } else if (command.startsWith("/ban") && command.length() > 5) {
            String targetUUIDStr = command.substring(5);
            banClient(targetUUIDStr);
        } else if (command.startsWith("/say") && command.length() > 5) {
            String messageToSend = command.substring(5);
            // Send server messages to all clients with "Server" as the sender UUID
            sendMessageToAll(messageToSend, "Server");
        }
    }

    // Method to kick a client by UUID
    private static void kickClient(String targetUUIDStr) {
        UUID targetUUID = UUID.fromString(targetUUIDStr);
        for (ClientHandler handler : clients) {
            if (handler.getUUID().equals(targetUUID)) {
                handler.getWriter().println("You have been kicked by the server.");
                handler.getWriter().println("Your connection will be closed.");
                handler.getWriter().flush();
                try {
                    handler.getWriter().close();
                    handler.getClientSocket().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                clients.remove(handler);
                System.out.println("Kicked client with UUID: " + targetUUID);
                break;
            }
        }
    }

    // Method to ban a client by UUID
    private static void banClient(String targetUUIDStr) {
        UUID targetUUID = UUID.fromString(targetUUIDStr);
        // Add the banned UUID to the banlist
        banList.add(targetUUIDStr);
        // Save the updated banlist
        saveBanList();
        // Kick the banned client
        kickClient(targetUUIDStr);
        System.out.println("Banned client with UUID: " + targetUUID);
    }
}
