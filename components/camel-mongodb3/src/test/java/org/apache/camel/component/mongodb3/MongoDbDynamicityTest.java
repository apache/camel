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

import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

import com.mongodb.client.MongoCollection;

import org.apache.camel.builder.RouteBuilder;
import org.bson.Document;
import org.junit.Test;

import static com.mongodb.client.model.Filters.eq;
import static org.apache.camel.component.mongodb3.MongoDbConstants.MONGO_ID;

public class MongoDbDynamicityTest extends AbstractMongoDbTest {

    @Test
    public void testInsertDynamicityDisabled() {
        assertEquals(0, testCollection.count());
        mongo.getDatabase("otherDB").drop();
        db.getCollection("otherCollection").drop();
        assertFalse("The otherDB database should not exist", StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals));

        String body = "{\"_id\": \"testInsertDynamicityDisabled\", \"a\" : \"1\"}";
        Map<String, Object> headers = new HashMap<>();
        headers.put(MongoDbConstants.DATABASE, "otherDB");
        headers.put(MongoDbConstants.COLLECTION, "otherCollection");
        // Object result =
        template.requestBodyAndHeaders("direct:noDynamicity", body, headers);

        Document b = testCollection.find(eq(MONGO_ID, "testInsertDynamicityDisabled")).first();
        assertNotNull("No record with 'testInsertDynamicityDisabled' _id", b);

        body = "{\"_id\": \"testInsertDynamicityDisabledExplicitly\", \"a\" : \"1\"}";
        // result =
        template.requestBodyAndHeaders("direct:noDynamicityExplicit", body, headers);

        b = testCollection.find(eq(MONGO_ID, "testInsertDynamicityDisabledExplicitly")).first();
        assertNotNull("No record with 'testInsertDynamicityDisabledExplicitly' _id", b);

        assertFalse("The otherDB database should not exist", StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals));

    }

    @Test
    public void testInsertDynamicityEnabledDBOnly() {
        assertEquals(0, testCollection.count());
        mongo.getDatabase("otherDB").drop();
        db.getCollection("otherCollection").drop();
        assertFalse("The otherDB database should not exist", StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals));

        String body = "{\"_id\": \"testInsertDynamicityEnabledDBOnly\", \"a\" : \"1\"}";
        Map<String, Object> headers = new HashMap<>();
        headers.put(MongoDbConstants.DATABASE, "otherDB");
        // Object result =
        template.requestBodyAndHeaders("direct:dynamicityEnabled", body, headers);

        MongoCollection<Document> localDynamicCollection = mongo.getDatabase("otherDB").getCollection(testCollection.getNamespace().getCollectionName(), Document.class);

        Document b = localDynamicCollection.find(eq(MONGO_ID, "testInsertDynamicityEnabledDBOnly")).first();
        assertNotNull("No record with 'testInsertDynamicityEnabledDBOnly' _id", b);

        b = testCollection.find(eq(MONGO_ID, "testInsertDynamicityEnabledDBOnly")).first();
        assertNull("There is a record with 'testInsertDynamicityEnabledDBOnly' _id in the test collection", b);

        assertTrue("The otherDB database should exist", StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals));

    }

    @Test
    public void testInsertDynamicityEnabledCollectionOnly() {
        assertEquals(0, testCollection.count());
        mongo.getDatabase("otherDB").drop();
        db.getCollection("otherCollection").drop();
        assertFalse("The otherDB database should not exist", StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals));

        String body = "{\"_id\": \"testInsertDynamicityEnabledCollectionOnly\", \"a\" : \"1\"}";
        Map<String, Object> headers = new HashMap<>();
        headers.put(MongoDbConstants.COLLECTION, "otherCollection");
        // Object result =
        template.requestBodyAndHeaders("direct:dynamicityEnabled", body, headers);

        MongoCollection<Document> loaclDynamicCollection = db.getCollection("otherCollection", Document.class);

        Document b = loaclDynamicCollection.find(eq(MONGO_ID, "testInsertDynamicityEnabledCollectionOnly")).first();
        assertNotNull("No record with 'testInsertDynamicityEnabledCollectionOnly' _id", b);

        b = testCollection.find(eq(MONGO_ID, "testInsertDynamicityEnabledDBOnly")).first();
        assertNull("There is a record with 'testInsertDynamicityEnabledCollectionOnly' _id in the test collection", b);

        assertFalse("The otherDB database should not exist", StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals));
    }

    @Test
    public void testInsertDynamicityEnabledDBAndCollection() {
        assertEquals(0, testCollection.count());
        mongo.getDatabase("otherDB").drop();
        db.getCollection("otherCollection").drop();
        assertFalse("The otherDB database should not exist", StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals));

        String body = "{\"_id\": \"testInsertDynamicityEnabledDBAndCollection\", \"a\" : \"1\"}";
        Map<String, Object> headers = new HashMap<>();
        headers.put(MongoDbConstants.DATABASE, "otherDB");
        headers.put(MongoDbConstants.COLLECTION, "otherCollection");
        // Object result =
        template.requestBodyAndHeaders("direct:dynamicityEnabled", body, headers);

        MongoCollection<Document> loaclDynamicCollection = mongo.getDatabase("otherDB").getCollection("otherCollection", Document.class);

        Document b = loaclDynamicCollection.find(eq(MONGO_ID, "testInsertDynamicityEnabledDBAndCollection")).first();
        assertNotNull("No record with 'testInsertDynamicityEnabledDBAndCollection' _id", b);

        b = testCollection.find(eq(MONGO_ID, "testInsertDynamicityEnabledDBOnly")).first();
        assertNull("There is a record with 'testInsertDynamicityEnabledDBAndCollection' _id in the test collection", b);

        assertTrue("The otherDB database should exist", StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                from("direct:noDynamicity").to("mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert");
                from("direct:noDynamicityExplicit").to("mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert&dynamicity=false");
                from("direct:dynamicityEnabled").to("mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert&dynamicity=true");

            }
        };
    }
}
