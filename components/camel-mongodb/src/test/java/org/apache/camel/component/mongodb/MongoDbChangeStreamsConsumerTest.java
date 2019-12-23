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

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.CreateCollectionOptions;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MongoDbChangeStreamsConsumerTest extends AbstractMongoDbTest {

    private MongoCollection<Document> mongoCollection;
    private String collectionName;

    @Override
    public void doPostSetup() {
        super.doPostSetup();

        collectionName = "camelTest";
        mongoCollection = db.getCollection(collectionName, Document.class);
        mongoCollection.drop();

        CreateCollectionOptions collectionOptions = new CreateCollectionOptions();
        db.createCollection(collectionName, collectionOptions);
        mongoCollection = db.getCollection(collectionName, Document.class);
    }

    @Test
    public void basicTest() throws Exception {
        assertEquals(0, mongoCollection.countDocuments());
        MockEndpoint mock = getMockEndpoint("mock:test");
        mock.expectedMessageCount(10);

        String consumerRouteId = "simpleConsumer";
        addTestRoutes();
        context.getRouteController().startRoute(consumerRouteId);

        Thread t = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                mongoCollection.insertOne(new Document("increasing", i).append("string", "value" + i));
            }
        });

        t.start();
        t.join();

        mock.assertIsSatisfied();
        context.getRouteController().stopRoute(consumerRouteId);
    }

    @Test
    public void filterTest() throws Exception {
        assertEquals(0, mongoCollection.countDocuments());
        MockEndpoint mock = getMockEndpoint("mock:test");
        mock.expectedMessageCount(1);

        String consumerRouteId = "filterConsumer";
        addTestRoutes();
        context.getRouteController().startRoute(consumerRouteId);

        Thread t = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                mongoCollection.insertOne(new Document("increasing", i).append("string", "value" + i));
            }
        });

        t.start();
        t.join();

        mock.assertIsSatisfied();

        Document actualDocument = mock.getExchanges().get(0).getIn().getBody(Document.class);
        assertEquals("value2", actualDocument.get("string"));

        context.getRouteController().stopRoute(consumerRouteId);
    }


    protected void addTestRoutes() throws Exception {
        context.addRoutes(new RouteBuilder() {

            @Override
            public void configure() {
                from("mongodb:myDb?consumerType=changeStreams&database={{mongodb.testDb}}&collection={{mongodb.testCollection}}")
                        .id("simpleConsumer")
                        .autoStartup(false)
                        .to("mock:test");

                from("mongodb:myDb?consumerType=changeStreams&database={{mongodb.testDb}}&collection={{mongodb.testCollection}}")
                        .routeProperty("streamFilter", "{'$match':{'$or':[{'fullDocument.string': 'value2'}]}}")
                        .id("filterConsumer")
                        .autoStartup(false)
                        .to("mock:test");
            }
        });
    }
}
