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
package org.apache.camel.component.mongodb.dst;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoInterruptedException;
import com.mongodb.MongoSocketReadException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.ServerCursor;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.ListCollectionNamesIterable;
import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.client.model.changestream.OperationType;
import org.apache.camel.test.dst.Dst;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonTimestamp;
import org.bson.Document;

/**
 * A fake MongoDB deployment (a replica set seen as one server) with one database, one collection and its change stream,
 * behind the MongoClient of the connectionBean option. Written from the MongoDB manual (8.0) and the Java driver
 * documentation (5.x); the rules it follows are quoted where they are implemented and marked:
 * <ul>
 * <li>[M] MongoDB manual, "Change Streams", "Resume a Change Stream", "Change Events" (drop, invalidate);</li>
 * <li>[D] the Java driver: ChangeStreamIterable and MongoChangeStreamCursor Javadoc, and the resume of a change stream
 * on a resumable error (ChangeStreamBatchCursor of driver 5.13, read with javap);</li>
 * <li>[A] an assumption, where the documents leave the behaviour open; the seed picks between the allowed choices.</li>
 * </ul>
 * <p>
 * Modelled: the events of the collection in order (insert, update, replace, delete) with their resume tokens; a change
 * stream opened at the current time, after a resume token (resumeAfter) or after an invalidate event (startAfter); a
 * dropped collection, whose open change streams get a drop and an invalidate event and are then closed, and which an
 * insert makes again; the server out of reach for a while (a change stream cannot be opened, a getMore fails, and the
 * driver resumes the change stream itself on that resumable error until server selection times out); the fullDocument
 * option (the document of an update is only there with updateLookup).
 * <p>
 * Not modelled: the oplog window (a resume token that fell out of the oplog, ChangeStreamHistoryLost), sharded
 * clusters, pipelines (streamFilter), pre-images, renames and dropDatabase (they end the change stream as a drop does),
 * transactions, and batching (one event per getMore).
 * <p>
 * [A] The post batch resume token that the cursor gives after a getMore without an event (tryNext returned null) is the
 * token of the last event the cursor returned, and null before its first event: events are never filtered and no other
 * collection is written, so the change stream has read the oplog up to that event (a real server returns a token for
 * the start position too; null makes the consumer keep the token it has).
 * <p>
 * Timing (the one-stack rule of the harness): a getMore waits up to its maxAwaitTime for an event and takes a round
 * trip; writes of the other clients take effect at once and never block.
 */
final class FakeMongo {

    static final String DATABASE = "dst";
    static final String COLLECTION = "orders";
    /** [D] MongoClientSettings: serverSelectionTimeout defaults to 30 seconds. */
    long serverSelectionTimeout = 30_000;

    private final Dst dst;
    final List<String> violations = new ArrayList<>();
    boolean faults = true;
    double getMoreFailRate;
    double slowRate;

    private final List<Event> oplog = new ArrayList<>();
    private boolean collectionExists = true;
    private long unreachableUntil;
    private final MongoClient client;
    private final List<FakeCursor> cursors = new ArrayList<>();
    /** The consumer thread is interrupted at its next call of a cursor (see the world). */
    boolean interruptRequested;
    Consumer<String> onOpen = s -> {
    };
    Runnable onInterrupt = () -> {
    };

    /** An entry of the change stream of the collection. seq is 1-based (the index in the oplog + 1). */
    record Event(long seq, OperationType type, String id) {
        BsonDocument token() {
            return new BsonDocument("_data", new BsonString(String.format("%016x", seq)));
        }
    }

    FakeMongo(Dst dst) {
        this.dst = dst;
        this.client = proxy(MongoClient.class, (m, args) -> switch (m) {
            case "getDatabase" -> database((String) args[0]);
            case "getClusterDescription" -> null;
            default -> notModelled("MongoClient." + m);
        });
    }

    MongoClient client() {
        return client;
    }

