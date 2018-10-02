package com.maksbelosh.dataxupool;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ThreadSafeObjectPool<R> implements ObjectPool<R> {
    private ConcurrentLinkedQueue<R> pool;

    private ScheduledExecutorService executorService;


    public void open() {

    }

    public boolean isOpen() {
        return false;
    }

    public void close() {

    }

    public R acquire() {
        return null;
    }

    public R accquire(long timeout, TimeUnit timeUnit) {
        return null;
    }

    public void release(R resource) {

    }

    public boolean add(R resource) {
        return false;
    }

    public boolean remove(R resource) {
        return false;
    }

    public boolean removeNow(R resource) {
        return false;
    }

    public void closeNow() {

    }
}
