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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import org.apache.camel.builder.RouteBuilder;
import org.bson.Document;
import org.junit.Ignore;
import org.junit.Test;

public class MongoDbIndexTest extends AbstractMongoDbTest {

    @Test
    public void testInsertDynamicityEnabledDBAndCollectionAndIndex() {
        assertEquals(0, testCollection.count());
        mongo.getDatabase("otherDB").drop();
        db.getCollection("otherCollection").drop();
        assertFalse("The otherDB database should not exist",
                StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false)
                        .anyMatch("otherDB"::equals));

        String body = "{\"_id\": \"testInsertDynamicityEnabledDBAndCollection\", \"a\" : 1, \"b\" : 2}";
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(MongoDbConstants.DATABASE, "otherDB");
        headers.put(MongoDbConstants.COLLECTION, "otherCollection");

        List<DBObject> objIndex = new ArrayList<DBObject>();
        DBObject index1 = new BasicDBObject();
        index1.put("a", 1);
        DBObject index2 = new BasicDBObject();
        index2.put("b", -1);
        objIndex.add(index1);
        objIndex.add(index2);

        headers.put(MongoDbConstants.COLLECTION_INDEX, objIndex);

        Object result = template.requestBodyAndHeaders("direct:dynamicityEnabled", body, headers);

        assertEquals("Response isn't of type WriteResult", BasicDBObject.class, result.getClass());

        MongoCollection<BasicDBObject> dynamicCollection = mongo.getDatabase("otherDB").getCollection("otherCollection", BasicDBObject.class);

        ListIndexesIterable<DBObject> indexInfos = dynamicCollection.listIndexes(DBObject.class);

        MongoCursor<DBObject> iterator = indexInfos.iterator();
        iterator.next();
        BasicDBObject key1 = (BasicDBObject) iterator.next().get("key");
        BasicDBObject key2 = (BasicDBObject) iterator.next().get("key");

        assertTrue("No index on the field a", key1.containsField("a") && "1".equals(key1.getString("a")));
        assertTrue("No index on the field b", key2.containsField("b") && "-1".equals(key2.getString("b")));

        DBObject b = dynamicCollection.find(new BasicDBObject("_id", "testInsertDynamicityEnabledDBAndCollection")).first();
        assertNotNull("No record with 'testInsertDynamicityEnabledDBAndCollection' _id", b);

        b = testCollection.find(new BasicDBObject("_id", "testInsertDynamicityEnabledDBOnly")).first();
        assertNull("There is a record with 'testInsertDynamicityEnabledDBAndCollection' _id in the test collection", b);

