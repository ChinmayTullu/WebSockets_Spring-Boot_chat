package com.example.chat;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@ServerEndpoint("/ws/chat")
public class ChatEndpoint {

    private static Set<Session> sessions = new CopyOnWriteArraySet<>();

    public ChatEndpoint() {
        System.out.println("ChatEndpoint constructor called");
    }

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        System.out.println("New connection: " + session.getId());
        sendMessageToAll("User " + session.getId() + " joined the chat.");
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        System.out.println("Received from " + session.getId() + ": " + message);
        sendMessageToAll("User " + session.getId() + ": " + message);
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        System.out.println("Connection closed: " + session.getId());
        sendMessageToAll("User " + session.getId() + " left the chat.");
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.out.println("Error from " + session.getId() + ": " + throwable.getMessage());
    }

    private void sendMessageToAll(String message) {
        for (Session s : sessions) {
            if (s.isOpen()) {
                try {
                    s.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
