package com.maksbelosh.dataxupool;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ResourcePoolTest {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    // TODO rename tests and add description about cases

    @Test
    public void testOpen() {
        ResourcePool<Object> resourcePool = new ResourcePool<>();

        Assert.assertFalse(resourcePool.isOpen());
        resourcePool.add(new Object());
        Assert.assertFalse(resourcePool.isOpen());

        resourcePool.open();
        Assert.assertTrue(resourcePool.isOpen());
    }


    @Test(expected = IllegalStateException.class)
    public void close() throws InterruptedException, ExecutionException {
        ResourcePool<Integer> resourcePool = new ResourcePool<>();
        resourcePool.add(10);
        resourcePool.open();
        Integer resource = resourcePool.acquire();
        Assert.assertEquals(Integer.valueOf(10), resource);

        // Close pool in separate thread
        FutureTask<Long> futureTask = new FutureTask<>(() -> separatelyClosePool(resourcePool));
        new Thread(futureTask).start();

        Thread.sleep(1000); // Emulate waiting while release performed
        resourcePool.release(resource);

        Assert.assertTrue(1000 >= futureTask.get()); // Verify if close method was blocking no more 3000 ms
        Assert.assertFalse(resourcePool.isOpen());
        resourcePool.acquire(); // check if acquire is not permitted
    }

    private long separatelyClosePool(ResourcePool<Integer> resourcePool) {
        long currentTimeMills = System.currentTimeMillis();
        resourcePool.close();
        return System.currentTimeMillis() - currentTimeMills; // Return block time
    }

    // Close the pool in separate thread, and evaluated time for blocking should be 0
    @Test(expected = IllegalStateException.class)
    public void closeNow() throws Exception {
        ResourcePool<Integer> resourcePool = new ResourcePool<>();
        resourcePool.add(10);
        resourcePool.open();
        Integer resource = resourcePool.acquire();
        Assert.assertEquals(Integer.valueOf(10), resource);

        // Close pool in separate thread
        FutureTask<Long> futureTask = new FutureTask<>(() -> separatelyCloseNowPool(resourcePool));
        new Thread(futureTask).start();

        Assert.assertEquals(Long.valueOf(0), futureTask.get()); // Verify if was not waiting
        Assert.assertFalse(resourcePool.isOpen()); // No release operation happen and pool should be closed now without blocking
        resourcePool.acquire(); // check if acquire is not permitted
    }

    private long separatelyCloseNowPool(ResourcePool<Integer> resourcePool) {
        long currentTimeMills = System.currentTimeMillis();
        resourcePool.closeNow();
        return System.currentTimeMillis() - currentTimeMills; // Check block time while released intime
    }

    //Verify if multiple thread will work with resource consequentially
    @Test
    public void acquireConsequentiallyForTwoThreads() throws Exception {
        ResourcePool<Resource> resourcePool = new ResourcePool<>();
        Resource resource = new Resource();
        Assert.assertEquals(10, resource.getResourceFieldValue());

        resourcePool.add(resource);
        resourcePool.open();

        CyclicBarrier gate = new CyclicBarrier(3);

        Thread t1 = new Thread(() -> incrementResource(gate, resourcePool));
        Thread t2 = new Thread(() -> incrementResource(gate, resourcePool));

        startThreadsAndWaitForExecution(gate, t1, t2);

        Assert.assertEquals(12, resource.getResourceFieldValue());
    }

    private void incrementResource(CyclicBarrier gate, ResourcePool<Resource> resourcePool) {
        try {
            gate.await();

            Resource resource = resourcePool.acquire();
            logger.info("Resource retrieved");

            resource.incrementResource();
            logger.info("Resource incremented");

            resourcePool.release(resource);
            logger.info("Resource released");

        } catch (Exception e) {
            logger.error("Exception in consumer thread");
            throw new RuntimeException("Exception in consumer thread");
        }
    }

    // Ensure that in case of multiple threads in one time try to acquire resource with timeout,
    // and return in with delay > timeout, than firs thread will be able to work with resource
    @Test
    public void testSimultaneousAcquireWithTimeout() throws Exception {
        ResourcePool<Resource> resourcePool = new ResourcePool<>();
        Resource resource = new Resource();
        Assert.assertEquals(10, resource.getResourceFieldValue());

        resourcePool.add(resource);
        resourcePool.open();

        CyclicBarrier gate = new CyclicBarrier(3);
        Thread t1 = new Thread(() -> incrementResource(gate, resourcePool, 100));
        Thread t2 = new Thread(() -> incrementResource(gate, resourcePool, 150));

        startThreadsAndWaitForExecution(gate, t1, t2);

        Assert.assertEquals(11, resource.getResourceFieldValue()); // Only first thread changed the value
    }

    private void incrementResource(CyclicBarrier gate, ResourcePool<Resource> resourcePool, long timeOut) {
        try {
            gate.await();

            Optional<Resource> optional = resourcePool.acquire(timeOut, TimeUnit.MILLISECONDS);
            logger.info("Resource retrieved - {}", optional);

            if(optional.isPresent()) {
                Resource resource = optional.get();
                resource.incrementResource();
                logger.info("Resource incremented");

                Thread.sleep(200); // X2 pause on processing compare to timeout

                resourcePool.release(resource);
                logger.info("Resource released");
            }
        } catch (Exception e) {
            logger.error("Exception in consumer thread");
            throw new RuntimeException("Exception in consumer thread");
        }
    }

    // Release method was tested in terms of acquire / remove / close methods
//    @Test
//    public void release() throws Exception {
//
//    }

    // Scenario: simultaneously
    @Test
    public void testAddDuplicates() throws Exception {
        ResourcePool<Resource> resourcePool = new ResourcePool<>();
        resourcePool.open();

        Resource resource = new Resource();

        CyclicBarrier gate = new CyclicBarrier(3);
        Thread t1 = new Thread(() -> addResource(gate, resourcePool, resource));
        Thread t2 = new Thread(() -> addResource(gate, resourcePool, resource));

        startThreadsAndWaitForExecution(gate, t1, t2);

        Assert.assertEquals(1, resourcePool.size()); // Only one resource added
    }

    private void addResource(CyclicBarrier gate, ResourcePool<Resource> resourcePool, Resource resource) {
        try {
            gate.await();

            boolean added = resourcePool.add(resource);
            logger.info("Resource remove request performed with status - {}", added);

            // No checks thread should not stuck on unavailable resource
        } catch (Exception e) {
            logger.error("Exception in consumer thread");
            throw new RuntimeException("Exception in consumer thread");
        }
    }

    // Scenario: two threads simultaneous remove the the required resource once released
    // Ensure that no stuck for the second thread in case if first successfully removed resource
    @Test
    public void testSimultaneousRemove() throws Exception {
        ResourcePool<Resource> resourcePool = new ResourcePool<>();

        resourcePool.add(new Resource());
        resourcePool.add(new Resource());
        resourcePool.open();

        Resource acquiredResource = resourcePool.acquire();

        CyclicBarrier gate = new CyclicBarrier(3);

        Thread t1 = new Thread(() -> removeResource(gate, resourcePool, acquiredResource));
        Thread t2 = new Thread(() -> removeResource(gate, resourcePool, acquiredResource));

        startThreadsAndWaitForExecution(gate, t1, t2);

        Assert.assertEquals(1, resourcePool.size()); // Only one resource removed, and no stuck on other thread
    }

    private void removeResource(CyclicBarrier gate, ResourcePool<Resource> resourcePool, Resource resource) {
        try {
            gate.await();

            boolean removed = resourcePool.remove(resource);
            logger.info("Resource remove request performed with status - {}", removed);

            // No checks thread should not stuck on unavailable resource
        } catch (Exception e) {
            logger.error("Exception in consumer thread");
            throw new RuntimeException("Exception in consumer thread");
        }
    }

    // Scenario: remove resource without preliminary or simultaneous releasing resource
    @Test
    public void removeNow() throws Exception {
        ResourcePool<Resource> resourcePool = new ResourcePool<>();

        Resource resource = new Resource();

        resourcePool.add(resource);
        resourcePool.open();
        resourcePool.removeNow(resource);

        Assert.assertEquals(0, resourcePool.size()); // Last resource removed, without blocking
    }


    private void startThreadsAndWaitForExecution(CyclicBarrier gate, Thread t1, Thread t2) throws InterruptedException, BrokenBarrierException {
        t1.setName("Consumer1");
        t1.start();
        t2.setName("Consumer2");
        t2.start();

        gate.await(); // Start booth threads simultaneously

        // waiting while consumers perform work
        t1.join();
        t2.join();
    }

    class Resource {
        private AtomicInteger resourceField = new AtomicInteger(10);

        public void incrementResource() {
            resourceField.getAndIncrement();
        }

        public int getResourceFieldValue() {
            return resourceField.get();
        }
    }
}