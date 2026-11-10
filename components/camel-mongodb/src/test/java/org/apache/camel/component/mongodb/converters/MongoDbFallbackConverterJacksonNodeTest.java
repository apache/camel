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
package org.apache.camel.component.mongodb.converters;

import com.mongodb.DBObject;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Jackson node body of either line converts to its Document or DBObject, as a route that unmarshals with
 * camel-jackson hands a Jackson 2 node to the producer.
 */
public class MongoDbFallbackConverterJacksonNodeTest {

    private static final String JSON = "{\"name\": \"x\", \"n\": 1, \"d\": 1.5, \"nested\": {\"k\": true}, \"list\": [1, 2]}";

    private CamelContext context;
    private Exchange exchange;

    @BeforeEach
    public void setup() {
        context = new DefaultCamelContext();
        context.start();
        exchange = new DefaultExchange(context);
    }

    @AfterEach
    public void teardown() {
        context.stop();
    }

    @Test
    public void jackson2NodeToDocument() throws Exception {
        Object node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(JSON);
        assertEquals(Document.parse(JSON), context.getTypeConverter().convertTo(Document.class, exchange, node));
    }

    @Test
    public void jackson3NodeToDocument() {
        Object node = JsonMapper.builder().build().readTree(JSON);
        assertEquals(Document.parse(JSON), context.getTypeConverter().convertTo(Document.class, exchange, node));
    }

    @Test
    public void jackson2NodeToDBObject() throws Exception {
        Object node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(JSON);
        DBObject dbObject = context.getTypeConverter().convertTo(DBObject.class, exchange, node);
        assertEquals("x", dbObject.get("name"));
        assertEquals(1, dbObject.get("n"));
        assertEquals(Document.parse(JSON).toJson(), new Document(dbObject.toMap()).toJson());
    }
}
