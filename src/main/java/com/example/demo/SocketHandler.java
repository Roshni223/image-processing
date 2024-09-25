package com.example.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class SocketHandler extends TextWebSocketHandler {

    private static final long TIMEOUT_THRESHOLD = 1200000; // 20 minutes
    public static final Set<WebSocketSession> listeners = new CopyOnWriteArraySet<>();

    @Autowired
    private CrowdMonitoringProperties crowdMonitoringProperties;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = generateSessionId(); // Implement this method to generate a unique session ID
        session.getAttributes().put("sessionId", sessionId);
        listeners.add(session);
        session.sendMessage(new TextMessage("SESSION_ID:" + sessionId));
        // Send session ID to the client
        log.info("Session ID:" + sessionId);
        log.info("New session connected! Connected listeners: {}", listeners.size());
    }

    @Scheduled(fixedDelay = 60000) // Check every minute
    public void checkForIdleSessions() {
        long currentTime = System.currentTimeMillis();
        listeners.removeIf(session -> {
            Long lastActivity = (Long) session.getAttributes().get("lastActivity");
            if (lastActivity != null && currentTime - lastActivity > TIMEOUT_THRESHOLD) {
                try {
                    session.close();
                } catch (IOException e) {
                    log.error("Error closing idle session", e);
                }
                return true;
            }
            return false;
        });
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String payload = message.getPayload().toString();
        sendMessage(payload, session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        if (session.isOpen()) {
            session.close();
        }
        log.info("Error in WebSocket transport: {}", exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        listeners.remove(session);
        log.info("Session closed! Connected listeners: {}", listeners.size());
    }

    public void executeScriptAndBroadcast(String modelName, String filePath, String sessionId) {
        System.out.println("model name :: " + modelName);
        System.out.println("map :: " + crowdMonitoringProperties.getMODEL_VS_CHECKPOINT());
        String scriptPath = crowdMonitoringProperties.getMODEL_VS_CHECKPOINT().get(modelName);
        System.out.println("scriptPath :: " + scriptPath);

        try {
            List<String> command = new ArrayList<>();
            command.add("/bin/bash");
            command.add(scriptPath);
            command.add(filePath);
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                sendMessageToSession(line, sessionId); // Send each line to the specific session
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                sendMessageToSession("Processing completed successfully for: " + filePath, sessionId);
            } else {
                sendMessageToSession("Script failed with exit code: " + exitCode, sessionId);
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendMessageToSession("Error during script execution: " + e.getMessage(), sessionId);
        }
    }
    public void sendMessageToSession(String message, String sessionId) {
        for (WebSocketSession session : listeners) {
            String id = (String) session.getAttributes().get("sessionId");
            if (session.isOpen() && id.equals(sessionId)) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    log.error("Error sending message to session {}: {}", sessionId, e.getMessage());
                }
            }
        }
    }


    public void sendMessage(String message, String sessionId) {
        for (WebSocketSession session : listeners) {
            String id = (String) session.getAttributes().get("sessionId");
            if (session.isOpen() && id.equals(sessionId)) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    log.error("Error sending message", e);
                }
            }
        }
    }

    public void broadcastMessage(String message) {
        for (WebSocketSession session : listeners) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    log.error("Error sending broadcast message", e);
                }
            }
        }
    }

    private String generateSessionId() {
        // Implement your logic to generate a unique session ID
        return Long.toHexString(System.currentTimeMillis());
    }


public byte[] downloadFile(String fileName){
        final String downloadDirectory = "/home/vassarlabs/model_output/";
        try {
            if (fileName.contains(".mp4")){
                fileName = fileName.replace(".mp4", ".avi");
            }
            File file = new File(downloadDirectory, fileName);

            if (!file.exists()) {
                throw new FileNotFoundException(fileName);
            }

            return Files.readAllBytes(file.toPath());
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
}
