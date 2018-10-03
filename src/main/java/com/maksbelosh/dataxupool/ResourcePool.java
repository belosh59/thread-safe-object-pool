package com.maksbelosh.dataxupool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Used CopyOnWriteArrayList to exclude fail-fast iteration problems
 * get concurrent modification abilities
 * and avoid multiple structures handling via queues, etc.
 * @param <R>
 */

public class ResourcePool<R> implements Pool<R> {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private volatile boolean isPoolClosed = true; // On creation pool by default closed
    private final List<ResourceWrapper<R>> resourceWrappers = new CopyOnWriteArrayList<>();

    public void open() {
        isPoolClosed = false;
    }

    public boolean isOpen() {
        return !isPoolClosed;
    }

    public void close() {
        closeNow();
        for (ResourceWrapper<R> resourceWrapper : resourceWrappers) {
            waitOnTheResource(resourceWrapper);
        }
    }

    public void closeNow() {
        isPoolClosed = true;
    }

    public synchronized R acquire() {
        if(isPoolClosed) {
            throw new IllegalStateException("Object pool closes");
        }

        synchronized (resourceWrappers) {
            while (true) { // Prevent suspicious wakeup of the thread
                for (ResourceWrapper<R> resourceWrapper : resourceWrappers) {
                    if (!resourceWrapper.isAcquired()) {
                        resourceWrapper.setAcquired(true);
                        return resourceWrapper.getResource();
                    }
                }
                try {
                    logger.debug("Thread is waiting for free resource");
                    resourceWrappers.wait();
                    logger.debug("Thread was notified about free resource");

                } catch (InterruptedException e) {
                    logger.debug("Waiting tread has bean interrupted");
                    throw new RuntimeException("Waiting tread has bean interrupted", e);
                }
            }
        }
    }

    public Optional<R> acquire(long timeout, TimeUnit timeUnit) {
        if(isPoolClosed) {
            throw new IllegalStateException("Object pool closed");
        }

        synchronized (resourceWrappers) {
            long overallTimeout = timeUnit.convert(timeout, TimeUnit.MILLISECONDS);
            long outTime = System.currentTimeMillis() + overallTimeout;

            while (System.currentTimeMillis() < outTime) { // Prevent suspicious wakeup of the thread and go out only on timeout
                for (ResourceWrapper<R> resourceWrapper : resourceWrappers) {
                    if (!resourceWrapper.isAcquired() && !resourceWrapper.isRemoved()) { // Check both cases for resources
                        resourceWrapper.setAcquired(true);
                        return Optional.of(resourceWrapper.getResource());
                    }
                }
                try {
                    resourceWrappers.wait(overallTimeout);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Waiting tread has bean interrupted: " +
                            Thread.currentThread().getName(), e);
                }
            }
        }
        return Optional.empty();
    }

    public void release(R resource) {
        Optional<ResourceWrapper<R>> resourceWrapperOptional = getWrapper(resource);
        if (resourceWrapperOptional.isPresent()) {
            ResourceWrapper<R> resourceWrapper = resourceWrapperOptional.get();
            resourceWrapper.setAcquired(false);
            synchronized (resourceWrappers) {
                resourceWrappers.notify(); // Notification for acquire method
            }
            synchronized (resourceWrapper.getResource()) {
                resourceWrapper.getResource().notifyAll(); // Notification for remove/close method that resource is available
            }
        }
    }

    public boolean add(R resource) {
        synchronized (resourceWrappers) { // Prevent simultaneous add operations. No block with acquire WAIT_LIST threads
            // Ensure no duplicates
            Optional<ResourceWrapper<R>> resourceWrapperOptional = getWrapper(resource);
            if (resourceWrapperOptional.isPresent()) {
                return false;
            }

            ResourceWrapper<R> resourceWrapper = new ResourceWrapper<>(resource);
            boolean result = resourceWrappers.add(resourceWrapper);
            resourceWrappers.notify();
            return result;
        }
    }

    public boolean remove(R resource) {
        Optional<ResourceWrapper<R>> resourceWrapperOptional = getWrapper(resource);
        if (!resourceWrapperOptional.isPresent()) {
            return false;
        }

        ResourceWrapper<R> resourceWrapper = resourceWrapperOptional.get();
        if (!resourceWrapper.isRemoved) { // require verification
            resourceWrapper.setRemoved(true); // prevent acquire while remove in progress
            waitOnTheResource(resourceWrapper);
            return resourceWrappers.remove(resourceWrapper);
        } else {
            return false;
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
        // TODO: check if resource was not removed in another thread
        synchronized (resourceWrapper.getResource()) { // waiting on specified resource monitor
            while (resourceWrapper.isAcquired() && !resourceWrapper.isRemoved()) {
                try {
                    resourceWrapper.getResource().wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException("Waiting tread has bean interrupted: " +
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

    private static class ResourceWrapper<T> {
        private volatile boolean isAcquired;
        private volatile boolean isRemoved;
        private final T resource;

        ResourceWrapper(T resource) {
            this.resource = resource;
        }

        boolean isAcquired() {
            return isAcquired;
        }

        void setAcquired(boolean acquired) {
            this.isAcquired = acquired;
        }

        T getResource() {
            return resource;
        }

        public boolean isRemoved() {
            return isRemoved;
        }

        public void setRemoved(boolean removed) {
            isRemoved = removed;
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
