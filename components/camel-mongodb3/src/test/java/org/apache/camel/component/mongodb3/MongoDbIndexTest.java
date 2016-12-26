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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import com.mongodb.WriteResult;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import org.apache.camel.builder.RouteBuilder;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.Ignore;
import org.junit.Test;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Indexes.ascending;
import static com.mongodb.client.model.Indexes.descending;
import static org.apache.camel.component.mongodb3.MongoDbConstants.MONGO_ID;

public class MongoDbIndexTest extends AbstractMongoDbTest {

    @Test
    public void testInsertDynamicityEnabledDBAndCollectionAndIndex() {
        assertEquals(0, testCollection.count());
        mongo.getDatabase("otherDB").drop();
        db.getCollection("otherCollection").drop();
        assertFalse("The otherDB database should not exist", StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals));

        String body = "{\"_id\": \"testInsertDynamicityEnabledDBAndCollection\", \"a\" : 1, \"b\" : 2}";
        Map<String, Object> headers = new HashMap<>();
        headers.put(MongoDbConstants.DATABASE, "otherDB");
        headers.put(MongoDbConstants.COLLECTION, "otherCollection");

        List<Document> objIndex = new ArrayList<>();
        Document index1 = new Document();
        index1.put("a", 1);
        Document index2 = new Document();
        index2.put("b", -1);
        objIndex.add(index1);
        objIndex.add(index2);

        headers.put(MongoDbConstants.COLLECTION_INDEX, objIndex);

        Object result = template.requestBodyAndHeaders("direct:dynamicityEnabled", body, headers);

        assertEquals("Response isn't of type WriteResult", Document.class, result.getClass());

        MongoCollection<Document> localDynamicCollection = mongo.getDatabase("otherDB").getCollection("otherCollection", Document.class);

        ListIndexesIterable<Document> indexInfos = localDynamicCollection.listIndexes(Document.class);

        MongoCursor<Document> iterator = indexInfos.iterator();
        iterator.next();
        Document key1 = iterator.next().get("key", Document.class);
        Document key2 = iterator.next().get("key", Document.class);

        assertTrue("No index on the field a", key1.containsKey("a") && 1 == key1.getInteger("a"));
        assertTrue("No index on the field b", key2.containsKey("b") && -1 == key2.getInteger("b"));

        Document b = localDynamicCollection.find(new Document(MONGO_ID, "testInsertDynamicityEnabledDBAndCollection")).first();
        assertNotNull("No record with 'testInsertDynamicityEnabledDBAndCollection' _id", b);

        b = testCollection.find(new Document(MONGO_ID, "testInsertDynamicityEnabledDBOnly")).first();
        assertNull("There is a record with 'testInsertDynamicityEnabledDBAndCollection' _id in the test collection", b);

