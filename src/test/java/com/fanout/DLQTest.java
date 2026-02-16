package com.fanout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fanout.dlq.DeadLetterQueue;

/**
 * Dead Letter Queue tests
 */
public class DLQTest {

    private DeadLetterQueue dlq;
    
    @TempDir
    java.nio.file.Path tempDir;

    @BeforeEach
    void setUp() {
        String dlqPath = tempDir.resolve("dlq.jsonl").toString();
        dlq = new DeadLetterQueue(dlqPath, true);
    }

    @Test
    void testRecordFailure() {
        // Act
        dlq.recordFailure("{\"id\":1}", "REST", 3, "Max retries exceeded");
        
        // Wait for async write
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Assert
        assertEquals(1, dlq.getFailedCount());
        assertFalse(dlq.getFailedRecords().isEmpty());
    }

    @Test
    void testMultipleFailures() {
        // Act
        dlq.recordFailure("{\"id\":1}", "REST", 3, "Network timeout");
        dlq.recordFailure("{\"id\":2}", "GRPC", 3, "Invalid response");
        dlq.recordFailure("{\"id\":3}", "MQ", 3, "Connection refused");
        
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Assert
        assertEquals(3, dlq.getFailedCount());
    }

    @Test
    void testDLQDisabled() {
        // Arrange
        DeadLetterQueue disabledDLQ = new DeadLetterQueue("dummy-path", false);

        // Act
        disabledDLQ.recordFailure("{\"id\":1}", "REST", 3, "Test");

        // Assert
        assertEquals(0, disabledDLQ.getFailedCount());
    }

    @Test
    void testFailedRecordJson() {
        // Arrange
        DeadLetterQueue.FailedRecord failed = new DeadLetterQueue.FailedRecord(
            "{\"id\":1}",
            "REST",
            3,
            "Test error",
            "2024-01-01T00:00:00Z"
        );

        // Act
        String jsonLine = failed.toJsonLine();

        // Assert
        assertTrue(jsonLine.contains("\"id\":1"));
        assertTrue(jsonLine.contains("REST"));
        assertTrue(jsonLine.contains("attempts"));
    }

    @Test
    void testDLQClear() {
        // Arrange
        dlq.recordFailure("{\"id\":1}", "REST", 3, "Test");
        dlq.recordFailure("{\"id\":2}", "GRPC", 3, "Test");

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        dlq.clear();

        // Assert
        assertEquals(0, dlq.getFailedCount());
    }
}
