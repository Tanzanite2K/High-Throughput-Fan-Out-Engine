package com.fanout.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Metrics {
    public AtomicLong processed = new AtomicLong();
    public ConcurrentHashMap<String, AtomicLong> success = new ConcurrentHashMap<>();
    public ConcurrentHashMap<String, AtomicLong> fail = new ConcurrentHashMap<>();

    public void incSuccess(String s) {
        success.computeIfAbsent(s, k -> new AtomicLong()).incrementAndGet();
    }

    public void incFail(String s) {
        fail.computeIfAbsent(s, k -> new AtomicLong()).incrementAndGet();
    }

    public long startTime = System.currentTimeMillis();

    public long throughput() {
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        if (elapsed <= 0) return processed.get();
        return processed.get() / Math.max(1, elapsed);
    }
}