package com.maksbelosh.dataxupool;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
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

        Thread.sleep(3000); // Emulate waiting while release performed
        resourcePool.release(resource);

        Assert.assertEquals(Long.valueOf(3000), futureTask.get()); // Verify if was waiting exactly 3000 ms
        Assert.assertFalse(resourcePool.isOpen());
        resourcePool.acquire(); // check if acquire is not permitted
    }

    private long separatelyClosePool(ResourcePool<Integer> resourcePool) {
        long currentTimeMills = System.currentTimeMillis();
        resourcePool.close();
        return System.currentTimeMillis() - currentTimeMills; // Check block time while released intime
    }

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

        Assert.assertEquals(Long.valueOf(0), futureTask.get()); // Verify if was waiting exactly 3000 ms
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
    public void acquireConsequentially() throws Exception {
        ResourcePool<Resource> resourcePool = new ResourcePool<>();
        Resource resource = new Resource();
        Assert.assertEquals(10, resource.getResourceFieldValue());

        resourcePool.add(resource);
        resourcePool.open();

        CyclicBarrier gate = new CyclicBarrier(3);

        Thread t1 = new Thread(() -> incrementResource(gate, resourcePool));
        Thread t2 = new Thread(() -> incrementResource(gate, resourcePool));

        t1.setName("Consumer1");
        t1.start();
        t2.setName("Consumer2");
        t2.start();

        gate.await(); // Start booth consumers simultaniously

        // waiting while consumers perform work
        t1.join();
        t2.join();

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

    @Test
    public void acquireWithTimeout() throws Exception {
        ResourcePool<Resource> resourcePool = new ResourcePool<>();
        Resource resource = new Resource();
        Assert.assertEquals(10, resource.getResourceFieldValue());

        resourcePool.add(resource);
        resourcePool.open();

        CyclicBarrier gate = new CyclicBarrier(3);

        Thread t1 = new Thread(() -> incrementResource(gate, resourcePool, 100));
        Thread t2 = new Thread(() -> incrementResource(gate, resourcePool, 100));

        t1.setName("Consumer1");
        t1.start();
        t2.setName("Consumer2");
        t2.start();

        gate.await(); // Start booth consumers simultaniously

        // waiting while consumers perform work
        t1.join();
        t2.join();

        Assert.assertEquals(11, resource.getResourceFieldValue());
    }

    private void incrementResource(CyclicBarrier gate, ResourcePool<Resource> resourcePool, long timeOut) {
        try {
            gate.await();

            Optional<Resource> optional = resourcePool.acquire(timeOut, TimeUnit.MILLISECONDS);
            logger.info("Resource retrieved");

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

    @Test
    public void release() throws Exception {
    }

    @Test
    public void add() throws Exception {
    }

    @Test
    public void remove() throws Exception {
    }

    @Test
    public void removeNow() throws Exception {
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