package com.maksbelosh.dataxupool;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public interface Pool<R> {

    /**
     * The pool shall not allow any resource to be acquired unless the pool is open.
     */
    void open();

    boolean isOpen();

    /**
     * The close() method blocks current thread until all acquired resources are released.
     */
    void close();

    /**
     * The acquire() method block until a resource is available.
     * @return resource
     */
    R acquire();

    /**
     * If a resource cannot be acquired within the timeout
     * interval specified in the acquire(long, TimeUnit) method,
     * either a null can be returned or an exception can be thrown.
     * // TODO: TODO: Figure out requirement -if I'm able to return Optioanl instead of null
     * @param timeout - count of time units
     * @param timeUnit - specified units of timeout count
     * @return
     */
    Optional<R> acquire(long timeout, TimeUnit timeUnit);

    /**
     * Resources can be released at any time.
     * @param resource - resource to be release
     */
    void release(R resource);

    /**
     * The add(R) method return true if the pool
     * was modified as a result of the method call or false if the pool was not modified.
     * It is possible to add resources when pool is not opened or it is closes
     * @param resource - resource to be added
     * @return boolean status of add operation
     */
    boolean add(R resource);

    /**
     * The remove(R) methods return true if the pool
     * was modified as a result of the method call or false if
     * the pool was not modified. If the resource
     * that is being removed is currently in use, the remove
     * operation will block until that resource has been released.
     * @param resource - resource to be removed
     * @return boolean status of remove operation
     */
    boolean remove(R resource);

    /**
     * Removes the given resource immediately without waiting
     * for it to be released. It returns true if the call
     * resulted in the pool being modified and false otherwise.
     * @param resource - resource to be removed
     * @return boolean status of remove operation
     */
    boolean removeNow(R resource);

    /**
     * closes the pool immediately without waiting for all acquired resources to be released.
     */
    void closeNow();
}
