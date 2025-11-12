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
package org.apache.camel.component.springai.embeddings;

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
 * Spring AI Embeddings
 */
@UriEndpoint(firstVersion = "4.17.0", scheme = SpringAiEmbeddings.SCHEME, title = "Spring AI Embeddings",
             syntax = "spring-ai-embeddings:embeddingId", producerOnly = true, category = {
                     Category.AI
             }, headersClass = SpringAiEmbeddingsHeaders.class)
public class SpringAiEmbeddingsEndpoint extends DefaultEndpoint {
    @Metadata(required = true)
    @UriPath(description = "The id")
    private final String embeddingId;

    @UriParam
    private SpringAiEmbeddingsConfiguration configuration;

    public SpringAiEmbeddingsEndpoint(
                                      String endpointUri,
                                      Component component,
                                      String embeddingId,
                                      SpringAiEmbeddingsConfiguration configuration) {

        super(endpointUri, component);

        this.embeddingId = embeddingId;
        this.configuration = configuration;
    }

    public SpringAiEmbeddingsConfiguration getConfiguration() {
        return this.configuration;
    }

    public String getEmbeddingId() {
        return this.embeddingId;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SpringAiEmbeddingsProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not implemented for this component");
    }
}
