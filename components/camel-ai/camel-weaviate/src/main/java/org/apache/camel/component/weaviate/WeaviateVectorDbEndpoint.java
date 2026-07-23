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
package org.apache.camel.component.weaviate;

import io.weaviate.client6.v1.api.Authentication;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.WeaviateException;
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
 * Perform operations on the Weaviate Vector Database.
 */
@UriEndpoint(
             firstVersion = "4.12.0",
             scheme = WeaviateVectorDb.SCHEME,
             title = "weaviate",
             syntax = "weaviate:collection",
             producerOnly = true,
             category = {
                     Category.DATABASE,
                     Category.AI
             },
             headersClass = WeaviateVectorDbHeaders.class)
public class WeaviateVectorDbEndpoint extends DefaultEndpoint {

    @Metadata(required = true)
    @UriPath(description = "The collection Name")
    private final String collection;

    @UriParam
    private WeaviateVectorDbConfiguration configuration;

    private volatile boolean closeClient;
    private volatile WeaviateClient client;

    public WeaviateVectorDbEndpoint(
                                    String endpointUri,
                                    Component component,
                                    String collection,
                                    WeaviateVectorDbConfiguration configuration) {

        super(endpointUri, component);

        this.collection = collection;
        this.configuration = configuration;
    }

    public WeaviateVectorDbConfiguration getConfiguration() {
        return configuration;
    }

    public String getCollection() {
        return collection;
    }

    public WeaviateClient getClient() throws WeaviateException {
        if (this.client == null) {
            lock.lock();
            try {
                if (this.client == null) {
                    this.client = this.configuration.getClient();
                    this.closeClient = false;

                    if (this.client == null) {
                        this.client = createClient();
                        this.closeClient = true;
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
        return new WeaviateVectorDbProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not implemented for this component");
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();
        if (this.client != null && this.closeClient) {
            this.client.close();
            this.client = null;
            this.closeClient = false;
        }
    }

    private WeaviateClient createClient() throws WeaviateException {
        String scheme = configuration.getScheme();
        String host = configuration.getHost();
        if (host == null) {
            throw new IllegalArgumentException("Weaviate host must be configured");
        }

        // Parse host:port if port is embedded in the host string
        String httpHost = host;
        // Weaviate's default HTTP port; overridden below if host contains ":port"
        int httpPort = 8080;
        if (host.contains(":")) {
            String[] parts = host.split(":");
            httpHost = parts[0];
            httpPort = Integer.parseInt(parts[1]);
        }

        final String resolvedHttpHost = httpHost;
        final int resolvedHttpPort = httpPort;
        final String grpcHost = configuration.getGrpcHost() != null ? configuration.getGrpcHost() : httpHost;
        final int grpcPort = configuration.getGrpcPort();

        return WeaviateClient.connectToCustom(conn -> {
            conn.scheme(scheme)
                    .httpHost(resolvedHttpHost)
                    .httpPort(resolvedHttpPort)
                    .grpcHost(grpcHost)
                    .grpcPort(grpcPort);
            if (configuration.getApiKey() != null) {
                conn.authentication(Authentication.apiKey(configuration.getApiKey()));
            }
            return conn;
        });
    }
}
