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

import com.mongodb.CursorType;
import com.mongodb.MongoCursorNotFoundException;
import com.mongodb.client.MongoCursor;
import org.apache.camel.Exchange;
import org.bson.Document;

import static com.mongodb.client.model.Filters.gt;
import static org.apache.camel.component.mongodb.MongoDbConstants.MONGO_ID;

class MongoDbTailingThread extends MongoAbstractConsumerThread {

    private static final String CAPPED_KEY = "capped";
    private MongoDbTailTrackingManager tailTracking;

    MongoDbTailingThread(MongoDbEndpoint endpoint, MongoDbTailableCursorConsumer consumer, MongoDbTailTrackingManager tailTrack) {
        super(endpoint, consumer);
        this.tailTracking = tailTrack;
    }

    /**
     * Initialise the tailing process, the cursor and if persistent tail tracking is enabled,
     * recover the cursor from the persisted point.
     * As part of the initialisation process,
     * the component will validate that the collection we are targeting is 'capped'.
     */
    @Override
    protected void init() {
        if (log.isInfoEnabled()) {
            log.info("Starting MongoDB Tailable Cursor consumer, binding to collection: {}",
                    String.format("db: %s, col: %s", endpoint.getMongoDatabase(), endpoint.getCollection()));
        }

        if (!isCollectionCapped()) {
            throw new CamelMongoDbException(
                    String.format("Tailable cursors are only compatible with capped collections, and collection %s is not capped",
                            endpoint.getCollection()));
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

    @Override
    // no arguments, will ask DB what the last updated Id was (checking persistent storage)
    protected MongoCursor<Document> initializeCursor() {
        Object lastVal = tailTracking.lastVal;
        // lastVal can be null if we are initializing and there is no persistence enabled
        MongoCursor<Document> answer;
        if (lastVal == null) {
            answer = dbCol.find().cursorType(CursorType.TailableAwait).iterator();
        } else {
            MongoCursor<Document> iterator = dbCol.find(gt(tailTracking.getIncreasingFieldName(), lastVal))
                    .cursorType(CursorType.TailableAwait)
                    .iterator();
            answer = iterator;
        }
        return answer;
    }

    @Override
    protected void regeneratingCursor() {
        if (log.isDebugEnabled()) {
            log.debug("Regenerating cursor with lastVal: {}, waiting {} ms first", tailTracking.lastVal, cursorRegenerationDelay);
        }
    }

    /**
     * The heart of the tailing process.
     */
    @Override
    protected void doRun() {
        // while the cursor has more values, keepRunning is true and the
        // cursorId is not 0, which symbolizes that the cursor is dead
        try {
            while (cursor.hasNext() && keepRunning) {
                Document dbObj = (Document) cursor.next();
                Exchange exchange = endpoint.createMongoDbExchange(dbObj);
                try {
                    if (log.isTraceEnabled()) {
                        log.trace("Sending exchange: {}, ObjectId: {}", exchange, dbObj.get(MONGO_ID));
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
                log.debug("Cursor not found exception from MongoDB, will regenerate cursor. This is normal behaviour with tailable cursors.", e);
            }
        } catch (IllegalStateException e) {
            // this is happening when the consumer is stopped or the mongo interrupted (ie, junit ending test)
            // as we cannot resume, we shutdown the thread gracefully
            log.info("Cursor was closed, likely the consumer was stopped and closed the cursor on purpose.", e);
            if (cursor != null) {
                cursor.close();
            }
            keepRunning = false;
        } finally {
            // the loop finished, persist the lastValue just in case we are shutting down
            // TODO: perhaps add a functionality to persist every N records
            tailTracking.persistToStore();
        }
    }
}
