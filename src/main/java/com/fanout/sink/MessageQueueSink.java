
package com.fanout.sink;

import java.util.concurrent.*;

public class MessageQueueSink extends BaseSink {

    public MessageQueueSink(int rate) {
        super(rate);
    }

    public CompletableFuture<Boolean> send(String data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                acquire();
                Thread.sleep(10);
                return Math.random() > 0.05;
            } catch (Exception e) {
                return false;
            }
        });
    }

    public String name() { return "MQ"; }
}
