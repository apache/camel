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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.mongodb.CursorType;
import com.mongodb.MongoSocketReadException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ServerAddress;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.ListCollectionNamesIterable;
import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.client.model.changestream.OperationType;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * When the MongoDB server cannot be reached, reading from the cursor fails and the consumer thread opens a new cursor
 * after {@code cursorRegenerationDelay}. Opening it fails too while the server is still unreachable (the driver gives
 * up after its server selection timeout, 30 seconds by default). The consumer thread must then try again, and not end
 * while the route stays started.
 */
class MongoDbConsumerCursorRegenerationTest {

    private static final ServerAddress SERVER = new ServerAddress("localhost", 27017);

    private final MongoClient client = mock(MongoClient.class);
    private final MongoCollection<Document> collection = mockCollection();
    private CamelContext context;

    @SuppressWarnings("unchecked")
    private static MongoCollection<Document> mockCollection() {
        return mock(MongoCollection.class);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        MongoDatabase database = mock(MongoDatabase.class);
        ListCollectionNamesIterable names = mock(ListCollectionNamesIterable.class);
        when(names.spliterator()).thenAnswer(i -> List.of("events").spliterator());
        when(database.listCollectionNames()).thenReturn(names);
        when(database.getCollection("events", Document.class)).thenReturn(collection);
        when(database.getName()).thenReturn("test");
        // the tailable consumer only accepts a capped collection
        when(database.runCommand(any(Bson.class))).thenReturn(new Document("capped", true));
        when(client.getDatabase("test")).thenReturn(database);

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
    @SuppressWarnings("unchecked")
    void tailableConsumerOpensANewCursorAfterAFailedRegeneration() throws Exception {
        MongoCursor<Document> broken = mock(MongoCursor.class);
        when(broken.hasNext()).thenThrow(new MongoSocketReadException("Prematurely reached end of stream", SERVER));

        MongoCursor<Document> recovered = mock(MongoCursor.class);
        when(recovered.hasNext()).thenReturn(true, false);
        when(recovered.next()).thenReturn(new Document("increasing", 1).append("text", "after the outage"));

        MongoCursor<Document> idle = mock(MongoCursor.class);
        when(idle.hasNext()).thenReturn(false);

        FindIterable<Document> find = mock(FindIterable.class);
        when(collection.find()).thenReturn(find);
        when(collection.find(any(Bson.class))).thenReturn(find);
        when(find.cursorType(CursorType.TailableAwait)).thenReturn(find);
        when(find.iterator())
                // the cursor of the start, whose read fails when the server goes away
                .thenReturn(broken)
                // the first regeneration, while the server is still unreachable
                .thenThrow(new MongoTimeoutException("Timed out while waiting for a server"))
                // the server is back
                .thenReturn(recovered)
                .thenReturn(idle);

        MockEndpoint mock = startRoute("mongodb:myDb?database=test&collection=events&tailTrackIncreasingField=increasing"
                                       + "&cursorRegenerationDelay=100");

        mock.expectedBodiesReceived(new Document("increasing", 1).append("text", "after the outage"));
        mock.assertIsSatisfied();
        verify(find, atLeast(3)).iterator();
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("mongo"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cursorOpenedWhileTheConsumerStopsIsClosedAndNotRead() throws Exception {
        MongoCursor<Document> broken = mock(MongoCursor.class);
        when(broken.hasNext()).thenThrow(new MongoSocketReadException("Prematurely reached end of stream", SERVER));
        MongoCursor<Document> opened = mock(MongoCursor.class);

        // the thread of the tailable consumer, run here on a thread of the test so that the test can stop it while
        // it opens a new cursor
        context.start();
        MongoDbEndpoint endpoint = context.getEndpoint(
                "mongodb:myDb?database=test&collection=events&tailTrackIncreasingField=increasing"
                                                       + "&cursorRegenerationDelay=100",
                MongoDbEndpoint.class);
        MongoDbTailableCursorConsumer consumer
                = (MongoDbTailableCursorConsumer) endpoint.createConsumer(exchange -> {
                });
        MongoDbTailingThread tailingThread = new MongoDbTailingThread(endpoint, consumer, consumer.initTailTracking());

        CountDownLatch opening = new CountDownLatch(1);
        FindIterable<Document> find = mock(FindIterable.class);
        when(collection.find()).thenReturn(find);
        when(find.cursorType(CursorType.TailableAwait)).thenReturn(find);
        when(find.iterator())
                .thenReturn(broken)
                .thenAnswer(i -> {
                    // the regeneration is still opening the cursor when the consumer is stopped
                    opening.countDown();
                    await().atMost(10, TimeUnit.SECONDS).until(() -> !tailingThread.keepRunning);
                    return opened;
                });

        tailingThread.init();
        Thread thread = new Thread(tailingThread, "tailing");
        thread.start();
        assertTrue(opening.await(10, TimeUnit.SECONDS), "the consumer did not open a new cursor");
        // returns when the thread has ended
        tailingThread.stop();
        thread.join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(thread.isAlive(), "the thread of the consumer has ended");

        // stop() found no cursor to close; the one opened afterwards is closed by the thread
        verify(opened).close();
        verify(opened, never()).hasNext();
    }

    @Test
    @SuppressWarnings("unchecked")
    void interruptEndsTheThreadWhileTheCursorCannotBeOpened() throws Exception {
        context.start();
        MongoDbEndpoint endpoint = context.getEndpoint(
                "mongodb:myDb?database=test&collection=events&tailTrackIncreasingField=increasing"
                                                       + "&cursorRegenerationDelay=100",
                MongoDbEndpoint.class);
        MongoDbTailableCursorConsumer consumer
                = (MongoDbTailableCursorConsumer) endpoint.createConsumer(exchange -> {
                });
        MongoDbTailingThread tailingThread = new MongoDbTailingThread(endpoint, consumer, consumer.initTailTracking());

        // the first cursor breaks, then the server cannot be reached: every attempt to open a new cursor fails
        MongoCursor<Document> broken = mock(MongoCursor.class);
        when(broken.hasNext()).thenThrow(new MongoSocketReadException("Prematurely reached end of stream", SERVER));
        CountDownLatch attempts = new CountDownLatch(2);
        FindIterable<Document> find = mock(FindIterable.class);
        when(collection.find()).thenReturn(find);
        when(find.cursorType(CursorType.TailableAwait)).thenReturn(find);
        when(find.iterator())
                .thenReturn(broken)
                .thenAnswer(i -> {
                    attempts.countDown();
                    throw new MongoTimeoutException("Timed out while waiting for a server");
                });

        tailingThread.init();
        Thread thread = new Thread(tailingThread, "tailing");
        thread.start();
        assertTrue(attempts.await(10, TimeUnit.SECONDS), "the consumer did not try to open a cursor again");
        try {
            thread.interrupt();
            thread.join(TimeUnit.SECONDS.toMillis(5));
            assertFalse(thread.isAlive(), "an interrupt ends the thread of the consumer");
            assertTrue(tailingThread.keepRunning,
                    "the thread ended because of the interrupt, not because it was stopped");
        } finally {
            // without the guard the thread keeps looping: end it so it does not outlive the test
            tailingThread.keepRunning = false;
            thread.join(TimeUnit.SECONDS.toMillis(5));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void changeStreamsConsumerOpensANewCursorAfterAFailedRegeneration() throws Exception {
        MongoChangeStreamCursor<ChangeStreamDocument<Document>> broken = mock(MongoChangeStreamCursor.class);
        when(broken.hasNext()).thenThrow(new MongoSocketReadException("Prematurely reached end of stream", SERVER));

        ChangeStreamDocument<Document> event = mock(ChangeStreamDocument.class);
        when(event.getFullDocument()).thenReturn(new Document("_id", 1).append("text", "after the outage"));
        when(event.getDocumentKey()).thenReturn(new BsonDocument("_id", new BsonInt32(1)));
        when(event.getOperationType()).thenReturn(OperationType.INSERT);
        when(event.getResumeToken()).thenReturn(new BsonDocument("_data", new BsonInt32(1)));
        MongoChangeStreamCursor<ChangeStreamDocument<Document>> recovered = mock(MongoChangeStreamCursor.class);
        when(recovered.hasNext()).thenReturn(true, false);
        when(recovered.next()).thenReturn(event);

        MongoChangeStreamCursor<ChangeStreamDocument<Document>> idle = mock(MongoChangeStreamCursor.class);
        when(idle.hasNext()).thenReturn(false);

        ChangeStreamIterable<Document> watch = mock(ChangeStreamIterable.class);
        when(collection.watch()).thenReturn(watch);
        when(watch.fullDocument(any(FullDocument.class))).thenReturn(watch);
        when(watch.resumeAfter(any(BsonDocument.class))).thenReturn(watch);
        when(watch.iterator())
                .thenReturn(broken)
                .thenThrow(new MongoTimeoutException("Timed out while waiting for a server"))
                .thenReturn(recovered)
                .thenReturn(idle);

        MockEndpoint mock = startRoute("mongodb:myDb?database=test&collection=events&consumerType=changeStreams"
                                       + "&cursorRegenerationDelay=100");

        mock.expectedBodiesReceived(new Document("_id", 1).append("text", "after the outage"));
        mock.assertIsSatisfied();
        verify(watch, atLeast(3)).iterator();
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("mongo"));
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
}
