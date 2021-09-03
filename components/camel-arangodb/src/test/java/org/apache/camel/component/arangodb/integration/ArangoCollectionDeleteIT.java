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

import static org.apache.camel.component.arangodb.ArangoDbConstants.MULTI_DELETE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ArangoCollectionDeleteIT extends BaseCollection {

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
        TestDocumentEntity test2 = new TestDocumentEntity("bar2", 10);
        TestDocumentEntity test3 = new TestDocumentEntity("bar3");

        collection.insertDocument(test1);
        collection.insertDocument(test2);
        collection.insertDocument(test3);

        template.request("direct:delete", exchange -> {
            exchange.getMessage().setBody(Arrays.asList(test1.getKey(), test2.getKey()));
            exchange.getMessage().setHeader(MULTI_DELETE, true);
        });

        // document is deleted
        TestDocumentEntity document = collection.getDocument(test1.getKey(), TestDocumentEntity.class);
        assertNull(document);

        // document is deleted
        document = collection.getDocument(test2.getKey(), TestDocumentEntity.class);
        assertNull(document);

        // document is not delete
        document = collection.getDocument(test3.getKey(), TestDocumentEntity.class);
        assertNotNull(document);
        assertEquals(test3.getFoo(), document.getFoo());
    }

}
