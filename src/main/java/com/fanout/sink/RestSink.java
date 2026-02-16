
package com.fanout.sink;

import java.util.concurrent.*;

public class RestSink extends BaseSink {

    public RestSink(int rate) {
        super(rate);
    }

    public CompletableFuture<Boolean> send(String data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                acquire();
                Thread.sleep(20);
                return Math.random() > 0.1;
            } catch (Exception e) {
                return false;
            }
        });
    }

    public String name() { return "REST"; }
}
