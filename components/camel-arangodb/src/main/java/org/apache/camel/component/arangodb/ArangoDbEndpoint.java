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
 * Perform operations on ArangoDb when used as a Document Database, or as a Graph Database
 */
@UriEndpoint(firstVersion = "3.5.0", scheme = "arangodb", title = "ArangoDb", syntax = "arangodb:database",
             category = { Category.DATABASE }, producerOnly = true, headersClass = ArangoDbConstants.class)
public class ArangoDbEndpoint extends DefaultEndpoint {

    @UriPath(description = "database name")
    @Metadata(required = true)
    private String database;
    @UriParam
    private ArangoDbConfiguration configuration;
    @UriParam(label = "advanced")
    private ArangoDB arangoDB;

    public ArangoDbEndpoint() {
    }

    public ArangoDbEndpoint(String uri, ArangoDbComponent component, ArangoDbConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
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
