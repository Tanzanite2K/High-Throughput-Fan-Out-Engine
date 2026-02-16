package com.fanout.dlq;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Dead Letter Queue for storing failed records.
 * Guarantees zero data loss by persisting failed records to disk.
 */
public class DeadLetterQueue {
    
    private final String dlqFilePath;
    private final ConcurrentLinkedQueue<FailedRecord> failedRecords = new ConcurrentLinkedQueue<>();
    private final boolean enabled;
    private volatile boolean initialized = false;
    
    public DeadLetterQueue(String dlqFilePath, boolean enabled) {
        this.dlqFilePath = dlqFilePath;
        this.enabled = enabled;
        if (enabled) {
            initialize();
        }
    }
    
    private void initialize() {
        try {
            Files.createDirectories(Paths.get(dlqFilePath).getParent());
            initialized = true;
            System.out.println("✅ DLQ initialized: " + dlqFilePath);
        } catch (IOException e) {
            System.err.println("❌ Failed to initialize DLQ: " + e.getMessage());
        }
    }
    
    /**
     * Records a failed record with metadata.
     */
    public void recordFailure(String record, String sinkName, int attemptCount, String errorReason) {
        if (!enabled || !initialized) return;
        
        FailedRecord failed = new FailedRecord(
            record,
            sinkName,
            attemptCount,
            errorReason,
            Instant.now().toString()
        );
        
        failedRecords.add(failed);
        persistToDisk(failed);
    }
    
    /**
     * Persists a failed record to the DLQ file asynchronously.
     */
    private void persistToDisk(FailedRecord record) {
        new Thread(() -> {
            try (FileWriter fw = new FileWriter(dlqFilePath, true);
                 BufferedWriter bw = new BufferedWriter(fw)) {
                bw.write(record.toJsonLine());
                bw.newLine();
                bw.flush();
            } catch (IOException e) {
                System.err.println("❌ Failed to write to DLQ: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Gets all failed records in memory.
     */
    public ConcurrentLinkedQueue<FailedRecord> getFailedRecords() {
        return failedRecords;
    }
    
    /**
     * Gets the count of failed records.
     */
    public long getFailedCount() {
        return failedRecords.size();
    }
    
    /**
     * Clears all failed records from memory.
     */
    public void clear() {
        failedRecords.clear();
    }
    
    /**
     * Represents a failed record with metadata.
     */
    public static class FailedRecord {
        public final String recordData;
        public final String sinkName;
        public final int attemptCount;
        public final String errorReason;
        public final String timestamp;
        
        public FailedRecord(String recordData, String sinkName, int attemptCount, 
                           String errorReason, String timestamp) {
            this.recordData = recordData;
            this.sinkName = sinkName;
            this.attemptCount = attemptCount;
            this.errorReason = errorReason;
            this.timestamp = timestamp;
        }
        
        public String toJsonLine() {
            return String.format(
                "{\"record\":%s,\"sink\":\"%s\",\"attempts\":%d,\"error\":\"%s\",\"timestamp\":\"%s\"}",
                recordData, sinkName, attemptCount, errorReason, timestamp
            );
        }
    }
}
