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
package org.apache.camel.component.pgvector;

import javax.sql.DataSource;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Perform operations on the PostgreSQL pgvector Vector Database.
 */
@UriEndpoint(
             firstVersion = "4.19.0",
             scheme = PgVector.SCHEME,
             title = "PGVector",
             syntax = "pgvector:collection",
             producerOnly = true,
             category = {
                     Category.DATABASE,
                     Category.AI
             },
             headersClass = PgVectorHeaders.class)
public class PgVectorEndpoint extends DefaultEndpoint {

    @Metadata(required = true)
    @UriPath(description = "The collection (table) name")
    private final String collection;

    @UriParam
    private PgVectorConfiguration configuration;

    public PgVectorEndpoint(
                            String endpointUri,
                            Component component,
                            String collection,
                            PgVectorConfiguration configuration) {

        super(endpointUri, component);

        this.collection = collection;
        this.configuration = configuration;
    }

    public PgVectorConfiguration getConfiguration() {
        return configuration;
    }

    public String getCollection() {
        return collection;
    }

    public DataSource getDataSource() {
        return this.configuration.getDataSource();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new PgVectorProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not implemented for this component");
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        if (configuration.getDataSource() == null) {
            throw new IllegalArgumentException("DataSource must be configured on the pgvector component or endpoint");
        }
    }
}
