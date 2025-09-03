package com.example.chat;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@ServerEndpoint("/ws/chat")
public class ChatEndpoint {

    // Map to store username -> session mapping
    private static Map<String, Session> userSessions = new ConcurrentHashMap<>();
    // Map to store session -> username mapping  
    private static Map<Session, String> sessionUsers = new ConcurrentHashMap<>();
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public ChatEndpoint() {
        System.out.println("ChatEndpoint constructor called");
    }

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("New connection: " + session.getId());
        // Send connection confirmation
        sendToSession(session, createSystemMessage("Connected to chat server. Please set your username."));
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            System.out.println("Received from " + session.getId() + ": " + message);
            
            JsonNode messageNode = objectMapper.readTree(message);
            String type = messageNode.get("type").asText();
            
            switch (type) {
                case "setUsername":
                    handleSetUsername(messageNode, session);
                    break;
                case "privateMessage":
                    handlePrivateMessage(messageNode, session);
                    break;
                case "getUserList":
                    sendUserList(session);
                    break;
                default:
                    sendToSession(session, createErrorMessage("Unknown message type"));
            }
            
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            sendToSession(session, createErrorMessage("Invalid message format"));
        }
    }

    @OnClose
    public void onClose(Session session) {
        String username = sessionUsers.remove(session);
        if (username != null) {
            userSessions.remove(username);
            System.out.println("User " + username + " disconnected");
            broadcastUserList();
        }
        System.out.println("Connection closed: " + session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.out.println("Error from " + session.getId() + ": " + throwable.getMessage());
    }
    
    private void handleSetUsername(JsonNode messageNode, Session session) {
        String username = messageNode.get("username").asText().trim();
        
        if (username.isEmpty()) {
            sendToSession(session, createErrorMessage("Username cannot be empty"));
            return;
        }
        
        if (userSessions.containsKey(username)) {
            sendToSession(session, createErrorMessage("Username already taken"));
            return;
        }
        
        // Remove old username if exists
        String oldUsername = sessionUsers.get(session);
        if (oldUsername != null) {
            userSessions.remove(oldUsername);
        }
        
        // Set new username
        userSessions.put(username, session);
        sessionUsers.put(session, username);
        
        sendToSession(session, createSystemMessage("Username set to: " + username));
        broadcastUserList();
        
        System.out.println("User " + username + " joined the chat");
    }
    
    private void handlePrivateMessage(JsonNode messageNode, Session senderSession) {
        String senderUsername = sessionUsers.get(senderSession);
        if (senderUsername == null) {
            sendToSession(senderSession, createErrorMessage("Please set your username first"));
            return;
        }
        
        String recipientUsername = messageNode.get("to").asText();
        String messageText = messageNode.get("message").asText();
        
        Session recipientSession = userSessions.get(recipientUsername);
        if (recipientSession == null) {
            sendToSession(senderSession, createErrorMessage("User " + recipientUsername + " not found"));
            return;
        }
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        
        // Send message to recipient
        String messageToRecipient = createPrivateMessage(senderUsername, recipientUsername, messageText, timestamp, false);
        sendToSession(recipientSession, messageToRecipient);
        
        // Send confirmation to sender
        String messageToSender = createPrivateMessage(senderUsername, recipientUsername, messageText, timestamp, true);
        sendToSession(senderSession, messageToSender);
        
        System.out.println("Private message from " + senderUsername + " to " + recipientUsername + ": " + messageText);
    }
    
    private void sendUserList(Session session) {
        try {
            String userListJson = "{"
                + "\"type\": \"userList\","
                + "\"users\": [" + String.join(",", userSessions.keySet().stream()
                    .map(user -> "\"" + user + "\"").toArray(String[]::new)) + "]"
                + "}";
            sendToSession(session, userListJson);
        } catch (Exception e) {
            System.err.println("Error sending user list: " + e.getMessage());
        }
    }
    
    private void broadcastUserList() {
        for (Session session : sessionUsers.keySet()) {
            sendUserList(session);
        }
    }
    
    private void sendToSession(Session session, String message) {
        if (session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                System.err.println("Error sending message to session: " + e.getMessage());
            }
        }
    }
    
    private String createSystemMessage(String message) {
        return "{"
            + "\"type\": \"system\","
            + "\"message\": \"" + escapeJson(message) + "\","
            + "\"timestamp\": \"" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\""
            + "}";
    }
    
    private String createErrorMessage(String message) {
        return "{"
            + "\"type\": \"error\","
            + "\"message\": \"" + escapeJson(message) + "\","
            + "\"timestamp\": \"" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\""
            + "}";
    }
    
    private String createPrivateMessage(String from, String to, String message, String timestamp, boolean isSent) {
        return "{"
            + "\"type\": \"privateMessage\","
            + "\"from\": \"" + escapeJson(from) + "\","
            + "\"to\": \"" + escapeJson(to) + "\","
            + "\"message\": \"" + escapeJson(message) + "\","
            + "\"timestamp\": \"" + timestamp + "\","
            + "\"isSent\": " + isSent
            + "}";
    }
    
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
