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

import java.util.Formatter;
import java.util.List;

import static java.util.Arrays.asList;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.util.JSON;

import de.flapdoodle.embed.process.collections.Collections;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Test;

public class MongoDbOperationsTest extends AbstractMongoDbTest {

    @Test
    public void testCountOperation() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        Object result = template.requestBody("direct:count", "irrelevantBody");
        assertTrue("Result is not of type Long", result instanceof Long);
        assertEquals("Test collection should not contain any records", 0L, result);

        // Insert a record and test that the endpoint now returns 1
        testCollection.insertOne((BasicDBObject) JSON.parse("{a:60}"));
        result = template.requestBody("direct:count", "irrelevantBody");
        assertTrue("Result is not of type Long", result instanceof Long);
        assertEquals("Test collection should contain 1 record", 1L, result);
        testCollection.deleteOne(new BasicDBObject());
        
        // test dynamicity
        dynamicCollection.insertOne((BasicDBObject) JSON.parse("{a:60}"));
        result = template.requestBodyAndHeader("direct:count", "irrelevantBody", MongoDbConstants.COLLECTION, dynamicCollectionName);
        assertTrue("Result is not of type Long", result instanceof Long);
        assertEquals("Dynamic collection should contain 1 record", 1L, result);
        
    }

    @Test
    public void testInsertString() throws Exception {
        assertEquals(0, testCollection.count());
        Object result = template.requestBody("direct:insert", "{\"_id\":\"testInsertString\", \"scientist\":\"Einstein\"}");
        assertTrue(result instanceof BasicDBObject);
        DBObject b = testCollection.find(new BasicDBObject("_id", "testInsertString")).first();
        assertNotNull("No record with 'testInsertString' _id", b);
    }

    @Test
    public void testMultiInsertStringFromDBListNoHeader() throws Exception {
        assertEquals(0, testCollection.count());
        Object result = template.requestBody("direct:insert", "[{\"_id\":\"testInsertString2\", \"scientist\":\"Einstein\"}, "
                + "{\"_id\":\"testInsertString3\", \"scientist\":\"Einstein Too\"}]");
        assertTrue(result instanceof List);
        DBObject b = testCollection.find(new BasicDBObject("_id", "testInsertString2")).first();
        assertNotNull("No record with 'testInsertString2' _id", b);
        b = testCollection.find(new BasicDBObject("_id", "testInsertString3")).first();
        assertNotNull("No record with 'testInsertString3' _id", b);
    }

    @Test
    public void testMultiInsertStringFromListNoHeader() throws Exception {
        assertEquals(0, testCollection.count());
        Object result = template.requestBody("direct:insert", 
                Collections.newArrayList("{\"_id\":\"testInsertString4\", \"scientist\":\"Einstein\"}",
                        "{\"_id\":\"testInsertString5\", \"scientist\":\"Einstein Too\"}"));
        assertTrue(result instanceof List);
        DBObject b = testCollection.find(new BasicDBObject("_id", "testInsertString4")).first();
        assertNotNull("No record with 'testInsertString4' _id", b);
        b = testCollection.find(new BasicDBObject("_id", "testInsertString5")).first();
        assertNotNull("No record with 'testInsertString5' _id", b);
    }

    
    @Test
    public void testMultiInsertStringFromDBListHeader() throws Exception {
        assertEquals(0, testCollection.count());
        Object result = template.requestBody("direct:multiinsert", "[{\"_id\":\"testInsertString6\", \"scientist\":\"Einstein\"}, "
                + "{\"_id\":\"testInsertString7\", \"scientist\":\"Einstein Too\"}]");
        assertTrue(result instanceof List);
        DBObject b = testCollection.find(new BasicDBObject("_id", "testInsertString6")).first();
        assertNotNull("No record with 'testInsertString6' _id", b);
        b = testCollection.find(new BasicDBObject("_id", "testInsertString7")).first();
        assertNotNull("No record with 'testInsertString7' _id", b);
    }

    @Test
    public void testMultiInsertStringFromListHeader() throws Exception {
        assertEquals(0, testCollection.count());
        Object result = template.requestBody("direct:multiinsert", 
                Collections.newArrayList("{\"_id\":\"testInsertString8\", \"scientist\":\"Einstein\"}",
                        "{\"_id\":\"testInsertString9\", \"scientist\":\"Einstein Too\"}"));
        assertTrue(result instanceof List);
        DBObject b = testCollection.find(new BasicDBObject("_id", "testInsertString8")).first();
        assertNotNull("No record with 'testInsertString8' _id", b);
        b = testCollection.find(new BasicDBObject("_id", "testInsertString9")).first();
        assertNotNull("No record with 'testInsertString9' _id", b);
    }
    
    @Test
    public void testStoreOidOnInsert() throws Exception {
        DBObject dbObject = new BasicDBObject();
        ObjectId oid = template.requestBody("direct:testStoreOidOnInsert", dbObject, ObjectId.class);
        assertEquals(dbObject.get("_id"), oid);
    }

    @Test
    public void testStoreOidsOnInsert() throws Exception {
        DBObject firstDbObject = new BasicDBObject();
        DBObject secondDbObject = new BasicDBObject();
        List<?> oids = template.requestBody("direct:testStoreOidOnInsert", asList(firstDbObject, secondDbObject), List.class);
        assertTrue(oids.contains(firstDbObject.get("_id")));
        assertTrue(oids.contains(secondDbObject.get("_id")));
    }

    @Test
    public void testSave() throws Exception {
        // Prepare test
        assertEquals(0, testCollection.count());
        Object[] req = new Object[] {"{\"_id\":\"testSave1\", \"scientist\":\"Einstein\"}", "{\"_id\":\"testSave2\", \"scientist\":\"Copernicus\"}"};
        Object result = template.requestBody("direct:insert", req);
        assertTrue(result instanceof List);
        assertEquals("Number of records persisted must be 2", 2, testCollection.count());
        
        // Testing the save logic
        DBObject record1 = testCollection.find(new BasicDBObject("_id", "testSave1")).first();
        assertEquals("Scientist field of 'testSave1' must equal 'Einstein'", "Einstein", record1.get("scientist"));
        record1.put("scientist", "Darwin");
        
        result = template.requestBody("direct:save", record1);
        assertTrue(result instanceof UpdateResult);
        
        record1 = testCollection.find(new BasicDBObject("_id", "testSave1")).first();
        assertEquals("Scientist field of 'testSave1' must equal 'Darwin' after save operation", "Darwin", record1.get("scientist"));

    }

    @Test
    public void testStoreOidOnSave() throws Exception {
        DBObject dbObject = new BasicDBObject();
        ObjectId oid = template.requestBody("direct:testStoreOidOnSave", dbObject, ObjectId.class);
        assertEquals(dbObject.get("_id"), oid);
    }
    
    @Test
    public void testUpdate() throws Exception {
        // Prepare test
        assertEquals(0, testCollection.count());
        for (int i = 1; i <= 100; i++) {
            String body = null;
            Formatter f = new Formatter();
            if (i % 2 == 0) {
                body = f.format("{\"_id\":\"testSave%d\", \"scientist\":\"Einstein\"}", i).toString();
            } else {
                body = f.format("{\"_id\":\"testSave%d\", \"scientist\":\"Einstein\", \"extraField\": true}", i).toString();
            }
            f.close();
            template.requestBody("direct:insert", body);
        }
        assertEquals(100L, testCollection.count());
        
        // Testing the update logic
        BasicDBObject extraField = new BasicDBObject("extraField", true);
        assertEquals("Number of records with 'extraField' flag on must equal 50", 50L, testCollection.count(extraField));
        assertEquals("Number of records with 'scientist' field = Darwin on must equal 0", 0, testCollection.count(new BasicDBObject("scientist", "Darwin")));

        DBObject updateObj = new BasicDBObject("$set", new BasicDBObject("scientist", "Darwin"));
        
        Exchange resultExchange = template.request("direct:update", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(new Object[] {extraField, updateObj});
                exchange.getIn().setHeader(MongoDbConstants.MULTIUPDATE, true);
            }
        });
        Object result = resultExchange.getOut().getBody();
        assertTrue(result instanceof UpdateResult);
        assertEquals("Number of records updated header should equal 50", 50L, resultExchange.getOut().getHeader(MongoDbConstants.RECORDS_AFFECTED));
        
        assertEquals("Number of records with 'scientist' field = Darwin on must equal 50 after update", 50, 
                testCollection.count(new BasicDBObject("scientist", "Darwin")));

    }
    
    @Test
    public void testRemove() throws Exception {
        // Prepare test
        assertEquals(0, testCollection.count());
        for (int i = 1; i <= 100; i++) {
            String body = null;
            Formatter f = new Formatter();
            if (i % 2 == 0) {
                body = f.format("{\"_id\":\"testSave%d\", \"scientist\":\"Einstein\"}", i).toString();
            } else {
                body = f.format("{\"_id\":\"testSave%d\", \"scientist\":\"Einstein\", \"extraField\": true}", i).toString();
            }
            f.close();
            template.requestBody("direct:insert", body);
        }
        assertEquals(100L, testCollection.count());
        
        // Testing the update logic
        BasicDBObject extraField = new BasicDBObject("extraField", true);
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
        
        assertEquals("Number of records with 'extraField' flag on must be 0 after remove", 0, 
                testCollection.count(extraField));

    }
    
    @Test
    public void testAggregate() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        pumpDataIntoTestCollection();

        // Repeat ten times, obtain 10 batches of 100 results each time
        Object result = template
            .requestBody("direct:aggregate",
                         "[{ $match : {$or : [{\"scientist\" : \"Darwin\"},{\"scientist\" : \"Einstein\"}]}},{ $group: { _id: \"$scientist\", count: { $sum: 1 }} } ]");
        assertTrue("Result is not of type List", result instanceof List);

        @SuppressWarnings("unchecked")
        List<BasicDBObject> resultList = (List<BasicDBObject>)result;
        assertListSize("Result does not contain 2 elements", resultList, 2);
        // TODO Add more asserts
    }
    
    @Test
    public void testDbStats() throws Exception {
        assertEquals(0, testCollection.count());
        Object result = template.requestBody("direct:getDbStats", "irrelevantBody");
        assertTrue("Result is not of type DBObject", result instanceof Document);
        assertTrue("The result should contain keys", ((Document) result).keySet().size() > 0);
    }
    
    @Test
    public void testColStats() throws Exception {
        assertEquals(0, testCollection.count());
        
        // Add some records to the collection (and do it via camel-mongodb)
        for (int i = 1; i <= 100; i++) {
            String body = null;
            Formatter f = new Formatter();
            body = f.format("{\"_id\":\"testSave%d\", \"scientist\":\"Einstein\"}", i).toString();
            f.close();
            template.requestBody("direct:insert", body);
        }
        
        Object result = template.requestBody("direct:getColStats", "irrelevantBody");
        assertTrue("Result is not of type DBObject", result instanceof Document);
        assertTrue("The result should contain keys", ((Document) result).keySet().size() > 0);
    }

    @Test
    public void testCommand() throws Exception {
        //Call hostInfo, command working with every configuration
        Object result = template
                .requestBody("direct:command",
                        "{\"hostInfo\":\"1\"}");
        assertTrue("Result is not of type DBObject", result instanceof Document);
        assertTrue("The result should contain keys", ((Document) result).keySet().size() > 0);
    }

    @Test
    public void testOperationHeader() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        
        // check that the count operation was invoked instead of the insert operation
        Object result = template.requestBodyAndHeader("direct:insert", "irrelevantBody", MongoDbConstants.OPERATION_HEADER, "count");
        assertTrue("Result is not of type Long", result instanceof Long);
        assertEquals("Test collection should not contain any records", 0L, result);
        
        
        // check that the count operation was invoked instead of the insert operation
        result = template.requestBodyAndHeader("direct:insert", "irrelevantBody", MongoDbConstants.OPERATION_HEADER, MongoDbOperation.count);
        assertTrue("Result is not of type Long", result instanceof Long);
        assertEquals("Test collection should not contain any records", 0L, result);
        
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                
                from("direct:count").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=count&dynamicity=true");
                from("direct:multiinsert").setHeader(MongoDbConstants.MULTIINSERT).constant(true).
                    to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert&writeConcern=SAFE");
                from("direct:insert").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert&writeConcern=SAFE");
                from("direct:testStoreOidOnInsert").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert&writeConcern=SAFE").
                    setBody().header(MongoDbConstants.OID);
                from("direct:save").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=save&writeConcern=SAFE");
                from("direct:testStoreOidOnSave").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=save&writeConcern=SAFE").
                    setBody().header(MongoDbConstants.OID);
                from("direct:update").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=update&writeConcern=SAFE");
                from("direct:remove").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=remove&writeConcern=SAFE");
                from("direct:aggregate").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=aggregate&writeConcern=SAFE");
                from("direct:getDbStats").to("mongodb:myDb?database={{mongodb.testDb}}&operation=getDbStats");
                from("direct:getColStats").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=getColStats");
                from("direct:command").to("mongodb:myDb?database={{mongodb.testDb}}&operation=command");

            }
        };
    }
}

