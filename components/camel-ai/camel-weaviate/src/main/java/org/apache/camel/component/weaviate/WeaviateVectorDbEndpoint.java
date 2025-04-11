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

import io.weaviate.client.Config;
import io.weaviate.client.WeaviateAuthClient;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.auth.exception.AuthException;
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
             firstVersion = "4.11.0",
             scheme = WeaviateVectorDb.SCHEME,
             title = "weaviate",
             syntax = "weaviate:collection",
             producerOnly = true,
             category = {
                     Category.DATABASE,
                     Category.AI
             },
             headersClass = WeaviateVectorDb.Headers.class)
public class WeaviateVectorDbEndpoint extends DefaultEndpoint {

    @Metadata(required = true)
    @UriPath(description = "The collection Name")
    private final String collection;

    @UriParam
    private WeaviateVectorDbConfiguration configuration;

    private WeaviateClient client;

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

    public WeaviateClient getClient() throws AuthException {
        lock.lock();
        try {
            if (this.client == null) {
                this.client = this.configuration.getClient();
                if (this.client == null) {
                    this.client = createClient();
                }
            }
            return this.client;
        } finally {
            lock.unlock();
        }
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
    public void doStart() throws Exception {
        super.doStart();
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();
    }

    private WeaviateClient createClient() throws AuthException {
        String scheme = configuration.getScheme();
        String host = configuration.getHost();
        Config config = new Config(scheme, host);

        // Configure proxy if we have proxy details within the configuration
        if (configuration.getProxyHost() != null
                && configuration.getProxyPort() != null
                && configuration.getProxyScheme() != null) {
            config.setProxy(configuration.getProxyHost(), configuration.getProxyPort().intValue(),
                    configuration.getProxyScheme());
        }

        WeaviateClient weaviate;

        if (configuration.getApiKey() != null) {
            weaviate = WeaviateAuthClient.apiKey(config, configuration.getApiKey());
        } else {
            weaviate = new WeaviateClient(config);
        }

        Result<Boolean> result = weaviate.misc().readyChecker().run();
        if (result.hasErrors()) {
            throw new AuthException(result.getError().toString());
        }

        return weaviate;
    }
}
