/**
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class MongoDbOutputTypeTest extends AbstractMongoDbTest {

    @Test
    public void testFindAllDBCursor() {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        pumpDataIntoTestCollection();

        // Repeat ten times, obtain 10 batches of 100 results each time
        int numToSkip = 0;
        final int limit = 100;
        for (int i = 0; i < 10; i++) {
            Map<String, Object> headers = new HashMap<String, Object>();
            headers.put(MongoDbConstants.NUM_TO_SKIP, numToSkip);
            headers.put(MongoDbConstants.LIMIT, 100);
            Object result = template.requestBodyAndHeaders("direct:findAllDBCursor", (Object) null, headers);
            assertTrue("Result is not of type DBCursor", result instanceof DBCursor);

            DBCursor resultCursor = (DBCursor) result;
            // Ensure that all returned documents contain all fields
            while (resultCursor.hasNext()) {
                DBObject dbObject = resultCursor.next();
                assertNotNull("DBObject in returned list should contain all fields", dbObject.get("_id"));
                assertNotNull("DBObject in returned list should contain all fields", dbObject.get("scientist"));
                assertNotNull("DBObject in returned list should contain all fields", dbObject.get("fixedField"));
            }

            numToSkip = numToSkip + limit;
        }
    }

    @Test
    public void testFindAllDBObjectList() {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        pumpDataIntoTestCollection();
        Object result = template.requestBody("direct:findAllDBObjectList", (Object) null);
        assertTrue("Result is not of type List", result instanceof List);
        @SuppressWarnings("unchecked")
        List<DBObject> resultList = (List<DBObject>) result;

        assertListSize("Result does not contain 1000 elements", resultList, 1000);

        // Ensure that all returned documents contain all fields
        for (DBObject dbObject : resultList) {
            assertNotNull("DBObject in returned list should contain all fields", dbObject.get("_id"));
            assertNotNull("DBObject in returned list should contain all fields", dbObject.get("scientist"));
            assertNotNull("DBObject in returned list should contain all fields", dbObject.get("fixedField"));
        }

        for (Exchange resultExchange : getMockEndpoint("mock:resultFindAll").getReceivedExchanges()) {
            assertEquals("Result total size header should equal 1000", 1000, resultExchange.getIn().getHeader(MongoDbConstants.RESULT_TOTAL_SIZE));
        }
    }

    @Test
    public void testInitFindWithWrongOutputType() {
        try {
            RouteBuilder taillableRouteBuilder = new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findById&dynamicity=true&outputType=DBCursor").to("mock:dummy");
                }
            };
            template.getCamelContext().addRoutes(taillableRouteBuilder);
            fail("Endpoint should not be initialized with a non compatible outputType");
        } catch (Exception exception) {
            assertTrue("Exception is not of type IllegalArgumentException", exception.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testInitTailWithWrongOutputType() {
        try {
            RouteBuilder taillableRouteBuilder = new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.cappedTestCollection}}&tailTrackIncreasingField=increasing&outputType=DBCursor")
                            .id("tailableCursorConsumer1")
                            .autoStartup(false)
                            .to("mock:test");
                }
            };
            template.getCamelContext().addRoutes(taillableRouteBuilder);
            fail("Endpoint should not be initialized with a non compatible outputType");
        } catch (Exception exception) {
            assertTrue("Exception is not of type IllegalArgumentException", exception.getCause() instanceof IllegalArgumentException);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                from("direct:findAllDBCursor")
                        .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findAll&dynamicity=true&outputType=DBCursor")
                        .to("mock:resultFindAllDBCursor");
                from("direct:findAllDBObjectList")
                        .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findAll&outputType=DBObjectList")
                        .to("mock:resultFindAllDBObjectList");

            }
        };
    }
}
