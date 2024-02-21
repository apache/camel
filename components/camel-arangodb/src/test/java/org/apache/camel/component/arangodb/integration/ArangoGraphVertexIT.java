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
import com.arangodb.entity.VertexEntity;
import com.arangodb.entity.VertexUpdateEntity;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperties;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.apache.camel.component.arangodb.ArangoDbConstants.ARANGO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIfSystemProperties({
        @DisabledIfSystemProperty(named = "ci.env.name", matches = "apache.org",
                                  disabledReason = "Apache CI nodes are too resource constrained for this test"),
        @DisabledIfSystemProperty(named = "arangodb.tests.disable", matches = "true",
                                  disabledReason = "Manually disabled tests")
})
public class ArangoGraphVertexIT extends BaseGraph {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:insert")
                        .to("arangodb:{{arangodb.testDb}}?graph={{arangodb.testGraph}}&vertexCollection={{arangodb.testVertexCollection}}&operation=SAVE_VERTEX");
                from("direct:update")
                        .to("arangodb:{{arangodb.testDb}}?graph={{arangodb.testGraph}}&vertexCollection={{arangodb.testVertexCollection}}&operation=UPDATE_VERTEX");
                from("direct:delete")
                        .to("arangodb:{{arangodb.testDb}}?graph={{arangodb.testGraph}}&vertexCollection={{arangodb.testVertexCollection}}&operation=DELETE_VERTEX");
                from("direct:findDocByKey")
                        .to("arangodb:{{arangodb.testDb}}?graph={{arangodb.testGraph}}&vertexCollection={{arangodb.testVertexCollection}}&operation=FIND_VERTEX_BY_KEY");
            }
        };
    }

    @Test
    public void saveVertex() {
        BaseDocument myObject = new BaseDocument();
        myObject.addAttribute("a", "Foo");
        myObject.addAttribute("b", 42);

        Exchange result = template.request("direct:insert", exchange -> exchange.getMessage().setBody(myObject));

        assertTrue(result.getMessage().getBody() instanceof VertexEntity);
        VertexEntity vertexCreated = (VertexEntity) result.getMessage().getBody();
        assertNotNull(vertexCreated.getKey());

        BaseDocument actualResult = vertexCollection.getVertex(vertexCreated.getKey(),
                BaseDocument.class);
        assertEquals(vertexCreated.getKey(), actualResult.getKey());
        assertEquals("Foo", actualResult.getAttribute("a"));
        assertEquals(42, actualResult.getAttribute("b"));
    }

    @Test
    public void updateVertex() {
        BaseDocument myObject = new BaseDocument();
        myObject.setKey("myKey");
        myObject.addAttribute("foo", "bar");
        VertexEntity entity = vertexCollection.insertVertex(myObject);

        BaseDocument objectToUpdate = vertexCollection.getVertex(entity.getKey(), BaseDocument.class);

        // update
        objectToUpdate.updateAttribute("foo", "hello");
        objectToUpdate.addAttribute("gg", 42);

        Exchange result = template.request("direct:update", exchange -> {
            exchange.getMessage().setBody(objectToUpdate);
            exchange.getMessage().setHeader(ARANGO_KEY, objectToUpdate.getKey());
        });

        assertTrue(result.getMessage().getBody() instanceof VertexUpdateEntity);
        VertexUpdateEntity docUpdated = (VertexUpdateEntity) result.getMessage().getBody();
        assertEquals(myObject.getKey(), docUpdated.getKey());

        BaseDocument actualResult = vertexCollection.getVertex(docUpdated.getKey(),
                BaseDocument.class);
        assertEquals(objectToUpdate.getKey(), actualResult.getKey());
        assertEquals("hello", actualResult.getAttribute("foo"));
        assertEquals(42, actualResult.getAttribute("gg"));
    }

    @Test
    public void deleteVertex() {
        BaseDocument myObject = new BaseDocument();
        myObject.setKey("myKey");
        myObject.addAttribute("foo", "bar");
        verticesCollection.insertDocument(myObject);

        template.request("direct:delete", exchange -> exchange.getMessage().setBody("myKey"));

        BaseDocument documentDeleted = verticesCollection.getDocument(myObject.getKey(), BaseDocument.class);
        assertNull(documentDeleted);
    }

    @Test
    public void findVertexByKey() {
        BaseDocument myObject = new BaseDocument();
        myObject.setKey("myKey");
        myObject.addAttribute("foo", "bar");
        VertexEntity entity = vertexCollection.insertVertex(myObject);

        Exchange result = template.request("direct:findDocByKey", exchange -> exchange.getMessage().setBody(entity.getKey()));

        assertTrue(result.getMessage().getBody() instanceof BaseDocument);
        BaseDocument docResult = (BaseDocument) result.getMessage().getBody();
        assertEquals("bar", docResult.getAttribute("foo"));
    }
}