    static long seqOf(String tokenJson) {
        return Long.parseLong(BsonDocument.parse(tokenJson).getString("_data").getValue(), 16);
    }

    // ------------------------------------------------------------------------------------------------------------
    // the other clients and the operator

    /** Another client writes: [M] an insert makes the collection when it does not exist. */
    Event write(OperationType type, String id) {
        if (!collectionExists) {
            if (type != OperationType.INSERT) {
                return null;
            }
            collectionExists = true;
            dst.trace("mongo: collection made again by an insert");
        }
        return append(type, id);
    }

    /**
     * The collection is dropped: [M] "drop: Occurs when a collection is dropped"; "invalidate: ... occur after a drop
     * ... closes the change stream cursor".
     */
    void drop() {
        if (!collectionExists) {
            return;
        }
        collectionExists = false;
        append(OperationType.DROP, null);
        append(OperationType.INVALIDATE, null);
        dst.trace("mongo: collection dropped");
    }

    /** The server cannot be reached by the consumer for {@code millis}. */
    void unreachable(long millis) {
        unreachableUntil = Math.max(unreachableUntil, dst.now() + millis);
        dst.trace("mongo: unreachable until t=" + unreachableUntil);
    }

    boolean collectionExists() {
        return collectionExists;
    }

    boolean isReachable() {
        return dst.now() >= unreachableUntil;
    }

    List<Event> oplog() {
        return oplog;
    }

    private Event append(OperationType type, String id) {
        Event e = new Event(oplog.size() + 1, type, id);
        oplog.add(e);
        return e;
    }

    // ------------------------------------------------------------------------------------------------------------
    // the client

    private MongoDatabase database(String name) {
        if (!DATABASE.equals(name)) {
            violations.add("unexpected database " + name);
        }
        return proxy(MongoDatabase.class, (m, args) -> switch (m) {
            case "getName" -> DATABASE;
            case "getCollection" -> collection((String) args[0]);
            case "listCollectionNames" -> collectionNames();
            case "runCommand" -> runCommand(args);
            default -> notModelled("MongoDatabase." + m);
        });
    }

    /**
     * [M] ping: the reply of a replica set carries operationTime, the timestamp of the newest oplog entry for read
     * concern local (runCommand reference). The event of seq s has the operation time (1, s).
     */
    private BsonDocument runCommand(Object[] args) {
        if (args.length != 3 || !(args[0] instanceof BsonDocument cmd) || !cmd.containsKey("ping")
                || !ReadPreference.primary().equals(args[1]) || args[2] != BsonDocument.class) {
            violations.add("not modelled: runCommand " + args[0]);
            throw new UnsupportedOperationException("dst: not modelled: runCommand");
        }
        checkInterrupt();
        selectServer("ping");
        roundTrip("ping");
        return new BsonDocument("ok", new BsonInt32(1)).append("operationTime", new BsonTimestamp(1, oplog.size()));
    }

    private ListCollectionNamesIterable collectionNames() {
        selectServer("listCollectionNames");
        roundTrip("listCollectionNames");
        List<String> names = collectionExists ? List.of(COLLECTION) : List.of();
        return proxy(ListCollectionNamesIterable.class, (m, args) -> switch (m) {
            case "iterator" -> names.iterator();
            case "spliterator" -> names.spliterator();
            case "forEach" -> {
                names.forEach((Consumer<? super String>) args[0]);
                yield null;
            }
            default -> notModelled("ListCollectionNamesIterable." + m);
        });
    }

    private MongoCollection<Document> collection(String name) {
        if (!COLLECTION.equals(name)) {
            violations.add("unexpected collection " + name);
        }
        return proxy(MongoCollection.class, (m, args) -> switch (m) {
            case "watch" -> {
                if (args != null && args.length > 0 && !(args[0] instanceof List)) {
                    yield notModelled("watch(" + args[0].getClass().getSimpleName() + ")");
                }
                if (args != null && args.length > 0 && !((List<?>) args[0]).isEmpty()) {
                    yield notModelled("watch with a pipeline");
                }
                yield new Watch().proxy();
            }
            case "getNamespace" -> null;
            case "getReadPreference" -> ReadPreference.primary();
            default -> notModelled("MongoCollection." + m);
        });
    }

