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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.mongodb.CursorType;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.ListCollectionNamesIterable;
import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.changestream.FullDocument;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Stopping a consumer closes its cursor and waits for the consumer thread to end. Closing the cursor does not end a
 * read that is in progress on the consumer thread: the driver only closes the cursor once that read returns, and a read
 * from a connection that stopped answering does not return. The stop must not wait for that thread without a bound, but
 * still wait for the step of the route in progress, without interrupting it.
 */
class MongoDbConsumerStopTest {

    private final MongoClient client = mock(MongoClient.class);
    private final MongoCollection<Document> collection = mockCollection();
    // the consumer thread is in the read of the cursor
    private final CountDownLatch reading = new CountDownLatch(1);
    // the server answers the read, only when the test ends
    private final CountDownLatch answer = new CountDownLatch(1);
    private final AtomicBoolean interruptedWhileReading = new AtomicBoolean();
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
        // how long the consumer waits for its thread to end, while it reads from MongoDB
        context.getExecutorServiceManager().setShutdownAwaitTermination(500);
        // longer than the test waits for the stop: the stop must not end because the graceful shutdown timed out
        context.getShutdownStrategy().setTimeout(60);
    }

    @AfterEach
    void tearDown() {
        // the read returns, so a consumer thread that the stop left behind ends
        answer.countDown();
        if (context != null) {
            context.stop();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void tailableConsumerStopsWhileItsThreadIsBlockedReadingTheCursor() throws Exception {
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(cursor.hasNext()).thenAnswer(i -> readThatDoesNotReturn());
        FindIterable<Document> find = mock(FindIterable.class);
        when(collection.find()).thenReturn(find);
        when(find.cursorType(CursorType.TailableAwait)).thenReturn(find);
        when(find.iterator()).thenReturn(cursor);

        assertStopDoesNotWaitForTheBlockedThread(
                "mongodb:myDb?database=test&collection=events&tailTrackIncreasingField=increasing",
                MongoDbTailingThread.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void changeStreamsConsumerStopsWhileItsThreadIsBlockedReadingTheCursor() throws Exception {
        MongoChangeStreamCursor<?> cursor = mock(MongoChangeStreamCursor.class);
        when(cursor.tryNext()).thenAnswer(i -> {
            readThatDoesNotReturn();
            return null;
        });
        ChangeStreamIterable<Document> watch = mock(ChangeStreamIterable.class);
        when(collection.watch()).thenReturn(watch);
        when(watch.fullDocument(any(FullDocument.class))).thenReturn(watch);
        when(watch.iterator()).thenReturn((MongoChangeStreamCursor) cursor);

        assertStopDoesNotWaitForTheBlockedThread(
                "mongodb:myDb?database=test&collection=events&consumerType=changeStreams",
                MongoDbChangeStreamsThread.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void stopWaitsForTheRouteWithoutInterruptingIt() throws Exception {
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(new Document("increasing", 1));
        FindIterable<Document> find = mock(FindIterable.class);
        when(collection.find()).thenReturn(find);
        when(find.cursorType(CursorType.TailableAwait)).thenReturn(find);
        when(find.iterator()).thenReturn(cursor);

        // the route takes longer than the consumer waits for a thread that reads from MongoDB
        CountDownLatch routing = new CountDownLatch(1);
        CountDownLatch routeMayEnd = new CountDownLatch(1);
        AtomicBoolean interruptedInRoute = new AtomicBoolean();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("mongodb:myDb?database=test&collection=events&tailTrackIncreasingField=increasing")
                        .routeId("mongo")
                        .process(exchange -> {
                            routing.countDown();
                            try {
                                if (!routeMayEnd.await(20, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("the test did not let the route end");
                                }
                            } catch (InterruptedException e) {
                                interruptedInRoute.set(true);
                                throw e;
                            }
                        });
            }
        });
        context.start();
        assertTrue(routing.await(10, TimeUnit.SECONDS), "the route processes the exchange");

        CountDownLatch stopped = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        Thread stopper = stopRouteInAnotherThread(stopped, failure);
        // well after the 500 millis that the stop waits for a thread that reads from MongoDB
        await().atMost(5, TimeUnit.SECONDS).during(1500, TimeUnit.MILLISECONDS).until(() -> stopped.getCount() == 1);

        routeMayEnd.countDown();
        assertTrue(stopped.await(10, TimeUnit.SECONDS), "the route stops once the processor returned");
        stopper.join(TimeUnit.SECONDS.toMillis(10));
        assertNull(failure.get());
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("mongo"));
        assertFalse(interruptedInRoute.get(), "the thread that routes the exchange is not interrupted");
    }

    /**
     * A read from a connection that does not answer: like a socket read, neither closing the cursor nor an interrupt
     * ends it.
     */
    private boolean readThatDoesNotReturn() {
        reading.countDown();
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    answer.await();
                    return false;
                } catch (InterruptedException e) {
                    interrupted = true;
                    interruptedWhileReading.set(true);
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void assertStopDoesNotWaitForTheBlockedThread(String uri, Class<?> threadClass) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId("mongo").to("mock:result");
            }
        });
        context.start();
        assertTrue(reading.await(10, TimeUnit.SECONDS), "the consumer thread reads from the cursor");

        List<LogEvent> events = new CopyOnWriteArrayList<>();
        AbstractAppender appender = new AbstractAppender("consumerStop", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                events.add(event.toImmutable());
            }
        };
        appender.start();
        Logger logger = (Logger) LogManager.getLogger(threadClass);
        logger.addAppender(appender);
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        try {
            stopRouteInAnotherThread(stopped, failure);
            assertTrue(stopped.await(10, TimeUnit.SECONDS),
                    "the route stops while the consumer thread is blocked reading the cursor");
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }

        assertNull(failure.get());
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("mongo"));
        // the stop returns once it interrupted the thread, which sets the flag when it wakes up from the interrupt
        await("the consumer thread left behind is interrupted").atMost(10, TimeUnit.SECONDS)
                .until(interruptedWhileReading::get);
        List<String> warnings = events.stream().filter(e -> e.getLevel() == Level.WARN)
                .map(e -> e.getMessage().getFormattedMessage()).toList();
        assertEquals(1, warnings.size(), "one WARN for the consumer thread left behind: " + warnings);
        assertTrue(warnings.get(0).contains("did not end within 500 millis"), warnings.get(0));
    }

    private Thread stopRouteInAnotherThread(CountDownLatch stopped, AtomicReference<Exception> failure) {
        Thread stopper = new Thread(() -> {
            try {
                context.getRouteController().stopRoute("mongo");
            } catch (Exception e) {
                failure.set(e);
            } finally {
                stopped.countDown();
            }
        }, "stopper");
        stopper.start();
        return stopper;
    }
}
