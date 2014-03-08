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
import java.util.Map;

import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;

import org.apache.camel.builder.RouteBuilder;

import org.junit.Test;

public class MongoDbDynamicityTest extends AbstractMongoDbTest {
    
    @Test
    public void testInsertDynamicityDisabled() {
        assertEquals(0, testCollection.count());
        mongo.getDB("otherDB").dropDatabase();
        db.getCollection("otherCollection").drop();
        assertFalse("The otherDB database should not exist", mongo.getDatabaseNames().contains("otherDB"));

        String body = "{\"_id\": \"testInsertDynamicityDisabled\", \"a\" : \"1\"}";
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(MongoDbConstants.DATABASE, "otherDB");
        headers.put(MongoDbConstants.COLLECTION, "otherCollection");
        Object result = template.requestBodyAndHeaders("direct:noDynamicity", body, headers);
        assertEquals("Response isn't of type WriteResult", WriteResult.class, result.getClass());
        
        DBObject b = testCollection.findOne("testInsertDynamicityDisabled");
        assertNotNull("No record with 'testInsertDynamicityDisabled' _id", b);
        
        body = "{\"_id\": \"testInsertDynamicityDisabledExplicitly\", \"a\" : \"1\"}";
        result = template.requestBodyAndHeaders("direct:noDynamicityExplicit", body, headers);
        assertEquals("Response isn't of type WriteResult", WriteResult.class, result.getClass());
        
        b = testCollection.findOne("testInsertDynamicityDisabledExplicitly");
        assertNotNull("No record with 'testInsertDynamicityDisabledExplicitly' _id", b);
        
        assertFalse("The otherDB database should not exist", mongo.getDatabaseNames().contains("otherDB"));

    }
    
    @Test
    public void testInsertDynamicityEnabledDBOnly() {
        assertEquals(0, testCollection.count());
        mongo.getDB("otherDB").dropDatabase();
        db.getCollection("otherCollection").drop();
        assertFalse("The otherDB database should not exist", mongo.getDatabaseNames().contains("otherDB"));

        String body = "{\"_id\": \"testInsertDynamicityEnabledDBOnly\", \"a\" : \"1\"}";
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(MongoDbConstants.DATABASE, "otherDB");
        Object result = template.requestBodyAndHeaders("direct:dynamicityEnabled", body, headers);
        
        assertEquals("Response isn't of type WriteResult", WriteResult.class, result.getClass());
        
        DBCollection dynamicCollection = mongo.getDB("otherDB").getCollection(testCollection.getName());
        
        DBObject b = dynamicCollection.findOne("testInsertDynamicityEnabledDBOnly");
        assertNotNull("No record with 'testInsertDynamicityEnabledDBOnly' _id", b);
        
        b = testCollection.findOne("testInsertDynamicityEnabledDBOnly");
        assertNull("There is a record with 'testInsertDynamicityEnabledDBOnly' _id in the test collection", b);
        
        assertTrue("The otherDB database should exist", mongo.getDatabaseNames().contains("otherDB"));
        
    }
    
    @Test
    public void testInsertDynamicityEnabledCollectionOnly() {
        assertEquals(0, testCollection.count());
        mongo.getDB("otherDB").dropDatabase();
        db.getCollection("otherCollection").drop();
        assertFalse("The otherDB database should not exist", mongo.getDatabaseNames().contains("otherDB"));

        String body = "{\"_id\": \"testInsertDynamicityEnabledCollectionOnly\", \"a\" : \"1\"}";
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(MongoDbConstants.COLLECTION, "otherCollection");
        Object result = template.requestBodyAndHeaders("direct:dynamicityEnabled", body, headers);
        
        assertEquals("Response isn't of type WriteResult", WriteResult.class, result.getClass());
        
        DBCollection dynamicCollection = db.getCollection("otherCollection");
        
        DBObject b = dynamicCollection.findOne("testInsertDynamicityEnabledCollectionOnly");
        assertNotNull("No record with 'testInsertDynamicityEnabledCollectionOnly' _id", b);
        
        b = testCollection.findOne("testInsertDynamicityEnabledDBOnly");
        assertNull("There is a record with 'testInsertDynamicityEnabledCollectionOnly' _id in the test collection", b);
        
        assertFalse("The otherDB database should not exist", mongo.getDatabaseNames().contains("otherDB"));
    }
    
    @Test
    public void testInsertDynamicityEnabledDBAndCollection() {
        assertEquals(0, testCollection.count());
        mongo.getDB("otherDB").dropDatabase();
        db.getCollection("otherCollection").drop();
        assertFalse("The otherDB database should not exist", mongo.getDatabaseNames().contains("otherDB"));

        String body = "{\"_id\": \"testInsertDynamicityEnabledDBAndCollection\", \"a\" : \"1\"}";
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(MongoDbConstants.DATABASE, "otherDB");
        headers.put(MongoDbConstants.COLLECTION, "otherCollection");
        Object result = template.requestBodyAndHeaders("direct:dynamicityEnabled", body, headers);
        
        assertEquals("Response isn't of type WriteResult", WriteResult.class, result.getClass());
        
        DBCollection dynamicCollection = mongo.getDB("otherDB").getCollection("otherCollection");
        
        DBObject b = dynamicCollection.findOne("testInsertDynamicityEnabledDBAndCollection");
        assertNotNull("No record with 'testInsertDynamicityEnabledDBAndCollection' _id", b);
        
        b = testCollection.findOne("testInsertDynamicityEnabledDBOnly");
        assertNull("There is a record with 'testInsertDynamicityEnabledDBAndCollection' _id in the test collection", b);
        
        assertTrue("The otherDB database should exist", mongo.getDatabaseNames().contains("otherDB"));
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                                
                from("direct:noDynamicity").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert&writeConcern=SAFE");
                from("direct:noDynamicityExplicit").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert&dynamicity=false&writeConcern=SAFE");
                from("direct:dynamicityEnabled").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert&dynamicity=true&writeConcern=SAFE");

            }
        };
    }
}
