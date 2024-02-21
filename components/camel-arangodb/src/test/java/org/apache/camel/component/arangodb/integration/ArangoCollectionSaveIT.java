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
package org.apache.camel.component.arangodb.integration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.arangodb.ArangoCursor;
import com.arangodb.entity.BaseDocument;
import com.arangodb.entity.DocumentCreateEntity;
import com.arangodb.entity.MultiDocumentEntity;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperties;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.apache.camel.component.arangodb.ArangoDbConstants.MULTI_INSERT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIfSystemProperties({
        @DisabledIfSystemProperty(named = "ci.env.name", matches = "apache.org",
                                  disabledReason = "Apache CI nodes are too resource constrained for this test"),
        @DisabledIfSystemProperty(named = "arangodb.tests.disable", matches = "true",
                                  disabledReason = "Manually disabled tests")
})
public class ArangoCollectionSaveIT extends BaseArangoDb {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:insert")
                        .to("arangodb:{{arangodb.testDb}}?documentCollection={{arangodb.testCollection}}&operation=SAVE_DOCUMENT");
            }
        };
    }

    @Test
    public void insertOneDocument() {
        BaseDocument myObject = new BaseDocument();
        myObject.addAttribute("a", "Foo");
        myObject.addAttribute("b", 42);

        Exchange result = template.request("direct:insert", exchange -> exchange.getMessage().setBody(myObject));

        assertTrue(result.getMessage().getBody() instanceof DocumentCreateEntity);
        DocumentCreateEntity<BaseDocument> docCreated = (DocumentCreateEntity<BaseDocument>) result.getMessage().getBody();
        assertNotNull(docCreated.getKey());

        BaseDocument actualResult = collection.getDocument(docCreated.getKey(),
                BaseDocument.class);
        assertEquals(docCreated.getKey(), actualResult.getKey());
        assertEquals("Foo", actualResult.getAttribute("a"));
        assertEquals(42, actualResult.getAttribute("b"));
    }

    @Test
    public void insertOneBeanDocumentEntity() {
        TestDocumentEntity myEntity = new TestDocumentEntity("bar");

        Exchange result = template.request("direct:insert", exchange -> {
            exchange.getMessage().setBody(myEntity);
            exchange.getMessage().setHeader("abc", "def");
        });

        assertTrue(result.getMessage().getBody() instanceof DocumentCreateEntity);
        DocumentCreateEntity<TestDocumentEntity> docCreated
                = (DocumentCreateEntity<TestDocumentEntity>) result.getMessage().getBody();
        assertNotNull(docCreated.getKey());

        TestDocumentEntity actualResult = collection.getDocument(docCreated.getKey(),
                TestDocumentEntity.class);
        assertEquals("bar", actualResult.getFoo());
        assertEquals(docCreated.getKey(), actualResult.getKey());
    }

    @Test
    public void insertOneBean() {
        TestEntity myEntity = new TestEntity("bar");

        Exchange result = template.request("direct:insert", exchange -> {
            exchange.getMessage().setBody(myEntity);
            exchange.getMessage().setHeader("abc", "def");
        });

        assertTrue(result.getMessage().getBody() instanceof DocumentCreateEntity);
        DocumentCreateEntity<TestEntity> docCreated = (DocumentCreateEntity<TestEntity>) result.getMessage().getBody();
        assertNotNull(docCreated.getKey());

        TestDocumentEntity actualResult = collection.getDocument(docCreated.getKey(),
                TestDocumentEntity.class);
        assertEquals("bar", actualResult.getFoo());
        assertEquals(docCreated.getKey(), actualResult.getKey());
    }

    @Test
    public void insertMultipleBeanDocuments() {
        TestDocumentEntity test1 = new TestDocumentEntity("bar1");

        TestDocumentEntity test2 = new TestDocumentEntity("bar2", 10);

        List<TestDocumentEntity> documents = Arrays.asList(test1, test2);
        Exchange result = template.request("direct:insert", exchange -> {
            exchange.getMessage().setBody(documents);
            exchange.getMessage().setHeader(MULTI_INSERT, true);
        });

        assertTrue(result.getMessage().getBody() instanceof MultiDocumentEntity);

        String query = "FOR t IN " + COLLECTION_NAME + " FILTER t.foo == @foo RETURN t";
        Map<String, Object> bindVars = Map.of("foo", test1.getFoo());
        ArangoCursor<TestDocumentEntity> cursor = arangoDatabase.query(query, TestDocumentEntity.class, bindVars, null);
        cursor.forEachRemaining(test1Inserted -> {
            assertNotNull(test1Inserted);
            assertNotNull(test1Inserted.getKey());
            assertNotNull(test1Inserted.getRev());
            assertEquals(test1.getFoo(), test1Inserted.getFoo());
            assertEquals(test1.getNumber(), test1Inserted.getNumber());

        });

        bindVars = Map.of("foo", test2.getFoo());
        cursor = arangoDatabase.query(query, TestDocumentEntity.class, bindVars, null);
        cursor.forEachRemaining(test2Inserted -> {
            assertNotNull(test2Inserted);
            assertNotNull(test2Inserted.getKey());
            assertNotNull(test2Inserted.getRev());
            assertEquals(test2.getFoo(), test2Inserted.getFoo());
            assertEquals(test2.getNumber(), test2Inserted.getNumber());
        });
    }

}