        assertTrue("The otherDB database should exist", StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals));
    }

    @Test
    public void testInsertDynamicityEnabledCollectionAndIndex() {
        assertEquals(0, testCollection.count());
        mongo.getDatabase("otherDB").drop();
        db.getCollection("otherCollection").drop();
        assertFalse("The otherDB database should not exist", StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals));

        String body = "{\"_id\": \"testInsertDynamicityEnabledCollectionAndIndex\", \"a\" : 1, \"b\" : 2}";
        Map<String, Object> headers = new HashMap<>();
        headers.put(MongoDbConstants.COLLECTION, "otherCollection");

        List<Bson> objIndex = Arrays.asList(ascending("a"), descending("b"));
        headers.put(MongoDbConstants.COLLECTION_INDEX, objIndex);

        Object result = template.requestBodyAndHeaders("direct:dynamicityEnabled", body, headers);

        assertEquals("Response isn't of type WriteResult", Document.class, result.getClass());

        MongoCollection<Document> localDynamicCollection = db.getCollection("otherCollection", Document.class);

        MongoCursor<Document> indexInfos = localDynamicCollection.listIndexes(Document.class).iterator();

        indexInfos.next();
        Document key1 = indexInfos.next().get("key", Document.class);
        Document key2 = indexInfos.next().get("key", Document.class);

        assertTrue("No index on the field a", key1.containsKey("a") && 1 == key1.getInteger("a"));
        assertTrue("No index on the field b", key2.containsKey("b") && -1 == key2.getInteger("b"));

        Document b = localDynamicCollection.find(eq(MONGO_ID, "testInsertDynamicityEnabledCollectionAndIndex")).first();
        assertNotNull("No record with 'testInsertDynamicityEnabledCollectionAndIndex' _id", b);

        b = testCollection.find(eq(MONGO_ID, "testInsertDynamicityEnabledDBOnly")).first();
        assertNull("There is a record with 'testInsertDynamicityEnabledDBAndCollection' _id in the test collection", b);

        assertFalse("The otherDB database should not exist", mongo.getUsedDatabases().contains("otherDB"));
    }

    @Test
    public void testInsertDynamicityEnabledCollectionOnlyAndURIIndex() {
        assertEquals(0, testCollection.count());
        mongo.getDatabase("otherDB").drop();
        db.getCollection("otherCollection").drop();
        assertFalse("The otherDB database should not exist", StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals));

        String body = "{\"_id\": \"testInsertDynamicityEnabledCollectionOnlyAndURIIndex\", \"a\" : 1, \"b\" : 2}";
        Map<String, Object> headers = new HashMap<>();
        headers.put(MongoDbConstants.COLLECTION, "otherCollection");

        Object result = template.requestBodyAndHeaders("direct:dynamicityEnabledWithIndexUri", body, headers);

        assertEquals("Response isn't of type WriteResult", Document.class, result.getClass());

        MongoCollection<Document> localDynamicCollection = db.getCollection("otherCollection", Document.class);

        MongoCursor<Document> indexInfos = localDynamicCollection.listIndexes().iterator();

        Document key1 = indexInfos.next().get("key", Document.class);

        assertFalse("No index on the field a", key1.containsKey("a") && "-1".equals(key1.getString("a")));

        Document b = localDynamicCollection.find(eq(MONGO_ID, "testInsertDynamicityEnabledCollectionOnlyAndURIIndex")).first();
        assertNotNull("No record with 'testInsertDynamicityEnabledCollectionOnlyAndURIIndex' _id", b);

        b = testCollection.find(eq(MONGO_ID, "testInsertDynamicityEnabledCollectionOnlyAndURIIndex")).first();
        assertNull("There is a record with 'testInsertDynamicityEnabledCollectionOnlyAndURIIndex' _id in the test collection", b);

        assertFalse("The otherDB database should not exist", StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals));
    }

    @Ignore
    @Test
    public void testInsertAutoCreateCollectionAndURIIndex() {
        assertEquals(0, testCollection.count());
        db.getCollection("otherCollection").deleteOne(new Document());

        String body = "{\"_id\": \"testInsertAutoCreateCollectionAndURIIndex\", \"a\" : 1, \"b\" : 2}";
        Map<String, Object> headers = new HashMap<>();

        Object result = template.requestBodyAndHeaders("direct:dynamicityDisabled", body, headers);
        assertEquals("Response isn't of type WriteResult", WriteResult.class, result.getClass());

        MongoCollection<Document> collection = db.getCollection("otherCollection", Document.class);
        MongoCursor<Document> indexInfos = collection.listIndexes().iterator();

        Document key1 = indexInfos.next().get("key", Document.class);
        Document key2 = indexInfos.next().get("key", Document.class);

        assertTrue("No index on the field b", key1.containsKey("b") && "-1".equals(key1.getString("b")));
        assertTrue("No index on the field a", key2.containsKey("a") && "1".equals(key2.getString("a")));

        Document b = collection.find(eq(MONGO_ID, "testInsertAutoCreateCollectionAndURIIndex")).first();
        assertNotNull("No record with 'testInsertAutoCreateCollectionAndURIIndex' _id", b);

        b = testCollection.find(eq(MONGO_ID, "testInsertAutoCreateCollectionAndURIIndex")).first();
        assertNull("There is a record with 'testInsertAutoCreateCollectionAndURIIndex' _id in the test collection", b);

        assertFalse("The otherDB database should not exist", StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false).anyMatch("otherDB"::equals));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:dynamicityEnabled").to("mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert&dynamicity=true");
                from("direct:dynamicityEnabledWithIndexUri")
                    .to("mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&collectionIndex={\"a\":1}&operation=insert&dynamicity=true");
                from("direct:dynamicityDisabled")
                    .to("mongodb3:myDb?database={{mongodb.testDb}}&collection=otherCollection&collectionIndex={\"a\":1,\"b\":-1}&operation=insert&dynamicity=false");
            }
        };
    }
}
