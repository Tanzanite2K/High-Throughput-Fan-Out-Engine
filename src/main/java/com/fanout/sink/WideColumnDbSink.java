
package com.fanout.sink;

import java.util.concurrent.*;

public class WideColumnDbSink extends BaseSink {

    public WideColumnDbSink(int rate) {
        super(rate);
    }

    public CompletableFuture<Boolean> send(String data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                acquire();
                Thread.sleep(5);
                return Math.random() > 0.02;
            } catch (Exception e) {
                return false;
            }
        });
    }

    public String name() { return "DB"; }
}