    /** The options of a change stream, up to the iterator that opens it. */
    private final class Watch {
        BsonDocument resumeAfter;
        BsonDocument startAfter;
        BsonTimestamp startAtOperationTime;
        FullDocument fullDocument = FullDocument.DEFAULT;
        ChangeStreamIterable<Document> self;

        ChangeStreamIterable<Document> proxy() {
            self = FakeMongo.this.proxy(ChangeStreamIterable.class, (m, args) -> switch (m) {
                case "fullDocument" -> {
                    fullDocument = (FullDocument) args[0];
                    yield self;
                }
                case "resumeAfter" -> {
                    resumeAfter = (BsonDocument) args[0];
                    yield self;
                }
                case "startAfter" -> {
                    startAfter = (BsonDocument) args[0];
                    yield self;
                }
                case "startAtOperationTime" -> {
                    startAtOperationTime = (BsonTimestamp) args[0];
                    yield self;
                }
                case "batchSize", "maxAwaitTime" -> self;
                case "iterator", "cursor" -> open(this);
                default -> notModelled("ChangeStreamIterable." + m);
            });
            return self;
        }
    }

    private void roundTrip(String what) {
        if (faults && dst.chance(slowRate)) {
            dst.block(dst.between(200, 3000), "slow " + what);
        } else {
            dst.block(dst.between(1, 20), what);
        }
    }

    /** [D] server selection: waits for the server up to serverSelectionTimeout, then MongoTimeoutException. */
    private void selectServer(String what) {
        if (dst.now() < unreachableUntil) {
            long wait = Math.min(unreachableUntil - dst.now(), serverSelectionTimeout);
            dst.block(wait, "server selection for " + what);
            if (dst.now() < unreachableUntil) {
                throw new MongoTimeoutException(
                        "dst: timed out after " + serverSelectionTimeout
                                                + " ms while waiting for a server (" + what + ")");
            }
        }
    }

    /** [M] the aggregate that opens a change stream. */
    private MongoChangeStreamCursor<ChangeStreamDocument<Document>> open(Watch w) {
        checkInterrupt();
        selectServer("open the change stream");
        roundTrip("aggregate $changeStream");
        int position;
        if (w.startAfter != null || w.resumeAfter != null) {
            BsonDocument token = w.startAfter != null ? w.startAfter : w.resumeAfter;
            long seq;
            try {
                seq = Long.parseLong(token.getString("_data").getValue(), 16);
            } catch (RuntimeException e) {
                violations.add("a resume token that the server did not make: " + token.toJson());
                throw command(260, "InvalidResumeToken", "dst: the resume token cannot be decoded");
            }
            // [A] a token of an event of the collection before it was dropped and made again still resumes, as long as
            // the oplog has it (the oplog window is not modelled): the change stream delivers the events after it,
            // also the drop and the invalidate of the earlier collection, as a token is a position in the oplog
            if (seq < 1 || seq > oplog.size()) {
                violations.add("a resume token that the server did not make: " + token.toJson());
                throw command(260, "InvalidResumeToken", "dst: the resume token was not found");
            }
            if (w.startAfter == null && oplog.get((int) seq - 1).type() == OperationType.INVALIDATE) {
                // [M] "You cannot use resumeAfter to resume a change stream after an invalidate event ... Starting in
                // MongoDB 4.2, you can use startAfter to start a new change stream after an invalidate event."
                throw command(260, "InvalidResumeToken",
                        "Attempting to resume a change stream using 'resumeAfter' is not allowed from an invalidate"
                                                         + " notification.");
            }
            position = (int) seq;
        } else if (w.startAtOperationTime != null) {
            // [M] "The change stream will only provide changes that occurred at or after the specified timestamp" (the
            // oplog window is not modelled)
            long time = w.startAtOperationTime.getInc();
            if (w.startAtOperationTime.getTime() != 1 || time < 1 || time > oplog.size() + 1) {
                violations.add("an operation time that the server did not report: " + w.startAtOperationTime);
            }
            position = (int) Math.max(0, Math.min(oplog.size(), time - 1));
        } else {
            // [M] a change stream without a resume token starts at the current time
            position = oplog.size();
        }
        FakeCursor c = new FakeCursor(position, w.fullDocument);
        cursors.add(c);
        dst.trace("mongo: change stream opened at " + position + (w.startAfter != null ? " (startAfter)"
                : w.resumeAfter != null ? " (resumeAfter)"
                : w.startAtOperationTime != null ? " (startAtOperationTime)" : " (now)"));
        String how = w.startAfter != null ? "startAfter" : w.resumeAfter != null ? "resumeAfter"
                : w.startAtOperationTime != null ? "startAtOperationTime" : "now";
        // the consumer thread runs on the executor named after the endpoint; the route start opens the first cursor
        onOpen.accept(how + (dst.currentExecutor().startsWith("mongodb:") ? " (consumer thread)" : " (route start)"));
        return c.proxy;
    }

