package com.example.logwatcher.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.util.*;

@Service
public class LogTailService {

    @Value("${logwatcher.file}")
    private String logFilePath;

    private final SimpMessagingTemplate messagingTemplate;

    public LogTailService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void startTailing() {
        File file = new File(logFilePath);
        if (!file.exists()) {
            System.err.println("Log file not found: " + logFilePath);
            return;
        }

        // Start tailing for new lines only
        new Thread(() -> tailFile(file)).start();
    }

    public void sendInitialLines() {
        File file = new File(logFilePath);
        if (!file.exists()) {
            System.err.println("Log file not found: " + logFilePath);
            return;
        }

        // Send the last 10 lines when requested
        try {
            List<String> lastLines = readLastNLines(file, 10);
            for (String line : lastLines) {
                messagingTemplate.convertAndSend("/topic/logs", line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

@Async
public void tailFile(File file) {
    try (RandomAccessFile reader = new RandomAccessFile(file, "r")) {
        long filePointer = file.length(); // start from end
        while (true) {
            long fileLength = file.length();
            if (fileLength < filePointer) {
                filePointer = fileLength;
            }
            if (fileLength > filePointer) {
                reader.seek(filePointer);
                String line;
                while ((line = reader.readLine()) != null) {
                    messagingTemplate.convertAndSend("/topic/logs", line);
                }
                filePointer = reader.getFilePointer();
            }
            Thread.sleep(1000);
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
}

    private List<String> readLastNLines(File file, int n) throws IOException {
        List<String> result = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileLength = raf.length();
            if (fileLength == 0) {
                return result;
            }
            
            long pos = fileLength - 1;
            List<String> lines = new ArrayList<>();
            StringBuilder currentLine = new StringBuilder();
            
            // Start from the very end of file and read backwards
            while (pos >= 0 && lines.size() < n) {
                raf.seek(pos);
                byte b = raf.readByte();
                char c = (char) (b & 0xFF);
                
                if (c == '\n') {
                    if (currentLine.length() > 0) {
                        // Reverse the current line since we built it backwards
                        lines.add(currentLine.reverse().toString());
                        currentLine.setLength(0);
                    }
                } else if (c != '\r') {
                    // Add character to beginning of current line
                    currentLine.append(c);
                }
                pos--;
            }
            
            // Add the first line if we have content and space
            if (currentLine.length() > 0 && lines.size() < n) {
                lines.add(currentLine.reverse().toString());
            }
            
            // Reverse the lines list to get chronological order
            Collections.reverse(lines);
            result = lines;
        }
        return result;
    }
}
