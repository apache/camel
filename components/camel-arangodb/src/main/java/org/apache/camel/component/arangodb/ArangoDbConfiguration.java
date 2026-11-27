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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class ArangoDbConfiguration implements Cloneable {

    @UriPath(description = "The name of the ArangoDB database that the producer works on. The producer does not create the"
                           + " database.")
    @Metadata(required = true)
    private String database;

    @UriParam(label = "producer",
              description = "The host name or IP address of the ArangoDB server. Must be set together with port, unless the"
                            + " arangoDB option sets a client, in which case it is not used.")
    private String host;
    @UriParam(label = "producer",
              description = "The port of the ArangoDB server, for example 8529, the default port of ArangoDB. Must be set"
                            + " together with host, unless the arangoDB option sets a client, in which case it is not used.")
    private int port;
    @UriParam(label = "security", security = "secret",
              description = "The name of the user that the ArangoDB client logs in with. Only used when password is set as"
                            + " well. If either is not set, the ArangoDB driver logs in as the user root with no password. Not"
                            + " used when the arangoDB option sets a client.")
    private String user;
    @UriParam(label = "security", security = "secret",
              description = "The password of the user that the ArangoDB client logs in with. Only used when user is set as"
                            + " well. Not used when the arangoDB option sets a client.")
    private String password;
    @UriParam(label = "producer",
              description = "The name of the document collection for the operations SAVE_DOCUMENT, FIND_DOCUMENT_BY_KEY,"
                            + " UPDATE_DOCUMENT and DELETE_DOCUMENT. The producer does not create the collection.")
    private String documentCollection;
    @UriParam(label = "producer",
              description = "The operation that the producer performs. Required: the producer fails to start without it, and"
                            + " no header replaces it. AQL_QUERY needs only the database, not a collection or a graph.")
    private ArangoDbOperation operation;
    @UriParam(label = "producer",
              description = "The name of the named graph for the vertex and edge operations, such as SAVE_VERTEX and"
                            + " SAVE_EDGE. Use it with vertexCollection for the vertex operations, and with edgeCollection for"
                            + " the edge operations.")
    private String graph;
    @UriParam(label = "producer",
              description = "The name of the vertex collection of the graph for the operations SAVE_VERTEX,"
                            + " FIND_VERTEX_BY_KEY, UPDATE_VERTEX and DELETE_VERTEX. The graph option must be set as well.")
    private String vertexCollection;
    @UriParam(label = "producer",
              description = "The name of the edge collection of the graph for the operations SAVE_EDGE, FIND_EDGE_BY_KEY,"
                            + " UPDATE_EDGE and DELETE_EDGE. The graph option must be set as well.")
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

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDocumentCollection() {
        return documentCollection;
    }

    public void setDocumentCollection(String documentCollection) {
        this.documentCollection = documentCollection;
    }

    public ArangoDbOperation getOperation() {
        return operation;
    }

    public void setOperation(ArangoDbOperation operation) {
        this.operation = operation;
    }

    public String getGraph() {
        return graph;
    }

    public void setGraph(String graph) {
        this.graph = graph;
    }

    public String getVertexCollection() {
        return vertexCollection;
    }

    public void setVertexCollection(String vertexCollection) {
        this.vertexCollection = vertexCollection;
    }

    public String getEdgeCollection() {
        return edgeCollection;
    }

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