    /**
     * [D] a call of an interrupted thread throws MongoInterruptedException and keeps the interrupt flag, so the next
     * call throws it too.
     */
    private void checkInterrupt() {
        if (!dst.currentExecutor().startsWith("mongodb:")) {
            // only the consumer thread (its executor is named after the endpoint) is interrupted
            return;
        }
        if (interruptRequested) {
            interruptRequested = false;
            Thread.currentThread().interrupt();
            dst.trace("mongo: the consumer thread is interrupted");
            onInterrupt.run();
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new MongoInterruptedException("dst: interrupted", null);
        }
    }

    private static MongoCommandException command(int code, String name, String message) {
        BsonDocument response = new BsonDocument("ok", new BsonInt32(0)).append("code", new BsonInt32(code))
                .append("codeName", new BsonString(name)).append("errmsg", new BsonString(message));
        return new MongoCommandException(response, new ServerAddress("fake", 27017));
    }

    /** The position of the consumer in the change stream that it opened most recently. */
    int lastCursorStart() {
        return cursors.isEmpty() ? -1 : cursors.get(cursors.size() - 1).start;
    }

    private final class FakeCursor {
        final int start;
        final FullDocument fullDocument;
        final MongoChangeStreamCursor<ChangeStreamDocument<Document>> proxy;
        int position;
        boolean closed;
        boolean invalidated;
        BsonDocument cachedToken;

        FakeCursor(int position, FullDocument fullDocument) {
            this.start = position;
            this.position = position;
            this.fullDocument = fullDocument;
            this.proxy = FakeMongo.this.proxy(MongoChangeStreamCursor.class, (m, args) -> switch (m) {
                case "hasNext" -> hasNext();
                case "next" -> next();
                case "tryNext" -> tryNext();
                case "getServerCursor" -> serverCursor();
                case "close" -> {
                    closed = true;
                    yield null;
                }
                case "getResumeToken" -> cachedToken;
                default -> notModelled("MongoChangeStreamCursor." + m);
            });
        }

        /** [D] hasNext blocks until the next change, and returns false once the server closed the cursor. */
        private boolean hasNext() {
            if (closed) {
                violations.add("hasNext on a closed cursor");
                throw new IllegalStateException("Cursor has been closed");
            }
            while (true) {
                checkInterrupt();
                if (invalidated) {
                    // [M] the invalidate event closes the change stream cursor: no event after it on this cursor
                    return false;
                }
                if (position < oplog.size()) {
                    return true;
                }
                getMore();
            }
        }

