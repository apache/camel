/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mongodb;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.support.DefaultConsumer;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class MongoAbstractConsumerThread implements Runnable {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    // local final copies of variables for increased performance
    protected final long cursorRegenerationDelay;
    protected final boolean cursorRegenerationDelayEnabled;

    protected final MongoCollection<Document> dbCol;
    protected final Consumer consumer;
    protected final MongoDbEndpoint endpoint;
    protected volatile MongoCursor cursor;

    volatile boolean keepRunning = true;
    // counted down when run() ends; created with the thread, so that a stop before run() begins can wait for it too
    private final CountDownLatch stoppedLatch = new CountDownLatch(1);
    // the thread that runs this consumer thread, while it runs
    private volatile Thread runner;
    // whether the thread routes an exchange, rather than reads from MongoDB
    private volatile boolean processing;

    MongoAbstractConsumerThread(MongoDbEndpoint endpoint, Consumer consumer) {
        this.endpoint = endpoint;
        this.consumer = consumer;
        this.dbCol = endpoint.getMongoCollection();
        this.cursorRegenerationDelay = endpoint.getCursorRegenerationDelay();
        this.cursorRegenerationDelayEnabled = !(this.cursorRegenerationDelay == 0);
    }

    /**
     * The exception handler of the consumer this thread feeds, so that a failure reaches the configured handler rather
     * than being discarded on the consumer thread.
     */
    protected ExceptionHandler getExceptionHandler() {
        return ((DefaultConsumer) consumer).getExceptionHandler();
    }

    /**
     * Routes the exchange and reports a failure to the consumer's exception handler.
     * <p>
     * A route that fails does not throw from {@code process}: the failure is left on the exchange, so it is checked
     * there.
     *
     * @param  exchange the exchange to route, created with {@code createExchange(false)} and released by the caller
     * @return          {@code true} if the route processed the exchange without a failure
     */
    protected boolean processExchange(Exchange exchange) {
        processing = true;
        try {
            consumer.getProcessor().process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            processing = false;
        }
        Exception cause = exchange.getException();
        if (cause != null) {
            getExceptionHandler().handleException("Error processing exchange", exchange, cause);
            return false;
        }
        return true;
    }

    protected abstract MongoCursor<Document> initializeCursor();

    protected abstract void init() throws Exception;

    protected abstract void doRun();

    protected abstract void regeneratingCursor();

    /**
     * Main loop.
     */
    @Override
    public void run() {
        runner = Thread.currentThread();
        // the failures since a cursor was last opened: while the server cannot be reached, every attempt fails the same
        // way, so only the first one logs the stack trace
        int failures = 0;
        try {
            // an interrupt ends the thread, as it did before the cursor was reopened inside the try
            while (keepRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    if (cursor == null) {
                        // a new cursor after the previous one ended. It is opened here, inside the try, so that a
                        // failure to open it (the server cannot be reached yet) is reported and tried again after
                        // the delay, instead of ending this thread while the consumer stays started
                        cursor = initializeCursor();
                        if (!keepRunning) {
                            // stopped while the cursor was being opened, so stop() could not close it
                            cursor.close();
                            break;
                        }
                        if (failures > 0) {
                            log.info("Opened a new cursor to consume from MongoDB again (consecutive failures: {})", failures);
                            failures = 0;
                        }
                    }
                    doRun();
                } catch (Exception e) {
                    if (keepRunning) {
                        failures++;
                        if (failures == 1) {
                            log.warn("Exception from consuming from MongoDB caused by {}. Will try again on next poll.",
                                    e.getMessage(), e);
                        } else {
                            // this one repeats for as long as the cause persists: the stack trace was logged with the
                            // first failure
                            log.warn("Exception from consuming from MongoDB caused by {} ({} consecutive failures)."
                                     + " Will try again on next poll.",
                                    e.getMessage(), failures);
                            log.debug("Exception from consuming from MongoDB", e);
                        }
                    } else {
                        log.warn("Exception from consuming from MongoDB caused by {}. ConsumerThread will be stopped.",
                                e.getMessage(), e);
                    }
                }
                // regenerate the cursor, if reading failed for some reason
                if (keepRunning) {
                    if (cursor != null) {
                        cursor.close();
                        cursor = null;
                    }
                    regeneratingCursor();

                    if (cursorRegenerationDelayEnabled) {
                        try {
                            Thread.sleep(cursorRegenerationDelay);
                        } catch (InterruptedException e) {
                            log.info("Interrupted while waiting for the cursor regeneration");
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        } finally {
            runner = null;
            stoppedLatch.countDown();
        }
    }

    protected void stop() throws Exception {
        if (log.isInfoEnabled()) {
            log.info("Stopping MongoDB Tailable Cursor consumer, bound to collection: {}",
                    String.format("db: %s, col: %s", endpoint.getDatabase(), endpoint.getCollection()));
        }

        keepRunning = false;
        if (cursor != null) {
            cursor.close();
        }
        if (awaitStopped()) {
            afterStopped();
        }

        if (log.isInfoEnabled()) {
            log.info("Stopped MongoDB Tailable Cursor consumer, bound to collection: {}",
                    String.format("db: %s, col: %s", endpoint.getDatabase(), endpoint.getCollection()));
        }
    }

    /**
     * Called by {@link #stop()} when the consumer thread was left behind by {@link #awaitStopped()}, that is, it did
     * not end while the thread that stops the consumer waited for it. Runs on that thread, before {@code stop()}
     * returns.
     */
    protected void afterStopped() {
    }

    /**
     * Waits for the thread to end.
     * <p>
     * Closing the cursor does not end a read from MongoDB in progress on the thread: the driver only closes the cursor
     * once that read returns, and a read from a connection that stopped answering does not return. So while the thread
     * reads from MongoDB, the wait is bounded, by the time to wait for a thread pool to shut down
     * ({@code ExecutorServiceManager.getShutdownAwaitTermination()}, 10 seconds by default): the thread is then
     * interrupted and left behind. An exchange that the thread routes is waited for as before, as the graceful shutdown
     * bounds the wait for an exchange in flight with its own timeout.
     */
    private boolean awaitStopped() throws InterruptedException {
        if (stoppedLatch.getCount() == 0) {
            return false;
        }
        log.info("Going to wait for stopping");
        // at least a little, so that the loop does not spin while the thread routes an exchange
        long timeout = Math.max(100, endpoint.getCamelContext().getExecutorServiceManager().getShutdownAwaitTermination());
        while (!stoppedLatch.await(timeout, TimeUnit.MILLISECONDS)) {
            if (!processing) {
                log.warn("The consumer thread did not end within {} millis after its cursor was closed, while it reads from"
                         + " MongoDB: closing the cursor does not end a read in progress, and a read from a connection"
                         + " that stopped answering does not return. The thread is interrupted and left behind, it ends"
                         + " once the read returns.",
                        timeout);
                Thread thread = runner;
                if (thread != null) {
                    thread.interrupt();
                }
                return true;
            }
        }
        return false;
    }
}
