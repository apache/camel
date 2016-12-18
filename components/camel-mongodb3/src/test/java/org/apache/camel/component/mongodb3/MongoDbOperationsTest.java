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
package org.apache.camel.component.mongodb3;

import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;

import static java.util.Arrays.asList;

import com.mongodb.MongoClient;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.Test;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.currentTimestamp;
import static com.mongodb.client.model.Updates.set;
import static org.apache.camel.component.mongodb3.MongoDbConstants.MONGO_ID;

public class MongoDbOperationsTest extends AbstractMongoDbTest {

    @Test
    public void testCountOperation() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        Object result = template.requestBody("direct:count", "irrelevantBody");
        assertTrue("Result is not of type Long", result instanceof Long);
        assertEquals("Test collection should not contain any records", 0L, result);

        // Insert a record and test that the endpoint now returns 1
        testCollection.insertOne(Document.parse("{a:60}"));
        result = template.requestBody("direct:count", "irrelevantBody");
        assertTrue("Result is not of type Long", result instanceof Long);
        assertEquals("Test collection should contain 1 record", 1L, result);
        testCollection.deleteOne(new Document());

        // test dynamicity
        dynamicCollection.insertOne(Document.parse("{a:60}"));
        result = template.requestBodyAndHeader("direct:count", "irrelevantBody", MongoDbConstants.COLLECTION, dynamicCollectionName);
        assertTrue("Result is not of type Long", result instanceof Long);
        assertEquals("Dynamic collection should contain 1 record", 1L, result);

    }

    @Test
    public void testInsertString() throws Exception {
        assertEquals(0, testCollection.count());
        Object result = template.requestBody("direct:insert", new Document(MONGO_ID, "testInsertString").append("scientist", "Einstein").toJson());
        assertTrue(result instanceof Document);
        Document b = testCollection.find(eq(MONGO_ID, "testInsertString")).first();
        assertNotNull("No record with 'testInsertString' _id", b);
    }

    @Test
    public void testStoreOidOnInsert() throws Exception {
        Document document = new Document();
        ObjectId oid = template.requestBody("direct:testStoreOidOnInsert", document, ObjectId.class);
        assertEquals(document.get(MONGO_ID), oid);
    }

    @Test
    public void testStoreOidsOnInsert() throws Exception {
        Document firsDocument = new Document();
        Document secondDoocument = new Document();
        List<?> oids = template.requestBody("direct:testStoreOidOnInsert", asList(firsDocument, secondDoocument), List.class);
        assertTrue(oids.contains(firsDocument.get(MONGO_ID)));
        assertTrue(oids.contains(secondDoocument.get(MONGO_ID)));
    }

    @Test
    public void testSave() throws Exception {
        // Prepare test
        assertEquals(0, testCollection.count());
        Object[] req = new Object[] {new Document(MONGO_ID, "testSave1").append("scientist", "Einstein").toJson(),
                                     new Document(MONGO_ID, "testSave2").append("scientist", "Copernicus").toJson()};
        Object result = template.requestBody("direct:insert", req);
        assertTrue(result instanceof List);
        assertEquals("Number of records persisted must be 2", 2, testCollection.count());

        // Testing the save logic
        Document record1 = testCollection.find(eq(MONGO_ID, "testSave1")).first();
        assertEquals("Scientist field of 'testSave1' must equal 'Einstein'", "Einstein", record1.get("scientist"));
        record1.put("scientist", "Darwin");

        result = template.requestBody("direct:save", record1);
        assertTrue(result instanceof UpdateResult);

        record1 = testCollection.find(eq(MONGO_ID, "testSave1")).first();
        assertEquals("Scientist field of 'testSave1' must equal 'Darwin' after save operation", "Darwin", record1.get("scientist"));

    }

    @Test
    public void testSaveWithoutId() {
        // Prepare test
        assertEquals(0, testCollection.count());
        // This document should not be modified
        Document doc = new Document("scientist", "Copernic");
        template.requestBody("direct:insert", doc);
        // save (upsert) a document without Id => insert with new Id
        doc = new Document("scientist", "Einstein");
        assertNull(doc.get(MONGO_ID));
        UpdateResult result = template.requestBody("direct:save", doc, UpdateResult.class);
        assertNotNull(result.getUpsertedId());
        // Without Id save perform an insert not an update.
        assertEquals(0, result.getModifiedCount());
        // Testing the save logic
        Document record1 = testCollection.find(eq(MONGO_ID, result.getUpsertedId())).first();
        assertEquals("Scientist field of '" + result.getUpsertedId() + "' must equal 'Einstein'", "Einstein", record1.get("scientist"));
    }

    @Test
    public void testStoreOidOnSaveWithoutId() throws Exception {
        Document document = new Document();
        ObjectId oid = template.requestBody("direct:testStoreOidOnSave", document, ObjectId.class);
        assertNotNull(oid);
    }

    @Test
    public void testStoreOidOnSave() throws Exception {
        Document document = new Document(MONGO_ID, new ObjectId("5847e39e0824d6b54194e197"));
        ObjectId oid = template.requestBody("direct:testStoreOidOnSave", document, ObjectId.class);
        assertEquals(document.get(MONGO_ID), oid);
    }

    @Test
    public void testUpdate() throws Exception {
        // Prepare test
        assertEquals(0, testCollection.count());
        for (int i = 1; i <= 100; i++) {
            String body = null;
            try (Formatter f = new Formatter();) {
                if (i % 2 == 0) {
                    body = f.format("{\"_id\":\"testSave%d\", \"scientist\":\"Einstein\"}", i).toString();
                } else {
                    body = f.format("{\"_id\":\"testSave%d\", \"scientist\":\"Einstein\", \"extraField\": true}", i).toString();
                }
                f.close();
            }
            template.requestBody("direct:insert", body);
        }
        assertEquals(100L, testCollection.count());

        // Testing the update logic
        Bson extraField = eq("extraField", true);
        assertEquals("Number of records with 'extraField' flag on must equal 50", 50L, testCollection.count(extraField));
        assertEquals("Number of records with 'scientist' field = Darwin on must equal 0", 0, testCollection.count(new Document("scientist", "Darwin")));

        Bson updateObj = combine(set("scientist", "Darwin"), currentTimestamp("lastModified"));

        Exchange resultExchange = template.request("direct:update", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(new Bson[] {extraField, updateObj});
                exchange.getIn().setHeader(MongoDbConstants.MULTIUPDATE, true);
            }
        });
        Object result = resultExchange.getOut().getBody();
        assertTrue(result instanceof UpdateResult);
        assertEquals("Number of records updated header should equal 50", 50L, resultExchange.getOut().getHeader(MongoDbConstants.RECORDS_AFFECTED));

        assertEquals("Number of records with 'scientist' field = Darwin on must equal 50 after update", 50, testCollection.count(new Document("scientist", "Darwin")));
    }

    @Test
    public void testUpdateFromString() throws Exception {
        // Prepare test
        assertEquals(0, testCollection.count());
        for (int i = 1; i <= 100; i++) {
            String body = null;
            try (Formatter f = new Formatter();) {
                if (i % 2 == 0) {
                    body = f.format("{\"_id\":\"testSave%d\", \"scientist\":\"Einstein\"}", i).toString();
                } else {
                    body = f.format("{\"_id\":\"testSave%d\", \"scientist\":\"Einstein\", \"extraField\": true}", i).toString();
                }
                f.close();
            }
            template.requestBody("direct:insert", body);
        }
        assertEquals(100L, testCollection.count());

        // Testing the update logic
        Bson extraField = eq("extraField", true);
        assertEquals("Number of records with 'extraField' flag on must equal 50", 50L, testCollection.count(extraField));
        assertEquals("Number of records with 'scientist' field = Darwin on must equal 0", 0, testCollection.count(new Document("scientist", "Darwin")));

        Bson updateObj = combine(set("scientist", "Darwin"), currentTimestamp("lastModified"));

        String updates = "[" + extraField.toBsonDocument(Document.class, MongoClient.getDefaultCodecRegistry()).toJson() + ","
                         + updateObj.toBsonDocument(Document.class, MongoClient.getDefaultCodecRegistry()).toJson() + "]";

        Exchange resultExchange = template.request("direct:update", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(updates);
                exchange.getIn().setHeader(MongoDbConstants.MULTIUPDATE, true);
            }
        });
        Object result = resultExchange.getOut().getBody();
        assertTrue(result instanceof UpdateResult);
        assertEquals("Number of records updated header should equal 50", 50L, resultExchange.getOut().getHeader(MongoDbConstants.RECORDS_AFFECTED));

        assertEquals("Number of records with 'scientist' field = Darwin on must equal 50 after update", 50, testCollection.count(new Document("scientist", "Darwin")));
    }

    @Test
    public void testUpdateUsingFieldsFilterHeader() throws Exception {
        // Prepare test
        assertEquals(0, testCollection.count());
        for (int i = 1; i <= 100; i++) {
            String body = null;
            try (Formatter f = new Formatter();) {
                if (i % 2 == 0) {
                    body = f.format("{\"_id\":\"testSave%d\", \"scientist\":\"Einstein\"}", i).toString();
                } else {
                    body = f.format("{\"_id\":\"testSave%d\", \"scientist\":\"Einstein\", \"extraField\": true}", i).toString();
                }
                f.close();
            }
            template.requestBody("direct:insert", body);
        }
        assertEquals(100L, testCollection.count());

        // Testing the update logic
        Bson extraField = eq("extraField", true);
        assertEquals("Number of records with 'extraField' flag on must equal 50", 50L, testCollection.count(extraField));
        assertEquals("Number of records with 'scientist' field = Darwin on must equal 0", 0, testCollection.count(new Document("scientist", "Darwin")));

        Bson updateObj = combine(set("scientist", "Darwin"), currentTimestamp("lastModified"));
        HashMap<String, Object> headers = new HashMap<>();
        headers.put(MongoDbConstants.MULTIUPDATE, true);
        headers.put(MongoDbConstants.CRITERIA, extraField);
        Object result = template.requestBodyAndHeaders("direct:update", updateObj, headers);
        assertTrue(result instanceof UpdateResult);
        assertEquals("Number of records updated header should equal 50", 50L, UpdateResult.class.cast(result).getModifiedCount());
        assertEquals("Number of records with 'scientist' field = Darwin on must equal 50 after update", 50, testCollection.count(new Document("scientist", "Darwin")));
    }

    @Test
    public void testRemove() throws Exception {
        // Prepare test
        assertEquals(0, testCollection.count());
        for (int i = 1; i <= 100; i++) {
            String body = null;
            try (Formatter f = new Formatter();) {
                if (i % 2 == 0) {
                    body = f.format("{\"_id\":\"testSave%d\", \"scientist\":\"Einstein\"}", i).toString();
                } else {
                    body = f.format("{\"_id\":\"testSave%d\", \"scientist\":\"Einstein\", \"extraField\": true}", i).toString();
                }
                f.close();
            }
            template.requestBody("direct:insert", body);
        }
        assertEquals(100L, testCollection.count());

        // Testing the update logic
        Document extraField = new Document("extraField", true);
        assertEquals("Number of records with 'extraField' flag on must equal 50", 50L, testCollection.count(extraField));

        Exchange resultExchange = template.request("direct:remove", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(extraField);
            }
        });
        Object result = resultExchange.getOut().getBody();
        assertTrue(result instanceof DeleteResult);
        assertEquals("Number of records deleted header should equal 50", 50L, resultExchange.getOut().getHeader(MongoDbConstants.RECORDS_AFFECTED));

        assertEquals("Number of records with 'extraField' flag on must be 0 after remove", 0, testCollection.count(extraField));

    }

    @Test
    public void testAggregate() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        pumpDataIntoTestCollection();

        // Repeat ten times, obtain 10 batches of 100 results each time
        List<Bson> aggregate = Arrays.asList(match(or(eq("scientist", "Darwin"), eq("scientist", "Einstein"))), group("$scientist", sum("count", 1)));
        Object result = template.requestBody("direct:aggregate", aggregate);
        assertTrue("Result is not of type List", result instanceof List);

        @SuppressWarnings("unchecked")
        List<Document> resultList = (List<Document>)result;
        assertListSize("Result does not contain 2 elements", resultList, 2);
        // TODO Add more asserts
    }

    @Test
    public void testDbStats() throws Exception {
        assertEquals(0, testCollection.count());
        Object result = template.requestBody("direct:getDbStats", "irrelevantBody");
        assertTrue("Result is not of type Document", result instanceof Document);
        assertTrue("The result should contain keys", Document.class.cast(result).keySet().size() > 0);
    }

    @Test
    public void testColStats() throws Exception {
        assertEquals(0, testCollection.count());

        // Add some records to the collection (and do it via camel-mongodb)
        for (int i = 1; i <= 100; i++) {
            String body = null;
            try (Formatter f = new Formatter();) {
                body = f.format("{\"_id\":\"testSave%d\", \"scientist\":\"Einstein\"}", i).toString();
                f.close();
            }
            template.requestBody("direct:insert", body);
        }

        Object result = template.requestBody("direct:getColStats", "irrelevantBody");
        assertTrue("Result is not of type Document", result instanceof Document);
        assertTrue("The result should contain keys", Document.class.cast(result).keySet().size() > 0);
    }

    @Test
    public void testCommand() throws Exception {
        // Call hostInfo, command working with every configuration
        Object result = template.requestBody("direct:command", "{\"hostInfo\":\"1\"}");
        assertTrue("Result is not of type Document", result instanceof Document);
        assertTrue("The result should contain keys", Document.class.cast(result).keySet().size() > 0);
    }

    @Test
    public void testOperationHeader() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());

        // check that the count operation was invoked instead of the insert
        // operation
        Object result = template.requestBodyAndHeader("direct:insert", "irrelevantBody", MongoDbConstants.OPERATION_HEADER, "count");
        assertTrue("Result is not of type Long", result instanceof Long);
        assertEquals("Test collection should not contain any records", 0L, result);

        // check that the count operation was invoked instead of the insert
        // operation
        result = template.requestBodyAndHeader("direct:insert", "irrelevantBody", MongoDbConstants.OPERATION_HEADER, MongoDbOperation.count);
        assertTrue("Result is not of type Long", result instanceof Long);
        assertEquals("Test collection should not contain any records", 0L, result);

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                from("direct:count").to("mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=count&dynamicity=true");
                from("direct:insert").to("mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert");
                from("direct:testStoreOidOnInsert").to("mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert").setBody()
                    .header(MongoDbConstants.OID);
                from("direct:save").to("mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=save");
                from("direct:testStoreOidOnSave").to("mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=save").setBody()
                    .header(MongoDbConstants.OID);
                from("direct:update").to("mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=update");
                from("direct:remove").to("mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=remove");
                from("direct:aggregate").to("mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=aggregate");
                from("direct:getDbStats").to("mongodb3:myDb?database={{mongodb.testDb}}&operation=getDbStats");
                from("direct:getColStats").to("mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=getColStats");
                from("direct:command").to("mongodb3:myDb?database={{mongodb.testDb}}&operation=command");

            }
        };
    }
}