        /**
         * [D] tryNext returns the next change, or sends one getMore and returns null when it brought none; once the
         * server closed the cursor it returns null without a getMore (CommandCursor.tryHasNext of driver 5.14).
         */
        private ChangeStreamDocument<Document> tryNext() {
            if (closed) {
                violations.add("tryNext on a closed cursor");
                throw new IllegalStateException("Cursor has been closed");
            }
            checkInterrupt();
            if (invalidated) {
                return null;
            }
            if (position >= oplog.size()) {
                getMore();
            }
            return position < oplog.size() ? next() : null;
        }

        /** [D] the server cursor, null once the server closed it (after the invalidate event). */
        private ServerCursor serverCursor() {
            if (closed) {
                violations.add("getServerCursor on a closed cursor");
                throw new IllegalStateException("Iterator has been closed");
            }
            return invalidated ? null : new ServerCursor(1, new ServerAddress("fake", 27017));
        }

        /** A getMore: waits up to maxAwaitTime (1 s by default) for an event, then answers. */
        private void getMore() {
            dst.awaitUntil(() -> position < oplog.size() || interruptRequested, 1000, "getMore");
            roundTrip("getMore");
            if (dst.now() < unreachableUntil || faults && dst.chance(getMoreFailRate)) {
                // [D] a resumable error (isResumableError is true for network errors): the driver opens the change
                // stream again after its cached token (ChangeStreamBatchCursor.resumeableOperation). The resume
                // itself is outside that retry, so what the resume throws reaches the caller: a server selection
                // timeout, or a network error of the resume aggregate
                dst.trace("mongo: getMore fails, the driver resumes the change stream");
                selectServer("resume the change stream");
                roundTrip("aggregate $changeStream (resume)");
                if (faults && dst.chance(0.5)) {
                    // [A] the resume aggregate fails with a network error too
                    throw new MongoSocketReadException(
                            "dst: the connection was reset", new ServerAddress("fake", 27017));
                }
            }
        }

        private ChangeStreamDocument<Document> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Event e = oplog.get(position++);
            if (e.type() == OperationType.INVALIDATE) {
                invalidated = true;
            }
            cachedToken = e.token();
            BsonDocument key = e.id() != null ? new BsonDocument("_id", new BsonString(e.id())) : null;
            Document full = null;
            if (e.type() == OperationType.INSERT || e.type() == OperationType.REPLACE
                    || e.type() == OperationType.UPDATE && fullDocument == FullDocument.UPDATE_LOOKUP) {
                full = new Document("_id", e.id()).append("seq", e.seq());
            }
            BsonDocument ns = e.type() == OperationType.INVALIDATE
                    ? null : new BsonDocument("db", new BsonString(DATABASE)).append("coll", new BsonString(COLLECTION));
            // positional arguments of the MongoDB Java driver 5.14: operationType, resumeToken, ns, nsType, to,
            // fullDocument, fullDocumentBeforeChange, documentKey, clusterTime, updateDescription, txnNumber, lsid,
            // wallTime, splitEvent, extraElements
            return new ChangeStreamDocument<>(
                    e.type().getValue(), e.token(), ns, null, null, full, null, key, null, null, null, null, null, null,
                    null);
        }
    }

    // ------------------------------------------------------------------------------------------------------------

    private interface Handler {
        Object invoke(String method, Object[] args) throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<?> type, Handler handler) {
        return (T) Proxy.newProxyInstance(FakeMongo.class.getClassLoader(), new Class<?>[] { type },
                (p, method, args) -> switch (method.getName()) {
                    case "toString" -> "FakeMongo." + type.getSimpleName();
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == args[0];
                    default -> handler.invoke(method.getName(), args);
                });
    }

    private Object notModelled(String what) {
        violations.add("not modelled: " + what);
        throw new UnsupportedOperationException("dst: not modelled: " + what);
    }
}
