package com.fanout.sink;

import java.util.concurrent.CompletableFuture;

public interface Sink {
    CompletableFuture<Boolean> send(String data);
    String name();
}