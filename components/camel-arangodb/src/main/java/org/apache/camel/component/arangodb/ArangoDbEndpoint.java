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

import java.util.Map;

import com.arangodb.ArangoDB;
import com.arangodb.http.HttpProtocolConfig;
import io.vertx.core.Vertx;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Perform operations on ArangoDb when used as a Document Database, or as a Graph Database
 */
@UriEndpoint(firstVersion = "3.5.0", scheme = "arangodb", title = "ArangoDb", syntax = "arangodb:database",
             category = { Category.DATABASE }, producerOnly = true, headersClass = ArangoDbConstants.class)
@BodyTypes(producerIn = @BodyType(value = { Carrier.MAP, Carrier.POJO, Carrier.LIST },
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "FIND_DOCUMENT_BY_KEY",
                                                         carriers = Carrier.TEXT),
                                          @BodyType.When(option = "operation", value = "DELETE_DOCUMENT",
                                                         carriers = { Carrier.TEXT, Carrier.LIST }),
                                          @BodyType.When(option = "operation", value = "AQL_QUERY", carriers = Carrier.TEXT),
                                          @BodyType.When(option = "operation", value = "SAVE_VERTEX",
                                                         carriers = { Carrier.MAP, Carrier.POJO }),
                                          @BodyType.When(option = "operation", value = "UPDATE_VERTEX",
                                                         carriers = { Carrier.MAP, Carrier.POJO }),
                                          @BodyType.When(option = "operation", value = "FIND_VERTEX_BY_KEY",
                                                         carriers = Carrier.TEXT),
                                          @BodyType.When(option = "operation", value = "DELETE_VERTEX",
                                                         carriers = Carrier.TEXT),
                                          @BodyType.When(option = "operation", value = "SAVE_EDGE",
                                                         carriers = { Carrier.MAP, Carrier.POJO }),
                                          @BodyType.When(option = "operation", value = "UPDATE_EDGE",
                                                         carriers = { Carrier.MAP, Carrier.POJO }),
                                          @BodyType.When(option = "operation", value = "FIND_EDGE_BY_KEY",
                                                         carriers = Carrier.TEXT),
                                          @BodyType.When(option = "operation", value = "DELETE_EDGE",
                                                         carriers = Carrier.TEXT) },
                                  description = "By the operation: the document to save or update as a Map or a POJO (a"
                                                + " List of them with the CamelArangoDbMultiInsert or CamelArangoDbMultiUpdate"
                                                + " header), the key as text to find or delete (a List of keys with"
                                                + " CamelArangoDbMultiDelete), and the AQL query as text unless the"
                                                + " CamelArangoDbAqlQuery header has it. JSON text, bytes or a stream is not a"
                                                + " document: unmarshal it to a Map first. The key of an update is the"
                                                + " CamelArangoDbKey header"),
           producerOut = @BodyType(value = Carrier.UNKNOWN,
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "SAVE_DOCUMENT",
                                                          carriers = Carrier.POJO),
                                           @BodyType.When(option = "operation", value = "FIND_DOCUMENT_BY_KEY",
                                                          carriers = { Carrier.POJO, Carrier.NULL }),
                                           @BodyType.When(option = "operation", value = "UPDATE_DOCUMENT",
                                                          carriers = Carrier.POJO),
                                           @BodyType.When(option = "operation", value = "DELETE_DOCUMENT",
                                                          carriers = Carrier.POJO),
                                           @BodyType.When(option = "operation", value = "AQL_QUERY", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "SAVE_VERTEX", carriers = Carrier.POJO,
                                                          javaType = "com.arangodb.entity.VertexEntity"),
                                           @BodyType.When(option = "operation", value = "FIND_VERTEX_BY_KEY",
                                                          carriers = { Carrier.POJO, Carrier.NULL }),
                                           @BodyType.When(option = "operation", value = "UPDATE_VERTEX",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.arangodb.entity.VertexUpdateEntity"),
                                           @BodyType.When(option = "operation", value = "DELETE_VERTEX",
                                                          carriers = Carrier.VALUE, javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "SAVE_EDGE", carriers = Carrier.POJO,
                                                          javaType = "com.arangodb.entity.EdgeEntity"),
                                           @BodyType.When(option = "operation", value = "FIND_EDGE_BY_KEY",
                                                          carriers = { Carrier.POJO, Carrier.NULL }),
                                           @BodyType.When(option = "operation", value = "UPDATE_EDGE", carriers = Carrier.POJO,
                                                          javaType = "com.arangodb.entity.EdgeUpdateEntity"),
                                           @BodyType.When(option = "operation", value = "DELETE_EDGE", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean") },
                                   description = "By the operation, which must be set: the result entity of the ArangoDB"
                                                 + " driver (DocumentCreateEntity, DocumentUpdateEntity,"
                                                 + " DocumentDeleteEntity, or a MultiDocumentEntity for the multi headers)"
                                                 + " for the document writes; the document found as a BaseDocument (or"
                                                 + " BaseEdgeDocument), or of the class in the CamelArangoDbResultClassType"
                                                 + " header, or null when there is none; a List of the AQL results, empty for"
                                                 + " a query that returns nothing; and true for a delete of a vertex or edge"))
public class ArangoDbEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriPath(description = "database name")
    @Metadata(required = true)
    private String database;
    @UriParam
    private ArangoDbConfiguration configuration;
    @UriParam(label = "advanced")
    private ArangoDB arangoDB;
    @UriParam(label = "advanced")
    private Vertx vertx;

    public ArangoDbEndpoint() {
    }

    public ArangoDbEndpoint(String uri, ArangoDbComponent component, ArangoDbConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public String getServiceUrl() {
        return configuration.getHost() + ":" + configuration.getPort();
    }

    @Override
    public String getServiceProtocol() {
        return "http";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getUser() != null) {
            return Map.of("username", configuration.getUser());
        }
        return null;
    }

    public Producer createProducer() {
        return new ArangoDbProducer(this);
    }

    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("You cannot receive messages at this endpoint: " + getEndpointUri());
    }

    public ArangoDB getArangoDB() {
        return arangoDB;
    }

    /**
     * To use an existing ArangDB client.
     */
    public void setArangoDB(ArangoDB arangoDB) {
        this.arangoDB = arangoDB;
    }

    public Vertx getVertx() {
        return vertx;
    }

    /**
     * To use an existing Vertx instance in the ArangoDB client.
     */
    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }

    public ArangoDbConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(ArangoDbConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (arangoDB == null) {

            final ArangoDB.Builder builder = new ArangoDB.Builder();

            if (ObjectHelper.isNotEmpty(configuration.getHost()) && ObjectHelper.isNotEmpty(configuration.getPort())) {
                builder.host(configuration.getHost(), configuration.getPort());
            }

            if (ObjectHelper.isNotEmpty(configuration.getUser()) && ObjectHelper.isNotEmpty(configuration.getPassword())) {
                builder.user(configuration.getUser()).password(configuration.getPassword());
            }

            if (vertx != null) {
                builder.protocolConfig(HttpProtocolConfig.builder().vertx(vertx).build());
            }

            arangoDB = builder.build();
        }

    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        if (arangoDB != null) {
            arangoDB.shutdown();
        }
    }
}
