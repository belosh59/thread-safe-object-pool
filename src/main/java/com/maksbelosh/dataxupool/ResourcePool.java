package com.maksbelosh.dataxupool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Used CopyOnWriteArrayList to exclude fail-fast iteration problems
 * get concurrent modification abilities and avoid multiple structures handling via queues, etc.
 *
 * Current implementation synchronized over List and ResourceWrapper,
 * It do not synchronized over specific resource in order to avoid side affect on consumer side of th pool
 *
 * @param <R> - resource Type
 */


public class ResourcePool<R> implements Pool<R> {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private volatile boolean poolClosed = true; // On creation pool by default closed
    private final List<ResourceWrapper<R>> resourceWrappers = new CopyOnWriteArrayList<>();

    public void open() {
        poolClosed = false;
    }

    public boolean isOpen() {
        return !poolClosed;
    }

    public void close() {
        closeNow();
        for (ResourceWrapper<R> resourceWrapper : resourceWrappers) {
            waitOnTheResource(resourceWrapper);
        }
    }

    public void closeNow() {
        poolClosed = true;
    }

    public R acquire() {
        if(poolClosed) {
            throw new IllegalStateException("Object pool closes");
        }

        synchronized (resourceWrappers) {
            while (true) { // Prevent suspicious wakeup of the thread
                for (ResourceWrapper<R> resourceWrapper : resourceWrappers) {
                    if (!resourceWrapper.isAcquired() && !resourceWrapper.isRemoved()) {

                        // double lock check to ensure not one removes while acquire
                        synchronized (resourceWrapper) {
                            if (!resourceWrapper.isAcquired() && !resourceWrapper.isRemoved()) {
                                resourceWrapper.setAcquired(true);
                                return resourceWrapper.getResource();
                            }
                        }
                    }
                }
                try {
                    logger.debug("Thread is waiting for free resource");
                    resourceWrappers.wait();
                    logger.debug("Thread was notified about allocated resource");

                } catch (InterruptedException e) {
                    logger.debug("Waiting thread has bean interrupted");
                    throw new RuntimeException("Waiting thread has bean interrupted", e);
                }
            }
        }
    }

    public R acquire(long timeout, TimeUnit timeUnit) {
        if(poolClosed) {
            throw new IllegalStateException("Object pool closed");
        }

        // we should synchronize readers because each reader should get unique resource
        synchronized (resourceWrappers) {
            long overallTimeout = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
            long outTime = System.currentTimeMillis() + overallTimeout;

            while (System.currentTimeMillis() < outTime) { // Prevent suspicious wakeup of the thread and go out before timeout
                for (ResourceWrapper<R> resourceWrapper : resourceWrappers) {
                    if (!resourceWrapper.isAcquired() && !resourceWrapper.isRemoved()) { // Check both cases for resources

                        // double lock check to ensure not one removes while acquire
                        synchronized (resourceWrapper) {
                            if(!resourceWrapper.isAcquired() && !resourceWrapper.isRemoved()) {
                                resourceWrapper.setAcquired(true);
                                logger.debug("Resource acquired in terms of timeout");
                                return resourceWrapper.getResource();
                            }
                        }
                    }
                }
                try {
                    logger.debug("Thread starts waiting for resource during timeout - {} {}", timeout, timeUnit);
                    resourceWrappers.wait(overallTimeout);
                    logger.debug("Thread wake up from wait in timeout");
                } catch (InterruptedException e) {
                    throw new RuntimeException("Waiting tread has bean interrupted: " +
                            Thread.currentThread().getName(), e);
                }
            }
        }
        return null;
    }

    public void release(R resource) {
        Optional<ResourceWrapper<R>> resourceWrapperOptional = getWrapper(resource);
        if (resourceWrapperOptional.isPresent()) {
            ResourceWrapper<R> resourceWrapper = resourceWrapperOptional.get();
            resourceWrapper.setAcquired(false);

            // Notification for acquire method
            synchronized (resourceWrappers) {
                resourceWrappers.notify();
            }
            // Notification for remove/close method that resource is available
            synchronized (resourceWrapper) {
                resourceWrapper.notifyAll();
            }
        }
    }

    public boolean add(R resource) {
        synchronized (resourceWrappers) { // Prevent simultaneous add operations. No block with acquire WAIT_LIST threads
            // Ensure no duplicates
            Optional<ResourceWrapper<R>> resourceWrapperOptional = getWrapper(resource); // contains impossible because of wrappers
            if (resourceWrapperOptional.isPresent()) {
                return false;
            }

            ResourceWrapper<R> resourceWrapper = new ResourceWrapper<>(resource);
            boolean result = resourceWrappers.add(resourceWrapper);
            resourceWrappers.notify(); // Notify acquire WAIT-LIST about new resource
            return result;
        }
    }

    public boolean remove(R resource) {
        Optional<ResourceWrapper<R>> resourceWrapperOptional = getWrapper(resource);
        if (!resourceWrapperOptional.isPresent()) {
            return false;
        }

        ResourceWrapper<R> resourceWrapper = resourceWrapperOptional.get();
        synchronized(resourceWrapper) {
            if (!resourceWrapper.removed) { // require verification
                resourceWrapper.setRemoved(true); // prevent acquire while remove in progress
                waitOnTheResource(resourceWrapper);
                return resourceWrappers.remove(resourceWrapper);
            } else {
                return false;
            }
        }
    }

    public boolean removeNow(R resource) {
        for (ResourceWrapper<R> resourceWrapper : resourceWrappers) {
            if (resourceWrapper.getResource().equals(resource)) {
                resourceWrapper.setRemoved(true);
                return resourceWrappers.remove(resourceWrapper);
            }
        }
        return false;
    }

    private void waitOnTheResource(ResourceWrapper<R> resourceWrapper) {
        synchronized (resourceWrapper) { // waiting on specified resource monitor
            while (resourceWrapper.isAcquired() && !resourceWrapper.isRemoved()) {
                try {
                    resourceWrapper.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException("Waiting thread has bean interrupted: " +
                            Thread.currentThread().getName(), e);
                }
            }
        }
    }

    private Optional<ResourceWrapper<R>> getWrapper(R resource) {
        return resourceWrappers.stream()
                .filter(wrapper -> wrapper.getResource().equals(resource))
                .findFirst();
    }

    // For testing purpose
    int size() {
        return resourceWrappers.size();
    }

    private static class ResourceWrapper<R> {
        private volatile boolean acquired;
        private volatile boolean removed;
        private final R resource;

        ResourceWrapper(R resource) {
            this.resource = resource;
        }

        boolean isAcquired() {
            return acquired;
        }

        void setAcquired(boolean acquired) {
            this.acquired = acquired;
        }

        R getResource() {
            return resource;
        }

        public boolean isRemoved() {
            return removed;
        }

        public void setRemoved(boolean removed) {
            this.removed = removed;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ResourceWrapper<?> that = (ResourceWrapper<?>) o;

            return resource != null ? resource.equals(that.resource) : that.resource == null;
        }

        @Override
        public int hashCode() {
            return resource != null ? resource.hashCode() : 0;
        }
    }
}
