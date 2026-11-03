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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.mongodb.MongoCommandException;
import com.mongodb.ServerAddress;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.ListCollectionNamesIterable;
import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.client.model.changestream.OperationType;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.processor.state.MemoryStateRepository;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A change stream ends with an {@code invalidate} event when its collection is dropped or renamed, or its database
 * dropped. The consumer goes on with a new change stream after that event, so it sees the changes of the collection
 * created again under the same name.
 */
class MongoDbChangeStreamsInvalidateTest {

    private static final ServerAddress SERVER = new ServerAddress("localhost", 27017);

    private static final BsonDocument INSERT_TOKEN = token("insert");
    private static final BsonDocument DROP_TOKEN = token("drop");
    private static final BsonDocument INVALIDATE_TOKEN = token("invalidate");
    private static final BsonDocument RECREATED_INSERT_TOKEN = token("insert-after-recreate");

    private final MongoClient client = mock(MongoClient.class);
    private final MongoCollection<Document> collection = mockCollection();
    // the resume options of every change stream the consumer opened, in order
    private final List<String> opened = Collections.synchronizedList(new ArrayList<>());
    private CamelContext context;

    @SuppressWarnings("unchecked")
    private static MongoCollection<Document> mockCollection() {
        return mock(MongoCollection.class);
    }

    private static BsonDocument token(String name) {
        return new BsonDocument("_data", new BsonString(name));
    }

    @BeforeEach
    void setUp() {
        MongoDatabase database = mock(MongoDatabase.class);
        ListCollectionNamesIterable names = mock(ListCollectionNamesIterable.class);
        when(names.spliterator()).thenAnswer(i -> List.of("events").spliterator());
        when(database.listCollectionNames()).thenReturn(names);
        when(database.getCollection("events", Document.class)).thenReturn(collection);
        when(database.getName()).thenReturn("test");
        when(client.getDatabase("test")).thenReturn(database);
        when(collection.watch()).thenAnswer(i -> new FakeChangeStream().iterable);

        context = new DefaultCamelContext();
        context.getRegistry().bind("myDb", client);
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void consumerGoesOnAfterTheCollectionIsDropped() throws Exception {
        MockEndpoint mock = startRoute("mongodb:myDb?database=test&collection=events&consumerType=changeStreams"
                                       + "&cursorRegenerationDelay=100");

        mock.expectedMessageCount(4);
        mock.assertIsSatisfied();
        assertEquals(List.of("insert", "drop", "invalidate", "insert"), operationTypes(mock));
        assertEquals(new Document("_id", 2).append("text", "after the collection was created again"),
                mock.getExchanges().get(3).getIn().getBody());
        // the next change stream resumes after the event of the new collection as usual
        await().atMost(10, TimeUnit.SECONDS).until(() -> opened.contains("resumeAfter " + RECREATED_INSERT_TOKEN));
        List<String> first;
        synchronized (opened) {
            // the consumer thread may still be opening change streams
            first = List.copyOf(opened.subList(0, 3));
        }
        assertEquals(List.of("new", "resumeAfter " + INVALIDATE_TOKEN, "startAfter " + INVALIDATE_TOKEN), first);
    }

    @Test
    void consumerStartsFromAStoredTokenOfAnInvalidateEvent() throws Exception {
        // the token stored by the previous run of the route is the one of the invalidate event
        MemoryStateRepository repository = new MemoryStateRepository();
        repository.setState("mongo/events", INVALIDATE_TOKEN.toJson());
        context.getRegistry().bind("tokens", repository);

        MockEndpoint mock = startRoute("mongodb:myDb?database=test&collection=events&consumerType=changeStreams"
                                       + "&cursorRegenerationDelay=100&changeStreamTokenRepository=#tokens");

        mock.expectedMessageCount(1);
        mock.assertIsSatisfied();
        assertEquals(List.of("insert"), operationTypes(mock));
        // the consumer stores the token after the exchange is done, which can be after the mock received it
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(RECREATED_INSERT_TOKEN.toJson(), repository.getState("mongo/events")));
    }

    private static List<Object> operationTypes(MockEndpoint mock) {
        return mock.getExchanges().stream()
                .map(e -> e.getIn().getHeader(MongoDbConstants.STREAM_OPERATION_TYPE))
                .toList();
    }

