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
package org.apache.camel.component.arangodb;

import java.util.Map;

import com.arangodb.ArangoCollection;
import com.arangodb.ArangoCursor;
import com.arangodb.util.MapBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.arangodb.ArangoDbConstants.*;
import static org.junit.jupiter.api.Assertions.*;

public class ArangoCollectionQueryTest extends AbstractArangoDbTest {

    private ArangoCollection collection;

    @BeforeEach
    public void beforeEach() {
        arangoDatabase.createCollection(COLLECTION_NAME);
        collection = arangoDatabase.collection(COLLECTION_NAME);
    }

    @AfterEach
    public void afterEach() {
        collection.drop();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:query")
                        .to("arangodb:{{arangodb.testDb}}?operation=AQL_QUERY");
            }
        };
    }

    @Test
    public void findByParameters() {

        TestDocumentEntity test = new TestDocumentEntity("bar", 10);
        collection.insertDocument(test);

        TestDocumentEntity test2 = new TestDocumentEntity("bar");
        collection.insertDocument(test2);

        TestDocumentEntity test3 = new TestDocumentEntity();
        test3.setNumber(10);
        collection.insertDocument(test3);

        String query = "FOR t IN " + COLLECTION_NAME + " FILTER t.foo == @foo AND t.number == @number RETURN t";
        Map<String, Object> bindVars = new MapBuilder().put("foo", test.getFoo())
                .put("number", test.getNumber())
                .get();

        Exchange result = template.request("direct:query", exchange -> {
            exchange.getMessage().setHeader(AQL_QUERY, query);
            exchange.getMessage().setHeader(AQL_QUERY_BIND_PARAMETERS, bindVars);
            exchange.getMessage().setHeader(AQL_QUERY_OPTIONS, null);
            exchange.getMessage().setHeader(RESULT_CLASS_TYPE, TestDocumentEntity.class);
        });

        assertTrue(result.getMessage().getBody() instanceof ArangoCursor);
        ArangoCursor<TestDocumentEntity> cursor = (ArangoCursor<TestDocumentEntity>) result.getMessage().getBody();

        assertTrue(cursor.hasNext());
        cursor.forEachRemaining(doc -> {
            assertNotNull(doc);
            assertNotNull(doc.getKey());
            assertNotNull(doc.getRev());
            assertEquals(test.getFoo(), doc.getFoo());
            assertEquals(test.getNumber(), doc.getNumber());
        });
    }

}
