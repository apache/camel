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

import com.arangodb.entity.BaseDocument;
import com.arangodb.entity.DocumentUpdateEntity;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperties;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.apache.camel.component.arangodb.ArangoDbConstants.ARANGO_KEY;
import static org.junit.jupiter.api.Assertions.*;

@DisabledIfSystemProperties({
        @DisabledIfSystemProperty(named = "ci.env.name", matches = "apache.org",
                                  disabledReason = "Apache CI nodes are too resource constrained for this test"),
        @DisabledIfSystemProperty(named = "arangodb.tests.disable", matches = "true",
                                  disabledReason = "Manually disabled tests")
})
public class ArangoCollectionUpdateIT extends BaseArangoDb {

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
        assertEquals(42, actualResult.getAttribute("gg"));
    }

}
