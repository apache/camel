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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.CreateCollectionOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MongoDbChangeStreamsConsumerIT extends AbstractMongoDbITSupport implements ConfigurableRoute {

    private MongoCollection<Document> mongoCollection;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

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

        mongoCollection = db.getCollection(AbstractMongoDbITSupport.testCollectionName, Document.class);
        mongoCollection.drop();

        CreateCollectionOptions collectionOptions = new CreateCollectionOptions();
        db.createCollection(AbstractMongoDbITSupport.testCollectionName, collectionOptions);
        mongoCollection = db.getCollection(AbstractMongoDbITSupport.testCollectionName, Document.class);
    }

    @Order(1)
    @Test
    public void basicTest() throws Exception {
        Assumptions.assumeTrue(0 == mongoCollection.countDocuments(), "The collection should have no documents");
        MockEndpoint mock = contextExtension.getMockEndpoint("mock:test");
        mock.expectedMessageCount(10);

        String consumerRouteId = "simpleConsumer";
        context.getRouteController().startRoute(consumerRouteId);

        Executors.newSingleThreadExecutor().submit(this::singleInsert).get();

        mock.assertIsSatisfied();
        context.getRouteController().stopRoute(consumerRouteId);
    }

    private void singleInsert() {
        for (int i = 0; i < 10; i++) {
            mongoCollection.insertOne(new Document("increasing", i).append("string", "value" + i));
        }
    }

    @Order(2)
    @Test
    public void filterTest() throws Exception {
        Assumptions.assumeTrue(0 == mongoCollection.countDocuments(), "The collection should have no documents");
        MockEndpoint mock = contextExtension.getMockEndpoint("mock:test");
        mock.expectedMessageCount(1);

        String consumerRouteId = "filterConsumer";
        context.getRouteController().startRoute(consumerRouteId);

        executorService.submit(this::singleInsert).get();

        mock.assertIsSatisfied();

        Document actualDocument = mock.getExchanges().get(0).getIn().getBody(Document.class);
        assertEquals("value2", actualDocument.get("string"));
        context.getRouteController().stopRoute(consumerRouteId);
    }

    @Order(3)
    @Test
    public void operationTypeAndIdHeaderTest() throws Exception {
        Assumptions.assumeTrue(0 == mongoCollection.countDocuments(), "The collection should have no documents");
        MockEndpoint mock = contextExtension.getMockEndpoint("mock:test");
        mock.expectedMessageCount(2);

        doPostSetup();

        String consumerRouteId = "simpleConsumer";
        context.getRouteController().startRoute(consumerRouteId);

        ObjectId objectId = new ObjectId();
        Executors.newSingleThreadExecutor().submit(() -> insertAndDelete(objectId)).get();

        mock.assertIsSatisfied();

        Exchange insertExchange = mock.getExchanges().get(0);
        assertEquals("insert", insertExchange.getIn().getHeader("CamelMongoDbStreamOperationType"));
        assertEquals(objectId, insertExchange.getIn().getHeader("_id"));

        Exchange deleteExchange = mock.getExchanges().get(1);
        Document deleteBodyDocument = deleteExchange.getIn().getBody(Document.class);

        assertEquals("delete", deleteExchange.getIn().getHeader("CamelMongoDbStreamOperationType"));
        assertEquals(objectId, deleteExchange.getIn().getHeader("_id"));
        assertEquals(1, deleteBodyDocument.size());
        assertTrue(deleteBodyDocument.containsKey("_id"));
        assertEquals(objectId.toHexString(), deleteBodyDocument.getObjectId("_id").toHexString());
        context.getRouteController().stopRoute(consumerRouteId);
    }

    private void insertAndDelete(ObjectId objectId) {
        mongoCollection.insertOne(new Document("_id", objectId).append("string", "value"));
        mongoCollection.deleteOne(new Document("_id", objectId));
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(new RouteBuilder() {

            @Override
            public void configure() {
                from("mongodb:myDb?consumerType=changeStreams&database={{mongodb.testDb}}&collection={{mongodb.testCollection}}")
                        .id("simpleConsumer")
                        .autoStartup(false)
                        .to("mock:test");

                from("mongodb:myDb?consumerType=changeStreams&database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&streamFilter={{myStreamFilter}}")
                        .id("filterConsumer")
                        .autoStartup(false)
                        .to("mock:test");
            }
        });
    }
}
