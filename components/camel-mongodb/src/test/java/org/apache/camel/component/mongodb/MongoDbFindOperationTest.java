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
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

import org.bson.types.ObjectId;
import org.junit.Test;

public class MongoDbFindOperationTest extends AbstractMongoDbTest {
 
    
    @Test
    public void testFindAllNoCriteriaOperation() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        pumpDataIntoTestCollection();
        
        Object result = template.requestBody("direct:findAll", (Object) null);
        assertTrue("Result is not of type List", result instanceof List);

        @SuppressWarnings("unchecked")
        List<DBObject> resultList = (List<DBObject>) result;

        assertListSize("Result does not contain all entries in collection", resultList, 1000);
        
        // Ensure that all returned documents contain all fields
        for (DBObject dbObject : resultList) {
            assertNotNull("DBObject in returned list should contain all fields", dbObject.get("_id"));
            assertNotNull("DBObject in returned list should contain all fields", dbObject.get("scientist"));
            assertNotNull("DBObject in returned list should contain all fields", dbObject.get("fixedField"));
        }
        
        Exchange resultExchange = getMockEndpoint("mock:resultFindAll").getReceivedExchanges().get(0);
        //TODO: decide what to do with total count
        //assertEquals("Result total size header should equal 1000", 1000, resultExchange.getIn().getHeader(MongoDbConstants.RESULT_TOTAL_SIZE));
        assertEquals("Result page size header should equal 1000", 1000, resultExchange.getIn().getHeader(MongoDbConstants.RESULT_PAGE_SIZE));

    }

    @Test
    public void testFindAllWithQueryAndNoFIlter() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        pumpDataIntoTestCollection();

        DBObject query = BasicDBObjectBuilder.start("scientist", "Einstein").get();
        Object result = template.requestBody("direct:findAll", query);
        assertTrue("Result is not of type List", result instanceof List);

        @SuppressWarnings("unchecked")
        List<DBObject> resultList = (List<DBObject>) result;

        assertListSize("Result does not contain correct number of Einstein entries", resultList, 100);

        // Ensure that all returned documents contain all fields, and that they only contain 'Einstein'
        for (DBObject dbObject : resultList) {
            assertNotNull("DBObject in returned list should not contain field _id", dbObject.get("_id"));
            assertNotNull("DBObject in returned list does not contain field 'scientist'", dbObject.get("scientist"));
            assertNotNull("DBObject in returned list should not contain field fixedField", dbObject.get("fixedField"));
            assertEquals("DBOject.scientist should only be Einstein", "Einstein", dbObject.get("scientist"));
        }

        Exchange resultExchange = getMockEndpoint("mock:resultFindAll").getReceivedExchanges().get(0);
        assertEquals("Result page size header should equal 100", 100, resultExchange.getIn().getHeader(MongoDbConstants.RESULT_PAGE_SIZE));
    }

    @Test
    public void testFindAllWithQueryAndFilter() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        pumpDataIntoTestCollection();

        DBObject fieldFilter = BasicDBObjectBuilder.start().add("_id", 0).add("fixedField", 0).get();
        DBObject query = BasicDBObjectBuilder.start("scientist", "Einstein").get();
        Object result = template.requestBodyAndHeader("direct:findAll", query, MongoDbConstants.FIELDS_FILTER, fieldFilter);
        assertTrue("Result is not of type List", result instanceof List);

        @SuppressWarnings("unchecked")
        List<DBObject> resultList = (List<DBObject>) result;

        assertListSize("Result does not contain correct number of Einstein entries", resultList, 100);

        // Ensure that all returned documents contain all fields, and that they only contain 'Einstein'
        for (DBObject dbObject : resultList) {
            assertNull("DBObject in returned list should not contain field _id", dbObject.get("_id"));
            assertNotNull("DBObject in returned list does not contain field 'scientist'", dbObject.get("scientist"));
            assertNull("DBObject in returned list should not contain field fixedField", dbObject.get("fixedField"));
            assertEquals("DBOject.scientist should only be Einstein", "Einstein", dbObject.get("scientist"));
        }

        Exchange resultExchange = getMockEndpoint("mock:resultFindAll").getReceivedExchanges().get(0);
        assertEquals("Result page size header should equal 100", 100, resultExchange.getIn().getHeader(MongoDbConstants.RESULT_PAGE_SIZE));
    }

    @Test
    public void testFindAllNoCriteriaWithFilterOperation() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        pumpDataIntoTestCollection();
        
        DBObject fieldFilter = BasicDBObjectBuilder.start().add("_id", 0).add("fixedField", 0).get();
        Object result = template.requestBodyAndHeader("direct:findAll", (Object) null, MongoDbConstants.FIELDS_FILTER, fieldFilter);
        assertTrue("Result is not of type List", result instanceof List);

        @SuppressWarnings("unchecked")
        List<DBObject> resultList = (List<DBObject>) result;

        assertListSize("Result does not contain all entries in collection", resultList, 1000);
        
        // Ensure that all returned documents contain all fields
        for (DBObject dbObject : resultList) {
            assertNull("DBObject in returned list should not contain field _id", dbObject.get("_id"));
            assertNotNull("DBObject in returned list does not contain field 'scientist'", dbObject.get("scientist"));
            assertNull("DBObject in returned list should not contain field fixedField", dbObject.get("fixedField"));
        }
        
        Exchange resultExchange = getMockEndpoint("mock:resultFindAll").getReceivedExchanges().get(0);
        //assertEquals("Result total size header should equal 1000", 1000, resultExchange.getIn().getHeader(MongoDbConstants.RESULT_TOTAL_SIZE));
        assertEquals("Result page size header should equal 1000", 1000, resultExchange.getIn().getHeader(MongoDbConstants.RESULT_PAGE_SIZE));
        
    }
    
    @Test
    public void testFindAllIterationOperation() throws Exception {
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
            Object result = template.requestBodyAndHeaders("direct:findAll", (Object) null, headers);
            assertTrue("Result is not of type List", result instanceof List);

            @SuppressWarnings("unchecked")
            List<DBObject> resultList = (List<DBObject>) result;

            assertListSize("Result does not contain 100 elements", resultList, 100);
            assertEquals("Id of first record is not as expected", numToSkip + 1, Integer.parseInt((String) resultList.get(0).get("_id")));
            
            // Ensure that all returned documents contain all fields
            for (DBObject dbObject : resultList) {
                assertNotNull("DBObject in returned list should contain all fields", dbObject.get("_id"));
                assertNotNull("DBObject in returned list should contain all fields", dbObject.get("scientist"));
                assertNotNull("DBObject in returned list should contain all fields", dbObject.get("fixedField"));
            }
            
            numToSkip = numToSkip + limit;
        }
        
        for (Exchange resultExchange : getMockEndpoint("mock:resultFindAll").getReceivedExchanges()) {
            //TODO: decide what to do with the total number of elements
            //assertEquals("Result total size header should equal 1000", 1000, resultExchange.getIn().getHeader(MongoDbConstants.RESULT_TOTAL_SIZE));
            assertEquals("Result page size header should equal 100", 100, resultExchange.getIn().getHeader(MongoDbConstants.RESULT_PAGE_SIZE));
        }
    }
    
    @Test
    public void testFindDistinctNoQuery() {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        pumpDataIntoTestCollection();

        Object result = template.requestBodyAndHeader("direct:findDistinct", null, MongoDbConstants.DISTINCT_QUERY_FIELD, "scientist");
        assertTrue("Result is not of type List", result instanceof List);

        @SuppressWarnings("unchecked")
        List<String> resultList = (List<String>)result;
        assertEquals(10, resultList.size());
    }
    
    @Test
    public void testFindDistinctWithQuery() {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        pumpDataIntoTestCollection();

        DBObject query = BasicDBObjectBuilder.start("scientist", "Einstein").get();
        
        Object result = template.requestBodyAndHeader("direct:findDistinct", query, MongoDbConstants.DISTINCT_QUERY_FIELD, "scientist");
        assertTrue("Result is not of type List", result instanceof List);

        @SuppressWarnings("unchecked")
        List<String> resultList = (List<String>)result;
        assertEquals(1, resultList.size());
        
        assertEquals("Einstein", resultList.get(0));
    }
    
    @Test
    public void testFindOneByQuery() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        pumpDataIntoTestCollection();
        
        DBObject query = BasicDBObjectBuilder.start("scientist", "Einstein").get();
        DBObject result = template.requestBody("direct:findOneByQuery", query, DBObject.class);
        assertTrue("Result is not of type DBObject", result instanceof DBObject);

        assertNotNull("DBObject in returned list should contain all fields", result.get("_id"));
        assertNotNull("DBObject in returned list should contain all fields", result.get("scientist"));
        assertNotNull("DBObject in returned list should contain all fields", result.get("fixedField"));
        
    }
    
    @Test
    public void testFindOneById() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        pumpDataIntoTestCollection();
        
        DBObject result = template.requestBody("direct:findById", "240", DBObject.class);
        assertTrue("Result is not of type DBObject", result instanceof DBObject);

        assertEquals("The ID of the retrieved DBObject should equal 240", "240", result.get("_id"));
        assertEquals("The scientist name of the retrieved DBObject should equal Einstein", "Einstein", result.get("scientist"));
        
        assertNotNull("DBObject in returned list should contain all fields", result.get("_id"));
        assertNotNull("DBObject in returned list should contain all fields", result.get("scientist"));
        assertNotNull("DBObject in returned list should contain all fields", result.get("fixedField"));
        
    }
    
    @Test
    public void testFindOneByIdWithObjectId() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        BasicDBObject insertObject = new BasicDBObject("scientist", "Einstein");
        testCollection.insertOne(insertObject);
        assertTrue("The ID of the inserted document should be ObjectId", insertObject.get("_id") instanceof ObjectId);
        ObjectId id = (ObjectId) insertObject.get("_id");
        
        DBObject result = template.requestBody("direct:findById", id, DBObject.class);
        assertTrue("Result is not of type DBObject", result instanceof DBObject);

        assertTrue("The ID of the retrieved DBObject should be ObjectId", result.get("_id") instanceof ObjectId);
        assertEquals("The ID of the retrieved DBObject should equal to the inserted", id, result.get("_id"));
        assertEquals("The scientist name of the retrieved DBObject should equal Einstein", "Einstein", result.get("scientist"));
        
        assertNotNull("DBObject in returned list should contain all fields", result.get("_id"));
        assertNotNull("DBObject in returned list should contain all fields", result.get("scientist"));
        
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                from("direct:findAll")
                    .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findAll&dynamicity=true")
                    .to("mock:resultFindAll");
                
                from("direct:findOneByQuery")
                    .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findOneByQuery&dynamicity=true")
                    .to("mock:resultFindOneByQuery");
                
                from("direct:findById")
                    .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findById&dynamicity=true")
                    .to("mock:resultFindById");

                from("direct:findDistinct").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findDistinct&dynamicity=true")
                    .to("mock:resultFindDistinct");
            }
        };
    }
}
