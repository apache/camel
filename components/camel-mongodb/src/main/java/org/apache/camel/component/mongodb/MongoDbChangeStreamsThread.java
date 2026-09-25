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
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.types.ObjectId;

import static org.apache.camel.component.mongodb.MongoDbConstants.MONGO_ID;

class MongoDbChangeStreamsThread extends MongoAbstractConsumerThread {

    private static final DocumentCodec DOCUMENT_CODEC = new DocumentCodec();

    private List<BsonDocument> bsonFilter;
    private BsonDocument resumeToken;
    private CommitManager commitManager;
    private String resumeTokenKey;

    MongoDbChangeStreamsThread(MongoDbEndpoint endpoint, MongoDbChangeStreamsConsumer consumer,
                               List<BsonDocument> bsonFilter) {
        super(endpoint, consumer);
        this.bsonFilter = bsonFilter;
    }

    @Override
    protected void init() {
        MongoDbChangeStreamsConsumer changeStreamsConsumer = (MongoDbChangeStreamsConsumer) consumer;
        commitManager = CommitManagers.createCommitManager(changeStreamsConsumer, endpoint);
        resumeTokenKey = changeStreamsConsumer.getResumeTokenKey();

        // Strategy-provided resume token has precedence, then endpoint/repository fallback.
        resumeToken = changeStreamsConsumer.getStartupResumeToken();
        if (resumeToken == null) {
            resumeToken = commitManager.readResumeToken();
        }
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

                Object documentId = readDocumentId(dbObj.getDocumentKey());
                OperationType operationType = dbObj.getOperationType();
                BsonDocument currentResumeToken = dbObj.getResumeToken();

                if (operationType != null) {
                    exchange.getIn().setHeader(MongoDbConstants.STREAM_OPERATION_TYPE, operationType.getValue());
                }
                if (documentId != null) {
                    exchange.getIn().setHeader(MongoDbConstants.MONGO_ID, documentId);
                }
                if (currentResumeToken != null) {
                    exchange.getIn().setHeader(Exchange.OFFSET, MongoDbResumable.of(resumeTokenKey, currentResumeToken));
                }
                if (operationType == OperationType.DELETE && documentId != null) {
                    exchange.getIn().setBody(new Document(MONGO_ID, documentId));
                }

                try {
                    if (log.isTraceEnabled()) {
                        log.trace("Sending exchange: {}, id: {}", exchange, documentId);
                    }
                    consumer.getProcessor().process(exchange);
                    this.resumeToken = currentResumeToken;
                    commitManager.recordResumeToken(currentResumeToken);
                    commitManager.commit();
                } catch (Exception e) {
                    // the resume token is not advanced for this event, but a later one that succeeds
                    // commits its own, so the failure has to be reported or it leaves no trace at all
                    getExceptionHandler().handleException("Error processing exchange", exchange, e);
                }
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

    /**
     * Reads the {@code _id} out of the change event's document key.
     * <p>
     * The key is absent on the events that do not belong to a single document ({@code invalidate}, {@code drop},
     * {@code rename}, {@code dropDatabase}), and {@code _id} is only an {@link ObjectId} when the collection lets
     * MongoDB generate it - a document may just as well be keyed by a string, a number or a compound value. Reading it
     * blindly as an {@link ObjectId} threw before the exchange was ever created, and since the resume token is only
     * advanced after a successful exchange, the regenerated cursor kept returning the same event.
     *
     * @param  documentKey the change event's document key, which may be {@code null}
     * @return             the id as its natural Java type, or {@code null} when the event carries no document key
     */
    static Object readDocumentId(BsonDocument documentKey) {
        if (documentKey == null || !documentKey.containsKey(MONGO_ID)) {
            return null;
        }

        if (documentKey.get(MONGO_ID).isObjectId()) {
            return documentKey.getObjectId(MONGO_ID).getValue();
        }

        // anything else - a string, a number, a compound key - is decoded the way the driver decodes a
        // document, so the header carries the id in its natural Java type
        Document decoded = DOCUMENT_CODEC.decode(documentKey.asBsonReader(), DecoderContext.builder().build());
        return decoded.get(MONGO_ID);
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
