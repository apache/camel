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

import java.util.List;

import com.mongodb.MongoException;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.OperationType;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.types.ObjectId;

import static org.apache.camel.component.mongodb.MongoDbConstants.MONGO_ID;

class MongoDbChangeStreamsThread extends MongoAbstractConsumerThread {
    private List<BsonDocument> bsonFilter;
    private BsonDocument resumeToken;

    MongoDbChangeStreamsThread(MongoDbEndpoint endpoint, MongoDbChangeStreamsConsumer consumer,
                               List<BsonDocument> bsonFilter) {
        super(endpoint, consumer);
        this.bsonFilter = bsonFilter;
    }

    @Override
    protected void init() {
        cursor = initializeCursor();
    }

    @Override
    protected MongoCursor initializeCursor() {
        ChangeStreamIterable<Document> iterable = bsonFilter != null
                ? dbCol.watch(bsonFilter)
                : dbCol.watch();

        iterable.fullDocument(endpoint.getFullDocument());

        if (resumeToken != null) {
            iterable = iterable.resumeAfter(resumeToken);
        }

        return iterable.iterator();
    }

    @Override
    protected void regeneratingCursor() {
        if (log.isDebugEnabled()) {
            log.debug("Regenerating cursor, waiting {}ms first", cursorRegenerationDelay);
        }
    }

    @Override
    protected void doRun() {
        try {
            while (cursor.hasNext() && keepRunning) {
                ChangeStreamDocument<Document> dbObj = (ChangeStreamDocument<Document>) cursor.next();
                Exchange exchange = createMongoDbExchange(dbObj.getFullDocument());

                ObjectId documentId = dbObj.getDocumentKey().getObjectId(MONGO_ID).getValue();
                OperationType operationType = dbObj.getOperationType();
                exchange.getIn().setHeader(MongoDbConstants.STREAM_OPERATION_TYPE, operationType.getValue());
                exchange.getIn().setHeader(MongoDbConstants.MONGO_ID, documentId);
                if (operationType == OperationType.DELETE) {
                    exchange.getIn().setBody(new Document(MONGO_ID, documentId));
                }

                try {
                    if (log.isTraceEnabled()) {
                        log.trace("Sending exchange: {}, ObjectId: {}", exchange, dbObj.getFullDocument().get(MONGO_ID));
                    }
                    consumer.getProcessor().process(exchange);
                } catch (Exception ignored) {
                }

                this.resumeToken = dbObj.getResumeToken();
            }
        } catch (MongoException e) {
            // cursor.hasNext() opens socket and waiting for data
            // it throws exception when cursor is closed in another thread
            // there is no way to stop hasNext() before closing cursor
            if (keepRunning) {
                log.debug("Exception from MongoDB, will regenerate cursor.", e);
            } else {
                throw e;
            }
        }
    }

    private Exchange createMongoDbExchange(Document dbObj) {
        Exchange exchange = consumer.createExchange(true);
        Message message = exchange.getIn();
        message.setHeader(MongoDbConstants.DATABASE, endpoint.getDatabase());
        message.setHeader(MongoDbConstants.COLLECTION, endpoint.getCollection());
        message.setHeader(MongoDbConstants.FROM_TAILABLE, true);
        message.setBody(dbObj);
        return exchange;
    }

}
