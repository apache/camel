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
package org.apache.camel.component.neo4j;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

/**
 * Perform operations on the Neo4j Graph Database
 */
@UriEndpoint(
             firstVersion = "4.10.0",
             scheme = Neo4j.SCHEME,
             title = "Neo4j",
             syntax = "neo4j:name",
             producerOnly = true,
             category = {
                     Category.DATABASE,
                     Category.AI
             },
             headersClass = Neo4j.Headers.class)
public class Neo4jEndpoint extends DefaultEndpoint implements EndpointServiceLocation {
    @Metadata(required = true)
    @UriPath(description = "The database Name")
    private final String name;

    @UriParam
    private Neo4jConfiguration configuration;

    private volatile Driver driver;

    public Neo4jEndpoint(
                         String endpointUri,
                         Component component,
                         String name,
                         Neo4jConfiguration configuration) {

        super(endpointUri, component);

        this.name = name;
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Neo4jProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not implemented for this component");
    }

    public Neo4jConfiguration getConfiguration() {
        return configuration;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getServiceUrl() {
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return null;
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();

        if (this.driver != null) {
            this.driver = null;
        }
    }

    public Driver getDriver() {
        lock.lock();
        try {
            if (this.driver == null) {
                this.driver = this.configuration.getDriver();
                if (this.driver == null) {
                    this.driver = createDriver();
                }
            }

        } finally {
            lock.unlock();
        }
        return this.driver;

    }

    private Driver createDriver() {
        // Check that Database URI is set
        String dbUri = this.configuration.getDbUri();
        ObjectHelper.notNull(dbUri, "dbUri");

        AuthToken authToken = createAuthToken();
        // check that Authentication isn't null
        ObjectHelper.notNull(authToken, "authentication credentials");

        // create driver
        return GraphDatabase.driver(dbUri, authToken);
    }

    private AuthToken createAuthToken() {
        // Case Kerberos Authentication
        if (this.configuration.getBase64() != null) {
            return AuthTokens.kerberos(this.configuration.getBase64());
        }

        // Case Bearer Authentication
        if (this.configuration.getToken() != null) {
            return AuthTokens.bearer(this.configuration.getToken());
        }

        // Case Basic Authentication
        if (this.configuration.getDbUser() != null && this.configuration.getDbPassword() != null) {
            if (this.configuration.getRealm() != null) {
                return AuthTokens.basic(this.configuration.getDbUser(), this.configuration.getDbPassword(),
                        this.configuration.getRealm());
            }
            return AuthTokens.basic(this.configuration.getDbUser(), this.configuration.getDbPassword());
        }

        return null;
    }
}
