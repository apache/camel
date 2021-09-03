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

import java.util.Map;

import com.arangodb.entity.BaseDocument;
import com.arangodb.velocypack.VPackSlice;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.arangodb.ArangoDbConstants.RESULT_CLASS_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ArangoCollectionFindByKeyIT extends BaseCollection {

    private BaseDocument myObject;

    @BeforeEach
    @Override
    public void beforeEach() {
        arangoDatabase.createCollection(COLLECTION_NAME);
        collection = arangoDatabase.collection(COLLECTION_NAME);

        myObject = new BaseDocument();
        myObject.setKey("myKey");
        myObject.addAttribute("foo", "bar");

        collection.insertDocument(myObject);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:findDocByKey")
                        .to("arangodb://dbTest?documentCollection={{arangodb.testCollection}}&operation=FIND_DOCUMENT_BY_KEY");
            }
        };
    }

    @Test
    public void findDefaultSearchByKey() {
        // test without header setting type of Message expected
        Exchange result = template.request("direct:findDocByKey", exchange -> exchange.getMessage().setBody(myObject.getKey()));

        assertTrue(result.getMessage().getBody() instanceof BaseDocument);
        BaseDocument docResult = (BaseDocument) result.getMessage().getBody();
        assertEquals("bar", docResult.getAttribute("foo"));
    }

    @Test
    public void findDocumentByKey() {
        // test with header setting type of Message expected
        Exchange result = template.request("direct:findDocByKey", exchange -> {
            exchange.getMessage().setBody(myObject.getKey());
            exchange.getMessage().setHeader(RESULT_CLASS_TYPE, BaseDocument.class);
        });

        assertTrue(result.getMessage().getBody() instanceof BaseDocument);
        BaseDocument docResult = (BaseDocument) result.getMessage().getBody();
        assertEquals("bar", docResult.getAttribute("foo"));
    }

    @Test
    public void findBeanByKey() {
        Exchange result = template.request("direct:findDocByKey", exchange -> {
            exchange.getMessage().setBody(myObject.getKey());
            exchange.getMessage().setHeader(RESULT_CLASS_TYPE, TestDocumentEntity.class);
        });

        assertTrue(result.getMessage().getBody() instanceof TestDocumentEntity);
        TestDocumentEntity docResult = (TestDocumentEntity) result.getMessage().getBody();
        assertEquals("bar", docResult.getFoo());

    }

    @Test
    public void getMapByKey() {
        Exchange result = template.request("direct:findDocByKey", exchange -> {
            exchange.getMessage().setBody(myObject.getKey());
            exchange.getMessage().setHeader(RESULT_CLASS_TYPE, Map.class);
        });

        assertTrue(result.getMessage().getBody() instanceof Map);
        Map<String, Object> docResult = (Map<String, Object>) result.getMessage().getBody();
        assertNotNull(docResult);
        assertNotNull(docResult.get("foo"));
        assertEquals("bar", String.valueOf(docResult.get("foo")));
    }

    @Test
    public void getVpackSliceByKey() {
        Exchange result = template.request("direct:findDocByKey", exchange -> {
            exchange.getMessage().setBody(myObject.getKey());
            exchange.getMessage().setHeader(RESULT_CLASS_TYPE, VPackSlice.class);
        });

        assertTrue(result.getMessage().getBody() instanceof VPackSlice);
        VPackSlice docResult = (VPackSlice) result.getMessage().getBody();
        assertNotNull(docResult);
        assertNotNull(docResult.get("foo"));
        assertTrue(docResult.get("foo").isString());
        assertEquals("bar", docResult.get("foo").getAsString());
    }

    @Test
    public void getJsonByKey() {
        Exchange result = template.request("direct:findDocByKey", exchange -> {
            exchange.getMessage().setBody(myObject.getKey());
            exchange.getMessage().setHeader(RESULT_CLASS_TYPE, String.class);
        });

        assertTrue(result.getMessage().getBody() instanceof String);
        String docResult = (String) result.getMessage().getBody();
        assertNotNull(docResult);
        assertTrue(docResult.contains("foo"));
        assertTrue(docResult.contains("bar"));
    }

}
