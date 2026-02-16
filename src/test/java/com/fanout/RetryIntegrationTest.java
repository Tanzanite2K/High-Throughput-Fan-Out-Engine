package com.fanout;

import org.junit.jupiter.api.Test;

import com.fanout.core.FanOutOrchestrator;

import static org.junit.jupiter.api.Assertions.*;

class RetryIntegrationTest {

    @Test
    void retryScenario() throws Exception {
        FanOutOrchestrator orchestrator = new FanOutOrchestrator(true);
        orchestrator.startTestMode(5);

        assertTrue(orchestrator.getMetrics().processed.get() > 0);
    }
}