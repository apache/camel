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

import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.MongoServerException;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.OperationType;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.types.ObjectId;

import static org.apache.camel.component.mongodb.MongoDbConstants.MONGO_ID;

class MongoDbChangeStreamsThread extends MongoAbstractConsumerThread {

    private static final DocumentCodec DOCUMENT_CODEC = new DocumentCodec();
    // the server error InvalidResumeToken
    private static final int INVALID_RESUME_TOKEN = 260;
    // the server errors for a start that the oplog no longer has: ChangeStreamHistoryLost (MongoDB 4.4, and the later
    // 4.2 releases), ChangeStreamFatalError and 40576 (earlier 4.2 releases)
    private static final int CHANGE_STREAM_HISTORY_LOST = 286;
    private static final int CHANGE_STREAM_FATAL_ERROR = 280;
    private static final int RESUME_POINT_NOT_IN_OPLOG = 40576;
    // the server errors for a startAtOperationTime that the server does not accept (MongoDB-compatible servers may
    // answer so): BadValue, FailedToParse, and 40415 (an unknown field of the $changeStream stage)
    private static final int BAD_VALUE = 2;
    private static final int FAILED_TO_PARSE = 9;
    private static final int UNKNOWN_FIELD = 40415;

    private List<BsonDocument> bsonFilter;
    // where a cursor opened again resumes: the token of the last event whose exchange completed or, once a getMore
    // returned no event and the exchange of the last event read before it completed, the post batch resume token of
    // the change stream
    private BsonDocument resumeToken;
    // the operation time read just before the change stream was opened without a resume token. Until an exchange
    // completed (and so there is a resume token), a cursor opened again starts at it instead of the current time, so
    // the changes made in between are not skipped. It is not stored: a route started again without a stored token
    // still starts at the current time
    private BsonTimestamp startTime;
    // whether the server refused startAtOperationTime: the consumer then no longer tries it
    private boolean startTimeRefused;
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
        if (resumeToken == null) {
            return openWithoutToken();
        }
        try {
            return watch().resumeAfter(resumeToken).iterator();
        } catch (MongoCommandException e) {
            if (isStartNotInOplog(e.getErrorCode())) {
                return openWithoutTokenAfterHistoryLost(e);
            }
            if (e.getErrorCode() != INVALID_RESUME_TOKEN) {
                throw e;
            }
            // this is the answer for the token of an invalidate event, the last event of a change stream whose
            // collection was dropped or renamed (or its database dropped): resumeAfter cannot resume from it, while
            // startAfter (MongoDB 4.2) opens a new change stream after it, which sees the changes of a collection
            // created again under the same name. A token that is invalid for another reason fails here too, and the
            // consumer tries again after the cursor regeneration delay, as before
            MongoCursor cursor;
            try {
                cursor = watch().startAfter(resumeToken).iterator();
            } catch (MongoCommandException startAfterFailure) {
                if (isStartNotInOplog(startAfterFailure.getErrorCode())) {
                    return openWithoutTokenAfterHistoryLost(startAfterFailure);
                }
                startAfterFailure.addSuppressed(e);
                throw startAfterFailure;
            } catch (RuntimeException startAfterFailure) {
                startAfterFailure.addSuppressed(e);
                throw startAfterFailure;
            }
            log.info("The change stream of collection {} cannot be resumed ({}). Started a new change stream after the"
                     + " event that ended it.",
                    endpoint.getCollection(), e.getErrorMessage());
            return cursor;
        }
    }

    /**
     * Opens the change stream at the current time when the oplog no longer has the resume token: it can never be
     * resumed after it (the server answers the same for every attempt, and for every start of the route while the token
     * is stored), and the changes after it that the oplog still has cannot be read either. The stored token is replaced
     * by the next one the consumer commits.
     */
    private MongoCursor openWithoutTokenAfterHistoryLost(MongoCommandException e) {
        log.warn("The change stream of collection {} cannot be resumed after its resume token, as the oplog no longer goes"
                 + " back that far ({}). Started a new change stream at the current time, changes made since that token"
                 + " may not have been consumed.",
                endpoint.getCollection(), e.getErrorMessage());
        resumeToken = null;
        // the start time is older than the token, so the oplog does not have it either
        startTime = null;
        return openWithoutToken();
    }

    private MongoCursor openWithoutToken() {
        if (startTime != null) {
            // no exchange completed since the change stream was first opened: start where it started
            try {
                return watch().startAtOperationTime(startTime).iterator();
            } catch (MongoCommandException e) {
                int code = e.getErrorCode();
                if (isStartNotInOplog(code)) {
                    // the oplog no longer goes back that far (no exchange completed for longer than the oplog window):
                    // go on from the current time, as the consumer did before it remembered the start
                    warnStartedAtCurrentTime(e.getErrorMessage());
                } else if (code == BAD_VALUE || code == FAILED_TO_PARSE || code == UNKNOWN_FIELD) {
                    // the server does not accept the option: go on from the current time, as the consumer did before
                    // it remembered the start, and do not try it again
                    log.warn("The change stream of collection {} cannot be started at an operation time, as the server"
                             + " does not accept startAtOperationTime ({}). Started a new change stream at the current"
                             + " time, changes made since the previous one was opened may not have been consumed.",
                            endpoint.getCollection(), e.getErrorMessage());
                    startTimeRefused = true;
                } else {
                    throw e;
                }
                startTime = null;
            }
        }
        startTime = startTimeRefused ? null : nextOperationTime();
        return watch().iterator();
    }

    private static boolean isStartNotInOplog(int code) {
        return code == CHANGE_STREAM_HISTORY_LOST || code == CHANGE_STREAM_FATAL_ERROR || code == RESUME_POINT_NOT_IN_OPLOG;
    }

    private void warnStartedAtCurrentTime(String error) {
        log.warn("The change stream of collection {} cannot be started again where it was first opened, as the oplog no"
                 + " longer goes back that far ({}). Started a new change stream at the current time, changes made"
                 + " since then may not have been consumed.",
                endpoint.getCollection(), error);
    }

    /**
     * The operation time right after the one in the reply of a {@code ping} sent just before the change stream is
     * opened at the current time, or {@code null} when the server does not report one (a standalone server, which has
     * no change streams) or answers the ping with an error.
     * <p>
     * A change stream without a resume option starts at the current time. The driver keeps the operation time of the
     * reply that opened it, to resume the change stream itself, but does not expose it, and the resume token of the
     * cursor is only known once a getMore succeeded. So this is where a cursor that the consumer thread opens again
     * before any exchange completed starts. When the ping and the change stream go to the same member it is not later
     * than where the first change stream started, so a cursor opened again may also deliver a change made between the
     * ping and the opening of the first one. With a secondary read preference they can go to different members, and a
     * cursor opened again then starts at most at a change made before the ping.
     */
    private BsonTimestamp nextOperationTime() {
        BsonDocument reply;
        try {
            BsonDocument ping = new BsonDocument("ping", new BsonInt32(1));
            // sent to a server the change stream may read from (runCommand alone selects the primary)
            reply = endpoint.getMongoDatabase().runCommand(ping, dbCol.getReadPreference(), BsonDocument.class);
        } catch (MongoServerException e) {
            // the change stream can be opened all the same, only it starts at the current time if it has to be opened
            // again. No server to send it to is not caught here: opening the change stream, with the same read
            // preference, would fail the same way
            log.debug("Cannot read the operation time of the server: {}", e.getMessage());
            return null;
        }
        if (reply == null || !reply.isTimestamp("operationTime")) {
            return null;
        }
        BsonTimestamp operationTime = reply.getTimestamp("operationTime");
        // for read concern local the operation time of a command that writes nothing is the timestamp of the newest
        // entry of the oplog (runCommand reference; the cluster time through mongos), and a change stream includes the
        // changes at its startAtOperationTime: start right after it
        return new BsonTimestamp(operationTime.getTime(), operationTime.getInc() + 1);
    }

    private ChangeStreamIterable<Document> watch() {
        ChangeStreamIterable<Document> iterable = bsonFilter != null
                ? dbCol.watch(bsonFilter)
                : dbCol.watch();

        iterable.fullDocument(endpoint.getFullDocument());
        return iterable;
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
            // whether the exchange of the last event this cursor returned completed, so that the post batch resume
            // token does not pass an event whose exchange failed. A failed event that a later one passed is not held
            // back: the token of the later event, committed when its exchange completed, already passed it
            boolean lastRouted = true;
            while (keepRunning) {
                // tryNext rather than hasNext: hasNext waits for the next event across the getMores that return none, and
                // only after one of those does the cursor give the post batch resume token, the position up to which
                // the change stream has read the oplog. Without an event a getMore waits on the server for up to its
                // maxAwaitTime
                ChangeStreamDocument<Document> dbObj = (ChangeStreamDocument<Document>) cursor.tryNext();
                if (dbObj == null) {
                    if (!keepRunning || cursor.getServerCursor() == null) {
                        // the server closed the cursor (an invalidate event ended the change stream), where hasNext
                        // returns false
                        break;
                    }
                    if (lastRouted) {
                        advanceToPostBatchResumeToken();
                    }
                    continue;
                }
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
                    // a failed event does not advance the resume token itself, but a later event that
                    // succeeds commits its own, so the failure is reported rather than retried
                    lastRouted = processExchange(exchange);
                    if (lastRouted) {
                        this.resumeToken = currentResumeToken;
                        commitManager.recordResumeToken(currentResumeToken);
                        commitManager.commit();
                    }
                } catch (Exception e) {
                    getExceptionHandler().handleException("Error committing the change stream resume token", exchange, e);
                } finally {
                    consumer.releaseExchange(exchange, false);
                }
            }
        } catch (MongoException e) {
            // cursor.hasNext() opens socket and waiting for data
            // it throws exception when cursor is closed in another thread
            // there is no way to stop hasNext() before closing cursor
            if (keepRunning && resumeToken == null && startTime != null && isStartNotInOplog(e.getCode())) {
                // a change stream started at the start time and failed on a getMore because the oplog no longer goes
                // back that far (should the server not have refused it in the aggregate): the next one starts at the
                // current time, as in openWithoutToken
                warnStartedAtCurrentTime(e.getMessage());
                startTime = null;
            } else if (keepRunning) {
                log.debug("Exception from MongoDB, will regenerate cursor.", e);
            } else {
                throw e;
            }
        }
    }

    /**
     * Takes the post batch resume token of the cursor after a getMore that returned no event, as the token a cursor
     * opened again resumes after, and commits it. On a collection that sees no change while the rest of the replica set
     * is written, the token of the last event soon falls out of the oplog, and a change stream that the driver cannot
     * resume itself (the server cannot be reached for longer than the server selection timeout) could then no longer be
     * resumed, while this token still can. A filter of the change stream ({@code streamFilter}) does not hold it back
     * either: it passes the events the filter left out.
     */
    private void advanceToPostBatchResumeToken() {
        BsonDocument postBatchResumeToken = cursor instanceof MongoChangeStreamCursor<?> changeStreamCursor
                ? changeStreamCursor.getResumeToken() : null;
        // before the first reply with a post batch resume token (MongoDB 4.0.7 and later send one with every reply) the
        // cursor gives the token it was opened with, or null when it was opened without one
        if (postBatchResumeToken == null || postBatchResumeToken.equals(resumeToken)) {
            return;
        }
        resumeToken = postBatchResumeToken;
        try {
            commitManager.recordResumeToken(postBatchResumeToken);
            commitManager.commit();
        } catch (Exception e) {
            getExceptionHandler().handleException("Error committing the change stream resume token", e);
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
        // released by doRun once the outcome has been read, as an auto-released exchange may already be reset by then
        Exchange exchange = consumer.createExchange(false);
        Message message = exchange.getIn();
        message.setHeader(MongoDbConstants.DATABASE, endpoint.getDatabase());
        message.setHeader(MongoDbConstants.COLLECTION, endpoint.getCollection());
        message.setHeader(MongoDbConstants.FROM_TAILABLE, true);
        message.setBody(dbObj);
        return exchange;
    }

}
