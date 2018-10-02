package com.maksbelosh.dataxupool;

import java.util.concurrent.TimeUnit;

public interface ObjectPool<R> {

    /**
     * The pool shall not allow any resource to be acquired unless the pool is open.
     */
    void open();

    boolean isOpen();

    /**
     * The close() method should be such that it blocks until
     all acquired resources are released.
     */
    void close();

    /**
     * The acquire() method should block until a resource is available.
     * @return
     */
    R acquire();

    /**
     * If a resource cannot be acquired within the timeout
     interval specified in the acquire(long, TimeUnit) method,
     either a null can be returned or an exception can be
     thrown.
     * @param timeout
     * @param timeUnit
     * @return
     */
    R accquire(long timeout, TimeUnit timeUnit);

    /**
     * Resources can be released at any time.
     * @param resource
     */
    void release(R resource);

    /**
     * The add(R) method return true if the pool
     was modified as a result of the method call or false if
     the pool was not modified.
     * @param resource
     * @return
     */
    boolean add(R resource);

    /**
     * The remove(R) methods return true if the pool
     was modified as a result of the method call or false if
     the pool was not modified. If the resource
     that is being removed is currently in use, the remove
     operation will block until that resource has been released.
     * @param resource
     * @return
     */
    boolean remove(R resource);

    /**
     * Removes the given resource immediately without waiting
     for it to be released. It returns true if the call
     resulted in the pool being modified and false otherwise.
     */
    boolean removeNow(R resource);

    /**
     *  closes the pool
     immediately without waiting for all acquired resources to
     be released.
     */
    void closeNow();
}
