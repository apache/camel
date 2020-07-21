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

import com.arangodb.ArangoDB;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Perform operations on ArangoDb documents, collections and graphs.
 */
@UriEndpoint(firstVersion = "3.5.0-SNAPSHOT", scheme = "arangodb", title = "ArangoDb", syntax = "arangodb:database", category = {Category.DATABASE, Category.NOSQL}, producerOnly = true)
public class ArangoDbEndpoint extends DefaultEndpoint {
    private ArangoDB arango;

    @UriPath
    @Metadata(required = true)
    private String database;
    @UriParam
    private String host;
    @UriParam
    private int port;
    @UriParam(label = "security", secret = true)
    private String user;
    @UriParam(label = "security", secret = true)
    private String password;
    @UriParam
    private String collection;
    @UriParam
    private ArangoDbOperation operation;

    public ArangoDbEndpoint() {
    }

    public ArangoDbEndpoint(String uri, ArangoDbComponent component) {
        super(uri, component);
    }

    public Producer createProducer() {
        return new ArangoDbProducer(this);
    }

    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("You cannot receive messages at this endpoint: " + getEndpointUri());
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

    public ArangoDB getArango() {
        return arango;
    }

    public void setArango(ArangoDB arango) {
        this.arango = arango;
    }

    public String getHost() {
        return host;
    }

    /**
     * host if host and/or port different from default
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
     * port if  host and/or port different from default
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
     * user if user and/or password different from default
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
     * password if user and/or password different from default
     *
     * @param password
     */
    public void setPassword(String password) {
        this.password = password;
    }


    public String getCollection() {
        return collection;
    }

    /**
     * collection in the database
     *
     * @param collection
     */
    public void setCollection(String collection) {
        this.collection = collection;
    }

    public ArangoDbOperation getOperation() {
        return operation;
    }

    /**
     * operation to perform
     *
     * @param operation
     */
    public void setOperation(ArangoDbOperation operation) {
        this.operation = operation;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (arango == null) {

            final ArangoDB.Builder builder = new ArangoDB.Builder();

            if (ObjectHelper.isNotEmpty(host) && ObjectHelper.isNotEmpty(port)) {
                builder.host(host, port);
            }

            if (ObjectHelper.isNotEmpty(user) && ObjectHelper.isNotEmpty(password)) {
                builder.user(user).password(password);
            }

            arango = builder.build();
        }

    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        if (arango != null) {
            arango.shutdown();
        }
    }

}
