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

import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Projections;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mongodb.MongoDbComponent;
import org.apache.camel.component.mongodb.MongoDbConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.mongodb.common.MongoDBProperties;
import org.apache.camel.test.infra.mongodb.services.MongoDBLocalContainerService;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.commons.lang3.ObjectUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.wait.strategy.Wait;

import static com.mongodb.client.model.Filters.eq;
import static org.apache.camel.component.mongodb.MongoDbConstants.MONGO_ID;
import static org.apache.camel.test.junit5.TestSupport.assertListSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MongoDbFindOperationIT extends CamelTestSupport {

    @RegisterExtension
    public static MongoDBLocalContainerService service;

    protected static String dbName = "test";
    protected static String testCollectionName;

    private static String mongoDbContainer;

    private static MongoClient mongo;
    private static MongoDatabase db;
    private static MongoCollection<Document> testCollection;

    static {

        // This one requires Mongo 4.4. This is related to
        // "CAMEL-15604 support allowDiskUse for MongoDB find operations"
        mongoDbContainer = System.getProperty(MongoDBProperties.MONGODB_CONTAINER, "mongo:4.4");

        service = new MongoDBLocalContainerService(mongoDbContainer);

        service.getContainer()
                .waitingFor(Wait.forListeningPort())
                .withCommand(
                        "--replSet", "replicationName",
                        "--oplogSize", "5000",
                        "--syncdelay", "0",
                        "--noauth");
    }

    @Override
    public void doPreSetup() throws Exception {
        super.doPreSetup();

        mongo = MongoClients.create(service.getReplicaSetUrl());
        db = mongo.getDatabase(dbName);
    }

    @Override
    protected void doPostSetup() {
        // Refresh the test collection - drop it and recreate it. We don't do
        // this for the database because MongoDB would create large
        // store files each time
        testCollectionName = "camelTest";
        testCollection = db.getCollection(testCollectionName, Document.class);
        testCollection.drop();
        testCollection = db.getCollection(testCollectionName, Document.class);
    }

    @Override
    protected CamelContext createCamelContext() {
        MongoDbComponent component = new MongoDbComponent();
        component.setMongoConnection(mongo);

        @SuppressWarnings("deprecation")
        CamelContext ctx = new DefaultCamelContext();
        ctx.getPropertiesComponent().setLocation("classpath:mongodb.test.properties");

        ctx.addComponent("mongodb", component);

        return ctx;
    }

    protected void pumpDataIntoTestCollection() {
        // there should be 100 of each
        String[] scientists
                = { "Einstein", "Darwin", "Copernicus", "Pasteur", "Curie", "Faraday", "Newton", "Bohr", "Galilei", "Maxwell" };
        for (int i = 1; i <= 1000; i++) {
            int index = i % scientists.length;
            Formatter f = new Formatter();
            String doc
                    = f.format("{\"_id\":\"%d\", \"scientist\":\"%s\", \"fixedField\": \"fixedValue\"}", i, scientists[index])
                            .toString();
            IOHelper.close(f);
            testCollection.insertOne(Document.parse(doc));
        }
        assertEquals(1000L, testCollection.countDocuments(), "Data pumping of 1000 entries did not complete entirely");
    }

    @BeforeEach
    void checkDocuments() {
        Assumptions.assumeTrue(0 == testCollection.countDocuments(), "The collection should have no documents");
    }

    @Test
    public void testFindAllNoCriteriaOperation() {
        pumpDataIntoTestCollection();

        Object result = template.requestBody("direct:findAll", ObjectUtils.NULL);
        assertTrue(result instanceof List, "Result is not of type List");

        @SuppressWarnings("unchecked")
        List<Document> resultList = (List<Document>) result;

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
        assertEquals(1000, resultExchange.getIn().getHeader(MongoDbConstants.RESULT_PAGE_SIZE),
                "Result page size header should equal 1000");

    }

    @Test
    public void testFindAllAllowDiskUse() {
        pumpDataIntoTestCollection();

        Object result
                = template.requestBodyAndHeader("direct:findAll", ObjectUtils.NULL, MongoDbConstants.ALLOW_DISK_USE, true);
        assertTrue(result instanceof List, "Result (allowDiskUse=true) is not of type List");
        assertListSize("Result (allowDiskUse=true) does not contain all entries in collection", (List<Document>) result, 1000);

        result = template.requestBodyAndHeader("direct:findAll", ObjectUtils.NULL, MongoDbConstants.ALLOW_DISK_USE, false);
        assertInstanceOf(List.class, result, "Result (allowDiskUse=false) is not of type List");
        assertListSize("Result (allowDiskUse=false) does not contain all entries in collection", (List<Document>) result,
                1000);

        result = template.requestBodyAndHeader("direct:findAll", ObjectUtils.NULL, MongoDbConstants.ALLOW_DISK_USE, null);
        assertInstanceOf(List.class, result, "Result (allowDiskUse=null) is not of type List");
        assertListSize("Result (allowDiskUse=null) does not contain all entries in collection", (List<Document>) result, 1000);
    }

    @Test
    public void testFindAllWithQueryAndNoFIlter() {
        pumpDataIntoTestCollection();

        Object result = template.requestBody("direct:findAll", eq("scientist", "Einstein"));
        assertTrue(result instanceof List, "Result is not of type List");

        @SuppressWarnings("unchecked")
        List<Document> resultList = (List<Document>) result;

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
        assertEquals(100, resultExchange.getIn().getHeader(MongoDbConstants.RESULT_PAGE_SIZE),
                "Result page size header should equal 100");
    }

    @Test
    public void testFindAllWithQueryAndFilter() {
        pumpDataIntoTestCollection();
        Bson fieldFilter = Projections.exclude(MONGO_ID, "fixedField");
        Bson query = eq("scientist", "Einstein");
        Object result = template.requestBodyAndHeader("direct:findAll", query, MongoDbConstants.FIELDS_PROJECTION, fieldFilter);
        assertTrue(result instanceof List, "Result is not of type List");

        @SuppressWarnings("unchecked")
        List<Document> resultList = (List<Document>) result;

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
        assertEquals(100, resultExchange.getIn().getHeader(MongoDbConstants.RESULT_PAGE_SIZE),
                "Result page size header should equal 100");
    }

    @Test
    public void testFindAllNoCriteriaWithFilterOperation() {
        pumpDataIntoTestCollection();

        Bson fieldFilter = Projections.exclude(MONGO_ID, "fixedField");
        Object result = template.requestBodyAndHeader("direct:findAll", ObjectUtils.NULL, MongoDbConstants.FIELDS_PROJECTION,
                fieldFilter);
        assertTrue(result instanceof List, "Result is not of type List");

        @SuppressWarnings("unchecked")
        List<Document> resultList = (List<Document>) result;

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
        assertEquals(1000, resultExchange.getIn().getHeader(MongoDbConstants.RESULT_PAGE_SIZE),
                "Result page size header should equal 1000");

    }

    @Test
    public void testFindAllIterationOperation() {
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
            List<Document> resultList = (List<Document>) result;

            assertListSize("Result does not contain 100 elements", resultList, 100);
            assertEquals(numToSkip + 1, Integer.parseInt((String) resultList.get(0).get(MONGO_ID)),
                    "Id of first record is not as expected");

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
            assertEquals(100, resultExchange.getIn().getHeader(MongoDbConstants.RESULT_PAGE_SIZE),
                    "Result page size header should equal 100");
        }
    }

    @Test
    public void testFindDistinctNoQuery() {
        pumpDataIntoTestCollection();

        Object result = template.requestBodyAndHeader("direct:findDistinct", null, MongoDbConstants.DISTINCT_QUERY_FIELD,
                "scientist");
        assertTrue(result instanceof List, "Result is not of type List");

        @SuppressWarnings("unchecked")
        List<String> resultList = (List<String>) result;
        assertEquals(10, resultList.size());
    }

    @Test
    public void testFindDistinctWithQuery() {
        pumpDataIntoTestCollection();

        Bson query = eq("scientist", "Einstein");

        Object result = template.requestBodyAndHeader("direct:findDistinct", query, MongoDbConstants.DISTINCT_QUERY_FIELD,
                "scientist");
        assertInstanceOf(List.class, result, "Result is not of type List");

        @SuppressWarnings("unchecked")
        List<String> resultList = (List<String>) result;
        assertEquals(1, resultList.size());

        assertEquals("Einstein", resultList.get(0));
    }

    @Test
    public void testFindOneByQuery() {
        pumpDataIntoTestCollection();

        Bson query = eq("scientist", "Einstein");
        Document result = template.requestBody("direct:findOneByQuery", query, Document.class);
        assertInstanceOf(Document.class, result, "Result is not of type Document");

        assertNotNull(result.get(MONGO_ID), "Document in returned list should contain all fields");
        assertNotNull(result.get("scientist"), "Document in returned list should contain all fields");
        assertNotNull(result.get("fixedField"), "Document in returned list should contain all fields");

    }

    @Test
    public void testFindOneById() {
        pumpDataIntoTestCollection();

        Document result = template.requestBody("direct:findById", "240", Document.class);
        assertInstanceOf(Document.class, result, "Result is not of type Document");

        assertEquals("240", result.get(MONGO_ID), "The ID of the retrieved Document should equal 240");
        assertEquals("Einstein", result.get("scientist"), "The scientist name of the retrieved Document should equal Einstein");

        assertNotNull(result.get(MONGO_ID), "Document in returned list should contain all fields");
        assertNotNull(result.get("scientist"), "Document in returned list should contain all fields");
        assertNotNull(result.get("fixedField"), "Document in returned list should contain all fields");

    }

    @Test
    public void testFindOneByIdWithObjectId() {
        Document insertObject = new Document("scientist", "Einstein");
        testCollection.insertOne(insertObject);
        assertTrue(insertObject.get(MONGO_ID) instanceof ObjectId, "The ID of the inserted document should be ObjectId");
        ObjectId id = insertObject.getObjectId(MONGO_ID);

        Document result = template.requestBody("direct:findById", id, Document.class);
        assertInstanceOf(Document.class, result, "Result is not of type Document");

        assertTrue(result.get(MONGO_ID) instanceof ObjectId, "The ID of the retrieved Document should be ObjectId");
        assertEquals(id, result.get(MONGO_ID), "The ID of the retrieved Document should equal to the inserted");
        assertEquals("Einstein", result.get("scientist"), "The scientist name of the retrieved Document should equal Einstein");

        assertNotNull(result.get(MONGO_ID), "Document in returned list should contain all fields");
        assertNotNull(result.get("scientist"), "Document in returned list should contain all fields");

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                from("direct:findAll").to(
                        "mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findAll&dynamicity=true")
                        .to("mock:resultFindAll");

                from("direct:findOneByQuery").to(
                        "mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findOneByQuery&dynamicity=true")
                        .to("mock:resultFindOneByQuery");

                from("direct:findById").to(
                        "mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findById&dynamicity=true")
                        .to("mock:resultFindById");

                from("direct:findDistinct").to(
                        "mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findDistinct&dynamicity=true")
                        .to("mock:resultFindDistinct");
            }
        };
    }
}
