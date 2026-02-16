package com.fanout;

import com.fanout.core.FanOutOrchestrator;

public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("🚀 Starting High Throughput Fan-Out Engine...");

        FanOutOrchestrator orchestrator = new FanOutOrchestrator();

        // Check if test mode is passed
        boolean testMode = false;
        if (args != null && args.length > 0) {
            if ("--testMode".equalsIgnoreCase(args[0])) {
                testMode = true;
                System.out.println("Running in TEST MODE...");
            }
        }

        orchestrator.start(testMode);

        System.out.println("Fan-Out Engine finished execution.");
    }
}