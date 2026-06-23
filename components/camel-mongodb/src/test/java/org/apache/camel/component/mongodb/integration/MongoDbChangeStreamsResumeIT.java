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

import com.mongodb.client.MongoCollection;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.processor.state.MemoryStateRepository;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MongoDbChangeStreamsResumeIT extends AbstractMongoDbITSupport implements ConfigurableRoute {

    private final MemoryStateRepository resumeTokenRepository = new MemoryStateRepository();
    private static final String ROUTE_ID = "resumeChangeStreamConsumer";

    @Test
    public void shouldResumeAfterRestartFromStoredResumeToken() throws Exception {
        MongoCollection<Document> mongoCollection
                = db.getCollection(AbstractMongoDbITSupport.testCollectionName, Document.class);

        MockEndpoint mock = contextExtension.getMockEndpoint("mock:test");
        mock.expectedMessageCount(2);

        context.getRouteController().startRoute(ROUTE_ID);

        mongoCollection.insertOne(new Document("increasing", 0).append("string", "value0"));
        mongoCollection.insertOne(new Document("increasing", 1).append("string", "value1"));

        mock.assertIsSatisfied();
        context.getRouteController().stopRoute(ROUTE_ID);

        String key = ROUTE_ID + '/' + AbstractMongoDbITSupport.testCollectionName;
        String storedToken = resumeTokenRepository.getState(key);
        assertNotNull(storedToken);

        // Insert while the route is stopped. The resume token should make these consumable on restart.
        mongoCollection.insertOne(new Document("increasing", 2).append("string", "value2"));
        mongoCollection.insertOne(new Document("increasing", 3).append("string", "value3"));

        mock.reset();
        mock.expectedMessageCount(2);

        context.getRouteController().startRoute(ROUTE_ID);
        mock.assertIsSatisfied();
        context.getRouteController().stopRoute(ROUTE_ID);

        assertEquals("value2", mock.getExchanges().get(0).getIn().getBody(Document.class).getString("string"));
        assertEquals("value3", mock.getExchanges().get(1).getIn().getBody(Document.class).getString("string"));
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.getRegistry().bind("changeStreamTokenRepo", resumeTokenRepository);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("mongodb:myDb?consumerType=changeStreams&database={{mongodb.testDb}}"
                     + "&collection={{mongodb.testCollection}}&changeStreamTokenRepository=#changeStreamTokenRepo")
                        .id(ROUTE_ID)
                        .autoStartup(false)
                        .to("mock:test");
            }
        });
    }
}
