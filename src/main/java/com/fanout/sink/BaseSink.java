
package com.fanout.sink;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class BaseSink implements Sink {

    private final Semaphore rateLimiter;

    public BaseSink(int rateLimitPerSecond) {
        this.rateLimiter = new Semaphore(rateLimitPerSecond);
        Executors.newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(() -> {
                rateLimiter.release(rateLimitPerSecond - rateLimiter.availablePermits());
            }, 1, 1, TimeUnit.SECONDS);
    }

    protected void acquire() throws InterruptedException {
        rateLimiter.acquire();
    }
}