        assertTrue("The otherDB database should exist",
                StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false)
                        .anyMatch("otherDB"::equals));
    }

    @Test
    public void testInsertDynamicityEnabledCollectionAndIndex() {
        assertEquals(0, testCollection.count());
        mongo.getDatabase("otherDB").drop();
        db.getCollection("otherCollection").drop();
        assertFalse("The otherDB database should not exist",
                StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false)
                        .anyMatch("otherDB"::equals));

        String body = "{\"_id\": \"testInsertDynamicityEnabledCollectionAndIndex\", \"a\" : 1, \"b\" : 2}";
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(MongoDbConstants.COLLECTION, "otherCollection");

        List<DBObject> objIndex = new ArrayList<DBObject>();
        DBObject index1 = new BasicDBObject();
        index1.put("a", 1);
        DBObject index2 = new BasicDBObject();
        index2.put("b", -1);
        objIndex.add(index1);
        objIndex.add(index2);
        headers.put(MongoDbConstants.COLLECTION_INDEX, objIndex);

        Object result = template.requestBodyAndHeaders("direct:dynamicityEnabled", body, headers);

        assertEquals("Response isn't of type WriteResult", BasicDBObject.class, result.getClass());

        MongoCollection<BasicDBObject> dynamicCollection = db.getCollection("otherCollection", BasicDBObject.class);

        MongoCursor<BasicDBObject> indexInfos = dynamicCollection.listIndexes(BasicDBObject.class).iterator();

        indexInfos.next();
        BasicDBObject key1 = (BasicDBObject) indexInfos.next().get("key");
        BasicDBObject key2 = (BasicDBObject) indexInfos.next().get("key");

        assertTrue("No index on the field a", key1.containsField("a") && "1".equals(key1.getString("a")));
        assertTrue("No index on the field b", key2.containsField("b") && "-1".equals(key2.getString("b")));

        DBObject b = dynamicCollection.find(new BasicDBObject("_id", "testInsertDynamicityEnabledCollectionAndIndex")).first();
        assertNotNull("No record with 'testInsertDynamicityEnabledCollectionAndIndex' _id", b);

        b = testCollection.find(new BasicDBObject("_id", "testInsertDynamicityEnabledDBOnly")).first();
        assertNull("There is a record with 'testInsertDynamicityEnabledDBAndCollection' _id in the test collection", b);

        assertFalse("The otherDB database should not exist", mongo.getUsedDatabases().contains("otherDB"));
    }

    @Test
    public void testInsertDynamicityEnabledCollectionOnlyAndURIIndex() {
        assertEquals(0, testCollection.count());
        mongo.getDatabase("otherDB").drop();
        db.getCollection("otherCollection").drop();
        assertFalse("The otherDB database should not exist",
                StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false)
                        .anyMatch("otherDB"::equals));

        String body = "{\"_id\": \"testInsertDynamicityEnabledCollectionOnlyAndURIIndex\", \"a\" : 1, \"b\" : 2}";
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(MongoDbConstants.COLLECTION, "otherCollection");

        Object result = template.requestBodyAndHeaders("direct:dynamicityEnabledWithIndexUri", body, headers);

        assertEquals("Response isn't of type WriteResult", BasicDBObject.class, result.getClass());

        MongoCollection<BasicDBObject> dynamicCollection = db.getCollection("otherCollection", BasicDBObject.class);

        MongoCursor<Document> indexInfos = dynamicCollection.listIndexes().iterator();

        Document key1 = (Document) indexInfos.next().get("key");

        assertFalse("No index on the field a", key1.containsKey("a") && "-1".equals(key1.getString("a")));

        DBObject b = dynamicCollection.find(new BasicDBObject("_id", "testInsertDynamicityEnabledCollectionOnlyAndURIIndex")).first();
        assertNotNull("No record with 'testInsertDynamicityEnabledCollectionOnlyAndURIIndex' _id", b);

        b = testCollection.find(new BasicDBObject("_id", "testInsertDynamicityEnabledCollectionOnlyAndURIIndex")).first();
        assertNull("There is a record with 'testInsertDynamicityEnabledCollectionOnlyAndURIIndex' _id in the test collection", b);

        assertFalse("The otherDB database should not exist",
                StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false)
                        .anyMatch("otherDB"::equals));
    }

    @Ignore
    @Test
    public void testInsertAutoCreateCollectionAndURIIndex() {
        assertEquals(0, testCollection.count());
        db.getCollection("otherCollection").deleteOne(new BasicDBObject());

        String body = "{\"_id\": \"testInsertAutoCreateCollectionAndURIIndex\", \"a\" : 1, \"b\" : 2}";
        Map<String, Object> headers = new HashMap<String, Object>();

        Object result = template.requestBodyAndHeaders("direct:dynamicityDisabled", body, headers);
        assertEquals("Response isn't of type WriteResult", WriteResult.class, result.getClass());

        MongoCollection<BasicDBObject> collection = db.getCollection("otherCollection", BasicDBObject.class);
        MongoCursor<Document> indexInfos = collection.listIndexes().iterator();

        BasicDBObject key1 = (BasicDBObject) indexInfos.next().get("key");
        BasicDBObject key2 = (BasicDBObject) indexInfos.next().get("key");

        assertTrue("No index on the field b", key1.containsField("b") && "-1".equals(key1.getString("b")));
        assertTrue("No index on the field a", key2.containsField("a") && "1".equals(key2.getString("a")));

        DBObject b = collection.find(new BasicDBObject("_id", "testInsertAutoCreateCollectionAndURIIndex")).first();
        assertNotNull("No record with 'testInsertAutoCreateCollectionAndURIIndex' _id", b);

        b = testCollection.find(new BasicDBObject("_id", "testInsertAutoCreateCollectionAndURIIndex")).first();
        assertNull("There is a record with 'testInsertAutoCreateCollectionAndURIIndex' _id in the test collection", b);

        assertFalse("The otherDB database should not exist",
                StreamSupport.stream(mongo.listDatabaseNames().spliterator(), false)
                        .anyMatch("otherDB"::equals));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:dynamicityEnabled")
                        .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert&dynamicity=true&writeConcern=SAFE");
                from("direct:dynamicityEnabledWithIndexUri")
                        .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&collectionIndex={\"a\":1}&operation=insert&dynamicity=true&writeConcern=SAFE");
                from("direct:dynamicityDisabled")
                        .to("mongodb:myDb?database={{mongodb.testDb}}&collection=otherCollection&collectionIndex={\"a\":1,\"b\":-1}&operation=insert&dynamicity=false&writeConcern=SAFE");
            }
        };
    }
}
