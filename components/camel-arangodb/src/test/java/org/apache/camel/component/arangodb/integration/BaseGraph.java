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

import java.util.Collections;

import com.arangodb.ArangoCollection;
import com.arangodb.ArangoEdgeCollection;
import com.arangodb.ArangoGraph;
import com.arangodb.ArangoVertexCollection;
import com.arangodb.entity.BaseDocument;
import com.arangodb.entity.BaseEdgeDocument;
import com.arangodb.entity.CollectionType;
import com.arangodb.entity.EdgeDefinition;
import com.arangodb.entity.EdgeEntity;
import com.arangodb.entity.VertexEntity;
import com.arangodb.model.CollectionCreateOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class BaseGraph extends BaseArangoDb {

    protected static ArangoCollection edgesCollection;
    protected static ArangoCollection verticesCollection;
    protected static ArangoEdgeCollection edgeCollection;
    protected static ArangoVertexCollection vertexCollection;

    protected static ArangoGraph graph;

    protected static VertexEntity vertexA;
    protected static VertexEntity vertexB;
    protected static VertexEntity vertexC;
    protected static VertexEntity vertexD;
    protected static VertexEntity vertexE;
    protected static VertexEntity vertexF;
    protected static VertexEntity vertexG;
    protected static VertexEntity vertexH;
    protected static VertexEntity vertexI;
    protected static VertexEntity vertexJ;
    protected static EdgeEntity edgeAtoB;
    protected static EdgeEntity edgeAtoC;
    protected static EdgeEntity edgeBtoD;
    protected static EdgeEntity edgeBtoE;
    protected static EdgeEntity edgeCtoF;
    protected static EdgeEntity edgeCtoG;
    protected static EdgeEntity edgeDtoH;
    protected static EdgeEntity edgeDtoI;
    protected static EdgeEntity edgeFtoJ;

    protected void initData() {
        // create vertex collection
        arangoDatabase.createCollection(VERTEX_COLLECTION_NAME);
        verticesCollection = arangoDatabase.collection(VERTEX_COLLECTION_NAME);

        // create Edge collection
        arangoDatabase.createCollection(EDGE_COLLECTION_NAME, new CollectionCreateOptions().type(CollectionType.EDGES));
        edgesCollection = arangoDatabase.collection(EDGE_COLLECTION_NAME);

        // create graph
        arangoDatabase.createGraph(GRAPH_NAME,
                Collections.singletonList(new EdgeDefinition()
                        .collection(EDGE_COLLECTION_NAME)
                        .from(VERTEX_COLLECTION_NAME)
                        .to(VERTEX_COLLECTION_NAME)),
                null);

        graph = arangoDatabase.graph(GRAPH_NAME);
        edgeCollection = graph.edgeCollection(EDGE_COLLECTION_NAME);
        vertexCollection = graph.vertexCollection(VERTEX_COLLECTION_NAME);

        // creating some vertices
        initTraversalGraph();
    }

    public void initTraversalGraph() {
        // creating vertices
        vertexA = vertexCollection.insertVertex(new BaseDocument("A"));
        vertexB = vertexCollection.insertVertex(new BaseDocument("B"));
        vertexC = vertexCollection.insertVertex(new BaseDocument("C"));
        vertexD = vertexCollection.insertVertex(new BaseDocument("D"));
        vertexE = vertexCollection.insertVertex(new BaseDocument("E"));
        vertexF = vertexCollection.insertVertex(new BaseDocument("F"));
        vertexG = vertexCollection.insertVertex(new BaseDocument("G"));
        vertexH = vertexCollection.insertVertex(new BaseDocument("H"));
        vertexI = vertexCollection.insertVertex(new BaseDocument("I"));
        vertexJ = vertexCollection.insertVertex(new BaseDocument("J"));

        // creating edges
        edgeAtoB = edgeCollection.insertEdge(new BaseEdgeDocument(vertexA.getId(), vertexB.getId()));
        edgeAtoC = edgeCollection.insertEdge(new BaseEdgeDocument(vertexA.getId(), vertexC.getId()));
        edgeBtoD = edgeCollection.insertEdge(new BaseEdgeDocument(vertexB.getId(), vertexD.getId()));
        edgeBtoE = edgeCollection.insertEdge(new BaseEdgeDocument(vertexB.getId(), vertexE.getId()));
        edgeCtoF = edgeCollection.insertEdge(new BaseEdgeDocument(vertexC.getId(), vertexF.getId()));
        edgeCtoG = edgeCollection.insertEdge(new BaseEdgeDocument(vertexC.getId(), vertexG.getId()));
        edgeDtoH = edgeCollection.insertEdge(new BaseEdgeDocument(vertexD.getId(), vertexH.getId()));
        edgeDtoI = edgeCollection.insertEdge(new BaseEdgeDocument(vertexD.getId(), vertexI.getId()));
        edgeFtoJ = edgeCollection.insertEdge(new BaseEdgeDocument(vertexF.getId(), vertexJ.getId()));
    }

    protected void dropData() {
        edgesCollection.drop();
        verticesCollection.drop();
        graph.drop();
    }

    @BeforeEach
    public void init() {
        initData();
    }

    @AfterEach
    public void tearDown() {
        dropData();
    }
}
