package com.maksbelosh.dataxupool;

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
    private volatile boolean isPoolCloses;
    private final List<ResourceWrapper<R>> resourceWrappers = new CopyOnWriteArrayList<>();

    public void open() {
        isPoolCloses = false;
    }

    public boolean isOpen() {
        return !isPoolCloses;
    }

    public void close() {
        closeNow();
        for (ResourceWrapper<R> resourceWrapper : resourceWrappers) {
            waitOnTheResource(resourceWrapper);
        }
    }

    public void closeNow() {
        isPoolCloses = true;
    }

    public synchronized R acquire() {
        if(!isPoolCloses) {
            synchronized (resourceWrappers) {
                while (true) { // Prevent suspicious wakeup of the thread
                    for (ResourceWrapper<R> resourceWrapper : resourceWrappers) {
                        if (!resourceWrapper.isAcquired()) {
                            resourceWrapper.setAcquired(true);
                            return resourceWrapper.getResource();
                        }
                    }
                    try {
                        resourceWrappers.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Waiting tread has bean interrupted: " +
                                Thread.currentThread().getName(), e);
                    }
                }
            }
        }
        throw new IllegalStateException("Object pool closes");
    }

    public R acquire(long timeout, TimeUnit timeUnit) {
        if(!isPoolCloses) {
            synchronized (resourceWrappers) {
                long overallTimeout = timeUnit.convert(timeout, TimeUnit.MILLISECONDS);
                long outTime = System.currentTimeMillis() + overallTimeout;

                while (System.currentTimeMillis() < outTime) { // Prevent suspicious wakeup of the thread and go out only on timeout
                    for (ResourceWrapper<R> resourceWrapper : resourceWrappers) {
                        if (!resourceWrapper.isAcquired()) {
                            resourceWrapper.setAcquired(true);
                            return resourceWrapper.getResource();
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
        }
        throw new IllegalStateException("Object pool closes");
    }

    public void release(R resource) {
        Optional<ResourceWrapper<R>> resourceWrapperOptional = getWrapper(resource);
        if (resourceWrapperOptional.isPresent()) {
            ResourceWrapper<R> resourceWrapper = new ResourceWrapper<>(resource);
            resourceWrapper.setAcquired(false);

            resourceWrappers.notify(); // Notification for acquire method
            resourceWrapper.getResource().notify(); // Notification for remove/close method that specific resource is now available
        }
    }

    public boolean add(R resource) {
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

    public boolean remove(R resource) {
        Optional<ResourceWrapper<R>> resourceWrapperOptional = getWrapper(resource);
        if (!resourceWrapperOptional.isPresent()) {
            return false;
        }

        ResourceWrapper<R> resourceWrapper = resourceWrapperOptional.get();
        waitOnTheResource(resourceWrapper);
        return resourceWrappers.remove(resourceWrapper);
    }

    public boolean removeNow(R resource) {
        for (ResourceWrapper<R> resourceWrapper : resourceWrappers) {
            if (resourceWrapper.getResource().equals(resource)) {
                return resourceWrappers.remove(resourceWrapper);
            }
        }
        return false;
    }

    private void waitOnTheResource(ResourceWrapper<R> resourceWrapper) {
        synchronized (resourceWrapper.getResource()) { // waiting on specified resource monitor
            while (resourceWrapper.isAcquired()) {
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

    private class ResourceWrapper<T> {
        private volatile boolean isAcquired;
        private final T resource;

        ResourceWrapper(T resource) {
            this.resource = resource;
        }

        boolean isAcquired() {
            return isAcquired;
        }

        void setAcquired(boolean acquired) {
            isAcquired = acquired;
        }

        T getResource() {
            return resource;
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
