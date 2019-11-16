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

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.mongodb.MongoDbConstants.MONGO_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MongoDbStopEndpointTest extends AbstractMongoDbTest {

    private static final String MY_ID = "myId";

    @EndpointInject("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert")
    MongoDbEndpoint endpoint;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:insertJsonString").routeId("insert").to(endpoint);
                from("direct:findById").routeId("find").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findById&dynamicity=true");
            }
        };
    }

    @Test
    public void testStopEndpoint() throws Exception {
        assertEquals(0, testCollection.countDocuments());

        template.requestBody("direct:insertJsonString", "{\"scientist\": \"Newton\", \"_id\": \"" + MY_ID + "\"}");

        endpoint.stop();

        Document result = template.requestBody("direct:findById", MY_ID, Document.class);

        assertEquals(MY_ID, result.get(MONGO_ID));
        assertEquals("Newton", result.get("scientist"));
    }
}
