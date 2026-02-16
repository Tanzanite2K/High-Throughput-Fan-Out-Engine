package com.fanout.util;

import java.util.concurrent.Semaphore;

public class SimpleRateLimiter {
    public final Semaphore semaphore;

    public SimpleRateLimiter(int rate){
        this.semaphore = new Semaphore(rate);
        new Thread(() -> {
            while(true){
                try{
                    Thread.sleep(1000);
                    semaphore.release(rate - semaphore.availablePermits());
                }catch(Exception ignored){}
            }
        }).start();
    }

    public void acquire() throws InterruptedException{
        semaphore.acquire();
    }
}