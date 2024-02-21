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

import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

import com.mongodb.client.MongoCollection;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mongodb.MongoDbConstants;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.bson.Document;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.mongodb.client.model.Filters.eq;
import static org.apache.camel.component.mongodb.MongoDbConstants.MONGO_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MongoDbDynamicityIT extends AbstractMongoDbITSupport implements ConfigurableRoute {

    @BeforeEach
    void checkDocuments() {
        Assumptions.assumeTrue(0 == testCollection.countDocuments(), "The collection should have no documents");
    }

    @Test
    public void testInsertDynamicityDisabled() {
        mongo.getDatabase("otherDB").drop();
        db.getCollection("otherCollection").drop();
        assertFalse(StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals),
                "The otherDB database should not exist");

        String body = "{\"_id\": \"testInsertDynamicityDisabled\", \"a\" : \"1\"}";
        Map<String, Object> headers = new HashMap<>();
        headers.put(MongoDbConstants.DATABASE, "otherDB");
        headers.put(MongoDbConstants.COLLECTION, "otherCollection");
        // Object result =
        template.requestBodyAndHeaders("direct:noDynamicity", body, headers);

        Document b = testCollection.find(eq(MONGO_ID, "testInsertDynamicityDisabled")).first();
        assertNotNull(b, "No record with 'testInsertDynamicityDisabled' _id");

        body = "{\"_id\": \"testInsertDynamicityDisabledExplicitly\", \"a\" : \"1\"}";
        // result =
        template.requestBodyAndHeaders("direct:noDynamicityExplicit", body, headers);

        b = testCollection.find(eq(MONGO_ID, "testInsertDynamicityDisabledExplicitly")).first();
        assertNotNull(b, "No record with 'testInsertDynamicityDisabledExplicitly' _id");

        assertFalse(StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals),
                "The otherDB database should not exist");

    }

    @Test
    public void testInsertDynamicityEnabledDBOnly() {
        mongo.getDatabase("otherDB").drop();
        db.getCollection("otherCollection").drop();
        assertFalse(StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals),
                "The otherDB database should not exist");

        String body = "{\"_id\": \"testInsertDynamicityEnabledDBOnly\", \"a\" : \"1\"}";
        Map<String, Object> headers = new HashMap<>();
        headers.put(MongoDbConstants.DATABASE, "otherDB");
        // Object result =
        template.requestBodyAndHeaders("direct:dynamicityEnabled", body, headers);

        MongoCollection<Document> localDynamicCollection
                = mongo.getDatabase("otherDB").getCollection(testCollection.getNamespace().getCollectionName(), Document.class);

        Document b = localDynamicCollection.find(eq(MONGO_ID, "testInsertDynamicityEnabledDBOnly")).first();
        assertNotNull(b, "No record with 'testInsertDynamicityEnabledDBOnly' _id");

        b = testCollection.find(eq(MONGO_ID, "testInsertDynamicityEnabledDBOnly")).first();
        assertNull(b, "There is a record with 'testInsertDynamicityEnabledDBOnly' _id in the test collection");

        assertTrue(StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals),
                "The otherDB database should exist");

    }

    @Test
    public void testInsertDynamicityEnabledCollectionOnly() {
        mongo.getDatabase("otherDB").drop();
        db.getCollection("otherCollection").drop();
        assertFalse(StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals),
                "The otherDB database should not exist");

        String body = "{\"_id\": \"testInsertDynamicityEnabledCollectionOnly\", \"a\" : \"1\"}";
        Map<String, Object> headers = new HashMap<>();
        headers.put(MongoDbConstants.COLLECTION, "otherCollection");
        // Object result =
        template.requestBodyAndHeaders("direct:dynamicityEnabled", body, headers);

        MongoCollection<Document> loaclDynamicCollection = db.getCollection("otherCollection", Document.class);

        Document b = loaclDynamicCollection.find(eq(MONGO_ID, "testInsertDynamicityEnabledCollectionOnly")).first();
        assertNotNull(b, "No record with 'testInsertDynamicityEnabledCollectionOnly' _id");

        b = testCollection.find(eq(MONGO_ID, "testInsertDynamicityEnabledDBOnly")).first();
        assertNull(b, "There is a record with 'testInsertDynamicityEnabledCollectionOnly' _id in the test collection");

        assertFalse(StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals),
                "The otherDB database should not exist");
    }

    @Test
    public void testInsertDynamicityEnabledDBAndCollection() {
        assertEquals(0, testCollection.countDocuments());
        mongo.getDatabase("otherDB").drop();
        db.getCollection("otherCollection").drop();
        assertFalse(StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals),
                "The otherDB database should not exist");

        String body = "{\"_id\": \"testInsertDynamicityEnabledDBAndCollection\", \"a\" : \"1\"}";
        Map<String, Object> headers = new HashMap<>();
        headers.put(MongoDbConstants.DATABASE, "otherDB");
        headers.put(MongoDbConstants.COLLECTION, "otherCollection");
        // Object result =
        template.requestBodyAndHeaders("direct:dynamicityEnabled", body, headers);

        MongoCollection<Document> loaclDynamicCollection
                = mongo.getDatabase("otherDB").getCollection("otherCollection", Document.class);

        Document b = loaclDynamicCollection.find(eq(MONGO_ID, "testInsertDynamicityEnabledDBAndCollection")).first();
        assertNotNull(b, "No record with 'testInsertDynamicityEnabledDBAndCollection' _id");

        b = testCollection.find(eq(MONGO_ID, "testInsertDynamicityEnabledDBOnly")).first();
        assertNull(b, "There is a record with 'testInsertDynamicityEnabledDBAndCollection' _id in the test collection");

        assertTrue(StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals),
                "The otherDB database should exist");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                from("direct:noDynamicity")
                        .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert");
                from("direct:noDynamicityExplicit").to(
                        "mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert&dynamicity=false");
                from("direct:dynamicityEnabled").to(
                        "mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert&dynamicity=true");

            }
        };
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }
}
