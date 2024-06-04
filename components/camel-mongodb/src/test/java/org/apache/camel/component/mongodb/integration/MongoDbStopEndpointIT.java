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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.bson.Document;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.mongodb.MongoDbConstants.MONGO_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MongoDbStopEndpointIT extends AbstractMongoDbITSupport implements ConfigurableRoute {

    private static final String MY_ID = "myId";

    String intermediate = "mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert";

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:insertJsonString").routeId("insert").to(intermediate);
                from("direct:findById").routeId("find").to(
                        "mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findById&dynamicity=true");
            }
        };
    }

    @BeforeEach
    void checkDocuments() {
        Assumptions.assumeTrue(0 == testCollection.countDocuments(), "The collection should have no documents");
    }

    @Test
    public void testStopEndpoint() {
        template.requestBody("direct:insertJsonString", "{\"scientist\": \"Newton\", \"_id\": \"" + MY_ID + "\"}");

        context.getEndpoint("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert")
                .stop();

        Document result = template.requestBody("direct:findById", MY_ID, Document.class);

        assertEquals(MY_ID, result.get(MONGO_ID));
        assertEquals("Newton", result.get("scientist"));
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }
}
