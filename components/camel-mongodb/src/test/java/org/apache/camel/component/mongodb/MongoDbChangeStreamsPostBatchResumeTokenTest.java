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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ReadPreference;
import com.mongodb.ServerCursor;
import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.OperationType;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.mongodb.MongoDbChangeStreamsMocks.ResumeOptions;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.processor.state.MemoryStateRepository;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.mongodb.MongoDbChangeStreamsMocks.SERVER;
import static org.apache.camel.component.mongodb.MongoDbChangeStreamsMocks.errorReply;
import static org.apache.camel.component.mongodb.MongoDbChangeStreamsMocks.event;
import static org.apache.camel.component.mongodb.MongoDbChangeStreamsMocks.mockCollection;
import static org.apache.camel.component.mongodb.MongoDbChangeStreamsMocks.mockDatabase;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The change stream of a collection that sees no change keeps reading the oplog, which the rest of the replica set
 * writes: its post batch resume token moves on, while the token of the last event of the collection falls out of the
 * oplog. A cursor that the consumer opens again (the driver could not resume the change stream itself), or a start of
 * the route, must resume after the post batch resume token; and a token that the oplog no longer has must not be tried
 * for ever.
 */
class MongoDbChangeStreamsPostBatchResumeTokenTest {

    private static final BsonDocument HISTORY_LOST = errorReply(286, "ChangeStreamHistoryLost",
            "Resume of change stream was not possible, as the resume point may no longer be in the oplog.");
    private static final String KEY = "mongo/events";

    private final MongoClient client = mock(MongoClient.class);
    private final MongoCollection<Document> collection = mockCollection();
    // a durable store of the token (a database table): started here, so the consumer does not stop it, as a stopped
    // MemoryStateRepository forgets its state
    private final MemoryStateRepository tokens = new MemoryStateRepository();

    // the oplog of the replica set: the entries of this collection ("events") and of other collections. The entry at
    // index i has the position i + 1
    private final Object lock = new Object();
    private final List<String[]> oplog = new ArrayList<>();
    // the oldest position the oplog still has
    private int historyStart = 1;
    // positions of the entries that are invalidate events of this collection
    private final Set<Integer> invalidates = new HashSet<>();
    // the next getMore fails, and the driver cannot resume the change stream (the server cannot be reached)
    private boolean outagePending;
    // the furthest position a change stream of the consumer read up to
    private volatile int scanned;
    // the getMores that returned no event
    private final AtomicInteger emptyGetMores = new AtomicInteger();
    // how the consumer opened change streams, and the answer
    private final List<String> opened = Collections.synchronizedList(new ArrayList<>());
    // the bodies whose exchange fails, once each
    private final Set<String> failOnce = Collections.synchronizedSet(new HashSet<>());
    private final List<LogEvent> logged = new CopyOnWriteArrayList<>();
    private AbstractAppender appender;
    private CamelContext context;

    private static BsonDocument token(int position) {
        return new BsonDocument("_data", new BsonString(Integer.toString(position)));
    }

    @BeforeEach
    void setUp() {
        MongoDatabase database = mockDatabase(client, collection, this::open);
        when(collection.getReadPreference()).thenReturn(ReadPreference.primary());
        // runCommand reference: for read concern local, the operation time of a command that writes nothing is the
        // timestamp of the newest entry of the oplog; the entry at position p has the operation time (1, p)
        when(database.runCommand(any(Bson.class), eq(ReadPreference.primary()), eq(BsonDocument.class)))
                .thenAnswer(i -> {
                    synchronized (lock) {
                        return new BsonDocument("ok", new BsonInt32(1))
                                .append("operationTime", new BsonTimestamp(1, oplog.size()));
                    }
                });
        // the first entry of the oplog of the replica set
        write("other", "initiate");
        tokens.start();

        appender = new AbstractAppender("postBatchResumeToken", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                logged.add(event.toImmutable());
            }
        };
        appender.start();
        ((Logger) LogManager.getLogger(MongoDbChangeStreamsThread.class)).addAppender(appender);

