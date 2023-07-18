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
package org.apache.camel.component.arangodb;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class ArangoDbConfiguration implements Cloneable {
    private String database;

    @UriParam(label = "producer")
    private String host;
    @UriParam(label = "producer")
    private int port;
    @UriParam(label = "security", secret = true)
    private String user;
    @UriParam(label = "security", secret = true)
    private String password;
    @UriParam(label = "producer")
    private String documentCollection;
    @UriParam(label = "producer")
    private ArangoDbOperation operation;
    @UriParam(label = "producer")
    private String graph;
    @UriParam(label = "producer")
    private String vertexCollection;
    @UriParam(label = "producer")
    private String edgeCollection;

    public ArangoDbConfiguration() {
    }

    public String getDatabase() {
        return database;
    }

    /**
     * database name
     *
     * @param database
     */
    public void setDatabase(String database) {
        this.database = database;
    }

    public String getHost() {
        return host;
    }

    /**
     * ArangoDB host. If host and port are default, this field is Optional.
     *
     * @param host
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * ArangoDB exposed port. If host and port are default, this field is Optional.
     *
     * @param port
     */
    public void setPort(int port) {
        this.port = port;
    }

    public String getUser() {
        return user;
    }

    /**
     * ArangoDB user. If user and password are default, this field is Optional.
     *
     * @param user
     */
    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    /**
     * ArangoDB password. If user and password are default, this field is Optional.
     *
     * @param password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getDocumentCollection() {
        return documentCollection;
    }

    /**
     * Collection name, when using ArangoDb as a Document Database. Set the documentCollection name when using the CRUD
     * operation on the document database collections (SAVE_DOCUMENT , FIND_DOCUMENT_BY_KEY, UPDATE_DOCUMENT,
     * DELETE_DOCUMENT).
     *
     * @param documentCollection
     */
    public void setDocumentCollection(String documentCollection) {
        this.documentCollection = documentCollection;
    }

    public ArangoDbOperation getOperation() {
        return operation;
    }

    /**
     * Operations to perform on ArangoDb. For the operation AQL_QUERY, no need to specify a collection or graph.
     *
     * @param operation
     */
    public void setOperation(ArangoDbOperation operation) {
        this.operation = operation;
    }

    public String getGraph() {
        return graph;
    }

    /**
     * Graph name, when using ArangoDb as a Graph Database. Combine this attribute with one of the two attributes
     * vertexCollection and edgeCollection.
     *
     * @param graph
     */
    public void setGraph(String graph) {
        this.graph = graph;
    }

    public String getVertexCollection() {
        return vertexCollection;
    }

    /**
     * Collection name of vertices, when using ArangoDb as a Graph Database. Set the vertexCollection name to perform
     * CRUD operation on vertices using these operations : SAVE_EDGE, FIND_EDGE_BY_KEY, UPDATE_EDGE, DELETE_EDGE. The
     * graph attribute is mandatory.
     *
     * @param vertexCollection
     */
    public void setVertexCollection(String vertexCollection) {
        this.vertexCollection = vertexCollection;
    }

    public String getEdgeCollection() {
        return edgeCollection;
    }

    /**
     * Collection name of vertices, when using ArangoDb as a Graph Database. Set the edgeCollection name to perform CRUD
     * operation on edges using these operations : SAVE_VERTEX, FIND_VERTEX_BY_KEY, UPDATE_VERTEX, DELETE_VERTEX. The
     * graph attribute is mandatory.
     *
     * @param edgeCollection
     */
    public void setEdgeCollection(String edgeCollection) {
        this.edgeCollection = edgeCollection;
    }

    public ArangoDbConfiguration copy() {
        try {
            return (ArangoDbConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
