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
package org.apache.camel.component.mongodb.integration;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.CreateCollectionOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.mongodb.MongoDbTailTrackingConfig;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.awaitility.Awaitility;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MongoDbTailableCursorConsumerIT extends AbstractMongoDbITSupport implements ConfigurableRoute {

    private MongoCollection<Document> cappedTestCollection;
    private String cappedTestCollectionName;
    private CreateCollectionOptions createCollectionOptions;
    private ExecutorService executorService = Executors.newCachedThreadPool();

    @BeforeEach
    void checkDocuments() {
        Assumptions.assumeTrue(0 == cappedTestCollection.countDocuments(), "The collection should have no documents");
    }

    @Test
    public void testThousandRecordsWithoutReadPreference() throws Exception {
        testThousandRecordsWithRouteId("tailableCursorConsumer1");
    }

    @Test
    public void testThousandRecordsWithReadPreference() throws Exception {
        testThousandRecordsWithRouteId("tailableCursorConsumer1.readPreference");
    }

    @Test
    public void testNoRecords() throws Exception {
        MockEndpoint mock = contextExtension.getMockEndpoint("mock:test");
        mock.expectedMessageCount(0);

        assertEquals(0, cappedTestCollection.countDocuments());

        context.getRouteController().startRoute("tailableCursorConsumer1");
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> mock.assertIsSatisfied());
        context.getRouteController().stopRoute("tailableCursorConsumer1");

    }

    @Test
    public void testMultipleBursts() throws Exception {
        MockEndpoint mock = contextExtension.getMockEndpoint("mock:test");
        mock.expectedMessageCount(5000);

        context.getRouteController().startRoute("tailableCursorConsumer1");

        Executors.newSingleThreadExecutor().submit(this::doInsert).get();

        mock.assertIsSatisfied();
        context.getRouteController().stopRoute("tailableCursorConsumer1");
    }

    // pump 5 bursts of 1000 records each with 500ms pause between burst and
    // burst
    private void doInsert() {
        for (int i = 0; i < 5000; i++) {
            if (i % 1000 == 0) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
            }
            cappedTestCollection.insertOne(new Document("increasing", i).append("string", "value" + i));
        }
    }

    @Test
    public void testHundredThousandRecords() throws Exception {
        final MockEndpoint mock = contextExtension.getMockEndpoint("mock:test");
        mock.expectedMessageCount(1000);

        context.getRouteController().startRoute("tailableCursorConsumer1");

        // continuous pump of 100000 records, asserting incrementally to reduce
        // overhead on the mock endpoint
        Executors.newSingleThreadExecutor().submit(() -> doLongerInsert(mock)).get();

        context.getRouteController().stopRoute("tailableCursorConsumer1");

    }

    private void doLongerInsert(MockEndpoint mock) {
        for (int i = 1; i <= 100000; i++) {
            cappedTestCollection.insertOne(new Document("increasing", i).append("string", "value" + i));

            // incrementally assert, as the mock endpoint stores all
            // messages and otherwise the test would be sluggish
            if (i % 1000 == 0) {
                try {
                    MongoDbTailableCursorConsumerIT.this.assertAndResetMockEndpoint(mock);
                } catch (Exception e) {
                    return;
                }
            }
        }
    }

    @Test
    public void testPersistentTailTrack() throws Exception {
        assertEquals(0, cappedTestCollection.countDocuments());
        final MockEndpoint mock = contextExtension.getMockEndpoint("mock:test");

        // drop the tracking collection
        db.getCollection(MongoDbTailTrackingConfig.DEFAULT_COLLECTION).drop();

        cappedTestCollection = db.getCollection(cappedTestCollectionName, Document.class);
        cappedTestCollection.createIndex(new Document("increasing", 1));

        context.getRouteController().startRoute("tailableCursorConsumer2");

        mock.expectedMessageCount(300);
        // pump 300 records
        Executors.newSingleThreadExecutor().submit(() -> doQuickInsert(1, 300)).get();

        mock.assertIsSatisfied();
        mock.reset();
        context.getRouteController().stopRoute("tailableCursorConsumer2");
        while (context.getRouteController().getRouteStatus("tailableCursorConsumer2") != ServiceStatus.Stopped) {
        }
        context.getRouteController().startRoute("tailableCursorConsumer2");

        // expect 300 messages and not 600
        mock.expectedMessageCount(300);
        // pump 300 records
        executorService.submit(() -> doQuickInsert(301, 600)).get();

        mock.assertIsSatisfied();

        // check that the first message received in this second batch
        // corresponds to increasing=301
        Object firstBody = mock.getExchanges().get(0).getIn().getBody();
        assertTrue(firstBody instanceof Document);
        assertEquals(301, Document.class.cast(firstBody).get("increasing"));

        // check that the lastVal is persisted at the right time: check before
        // and after stopping the route
        assertEquals(300, db.getCollection(MongoDbTailTrackingConfig.DEFAULT_COLLECTION).find(eq("persistentId", "darwin"))
                .first().get("lastTrackingValue"));
        // stop the route and verify the last value has been updated
        context.getRouteController().stopRoute("tailableCursorConsumer2");
        while (context.getRouteController().getRouteStatus("tailableCursorConsumer2") != ServiceStatus.Stopped) {
        }
        assertEquals(600, db.getCollection(MongoDbTailTrackingConfig.DEFAULT_COLLECTION).find(eq("persistentId", "darwin"))
                .first().get("lastTrackingValue"));

    }

    private void doQuickInsert(int initial, int limit) {
        for (int i = initial; i <= limit; i++) {
            cappedTestCollection.insertOne(new Document("increasing", i).append("string", "value" + i));
        }
    }

    @Test
    public void testPersistentTailTrackIncreasingDateField() throws Exception {
        assertEquals(0, cappedTestCollection.countDocuments());
        final MockEndpoint mock = contextExtension.getMockEndpoint("mock:test");
        final Calendar startTimestamp = Calendar.getInstance();

        // get default tracking collection
        MongoCollection<Document> trackingCol = db.getCollection(MongoDbTailTrackingConfig.DEFAULT_COLLECTION, Document.class);
        trackingCol.drop();
        trackingCol = db.getCollection(MongoDbTailTrackingConfig.DEFAULT_COLLECTION, Document.class);

        context.getRouteController().startRoute("tailableCursorConsumer2");

        mock.expectedMessageCount(300);
        // pump 300 records
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 1; i <= 300; i++) {
                    Calendar c = (Calendar) (startTimestamp.clone());
                    c.add(Calendar.MINUTE, i);
                    cappedTestCollection.insertOne(new Document("increasing", c.getTime()).append("string", "value" + i));
                }
            }
        });

        // start the data pumping
        t.start();
        // before we continue wait for the data pump to end
        t.join();
        mock.assertIsSatisfied();
        mock.reset();
        // ensure that the persisted lastVal is startTimestamp + 300min
        Calendar cal300 = (Calendar) startTimestamp.clone();
        cal300.add(Calendar.MINUTE, 300);
        context.getRouteController().stopRoute("tailableCursorConsumer2");
        assertEquals(cal300.getTime(),
                trackingCol.find(eq("persistentId", "darwin")).first().get(MongoDbTailTrackingConfig.DEFAULT_FIELD));
        context.getRouteController().startRoute("tailableCursorConsumer2");

        // expect 300 messages and not 600
        mock.expectedMessageCount(300);
        // pump 300 records
        t = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 301; i <= 600; i++) {
                    Calendar c = (Calendar) (startTimestamp.clone());
                    c.add(Calendar.MINUTE, i);
                    cappedTestCollection.insertOne(new Document("increasing", c.getTime()).append("string", "value" + i));
                }
            }
        });
        // start the data pumping
        t.start();
        // before we continue wait for the data pump to end
        t.join();
        mock.assertIsSatisfied();
        Object firstBody = mock.getExchanges().get(0).getIn().getBody();
        assertTrue(firstBody instanceof Document);
        Calendar cal301 = Calendar.class.cast(startTimestamp.clone());
        cal301.add(Calendar.MINUTE, 301);
        assertEquals(cal301.getTime(), Document.class.cast(firstBody).get("increasing"));
        // check that the persisted lastVal after stopping the route is
        // startTimestamp + 600min
        context.getRouteController().stopRoute("tailableCursorConsumer2");
        Calendar cal600 = (Calendar) startTimestamp.clone();
        cal600.add(Calendar.MINUTE, 600);
        assertEquals(cal600.getTime(),
                trackingCol.find(eq("persistentId", "darwin")).first().get(MongoDbTailTrackingConfig.DEFAULT_FIELD));
    }

    @Test
    public void testCustomTailTrackLocation() throws Exception {
        assertEquals(0, cappedTestCollection.countDocuments());
        final MockEndpoint mock = contextExtension.getMockEndpoint("mock:test");

        // get the custom tracking collection and drop it
        // (tailTrackDb=einstein&tailTrackCollection=curie&tailTrackField=newton)
        MongoCollection<Document> trackingCol = mongo.getDatabase("einstein").getCollection("curie", Document.class);
        trackingCol.drop();
        trackingCol = mongo.getDatabase("einstein").getCollection("curie", Document.class);

        context.getRouteController().startRoute("tailableCursorConsumer3");

        mock.expectedMessageCount(300);
        // pump 300 records
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                doQuickInsert(1, 300);
            }
        });

        // start the data pumping
        t.start();
        // before we continue wait for the data pump to end
        t.join();
        mock.assertIsSatisfied();
        mock.reset();

        // stop the route to ensure that our lastVal is persisted, and check it
        context.getRouteController().stopRoute("tailableCursorConsumer3");
        // ensure that the persisted lastVal is 300, newton is the name of the
        // trackingField we are using
        assertEquals(300, trackingCol.find(eq("persistentId", "darwin")).first().get("newton"));
        context.getRouteController().startRoute("tailableCursorConsumer3");

        // expect 300 messages and not 600
        mock.expectedMessageCount(300);
        // pump 300 records
        t = new Thread(new Runnable() {
            @Override
            public void run() {
                doQuickInsert(301, 600);
            }
        });
        // start the data pumping
        t.start();
        // before we continue wait for the data pump to end
        t.join();
        mock.assertIsSatisfied();
        // check that the first received body contains increasing=301 and not
        // increasing=1, i.e. it's not starting from the top
        Object firstBody = mock.getExchanges().get(0).getIn().getBody();
        assertTrue(firstBody instanceof Document);
        assertEquals(301, (Document.class.cast(firstBody)).get("increasing"));
        // check that the persisted lastVal after stopping the route is 600,
        // newton is the name of the trackingField we are using
        context.getRouteController().stopRoute("tailableCursorConsumer3");
        assertEquals(600, trackingCol.find(eq("persistentId", "darwin")).first().get("newton"));

    }

    public void assertAndResetMockEndpoint(MockEndpoint mock) throws Exception {
        mock.assertIsSatisfied();
        mock.reset();
    }

    private void testThousandRecordsWithRouteId(String routeId) throws Exception {
        assertEquals(0, cappedTestCollection.countDocuments());
        MockEndpoint mock = contextExtension.getMockEndpoint("mock:test");
        mock.expectedMessageCount(1000);

        for (int i = 0; i < 1000; i++) {
            cappedTestCollection.insertOne(new Document("increasing", i).append("string", "value" + i));
        }
        assertEquals(1000, cappedTestCollection.countDocuments());
        context.getRouteController().startRoute(routeId);
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> mock.assertIsSatisfied());
        context.getRouteController().stopRoute(routeId);
    }

    /*
     * NOTE: in the case of this test, we *DO* want to recreate everything after the test has executed, so that when
     * we manually start the routes, the collection is present.
     *
     * TODO: in the future, this test should be broken in so that it does not depend on complex behaviors after the test
     *  execution
     */
    @AfterEach
    protected void doPostSetup() {
        super.doPostSetup();

        // drop the capped collection and let each test create what it needs
        cappedTestCollectionName = "camelTestCapped";

        cappedTestCollection = db.getCollection(cappedTestCollectionName, Document.class);
        cappedTestCollection.drop();

        createCollectionOptions = new CreateCollectionOptions().capped(true).sizeInBytes(1000000000).maxDocuments(1000);
        db.createCollection(cappedTestCollectionName, createCollectionOptions);
        cappedTestCollection = db.getCollection(cappedTestCollectionName, Document.class);
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(new RouteBuilder() {

            @Override
            public void configure() {

                from("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.cappedTestCollection}}&tailTrackIncreasingField=increasing")
                        .id("tailableCursorConsumer1")
                        .autoStartup(false).to("mock:test");

                from("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.cappedTestCollection}}&tailTrackIncreasingField=increasing&persistentTailTracking=true&persistentId=darwin")
                        .id("tailableCursorConsumer2").autoStartup(false).to("mock:test");

                from("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.cappedTestCollection}}&tailTrackIncreasingField=increasing&"
                     + "persistentTailTracking=true&persistentId=darwin&tailTrackDb=einstein&tailTrackCollection=curie&tailTrackField=newton")
                        .id("tailableCursorConsumer3")
                        .autoStartup(false).to("mock:test");

                from("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.cappedTestCollection}}&tailTrackIncreasingField=increasing")// &readPreference=primary")
                        .id("tailableCursorConsumer1.readPreference").autoStartup(false).to("mock:test");

            }
        });
    }
}