    private MockEndpoint startRoute(String uri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId("mongo").to("mock:result");
            }
        });
        context.start();
        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        mock.setResultWaitTime(10000);
        return mock;
    }

    @SuppressWarnings("unchecked")
    private static ChangeStreamDocument<Document> event(
            OperationType type, BsonDocument token, Document fullDocument) {
        ChangeStreamDocument<Document> event = mock(ChangeStreamDocument.class);
        when(event.getOperationType()).thenReturn(type);
        when(event.getResumeToken()).thenReturn(token);
        when(event.getFullDocument()).thenReturn(fullDocument);
        if (fullDocument != null) {
            when(event.getDocumentKey()).thenReturn(new BsonDocument("_id", new BsonInt32(fullDocument.getInteger("_id"))));
        }
        return event;
    }

    /**
     * One change stream opened with {@code collection.watch()}, answering like a MongoDB server (4.2 or later) on which
     * the collection was dropped and then created again (change streams manual, "Invalidate Events" and "Resume a
     * Change Stream"):
     * <ul>
     * <li>without a resume option: the insert, the drop and the invalidate event; the server then closes the cursor, so
     * {@code hasNext()} returns false;</li>
     * <li>{@code resumeAfter} the token of the invalidate event: the aggregate command fails with InvalidResumeToken
     * (260), "Attempting to resume a change stream using 'resumeAfter' is not allowed from an invalidate notification",
     * which the driver throws from {@code iterator()} as a {@link MongoCommandException};</li>
     * <li>{@code startAfter} the token of the invalidate event: a new change stream, with the insert into the
     * collection created again;</li>
     * <li>{@code resumeAfter} any later token: no new event;</li>
     * <li>both {@code resumeAfter} and {@code startAfter}: the aggregate command fails with error 50865, "Do not
     * specify both 'resumeAfter' and 'startAfter' in a $changeStream stage" (the consumer never sets both).</li>
     * </ul>
     */
    private final class FakeChangeStream {

        private final ChangeStreamIterable<Document> iterable = mockIterable();
        private BsonDocument resumeAfter;
        private BsonDocument startAfter;

        @SuppressWarnings("unchecked")
        private static ChangeStreamIterable<Document> mockIterable() {
            return mock(ChangeStreamIterable.class);
        }

        private FakeChangeStream() {
            when(iterable.fullDocument(any(FullDocument.class))).thenReturn(iterable);
            when(iterable.resumeAfter(any(BsonDocument.class))).thenAnswer(i -> {
                resumeAfter = i.getArgument(0);
                return iterable;
            });
            when(iterable.startAfter(any(BsonDocument.class))).thenAnswer(i -> {
                startAfter = i.getArgument(0);
                return iterable;
            });
            when(iterable.iterator()).thenAnswer(i -> open());
        }

        private MongoChangeStreamCursor<ChangeStreamDocument<Document>> open() {
            List<ChangeStreamDocument<Document>> events;
            if (resumeAfter != null && startAfter != null) {
                throw commandFailed(50865, "Location50865", "Do not specify both 'resumeAfter' and 'startAfter' in a"
                                                            + " $changeStream stage");
            } else if (resumeAfter == null && startAfter == null) {
                opened.add("new");
                events = List.of(
                        event(OperationType.INSERT, INSERT_TOKEN, new Document("_id", 1).append("text", "first")),
                        event(OperationType.DROP, DROP_TOKEN, null),
                        event(OperationType.INVALIDATE, INVALIDATE_TOKEN, null));
            } else if (INVALIDATE_TOKEN.equals(resumeAfter)) {
                opened.add("resumeAfter " + resumeAfter);
                throw commandFailed(260, "InvalidResumeToken", "Attempting to resume a change stream using"
                                                               + " 'resumeAfter' is not allowed from an invalidate"
                                                               + " notification.");
            } else if (INVALIDATE_TOKEN.equals(startAfter)) {
                opened.add("startAfter " + startAfter);
                events = List.of(event(OperationType.INSERT, RECREATED_INSERT_TOKEN,
                        new Document("_id", 2).append("text", "after the collection was created again")));
            } else {
                opened.add("resumeAfter " + resumeAfter);
                events = List.of();
            }
            return cursor(events);
        }

        @SuppressWarnings("unchecked")
        private MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor(
                List<ChangeStreamDocument<Document>> events) {
            Iterator<ChangeStreamDocument<Document>> it = events.iterator();
            MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor = mock(MongoChangeStreamCursor.class);
            when(cursor.hasNext()).thenAnswer(i -> it.hasNext());
            when(cursor.next()).thenAnswer(i -> it.next());
            return cursor;
        }

        private static MongoCommandException commandFailed(int code, String codeName, String message) {
            return new MongoCommandException(
                    new BsonDocument("ok", new BsonInt32(0)).append("code", new BsonInt32(code))
                            .append("codeName", new BsonString(codeName)).append("errmsg", new BsonString(message)),
                    SERVER);
        }
    }

}
