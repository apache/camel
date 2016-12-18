/**
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
package org.apache.camel.component.mongodb3;

import java.util.concurrent.CountDownLatch;

import com.mongodb.CursorType;
import com.mongodb.MongoCursorNotFoundException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import org.apache.camel.Exchange;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mongodb.client.model.Filters.gt;
import static org.apache.camel.component.mongodb3.MongoDbConstants.MONGO_ID;

public class MongoDbTailingProcess implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbTailingProcess.class);
    private static final String CAPPED_KEY = "capped";

    public volatile boolean keepRunning = true;
    public volatile boolean stopped; // = false
    private volatile CountDownLatch stoppedLatch;

    private final MongoCollection<Document> dbCol;
    private final MongoDbEndpoint endpoint;
    private final MongoDbTailableCursorConsumer consumer;

    // create local, final copies of these variables for increased performance
    private final long cursorRegenerationDelay;
    private final boolean cursorRegenerationDelayEnabled;

    private MongoCursor<Document> cursor;
    private MongoDbTailTrackingManager tailTracking;

    public MongoDbTailingProcess(MongoDbEndpoint endpoint, MongoDbTailableCursorConsumer consumer, MongoDbTailTrackingManager tailTrack) {
        this.endpoint = endpoint;
        this.consumer = consumer;
        this.dbCol = endpoint.getMongoCollection();
        this.tailTracking = tailTrack;
        this.cursorRegenerationDelay = endpoint.getCursorRegenerationDelay();
        this.cursorRegenerationDelayEnabled = !(this.cursorRegenerationDelay == 0);
    }

    public MongoCursor<Document> getCursor() {
        return cursor;
    }

    /**
     * Initialise the tailing process, the cursor and if persistent tail
     * tracking is enabled, recover the cursor from the persisted point. As part
     * of the initialisation process, the component will validate that the
     * collection we are targeting is 'capped'.
     *
     * @throws Exception
     */
    public void initializeProcess() throws Exception {
        if (LOG.isInfoEnabled()) {
            LOG.info("Starting MongoDB Tailable Cursor consumer, binding to collection: {}", "db: " + endpoint.getMongoDatabase() + ", col: " + endpoint.getCollection());
        }

        if (!isCollectionCapped()) {
            throw new CamelMongoDbException("Tailable cursors are only compatible with capped collections, and collection " + endpoint.getCollection() + " is not capped");
        }
        try {
            // recover the last value from the store if it exists
            tailTracking.recoverFromStore();
            cursor = initializeCursor();
        } catch (Exception e) {
            throw new CamelMongoDbException("Exception occurred while initializing tailable cursor", e);
        }

        if (cursor == null) {
            throw new CamelMongoDbException("Tailable cursor was not initialized, or cursor returned is dead on arrival");
        }

    }

    private Boolean isCollectionCapped() {
        return endpoint.getMongoDatabase().runCommand(createCollStatsCommand()).getBoolean(CAPPED_KEY);
    }

    private Document createCollStatsCommand() {
        return new Document("collStats", endpoint.getCollection());
    }

    /**
     * The heart of the tailing process.
     */
    @Override
    public void run() {
        stoppedLatch = new CountDownLatch(1);
        while (keepRunning) {
            doRun();
            // if the previous call didn't return because we have stopped
            // running, then regenerate the cursor
            if (keepRunning) {
                cursor.close();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Regenerating cursor with lastVal: {}, waiting {}ms first", tailTracking.lastVal, cursorRegenerationDelay);
                }

                if (cursorRegenerationDelayEnabled) {
                    try {
                        Thread.sleep(cursorRegenerationDelay);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }

                cursor = initializeCursor();
            }
        }

        stopped = true;
        stoppedLatch.countDown();
    }

    protected void stop() throws Exception {
        if (LOG.isInfoEnabled()) {
            LOG.info("Stopping MongoDB Tailable Cursor consumer, bound to collection: {}", "db: " + endpoint.getDatabase() + ", col: " + endpoint.getCollection());
        }
        keepRunning = false;
        // close the cursor if it's open, so if it is blocked on hasNext() it
        // will return immediately
        if (cursor != null) {
            cursor.close();
        }
        awaitStopped();
        if (LOG.isInfoEnabled()) {
            LOG.info("Stopped MongoDB Tailable Cursor consumer, bound to collection: {}", "db: " + endpoint.getDatabase() + ", col: " + endpoint.getCollection());
        }
    }

    /**
     * The heart of the tailing process.
     */
    private void doRun() {
        // while the cursor has more values, keepRunning is true and the
        // cursorId is not 0, which symbolizes that the cursor is dead
        try {
            while (cursor.hasNext() && keepRunning) { // cursor.getCursorId() !=
                                                      // 0 &&
                Document dbObj = cursor.next();
                Exchange exchange = endpoint.createMongoDbExchange(dbObj);
                try {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Sending exchange: {}, ObjectId: {}", exchange, dbObj.get(MONGO_ID));
                    }
                    consumer.getProcessor().process(exchange);
                } catch (Exception e) {
                    // do nothing
                }
                tailTracking.setLastVal(dbObj);
            }
        } catch (MongoCursorNotFoundException e) {
            // we only log the warning if we are not stopping, otherwise it is
            // expected because the stop() method kills the cursor just in case
            // it is blocked
            // waiting for more data to arrive
            if (keepRunning) {
                LOG.debug("Cursor not found exception from MongoDB, will regenerate cursor. This is normal behaviour with tailable cursors.", e);
            }
        }

        // the loop finished, persist the lastValue just in case we are shutting
        // down
        // TODO: perhaps add a functionality to persist every N records
        tailTracking.persistToStore();
    }

    // no arguments, will ask DB what the last updated Id was (checking
    // persistent storage)
    private MongoCursor<Document> initializeCursor() {
        Object lastVal = tailTracking.lastVal;
        // lastVal can be null if we are initializing and there is no
        // persistence enabled
        MongoCursor<Document> answer;
        if (lastVal == null) {
            answer = dbCol.find().cursorType(CursorType.TailableAwait).iterator();
        } else {
            try (MongoCursor<Document> iterator = dbCol.find(gt(tailTracking.getIncreasingFieldName(), lastVal)).cursorType(CursorType.TailableAwait).iterator();) {
                answer = iterator;
            }
        }
        return answer;
    }

    private void awaitStopped() throws InterruptedException {
        if (!stopped) {
            LOG.info("Going to wait for stopping");
            stoppedLatch.await();
        }
    }

}
