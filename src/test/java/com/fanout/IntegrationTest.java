package com.fanout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.fanout.core.FanOutOrchestrator;

public class IntegrationTest {

    @Test
    void pipelineRuns() throws Exception {
        FanOutOrchestrator orchestrator = new FanOutOrchestrator(true);
        orchestrator.startTestMode(3);

        assertEquals(3, orchestrator.getMetrics().processed.get());
    }
}
