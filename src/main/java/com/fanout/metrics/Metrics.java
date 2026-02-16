
package com.fanout.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Metrics {

    public static AtomicLong totalProcessed = new AtomicLong();
    public static ConcurrentHashMap<String, AtomicLong> success = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, AtomicLong> failure = new ConcurrentHashMap<>();
    
}
