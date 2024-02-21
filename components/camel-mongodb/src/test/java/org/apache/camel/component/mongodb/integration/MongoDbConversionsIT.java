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

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mongodb.converters.MongoDbBasicConverters;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.mongodb.client.model.Filters.eq;
import static org.apache.camel.component.mongodb.MongoDbConstants.MONGO_ID;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MongoDbConversionsIT extends AbstractMongoDbITSupport implements ConfigurableRoute {

    @BeforeEach
    void checkDocuments() {
        Assumptions.assumeTrue(0 == testCollection.countDocuments(), "The collection should have no documents");
    }

    @Test
    public void testInsertMap() {
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
        // Object result =
        template.requestBody("direct:insertPojo", new MyPojoTest());
        Document b = testCollection.find(eq(MONGO_ID, "testInsertPojo")).first();
        assertNotNull(b, "No record with 'testInsertPojo' _id");
    }

    @Test
    public void testInsertJsonString() {
        // Object result =
        template.requestBody("direct:insertJsonString",
                "{\"fruits\": [\"apple\", \"banana\", \"papaya\"], \"veggie\": \"broccoli\", \"_id\": \"testInsertJsonString\"}");
        // assertTrue(result instanceof WriteResult);
        Document b = testCollection.find(eq(MONGO_ID, "testInsertJsonString")).first();
        assertNotNull(b, "No record with 'testInsertJsonString' _id");
    }

    @Test
    public void testInsertJsonInputStream() throws Exception {
        // Object result =
        template.requestBody("direct:insertJsonString",
                IOConverter.toInputStream(
                        "{\"fruits\": [\"apple\", \"banana\"], \"veggie\": \"broccoli\", \"_id\": \"testInsertJsonString\"}\n",
                        null));
        Document b = testCollection.find(eq(MONGO_ID, "testInsertJsonString")).first();
        assertNotNull(b, "No record with 'testInsertJsonString' _id");
    }

    @Test
    public void testInsertJsonInputStreamWithSpaces() throws Exception {
        template.requestBody("direct:insertJsonString",
                IOConverter.toInputStream("    {\"test\": [\"test\"], \"_id\": \"testInsertJsonStringWithSpaces\"}\n", null));
        Document b = testCollection.find(eq(MONGO_ID, "testInsertJsonStringWithSpaces")).first();
        assertNotNull(b, "No record with 'testInsertJsonStringWithSpaces' _id");
    }

    @Test
    public void testInsertBsonInputStream() {
        Document document = new Document(MONGO_ID, "testInsertBsonString");

        // Object result =
        template.requestBody("direct:insertJsonString", new ByteArrayInputStream(document.toJson().getBytes()));
        Document b = testCollection.find(eq(MONGO_ID, "testInsertBsonString")).first();
        assertNotNull(b, "No record with 'testInsertBsonString' _id");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                from("direct:insertMap")
                        .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert");
                from("direct:insertPojo")
                        .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert");
                from("direct:insertJsonString")
                        .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert");
                from("direct:insertJsonStringWriteResultInString")
                        .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert")
                        .convertBodyTo(String.class);

            }
        };
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }

    @SuppressWarnings("unused")
    private class MyPojoTest {
        public int number = 123;
        public String text = "hello";
        public String[] array = { "daVinci", "copernico", "einstein" };
        public String _id = "testInsertPojo";
    }

    @Test
    public void shouldConvertJsonStringListToBSONList() {
        String jsonListArray = "[{\"key\":\"value1\"}, {\"key\":\"value2\"}]";
        List<Bson> bsonList = MongoDbBasicConverters.fromStringToList(jsonListArray);
        assertNotNull(bsonList);
        assertEquals(2, bsonList.size());

        String jsonEmptyArray = "[]";
        bsonList = MongoDbBasicConverters.fromStringToList(jsonEmptyArray);
        assertNotNull(bsonList);
        assertEquals(0, bsonList.size());
    }

    @Test
    public void shouldNotConvertJsonStringListToBSONList() {
        String jsonSingleValue = "{\"key\":\"value1\"}";
        List<Bson> bsonList = MongoDbBasicConverters.fromStringToList(jsonSingleValue);
        assertNull(bsonList);
    }

}
