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
import com.arangodb.util.RawJson;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperties;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.apache.camel.component.arangodb.ArangoDbConstants.RESULT_CLASS_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIfSystemProperties({
        @DisabledIfSystemProperty(named = "ci.env.name", matches = "apache.org",
                                  disabledReason = "Apache CI nodes are too resource constrained for this test"),
        @DisabledIfSystemProperty(named = "arangodb.tests.disable", matches = "true",
                                  disabledReason = "Manually disabled tests")
})
public class ArangoCollectionFindByKeyIT extends BaseArangoDb {

    private BaseDocument myObject;

    @ContextFixture
    public void createCamelContext(CamelContext ctx) {
        super.createCamelContext(ctx);

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
    public void getJsonByKey() {
        Exchange result = template.request("direct:findDocByKey", exchange -> {
            exchange.getMessage().setBody(myObject.getKey());
            exchange.getMessage().setHeader(RESULT_CLASS_TYPE, RawJson.class);
        });

        assertTrue(result.getMessage().getBody() instanceof RawJson);
        RawJson docResult = (RawJson) result.getMessage().getBody();
        assertNotNull(docResult);
        assertTrue(docResult.get().contains("foo"));
        assertTrue(docResult.get().contains("bar"));
    }

}
