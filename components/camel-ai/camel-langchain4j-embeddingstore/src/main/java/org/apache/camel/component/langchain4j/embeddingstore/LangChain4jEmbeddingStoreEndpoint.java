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

package org.apache.camel.component.langchain4j.embeddingstore;

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
 * Perform operations on the Langchain4jEmbeddingStores.
 */
@UriEndpoint(
        firstVersion = "4.14.0",
        scheme = LangChain4jEmbeddingStore.SCHEME,
        title = "LangChain4j Embedding Store",
        syntax = "langchain4j-embeddings:embeddingStoreId",
        producerOnly = true,
        category = {Category.DATABASE, Category.AI},
        headersClass = LangChain4jEmbeddingStoreHeaders.class)
public class LangChain4jEmbeddingStoreEndpoint extends DefaultEndpoint {
    @Metadata(required = true)
    @UriPath(description = "The id of the embedding store")
    private final String embeddingStoreId;

    @UriParam
    private LangChain4jEmbeddingStoreConfiguration configuration;

    public LangChain4jEmbeddingStoreEndpoint(
            String endpointUri,
            Component component,
            String embeddingStoreId,
            LangChain4jEmbeddingStoreConfiguration configuration) {

        super(endpointUri, component);
        this.embeddingStoreId = embeddingStoreId;
        this.configuration = configuration;
    }

    public LangChain4jEmbeddingStoreConfiguration getConfiguration() {
        return configuration;
    }

    public String getEmbeddingId() {
        return this.embeddingStoreId;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new LangChain4jEmbeddingStoreProducer(this);
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
}
