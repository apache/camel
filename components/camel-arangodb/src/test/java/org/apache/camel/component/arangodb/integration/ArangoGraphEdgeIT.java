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
import com.arangodb.entity.BaseEdgeDocument;
import com.arangodb.entity.EdgeEntity;
import com.arangodb.entity.EdgeUpdateEntity;
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
public class ArangoGraphEdgeIT extends BaseGraph {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:insert")
                        .to("arangodb:{{arangodb.testDb}}?graph={{arangodb.testGraph}}&edgeCollection={{arangodb.testEdgeCollection}}&operation=SAVE_EDGE");
                from("direct:update")
                        .to("arangodb:{{arangodb.testDb}}?graph={{arangodb.testGraph}}&edgeCollection={{arangodb.testEdgeCollection}}&operation=UPDATE_EDGE");
                from("direct:delete")
                        .to("arangodb:{{arangodb.testDb}}?graph={{arangodb.testGraph}}&edgeCollection={{arangodb.testEdgeCollection}}&operation=DELETE_EDGE");
                from("direct:findDocByKey")
                        .to("arangodb:{{arangodb.testDb}}?graph={{arangodb.testGraph}}&edgeCollection={{arangodb.testEdgeCollection}}&operation=FIND_EDGE_BY_KEY");
            }
        };
    }

    public void initVertices() {
        vertexA = vertexCollection.insertVertex(new BaseDocument("A"));
        vertexB = vertexCollection.insertVertex(new BaseDocument("B"));
        vertexC = vertexCollection.insertVertex(new BaseDocument("C"));
    }

    @Test
    public void saveEdge() {
        // creating an edge A->B
        BaseEdgeDocument edge = new BaseEdgeDocument();
        edge.setFrom(vertexA.getId());
        edge.setTo(vertexB.getId());

        Exchange result = template.request("direct:insert", exchange -> exchange.getMessage().setBody(edge));

        assertTrue(result.getMessage().getBody() instanceof EdgeEntity);
        EdgeEntity edgeEntity = (EdgeEntity) result.getMessage().getBody();
        assertNotNull(edgeEntity.getKey());

        BaseEdgeDocument actualResult = edgeCollection.getEdge(edgeEntity.getKey(),
                BaseEdgeDocument.class);
        assertEquals(edgeEntity.getKey(), actualResult.getKey());
        assertEquals(vertexA.getId(), actualResult.getFrom());
        assertEquals(vertexB.getId(), actualResult.getTo());
    }

    @Test
    public void updateEdge() {
        // creating an edge A->B
        BaseEdgeDocument edge = new BaseEdgeDocument();
        edge.setFrom(vertexA.getId());
        edge.setTo(vertexB.getId());
        edge.addAttribute("foo", "bar");
        EdgeEntity entity = edgeCollection.insertEdge(edge);

        BaseEdgeDocument objectToUpdate = edgeCollection.getEdge(entity.getKey(), BaseEdgeDocument.class);

        // update
        objectToUpdate.updateAttribute("foo", "hello");
        // set direction of the edge A->C
        objectToUpdate.setTo(vertexC.getId());

        Exchange result = template.request("direct:update", exchange -> {
            exchange.getMessage().setBody(objectToUpdate);
            exchange.getMessage().setHeader(ARANGO_KEY, objectToUpdate.getKey());
        });

        assertTrue(result.getMessage().getBody() instanceof EdgeUpdateEntity);
        EdgeUpdateEntity docUpdated = (EdgeUpdateEntity) result.getMessage().getBody();
        assertEquals(entity.getKey(), docUpdated.getKey());

        BaseEdgeDocument actualResult = edgeCollection.getEdge(entity.getKey(),
                BaseEdgeDocument.class);
        assertEquals(vertexC.getId(), actualResult.getTo());
        assertEquals("hello", actualResult.getAttribute("foo"));
    }

    @Test
    public void deleteVertex() {
        // creating an edge A->B
        BaseEdgeDocument edge = new BaseEdgeDocument();
        edge.setFrom(vertexA.getId());
        edge.setTo(vertexB.getId());
        EdgeEntity entity = edgeCollection.insertEdge(edge);

        template.request("direct:delete", exchange -> exchange.getMessage().setBody(entity.getKey()));

        BaseEdgeDocument documentDeleted = edgesCollection.getDocument(entity.getKey(), BaseEdgeDocument.class);
        assertNull(documentDeleted);
    }

    @Test
    public void findVertexByKey() {
        // creating an edge A->B
        BaseEdgeDocument edge = new BaseEdgeDocument();
        edge.setFrom(vertexA.getId());
        edge.setTo(vertexB.getId());
        edge.addAttribute("foo", "bar");
        EdgeEntity entity = edgeCollection.insertEdge(edge);

        Exchange result = template.request("direct:findDocByKey", exchange -> exchange.getMessage().setBody(entity.getKey()));

        assertTrue(result.getMessage().getBody() instanceof BaseEdgeDocument);
        BaseEdgeDocument docResult = (BaseEdgeDocument) result.getMessage().getBody();
        assertEquals("bar", docResult.getAttribute("foo"));
        assertEquals(vertexA.getId(), docResult.getFrom());
        assertEquals(vertexB.getId(), docResult.getTo());
    }

}
