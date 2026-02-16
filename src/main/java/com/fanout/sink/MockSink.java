package com.fanout.sink;

import java.util.concurrent.CompletableFuture;

import com.fanout.util.SimpleRateLimiter;

public class MockSink implements Sink {

    private final String name;
    private final SimpleRateLimiter limiter;

    private final boolean testMode;

    public MockSink(String name, int rate, boolean testMode) {
        this.name = name;
        this.limiter = new SimpleRateLimiter(rate);
        this.testMode = testMode;
    }

    public CompletableFuture<Boolean> send(String data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!testMode) {
                    limiter.acquire();
                    Thread.sleep(20);
                }
                if (!testMode && Math.random() < 0.1) {
                    throw new RuntimeException("fail");
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    public String getName() {
        return name;
    }
    
    @Override
    public String name() {
        return name;
    }
}