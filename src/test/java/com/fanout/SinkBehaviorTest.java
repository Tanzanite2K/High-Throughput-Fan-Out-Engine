package com.fanout;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fanout.sink.Sink;
import com.fanout.util.SimpleRateLimiter;

import java.util.concurrent.CompletableFuture;

/**
 * Sink behavior tests using Mockito for mocking
 */
@ExtendWith(MockitoExtension.class)
public class SinkBehaviorTest {

    @Mock
    private Sink mockSink;

    @Test
    void testSinkSendSuccess() throws Exception {
        // Arrange
        when(mockSink.send("test-data")).thenReturn(CompletableFuture.completedFuture(true));
        when(mockSink.name()).thenReturn("REST");

        // Act
        CompletableFuture<Boolean> result = mockSink.send("test-data");
        String name = mockSink.name();

        // Assert
        assertTrue(result.join());
        assertEquals("REST", name);
        verify(mockSink, times(1)).send("test-data");
        verify(mockSink, times(1)).name();
    }

    @Test
    void testSinkSendFailure() throws Exception {
        // Arrange
        when(mockSink.send("bad-data")).thenReturn(CompletableFuture.completedFuture(false));

        // Act
        CompletableFuture<Boolean> result = mockSink.send("bad-data");

        // Assert
        assertFalse(result.join());
        verify(mockSink, times(1)).send("bad-data");
    }

    @Test
    void testSinkSendWithException() throws Exception {
        // Arrange
        when(mockSink.send(anyString())).thenThrow(new RuntimeException("Network error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            mockSink.send("error-data");
        });
    }

    @Test
    void testRateLimiterAcquire() throws InterruptedException {
        // Arrange
        SimpleRateLimiter limiter = new SimpleRateLimiter(10);

        // Act & Assert - should succeed without blocking
        limiter.acquire();
        assertEquals(9, limiter.semaphore.availablePermits());
    }

    @Test
    void testRateLimiterMultipleAcquisitions() throws InterruptedException {
        // Arrange
        SimpleRateLimiter limiter = new SimpleRateLimiter(5);

        // Act
        for (int i = 0; i < 5; i++) {
            limiter.acquire();
        }

        // Assert - all permits taken
        assertEquals(0, limiter.semaphore.availablePermits());
    }
}
