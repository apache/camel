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
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.OperationType;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.mongodb.MongoDbChangeStreamsMocks.ResumeOptions;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.processor.state.MemoryStateRepository;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.mongodb.MongoDbChangeStreamsMocks.commandFailed;
import static org.apache.camel.component.mongodb.MongoDbChangeStreamsMocks.cursor;
import static org.apache.camel.component.mongodb.MongoDbChangeStreamsMocks.event;
import static org.apache.camel.component.mongodb.MongoDbChangeStreamsMocks.mockCollection;
import static org.apache.camel.component.mongodb.MongoDbChangeStreamsMocks.mockDatabase;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * A change stream ends with an {@code invalidate} event when its collection is dropped or renamed, or its database
 * dropped. The consumer goes on with a new change stream after that event, so it sees the changes of the collection
 * created again under the same name.
 */
class MongoDbChangeStreamsInvalidateTest {

    private static final BsonDocument INSERT_TOKEN = token("insert");
    private static final BsonDocument DROP_TOKEN = token("drop");
    private static final BsonDocument INVALIDATE_TOKEN = token("invalidate");
    private static final BsonDocument RECREATED_INSERT_TOKEN = token("insert-after-recreate");

    private final MongoClient client = mock(MongoClient.class);
    private final MongoCollection<Document> collection = mockCollection();
    // the resume options of every change stream the consumer opened, in order
    private final List<String> opened = Collections.synchronizedList(new ArrayList<>());
    private CamelContext context;

    private static BsonDocument token(String name) {
        return new BsonDocument("_data", new BsonString(name));
    }

    @BeforeEach
    void setUp() {
        mockDatabase(client, collection, this::open);

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

    /**
     * Opens the cursor of one change stream ({@code collection.watch()} with the given resume options), answering like
     * a MongoDB server (4.2 or later) on which the collection was dropped and then created again (change streams
     * manual, "Invalidate Events" and "Resume a Change Stream"):
     * <ul>
     * <li>without a resume option: the insert, the drop and the invalidate event; the server then closes the cursor, so
     * {@code tryNext()} returns null and {@code getServerCursor()} null;</li>
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
    private MongoChangeStreamCursor<ChangeStreamDocument<Document>> open(ResumeOptions options) {
        BsonDocument resumeAfter = options.resumeAfter;
        BsonDocument startAfter = options.startAfter;
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

}
