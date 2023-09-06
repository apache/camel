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

import com.arangodb.entity.BaseDocument;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperties;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.apache.camel.component.arangodb.ArangoDbConstants.MULTI_DELETE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisabledIfSystemProperties({
        @DisabledIfSystemProperty(named = "ci.env.name", matches = "apache.org",
                                  disabledReason = "Apache CI nodes are too resource constrained for this test"),
        @DisabledIfSystemProperty(named = "arangodb.tests.disable", matches = "true",
                                  disabledReason = "Manually disabled tests")
})
public class ArangoCollectionDeleteIT extends BaseArangoDb {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:delete")
                        .to("arangodb:{{arangodb.testDb}}?documentCollection={{arangodb.testCollection}}&operation=DELETE_DOCUMENT");
            }
        };
    }

    @Test
    public void deleteOneDocument() {
        BaseDocument myObject = new BaseDocument();
        myObject.setKey("myKey");
        myObject.addAttribute("foo", "bar");
        collection.insertDocument(myObject);

        template.request("direct:delete", exchange -> exchange.getMessage().setBody("myKey"));

        BaseDocument documentDeleted = collection.getDocument(myObject.getKey(), BaseDocument.class);
        assertNull(documentDeleted);
    }

    @Test
    public void deleteMultipleDocuments() {
        TestDocumentEntity test1 = new TestDocumentEntity("bar1");
        var o = collection.insertDocument(test1);
        var k1 = o.getKey();

        TestDocumentEntity test2 = new TestDocumentEntity("bar2", 10);
        o = collection.insertDocument(test2);
        var k2 = o.getKey();

        TestDocumentEntity test3 = new TestDocumentEntity("bar3");
        o = collection.insertDocument(test3);
        var k3 = o.getKey();

        template.request("direct:delete", exchange -> {
            exchange.getMessage().setBody(Arrays.asList(k1, k2));
            exchange.getMessage().setHeader(MULTI_DELETE, true);
        });

        // document is deleted
        TestDocumentEntity document = collection.getDocument(k1, TestDocumentEntity.class);
        assertNull(document);

        // document is deleted
        document = collection.getDocument(k2, TestDocumentEntity.class);
        assertNull(document);

        // document is not delete
        document = collection.getDocument(k3, TestDocumentEntity.class);
        assertNotNull(document);
        assertEquals(test3.getFoo(), document.getFoo());
    }

}
