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

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.IOConverter;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static com.mongodb.client.model.Filters.eq;
import static org.apache.camel.component.mongodb.MongoDbConstants.MONGO_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MongoDbConversionsTest extends AbstractMongoDbTest {

    @Test
    public void testInsertMap() throws InterruptedException {
        assertEquals(0, testCollection.countDocuments());

        Map<String, Object> m1 = new HashMap<>();
        Map<String, String> m1Nested = new HashMap<>();

        m1Nested.put("nested1", "nestedValue1");
        m1Nested.put("nested2", "nestedValue2");

        m1.put("field1", "value1");
        m1.put("field2", "value2");
        m1.put("nestedField", m1Nested);
        m1.put(MONGO_ID, "testInsertMap");

        // Object result =
        template.requestBody("direct:insertMap", m1);
        Document b = testCollection.find(eq(MONGO_ID, "testInsertMap")).first();
        assertNotNull(b, "No record with 'testInsertMap' _id");
    }

    @Test
    public void testInsertPojo() {
        assertEquals(0, testCollection.countDocuments());
        // Object result =
        template.requestBody("direct:insertPojo", new MyPojoTest());
        Document b = testCollection.find(eq(MONGO_ID, "testInsertPojo")).first();
        assertNotNull(b, "No record with 'testInsertPojo' _id");
    }

    @Test
    public void testInsertJsonString() {
        assertEquals(0, testCollection.countDocuments());
        // Object result =
        template.requestBody("direct:insertJsonString", "{\"fruits\": [\"apple\", \"banana\", \"papaya\"], \"veggie\": \"broccoli\", \"_id\": \"testInsertJsonString\"}");
        // assertTrue(result instanceof WriteResult);
        Document b = testCollection.find(eq(MONGO_ID, "testInsertJsonString")).first();
        assertNotNull(b, "No record with 'testInsertJsonString' _id");
    }

    @Test
    public void testInsertJsonInputStream() throws Exception {
        assertEquals(0, testCollection.countDocuments());
        // Object result =
        template.requestBody("direct:insertJsonString",
                             IOConverter.toInputStream("{\"fruits\": [\"apple\", \"banana\"], \"veggie\": \"broccoli\", \"_id\": \"testInsertJsonString\"}\n", null));
        Document b = testCollection.find(eq(MONGO_ID, "testInsertJsonString")).first();
        assertNotNull(b, "No record with 'testInsertJsonString' _id");
    }

    @Test
    public void testInsertJsonInputStreamWithSpaces() throws Exception {
        assertEquals(0, testCollection.countDocuments());
        template.requestBody("direct:insertJsonString", IOConverter.toInputStream("    {\"test\": [\"test\"], \"_id\": \"testInsertJsonStringWithSpaces\"}\n", null));
        Document b = testCollection.find(eq(MONGO_ID, "testInsertJsonStringWithSpaces")).first();
        assertNotNull(b, "No record with 'testInsertJsonStringWithSpaces' _id");
    }

    @Test
    public void testInsertBsonInputStream() {
        assertEquals(0, testCollection.countDocuments());

        Document document = new Document(MONGO_ID, "testInsertBsonString");

        // Object result =
        template.requestBody("direct:insertJsonString", new ByteArrayInputStream(document.toJson().getBytes()));
        Document b = testCollection.find(eq(MONGO_ID, "testInsertBsonString")).first();
        assertNotNull(b, "No record with 'testInsertBsonString' _id");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                from("direct:insertMap").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert");
                from("direct:insertPojo").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert");
                from("direct:insertJsonString").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert");
                from("direct:insertJsonStringWriteResultInString").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert")
                    .convertBodyTo(String.class);

            }
        };
    }

    @SuppressWarnings("unused")
    private class MyPojoTest {
        public int number = 123;
        public String text = "hello";
        public String[] array = {"daVinci", "copernico", "einstein"};
        // CHECKSTYLE:OFF
        public String _id = "testInsertPojo";
        // CHECKSTYLE:ON
    }

}
