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

import java.util.ArrayList;
import java.util.List;

import com.arangodb.entity.BaseDocument;
import com.arangodb.entity.DocumentUpdateEntity;
import com.arangodb.entity.MultiDocumentEntity;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.arangodb.ArangoDbConstants.ARANGO_KEY;
import static org.apache.camel.component.arangodb.ArangoDbConstants.MULTI_UPDATE;
import static org.junit.jupiter.api.Assertions.*;

public class ArangoCollectionUpdateIT extends BaseCollection {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:update")
                        .to("arangodb:{{arangodb.testDb}}?documentCollection={{arangodb.testCollection}}&operation=UPDATE_DOCUMENT");
            }
        };
    }

    @Test
    public void testUpdateOneDocument() {
        BaseDocument myObject = new BaseDocument();
        myObject.setKey("myKey");
        myObject.addAttribute("foo", "bar");
        collection.insertDocument(myObject);

        // update
        myObject.updateAttribute("foo", "hello");
        myObject.addAttribute("gg", 42);

        Exchange result = template.request("direct:update", exchange -> {
            exchange.getMessage().setBody(myObject);
            exchange.getMessage().setHeader(ARANGO_KEY, myObject.getKey());
        });

        assertTrue(result.getMessage().getBody() instanceof DocumentUpdateEntity);
        DocumentUpdateEntity<BaseDocument> docUpdated = (DocumentUpdateEntity<BaseDocument>) result.getMessage().getBody();
        assertEquals(myObject.getKey(), docUpdated.getKey());

        BaseDocument actualResult = collection.getDocument(docUpdated.getKey(),
                BaseDocument.class);
        assertEquals(myObject.getKey(), actualResult.getKey());
        assertEquals("hello", actualResult.getAttribute("foo"));
        assertEquals(Long.valueOf(42), actualResult.getAttribute("gg"));
    }

    @Test
    public void testUpdateMultipleDocuments() {
        TestDocumentEntity test1 = new TestDocumentEntity();
        test1.setFoo("bar1");

        TestDocumentEntity test2 = new TestDocumentEntity();
        test2.setFoo("bar2");
        test2.setNumber(10);

        collection.insertDocument(test1);
        collection.insertDocument(test2);

        test1.setNumber(20);
        test2.setFoo("bar2.2");

        List<TestDocumentEntity> documents = new ArrayList<>();
        documents.add(test1);
        documents.add(test2);

        Exchange result = template.request("direct:update", exchange -> {
            exchange.getMessage().setBody(documents);
            exchange.getMessage().setHeader(MULTI_UPDATE, true);
        });
        assertTrue(result.getMessage().getBody() instanceof MultiDocumentEntity);
        MultiDocumentEntity<DocumentUpdateEntity<TestDocumentEntity>> updateDocs
                = (MultiDocumentEntity<DocumentUpdateEntity<TestDocumentEntity>>) result.getMessage().getBody();
        assertFalse(updateDocs.getDocuments().isEmpty());

        TestDocumentEntity test1Updated = collection.getDocument(test1.getKey(), TestDocumentEntity.class);
        assertNotNull(test1Updated);
        assertNotEquals(test1.getRev(), test1Updated.getRev());
        assertEquals(test1.getFoo(), test1Updated.getFoo());
        assertEquals(test1.getNumber(), test1Updated.getNumber());

        TestDocumentEntity test2Updated = collection.getDocument(test2.getKey(), TestDocumentEntity.class);
        assertNotNull(test2Updated);
        assertNotEquals(test2.getRev(), test2Updated.getRev());
        assertEquals(test2.getFoo(), test2Updated.getFoo());
        assertEquals(test2.getNumber(), test2Updated.getNumber());
    }

}
