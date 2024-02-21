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

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.apache.camel.Consumer;
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
    protected MongoCursor cursor;

    volatile boolean keepRunning = true;
    private volatile boolean stopped;
    private volatile CountDownLatch stoppedLatch;

    MongoAbstractConsumerThread(MongoDbEndpoint endpoint, Consumer consumer) {
        this.endpoint = endpoint;
        this.consumer = consumer;
        this.dbCol = endpoint.getMongoCollection();
        this.cursorRegenerationDelay = endpoint.getCursorRegenerationDelay();
        this.cursorRegenerationDelayEnabled = !(this.cursorRegenerationDelay == 0);
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
        stoppedLatch = new CountDownLatch(1);
        try {
            while (keepRunning) {
                try {
                    doRun();
                } catch (Exception e) {
                    if (keepRunning) {
                        log.warn("Exception from consuming from MongoDB caused by {}. Will try again on next poll.",
                                e.getMessage());
                    } else {
                        log.warn("Exception from consuming from MongoDB caused by {}. ConsumerThread will be stopped.",
                                e.getMessage(), e);
                    }
                }
                // regenerate the cursor, if reading failed for some reason
                if (keepRunning) {
                    cursor.close();
                    regeneratingCursor();

                    if (cursorRegenerationDelayEnabled) {
                        try {
                            Thread.sleep(cursorRegenerationDelay);
                        } catch (InterruptedException e) {
                            log.info("Interrupted while waiting for the cursor regeneration");
                            Thread.currentThread().interrupt();
                        }
                    }

                    cursor = initializeCursor();
                }
            }
        } finally {
            stopped = true;
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
        awaitStopped();

        if (log.isInfoEnabled()) {
            log.info("Stopped MongoDB Tailable Cursor consumer, bound to collection: {}",
                    String.format("db: %s, col: %s", endpoint.getDatabase(), endpoint.getCollection()));
        }
    }

    private void awaitStopped() throws InterruptedException {
        if (!stopped) {
            log.info("Going to wait for stopping");
            stoppedLatch.await();
        }
    }
}
