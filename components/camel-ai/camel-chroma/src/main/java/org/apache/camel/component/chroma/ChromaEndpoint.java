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
package org.apache.camel.component.chroma;

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
import tech.amikos.chromadb.Client;

/**
 * Perform operations on the Chroma Vector Database.
 */
@UriEndpoint(
             firstVersion = "4.17.0",
             scheme = Chroma.SCHEME,
             title = "Chroma",
             syntax = "chroma:collection",
             producerOnly = true,
             category = {
                     Category.DATABASE,
                     Category.AI
             },
             headersClass = ChromaHeaders.class)
public class ChromaEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @Metadata(required = true)
    @UriPath(description = "The collection name")
    private final String collection;

    @UriParam
    private ChromaConfiguration configuration;

    private volatile Client client;

    public ChromaEndpoint(
                          String endpointUri,
                          Component component,
                          String collection,
                          ChromaConfiguration configuration) {

        super(endpointUri, component);

        this.collection = collection;
        this.configuration = configuration;
    }

    @Override
    public String getServiceUrl() {
        return configuration.getHost();
    }

    @Override
    public String getServiceProtocol() {
        return "http";
    }

    public ChromaConfiguration getConfiguration() {
        return configuration;
    }

    public String getCollection() {
        return collection;
    }

    public Client getClient() {
        if (this.client == null) {
            lock.lock();
            try {
                if (this.client == null) {
                    this.client = this.configuration.getClient();

                    if (this.client == null) {
                        this.client = createClient();
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        return this.client;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new ChromaProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not implemented for this component");
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();

        this.client = null;
    }

    private Client createClient() {
        return new Client(configuration.getHost());
    }
}
