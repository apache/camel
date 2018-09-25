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

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.client.MongoIterable;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class MongoDbAggregateOperationTest extends AbstractMongoDbTest {

   
    @Test
    public void testAggregate() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        pumpDataIntoTestCollection();

        // result sorted by _id
        Object result = template
            .requestBody("direct:aggregate",
                         "[{ $match : {$or : [{\"scientist\" : \"Darwin\"},{\"scientist\" : \"Einstein\"}]}},"
                         + "{ $group: { _id: \"$scientist\", count: { $sum: 1 }} },{ $sort : { _id : 1}} ]");
        
        assertTrue("Result is not of type List", result instanceof List);

        @SuppressWarnings("unchecked")
        List<BasicDBObject> resultList = (List<BasicDBObject>) result;
        assertListSize("Result does not contain 2 elements", resultList, 2);

        assertEquals("First result DBOject._id should be Darwin", "Darwin", resultList.get(0).get("_id"));
        assertEquals("First result DBOject.count should be 100", 100, resultList.get(0).get("count"));
        assertEquals("Second result DBOject._id should be Einstein", "Einstein", resultList.get(1).get("_id"));
        assertEquals("Second result DBOject.count should be 100", 100, resultList.get(1).get("count"));
    }
    
    @Test
    public void testAggregateDBCursor() {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        pumpDataIntoTestCollection();

        Object result = template
                .requestBody("direct:aggregateDBCursor",
                        "[{ $match : {$or : [{\"scientist\" : \"Darwin\"},{\"scientist\" : \"Einstein\"}]}}]");
        
        assertTrue("Result is not of type DBCursor", result instanceof MongoIterable);

        MongoIterable<BasicDBObject> resultCursor = (MongoIterable<BasicDBObject>) result;
        // Ensure that all returned documents contain all fields
        int count = 0;
        for (DBObject dbObject : resultCursor) {
            assertNotNull("DBObject in returned list should contain all fields", dbObject.get("_id"));
            assertNotNull("DBObject in returned list should contain all fields", dbObject.get("scientist"));
            assertNotNull("DBObject in returned list should contain all fields", dbObject.get("fixedField"));
            count++;
        }
        assertEquals("Result does not contain 200 elements", 200, count);
    }

    @Test
    public void testAggregateWithOptions() {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        pumpDataIntoTestCollection();

        Map<String, Object> options = new HashMap<>();
        options.put(MongoDbConstants.BATCH_SIZE, 10);
        options.put(MongoDbConstants.ALLOW_DISK_USE, true);

        Object result = template
                .requestBodyAndHeaders("direct:aggregateDBCursor",
                        "[{ $match : {$or : [{\"scientist\" : \"Darwin\"},{\"scientist\" : \"Einstein\"}]}}]", options);
        
        
        assertTrue("Result is not of type DBCursor", result instanceof MongoIterable);

        MongoIterable<BasicDBObject> resultCursor = (MongoIterable<BasicDBObject>) result;

        // Ensure that all returned documents contain all fields
        int count = 0;
        for (DBObject dbObject : resultCursor) {
            assertNotNull("DBObject in returned list should contain all fields", dbObject.get("_id"));
            assertNotNull("DBObject in returned list should contain all fields", dbObject.get("scientist"));
            assertNotNull("DBObject in returned list should contain all fields", dbObject.get("fixedField"));
            count++;
        }
        assertEquals("Result does not contain 200 elements", 200, count);
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:aggregate")
                    .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=aggregate");
                from("direct:aggregateDBCursor")
                    .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=aggregate&dynamicity=true&outputType=DBCursor")
                      .to("mock:resultAggregateDBCursor");
                }
            };
    }
}

