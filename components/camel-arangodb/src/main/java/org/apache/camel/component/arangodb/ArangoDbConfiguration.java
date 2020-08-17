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
    private String collection;
    @UriParam(label = "producer")
    private ArangoDbOperation operation;

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
     * port if host and/or port different from default
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

    public ArangoDbConfiguration copy() {
        try {
            return (ArangoDbConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
