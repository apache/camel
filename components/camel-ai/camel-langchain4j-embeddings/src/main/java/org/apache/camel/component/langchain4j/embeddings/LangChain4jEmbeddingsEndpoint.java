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

package org.apache.camel.component.langchain4j.embeddings;

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
 * LangChain4j Embeddings
 */
@UriEndpoint(
        firstVersion = "4.5.0",
        scheme = LangChain4jEmbeddings.SCHEME,
        title = "LangChain4j Embeddings",
        syntax = "langchain4j-embeddings:embeddingId",
        producerOnly = true,
        category = {Category.AI},
        headersClass = LangChain4jEmbeddingsHeaders.class)
public class LangChain4jEmbeddingsEndpoint extends DefaultEndpoint {
    @Metadata(required = true)
    @UriPath(description = "The id")
    private final String embeddingId;

    @UriParam
    private LangChain4jEmbeddingsConfiguration configuration;

    public LangChain4jEmbeddingsEndpoint(
            String endpointUri,
            Component component,
            String embeddingId,
            LangChain4jEmbeddingsConfiguration configuration) {

        super(endpointUri, component);

        this.embeddingId = embeddingId;
        this.configuration = configuration;
    }

    public LangChain4jEmbeddingsConfiguration getConfiguration() {
        return this.configuration;
    }

    public String getEmbeddingId() {
        return this.embeddingId;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new LangChain4jEmbeddingsProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not implemented for this component");
    }
}
