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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mongodb.client.model.Projections;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.lang3.ObjectUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import static com.mongodb.client.model.Filters.eq;
import static org.apache.camel.component.mongodb.MongoDbConstants.MONGO_ID;
import static org.apache.camel.test.junit5.TestSupport.assertListSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MongoDbFindOperationTest extends AbstractMongoDbTest {

    @Test
    public void testFindAllNoCriteriaOperation() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.countDocuments());
        pumpDataIntoTestCollection();

        Object result = template.requestBody("direct:findAll", ObjectUtils.NULL);
        assertTrue(result instanceof List, "Result is not of type List");

        @SuppressWarnings("unchecked")
        List<Document> resultList = (List<Document>)result;

        assertListSize("Result does not contain all entries in collection", resultList, 1000);

        // Ensure that all returned documents contain all fields
        for (Document document : resultList) {
            assertNotNull(document.get(MONGO_ID), "Document in returned list should contain all fields");
            assertNotNull(document.get("scientist"), "Document in returned list should contain all fields");
            assertNotNull(document.get("fixedField"), "Document in returned list should contain all fields");
        }

        Exchange resultExchange = getMockEndpoint("mock:resultFindAll").getReceivedExchanges().get(0);
        // TODO: decide what to do with total count
        // assertEquals("Result total size header should equal 1000", 1000,
        // resultExchange.getIn().getHeader(MongoDbConstants.RESULT_TOTAL_SIZE));
        assertEquals(1000, resultExchange.getIn().getHeader(MongoDbConstants.RESULT_PAGE_SIZE), "Result page size header should equal 1000");

    }

    @Test
    public void testFindAllWithQueryAndNoFIlter() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.countDocuments());
        pumpDataIntoTestCollection();

        Object result = template.requestBody("direct:findAll", eq("scientist", "Einstein"));
        assertTrue(result instanceof List, "Result is not of type List");

        @SuppressWarnings("unchecked")
        List<Document> resultList = (List<Document>)result;

        assertListSize("Result does not contain correct number of Einstein entries", resultList, 100);

        // Ensure that all returned documents contain all fields, and that they
        // only contain 'Einstein'
        for (Document document : resultList) {
            assertNotNull(document.get(MONGO_ID), "Document in returned list should not contain field _id");
            assertNotNull(document.get("scientist"), "Document in returned list does not contain field 'scientist'");
            assertNotNull(document.get("fixedField"), "Document in returned list should not contain field fixedField");
            assertEquals("Einstein", document.get("scientist"), "Document.scientist should only be Einstein");
        }

        Exchange resultExchange = getMockEndpoint("mock:resultFindAll").getReceivedExchanges().get(0);
        assertEquals(100, resultExchange.getIn().getHeader(MongoDbConstants.RESULT_PAGE_SIZE), "Result page size header should equal 100");
    }

    @Test
    public void testFindAllWithQueryAndFilter() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.countDocuments());
        pumpDataIntoTestCollection();
        Bson fieldFilter = Projections.exclude(MONGO_ID, "fixedField");
        Bson query = eq("scientist", "Einstein");
        Object result = template.requestBodyAndHeader("direct:findAll", query, MongoDbConstants.FIELDS_PROJECTION, fieldFilter);
        assertTrue(result instanceof List, "Result is not of type List");

        @SuppressWarnings("unchecked")
        List<Document> resultList = (List<Document>)result;

        assertListSize("Result does not contain correct number of Einstein entries", resultList, 100);

        // Ensure that all returned documents contain all fields, and that they
        // only contain 'Einstein'
        for (Document document : resultList) {
            assertNull(document.get(MONGO_ID), "Document in returned list should not contain field _id");
            assertNotNull(document.get("scientist"), "Document in returned list does not contain field 'scientist'");
            assertNull(document.get("fixedField"), "Document in returned list should not contain field fixedField");
            assertEquals("Einstein", document.get("scientist"), "Document.scientist should only be Einstein");
        }

        Exchange resultExchange = getMockEndpoint("mock:resultFindAll").getReceivedExchanges().get(0);
        assertEquals(100, resultExchange.getIn().getHeader(MongoDbConstants.RESULT_PAGE_SIZE), "Result page size header should equal 100");
    }

    @Test
    public void testFindAllNoCriteriaWithFilterOperation() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.countDocuments());
        pumpDataIntoTestCollection();

        Bson fieldFilter = Projections.exclude(MONGO_ID, "fixedField");
        Object result = template.requestBodyAndHeader("direct:findAll", ObjectUtils.NULL, MongoDbConstants.FIELDS_PROJECTION, fieldFilter);
        assertTrue(result instanceof List, "Result is not of type List");

        @SuppressWarnings("unchecked")
        List<Document> resultList = (List<Document>)result;

        assertListSize("Result does not contain all entries in collection", resultList, 1000);

        // Ensure that all returned documents contain all fields
        for (Document document : resultList) {
            assertNull(document.get(MONGO_ID), "Document in returned list should not contain field _id");
            assertNotNull(document.get("scientist"), "Document in returned list does not contain field 'scientist'");
            assertNull(document.get("fixedField"), "Document in returned list should not contain field fixedField");
        }

        Exchange resultExchange = getMockEndpoint("mock:resultFindAll").getReceivedExchanges().get(0);
        // assertEquals("Result total size header should equal 1000", 1000,
        // resultExchange.getIn().getHeader(MongoDbConstants.RESULT_TOTAL_SIZE));
        assertEquals(1000, resultExchange.getIn().getHeader(MongoDbConstants.RESULT_PAGE_SIZE), "Result page size header should equal 1000");

    }

    @Test
    public void testFindAllIterationOperation() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.countDocuments());
        pumpDataIntoTestCollection();

        // Repeat ten times, obtain 10 batches of 100 results each time
        int numToSkip = 0;
        final int limit = 100;
        for (int i = 0; i < 10; i++) {
            Map<String, Object> headers = new HashMap<>();
            headers.put(MongoDbConstants.NUM_TO_SKIP, numToSkip);
            headers.put(MongoDbConstants.LIMIT, 100);
            Object result = template.requestBodyAndHeaders("direct:findAll", ObjectUtils.NULL, headers);
            assertTrue(result instanceof List, "Result is not of type List");

            @SuppressWarnings("unchecked")
            List<Document> resultList = (List<Document>)result;

            assertListSize("Result does not contain 100 elements", resultList, 100);
            assertEquals(numToSkip + 1, Integer.parseInt((String)resultList.get(0).get(MONGO_ID)), "Id of first record is not as expected");

            // Ensure that all returned documents contain all fields
            for (Document document : resultList) {
                assertNotNull(document.get(MONGO_ID), "Document in returned list should contain all fields");
                assertNotNull(document.get("scientist"), "Document in returned list should contain all fields");
                assertNotNull(document.get("fixedField"), "Document in returned list should contain all fields");
            }

            numToSkip = numToSkip + limit;
        }

        for (Exchange resultExchange : getMockEndpoint("mock:resultFindAll").getReceivedExchanges()) {
            // TODO: decide what to do with the total number of elements
            // assertEquals("Result total size header should equal 1000", 1000,
            // resultExchange.getIn().getHeader(MongoDbConstants.RESULT_TOTAL_SIZE));
            assertEquals(100, resultExchange.getIn().getHeader(MongoDbConstants.RESULT_PAGE_SIZE), "Result page size header should equal 100");
        }
    }

    @Test
    public void testFindDistinctNoQuery() {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.countDocuments());
        pumpDataIntoTestCollection();

        Object result = template.requestBodyAndHeader("direct:findDistinct", null, MongoDbConstants.DISTINCT_QUERY_FIELD, "scientist");
        assertTrue(result instanceof List, "Result is not of type List");

        @SuppressWarnings("unchecked")
        List<String> resultList = (List<String>)result;
        assertEquals(10, resultList.size());
    }

    @Test
    public void testFindDistinctWithQuery() {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.countDocuments());
        pumpDataIntoTestCollection();

        Bson query = eq("scientist", "Einstein");

        Object result = template.requestBodyAndHeader("direct:findDistinct", query, MongoDbConstants.DISTINCT_QUERY_FIELD, "scientist");
        assertTrue(result instanceof List, "Result is not of type List");

        @SuppressWarnings("unchecked")
        List<String> resultList = (List<String>)result;
        assertEquals(1, resultList.size());

        assertEquals("Einstein", resultList.get(0));
    }

    @Test
    public void testFindOneByQuery() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.countDocuments());
        pumpDataIntoTestCollection();

        Bson query = eq("scientist", "Einstein");
        Document result = template.requestBody("direct:findOneByQuery", query, Document.class);
        assertTrue(result instanceof Document, "Result is not of type Document");

        assertNotNull(result.get(MONGO_ID), "Document in returned list should contain all fields");
        assertNotNull(result.get("scientist"), "Document in returned list should contain all fields");
        assertNotNull(result.get("fixedField"), "Document in returned list should contain all fields");

    }

    @Test
    public void testFindOneById() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.countDocuments());
        pumpDataIntoTestCollection();

        Document result = template.requestBody("direct:findById", "240", Document.class);
        assertTrue(result instanceof Document, "Result is not of type Document");

        assertEquals("240", result.get(MONGO_ID), "The ID of the retrieved Document should equal 240");
        assertEquals("Einstein", result.get("scientist"), "The scientist name of the retrieved Document should equal Einstein");

        assertNotNull(result.get(MONGO_ID), "Document in returned list should contain all fields");
        assertNotNull(result.get("scientist"), "Document in returned list should contain all fields");
        assertNotNull(result.get("fixedField"), "Document in returned list should contain all fields");

    }

    @Test
    public void testFindOneByIdWithObjectId() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.countDocuments());
        Document insertObject = new Document("scientist", "Einstein");
        testCollection.insertOne(insertObject);
        assertTrue(insertObject.get(MONGO_ID) instanceof ObjectId, "The ID of the inserted document should be ObjectId");
        ObjectId id = insertObject.getObjectId(MONGO_ID);

        Document result = template.requestBody("direct:findById", id, Document.class);
        assertTrue(result instanceof Document, "Result is not of type Document");

        assertTrue(result.get(MONGO_ID) instanceof ObjectId, "The ID of the retrieved Document should be ObjectId");
        assertEquals(id, result.get(MONGO_ID), "The ID of the retrieved Document should equal to the inserted");
        assertEquals("Einstein", result.get("scientist"), "The scientist name of the retrieved Document should equal Einstein");

        assertNotNull(result.get(MONGO_ID), "Document in returned list should contain all fields");
        assertNotNull(result.get("scientist"), "Document in returned list should contain all fields");

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                from("direct:findAll").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findAll&dynamicity=true")
                    .to("mock:resultFindAll");

                from("direct:findOneByQuery").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findOneByQuery&dynamicity=true")
                    .to("mock:resultFindOneByQuery");

                from("direct:findById").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findById&dynamicity=true")
                    .to("mock:resultFindById");

                from("direct:findDistinct").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findDistinct&dynamicity=true")
                    .to("mock:resultFindDistinct");
            }
        };
    }
}
