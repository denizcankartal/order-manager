package com.ordermanager.service;

import com.ordermanager.model.Order;
import com.ordermanager.persistence.StatePersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asynchronous state persister using BlockingQueue.
 *
 * - Producer: WebSocket thread submit state snapshots to a bounded queue
 * - Consumer: Single background thread polls queue and writes to disk
 */
public class AsyncStatePersister {

    private static final Logger logger = LoggerFactory.getLogger(AsyncStatePersister.class);

    private static final int QUEUE_CAPACITY = 10;

    private final StatePersistence persistence;
    private final BlockingQueue<Map<String, Order>> writeQueue;
    private final ExecutorService writerThread;
    private final AtomicBoolean running;

    public AsyncStatePersister(StatePersistence persistence) {
        this.persistence = persistence;
        this.writeQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        this.writerThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AsyncStateWriter");
            t.setDaemon(true); // allow JVM to exit
            return t;
        });
        this.running = new AtomicBoolean(false);
    }

    /**
     * Start the background writer thread.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            writerThread.submit(this::writeLoop);
        }
    }

    /**
     * Background write loop (consumer).
     *
     * Continuously polls the queue and writes snapshots to disk.
     * Drains queue to get latest snapshot before writing (deduplication).
     */
    private void writeLoop() {
        while (running.get() || !writeQueue.isEmpty()) {
            try {
                // block waiting for next snapshot
                Map<String, Order> snapshot = writeQueue.poll(1, TimeUnit.SECONDS);

                if (snapshot != null) {
                    // drain queue to get latest snapshot (deduplication)
                    Map<String, Order> latest = snapshot;
                    Map<String, Order> next;
                    int drained = 0;

                    while ((next = writeQueue.poll()) != null) {
                        latest = next;
                        drained++;
                    }

                    if (drained > 0) {
                        logger.debug("Drained {} older snapshots, writing latest", drained);
                    }

                    // write latest snapshot to disk
                    try {
                        persistence.save(latest);
                        logger.debug("Wrote {} orders to disk", latest.size());
                    } catch (IOException e) {
                        logger.error("Failed to write state to disk: {}", e.getMessage());
                        // don't put it back - it would block the queue mext write will capture current
                        // state
                    }
                }

            } catch (InterruptedException e) {
                logger.debug("Write loop interrupted");
                Thread.currentThread().interrupt();
                break;
            }
        }

        logger.debug("Write loop stopped");
    }

    /**
     * Submit a state snapshot for async writing (producer).
     *
     * This is non-blocking. If the queue is full, the oldest snapshot
     * is dropped (we only care about the latest state).
     *
     * @param stateSnapshot State snapshot to persist
     */
    public void submitWrite(Map<String, Order> stateSnapshot) {
        if (!running.get()) {
            logger.warn("AsyncStatePersister not running, cannot submit write");
            return;
        }

        // try to add to queue (non-blocking)
        boolean added = writeQueue.offer(stateSnapshot);

        if (!added) {
            // queue is full - drop oldest and add new
            writeQueue.poll(); // Remove oldest
            writeQueue.offer(stateSnapshot); // add new
            logger.debug("Write queue full, dropped oldest snapshot");
        }
    }

    /**
     * Flush any pending writes immediately (blocking).
     *
     * Useful for shutdown to ensure all pending writes complete.
     */
    private void flush() {
        if (writeQueue.isEmpty()) {
            return;
        }

        // drain queue and write latest snapshot
        Map<String, Order> latest = null;
        Map<String, Order> snapshot;

        while ((snapshot = writeQueue.poll()) != null) {
            latest = snapshot;
        }

        if (latest != null) {
            try {
                persistence.save(latest);
                logger.info("Flushed {} orders to disk", latest.size());
            } catch (IOException e) {
                logger.error("Failed to flush state to disk: {}", e.getMessage());
            }
        }
    }

    /**
     * Shutdown the persister and wait for pending writes to complete.
     *
     * @param timeoutSeconds Maximum time to wait for shutdown
     */
    public void shutdown(int timeoutSeconds) {
        logger.info("Shutting down AsyncStatePersister...");

        running.set(false);

        flush();

        try {
            writerThread.shutdown();
            if (!writerThread.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                logger.warn("Persister did not shutdown cleanly, forcing shutdown");
                writerThread.shutdownNow();
            } else {
                logger.info("AsyncStatePersister shutdown complete");
            }
        } catch (InterruptedException e) {
            logger.error("Shutdown interrupted");
            writerThread.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
