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
package org.apache.camel.component.pinecone;

import io.pinecone.clients.Pinecone;
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
 * Perform operations on the Pinecone Vector Database.
 */
@UriEndpoint(
             firstVersion = "4.6.0",
             scheme = PineconeVectorDb.SCHEME,
             title = "Pinecone",
             syntax = "pinecone:collection",
             producerOnly = true,
             category = {
                     Category.DATABASE,
                     Category.AI
             },
             headersClass = PineconeVectorDb.Headers.class)
public class PineconeVectorDbEndpoint extends DefaultEndpoint {

    @Metadata(required = true)
    @UriPath(description = "The collection Name")
    private final String collection;

    @UriParam
    private PineconeVectorDbConfiguration configuration;

    private final Object lock;

    private volatile boolean closeClient;
    private volatile io.pinecone.clients.Pinecone client;

    public PineconeVectorDbEndpoint(
                                    String endpointUri,
                                    Component component,
                                    String collection,
                                    PineconeVectorDbConfiguration configuration) {

        super(endpointUri, component);

        this.collection = collection;
        this.configuration = configuration;

        this.lock = new Object();
    }

    public PineconeVectorDbConfiguration getConfiguration() {
        return configuration;
    }

    public String getCollection() {
        return collection;
    }

    public synchronized Pinecone getClient() {
        if (this.client == null) {
            synchronized (this.lock) {
                if (this.client == null) {
                    this.client = this.configuration.getClient();
                    this.closeClient = false;

                    if (this.client == null) {
                        this.client = createClient();
                        this.closeClient = true;
                    }
                }
            }
        }

        return this.client;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new PineconeVectorDbProducer(this);
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
    }

    private Pinecone createClient() {

        io.pinecone.clients.Pinecone pinecone = new io.pinecone.clients.Pinecone.Builder(configuration.getToken()).build();

        return pinecone;
    }
}