        context = new DefaultCamelContext();
        context.getRegistry().bind("myDb", client);
        context.getRegistry().bind("tokens", tokens);
    }

    @AfterEach
    void tearDown() {
        try {
            if (context != null) {
                context.stop();
            }
        } finally {
            ((Logger) LogManager.getLogger(MongoDbChangeStreamsThread.class)).removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    void cursorOpenedAgainOnAQuietCollectionResumesAfterThePostBatchResumeToken() throws Exception {
        MockEndpoint mock = startRoute();
        write("events", "a");
        await().atMost(10, TimeUnit.SECONDS).until(() -> token(2).toJson().equals(tokens.getState(KEY)));

        // only other collections are written; the change stream reads them
        write("other", "x1");
        write("other", "x2");
        write("other", "x3");
        awaitQuietAt(5);

        synchronized (lock) {
            // the oplog rolls past event a, and the server cannot be reached for longer than the server selection
            // timeout, so the driver cannot resume the change stream itself
            historyStart = 3;
            outagePending = true;
            lock.notifyAll();
        }

        // on main the consumer opened the change stream after the token of a, and the server refused it every time
        await().atMost(10, TimeUnit.SECONDS).until(() -> opened.size() >= 2);
        assertEquals(List.of("now 2", "resumeAfter 5 -> ok"), firstOpened(2));
        write("events", "b");
        await().atMost(10, TimeUnit.SECONDS).until(() -> bodies(mock).equals(List.of("a", "b")));
        assertNoHistoryLostWarning();
    }

    @Test
    void routeStartedAgainAfterAQuietPeriodResumesAfterTheStoredPostBatchResumeToken() throws Exception {
        MockEndpoint mock = startRoute();
        write("events", "a");
        await().atMost(10, TimeUnit.SECONDS).until(() -> bodies(mock).equals(List.of("a")));
        write("other", "x1");
        write("other", "x2");
        write("other", "x3");
        awaitQuietAt(5);
        context.getRouteController().stopRoute("mongo");

        synchronized (lock) {
            historyStart = 3;
        }
        // changed while the route is stopped
        write("events", "b");

        // on main the start failed with ChangeStreamHistoryLost, as the stored token was the one of a
        context.getRouteController().startRoute("mongo");
        await().atMost(10, TimeUnit.SECONDS).until(() -> bodies(mock).equals(List.of("a", "b")));
        assertEquals("resumeAfter 5 -> ok", opened.get(1));
        assertNoHistoryLostWarning();
    }

    @Test
    void storedTokenNoLongerInTheOplogStartsAtTheCurrentTime() throws Exception {
        write("events", "a");
        write("other", "x1");
        write("other", "x2");
        synchronized (lock) {
            historyStart = 3;
        }
        tokens.setState(KEY, token(2).toJson());

        // on main the start failed with ChangeStreamHistoryLost, and so would every later one
        MockEndpoint mock = startRoute();
        write("events", "b");

        await().atMost(10, TimeUnit.SECONDS).until(() -> bodies(mock).equals(List.of("b")));
        assertEquals(List.of("resumeAfter 2 -> 286", "now 5"), firstOpened(2));
        assertHistoryLostWarning();
        // the token of b replaces the one the oplog no longer has
        await().atMost(10, TimeUnit.SECONDS).until(() -> token(5).toJson().equals(tokens.getState(KEY)));
    }

    @Test
    void tokenNoLongerInTheOplogWhenTheCursorIsOpenedAgainStartsAtTheCurrentTime() throws Exception {
        // every event after a fails, so the token of a stays the one to resume after
        failOnce.add("b");
        MockEndpoint mock = startRoute();
        write("events", "a");
        await().atMost(10, TimeUnit.SECONDS).until(() -> token(2).toJson().equals(tokens.getState(KEY)));
        write("events", "b");
        write("other", "x1");
        awaitQuietAt(4);

        synchronized (lock) {
            historyStart = 3;
            outagePending = true;
            lock.notifyAll();
        }

        // on main the consumer opened the change stream after the token of a for as long as the route ran
        await().atMost(10, TimeUnit.SECONDS).until(() -> opened.size() >= 3);
        assertEquals(List.of("now 2", "resumeAfter 2 -> 286", "now 5"), firstOpened(3));
        write("events", "c");
        await().atMost(10, TimeUnit.SECONDS).until(() -> bodies(mock).equals(List.of("a", "c")));
        assertHistoryLostWarning();
    }

    @Test
    void postBatchResumeTokenDoesNotPassAnEventWhoseExchangeFailed() throws Exception {
        // control, as on main: a cursor opened again resumes after the last event whose exchange completed, so the
        // event that failed is routed again
        failOnce.add("b");
        MockEndpoint mock = startRoute();
        write("events", "a");
        await().atMost(10, TimeUnit.SECONDS).until(() -> token(2).toJson().equals(tokens.getState(KEY)));
        write("events", "b");
        write("other", "x1");
        awaitQuietAt(4);
        assertEquals(token(2).toJson(), tokens.getState(KEY));

        synchronized (lock) {
            outagePending = true;
            lock.notifyAll();
        }

        await().atMost(10, TimeUnit.SECONDS).until(() -> bodies(mock).equals(List.of("a", "b")));
        assertEquals(List.of("now 2", "resumeAfter 2 -> ok"), firstOpened(2));
    }

    @Test
    void storedInvalidateTokenNoLongerInTheOplogStartsAtTheCurrentTime() throws Exception {
        // the collection was dropped: its change stream ended with an invalidate event, whose token is stored
        write("events", "invalidate");
        synchronized (lock) {
            invalidates.add(2);
        }
        write("other", "x1");
        write("other", "x2");
        synchronized (lock) {
            historyStart = 3;
        }
        tokens.setState(KEY, token(2).toJson());

        // on main startAfter failed with ChangeStreamHistoryLost, and the route did not start
        MockEndpoint mock = startRoute();
        write("events", "b");

        await().atMost(10, TimeUnit.SECONDS).until(() -> bodies(mock).equals(List.of("b")));
        assertEquals(List.of("resumeAfter 2 -> 260", "startAfter 2 -> 286", "now 5"), firstOpened(3));
        assertHistoryLostWarning();
    }

    /** Waits until the change stream read the oplog up to the position and then answered empty getMores. */
    private void awaitQuietAt(int position) {
        await().atMost(10, TimeUnit.SECONDS).until(() -> scanned == position);
        int empty = emptyGetMores.get();
        // the consumer asks again only once it handled the previous answer
        await().atMost(10, TimeUnit.SECONDS).until(() -> emptyGetMores.get() >= empty + 2);
    }

    private void assertHistoryLostWarning() {
        assertEquals(1, historyLostWarnings().size(), historyLostWarnings().toString());
    }

    private void assertNoHistoryLostWarning() {
        assertEquals(List.of(), historyLostWarnings());
    }

    private List<String> historyLostWarnings() {
        return logged.stream().filter(e -> e.getLevel() == Level.WARN)
                .map(e -> e.getMessage().getFormattedMessage())
                .filter(m -> m.startsWith("The change stream of collection events cannot be resumed after its resume"
                                          + " token, as the oplog no longer goes back that far"))
                .toList();
    }

    private void write(String coll, String text) {
        synchronized (lock) {
            oplog.add(new String[] { coll, text });
            lock.notifyAll();
        }
    }

    private List<String> firstOpened(int count) {
        synchronized (opened) {
            // the consumer thread may still be opening change streams
            return List.copyOf(opened.subList(0, Math.min(count, opened.size())));
        }
    }

    private static List<String> bodies(MockEndpoint mock) {
        return mock.getExchanges().stream()
                .map(e -> e.getIn().getBody(Document.class).getString("text"))
                .toList();
    }

    private MockEndpoint startRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("mongodb:myDb?database=test&collection=events&consumerType=changeStreams"
                     + "&changeStreamTokenRepository=#tokens&cursorRegenerationDelay=100")
                        .routeId("mongo")
                        .process(exchange -> {
                            String text = exchange.getIn().getBody(Document.class).getString("text");
                            if (failOnce.remove(text)) {
                                throw new IllegalStateException("processing of " + text + " failed");
                            }
                        })
                        .to("mock:result");
            }
        });
        context.start();
        return context.getEndpoint("mock:result", MockEndpoint.class);
    }

    /**
     * The aggregate that opens a change stream (change streams manual, "Resume a Change Stream"): without a resume
     * option it starts at the current time; with {@code resumeAfter} after the position of the token, unless the oplog
     * no longer has that position (ChangeStreamHistoryLost, 286) or the token is the one of an invalidate event
     * (InvalidResumeToken, 260, "Attempting to resume a change stream using 'resumeAfter' is not allowed from an
     * invalidate notification", checked before the oplog is read); with {@code startAfter} after the position of the
     * token, unless the oplog no longer has it (286).
     */
    private MongoChangeStreamCursor<ChangeStreamDocument<Document>> open(ResumeOptions options) {
        synchronized (lock) {
            int position;
            if (options.resumeAfter != null || options.startAfter != null) {
                String how = options.resumeAfter != null ? "resumeAfter" : "startAfter";
                BsonDocument token = options.resumeAfter != null ? options.resumeAfter : options.startAfter;
                int t = Integer.parseInt(token.getString("_data").getValue());
                if (options.resumeAfter != null && invalidates.contains(t)) {
                    opened.add(how + " " + t + " -> 260");
                    throw new MongoCommandException(
                            errorReply(260, "InvalidResumeToken", "Attempting to resume a change stream using"
                                                                  + " 'resumeAfter' is not allowed from an invalidate"
                                                                  + " notification."),
                            SERVER);
                }
                if (t < historyStart) {
                    opened.add(how + " " + t + " -> 286");
                    throw new MongoCommandException(HISTORY_LOST, SERVER);
                }
                opened.add(how + " " + t + " -> ok");
                position = t;
                return new FakeCursor(position, token).cursor;
            } else if (options.startAtOperationTime != null) {
                position = options.startAtOperationTime.getInc() - 1;
                opened.add("startAtOperationTime " + (position + 1));
            } else {
                position = oplog.size();
                opened.add("now " + (position + 1));
            }
            return new FakeCursor(position, null).cursor;
        }
    }

    /**
     * A change stream cursor that answers like the one of the MongoDB Java driver 5.14 (MongoChangeStreamCursorImpl,
     * ChangeStreamBatchCursor and CommandCursor, read with javap), one event per batch:
     * <ul>
     * <li>{@code hasNext()} waits for the next event across the getMores that return none, without changing the resume
     * token of the cursor; {@code next()} returns the event, after which the resume token is the post batch resume
     * token of its batch, the token of the event;</li>
     * <li>{@code tryNext()} returns the next event, or sends one getMore, which waits up to its maxAwaitTime on the
     * server (50 millis here), and returns null when it brought no event: the resume token is then the post batch
     * resume token of that getMore, the position up to which the change stream read the oplog;</li>
     * <li>before any reply the resume token is the token the cursor was opened with, or null;</li>
     * <li>{@code getServerCursor()} is not null until the server closes the cursor; after {@code close()} it, and every
     * read that starts, throws IllegalStateException; a getMore in progress ends, and then {@code hasNext()} throws
     * IllegalStateException while {@code tryNext()} returns null;</li>
     * <li>a getMore that fails while the server cannot be reached, which the driver cannot resume either, throws
     * MongoTimeoutException.</li>
     * </ul>
     */
    private final class FakeCursor {
        @SuppressWarnings("unchecked")
        private final MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor = mock(MongoChangeStreamCursor.class);
        private int position;
        private BsonDocument resumeToken;
        private volatile boolean closed;

        private FakeCursor(int position, BsonDocument initialToken) {
            this.position = position;
            this.resumeToken = initialToken;
            when(cursor.hasNext()).thenAnswer(i -> hasNext());
            when(cursor.next()).thenAnswer(i -> next());
            when(cursor.tryNext()).thenAnswer(i -> tryNext());
            when(cursor.getResumeToken()).thenAnswer(i -> {
                synchronized (lock) {
                    return resumeToken;
                }
            });
            when(cursor.getServerCursor()).thenAnswer(i -> {
                if (closed) {
                    throw new IllegalStateException("Iterator has been closed");
                }
                return new ServerCursor(42, SERVER);
            });
            doAnswer(i -> {
                closed = true;
                synchronized (lock) {
                    lock.notifyAll();
                }
                return null;
            }).when(cursor).close();
        }

        private boolean hasNext() throws InterruptedException {
            synchronized (lock) {
                while (true) {
                    if (eventReady()) {
                        return true;
                    }
                    getMore();
                }
            }
        }

        private ChangeStreamDocument<Document> next() {
            synchronized (lock) {
                String text = oplog.get(position++)[1];
                resumeToken = token(position);
                return event(invalidates.contains(position) ? OperationType.INVALIDATE : OperationType.INSERT,
                        token(position), new Document("_id", position).append("text", text));
            }
        }

        private ChangeStreamDocument<Document> tryNext() throws InterruptedException {
            synchronized (lock) {
                if (eventReady()) {
                    return next();
                }
                getMore();
                if (closed) {
                    // closed while the getMore waited: the driver lets it end and returns no event
                    return null;
                }
                if (eventReady()) {
                    return next();
                }
                resumeToken = token(position);
                return null;
            }
        }

        /** Reads the oplog up to the next event of the collection; true if there is one. */
        private boolean eventReady() {
            if (closed) {
                throw new IllegalStateException("Cursor has been closed");
            }
            while (position < oplog.size() && !"events".equals(oplog.get(position)[0])) {
                position++;
            }
            scanned = Math.max(scanned, position);
            return position < oplog.size();
        }

        private void getMore() throws InterruptedException {
            if (outagePending) {
                outagePending = false;
                throw new MongoTimeoutException("Timed out while waiting for a server that matches ReadPreference");
            }
            lock.wait(50);
            if (!closed && !eventReady()) {
                emptyGetMores.incrementAndGet();
            }
        }
    }
}
