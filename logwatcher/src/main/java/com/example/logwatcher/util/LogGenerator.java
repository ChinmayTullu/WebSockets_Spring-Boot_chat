package com.example.logwatcher.util;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

public class LogGenerator {
    public static void main(String[] args) throws IOException, InterruptedException {
        String logFilePath = "D:/SpringBoot/sample.log";
        try (FileWriter writer = new FileWriter(logFilePath, true)) {
            for (int i = 1; i <= 500; i++) {
                writer.write(LocalDateTime.now() + " INFO  Log line " + i + "\n");
                writer.flush();
                TimeUnit.MILLISECONDS.sleep(1000);
            }
        }
    }
}
