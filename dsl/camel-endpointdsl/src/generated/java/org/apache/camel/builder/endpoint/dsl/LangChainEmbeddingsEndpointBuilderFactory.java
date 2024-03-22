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
package org.apache.camel.builder.endpoint.dsl;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;
import javax.annotation.processing.Generated;
import org.apache.camel.builder.EndpointConsumerBuilder;
import org.apache.camel.builder.EndpointProducerBuilder;
import org.apache.camel.builder.endpoint.AbstractEndpointBuilder;

/**
 * LangChain4j Embeddings
 * 
 * Generated by camel build tools - do NOT edit this file!
 */
@Generated("org.apache.camel.maven.packaging.EndpointDslMojo")
public interface LangChainEmbeddingsEndpointBuilderFactory {


    /**
     * Builder for endpoint for the LangChain4j Embeddings component.
     */
    public interface LangChainEmbeddingsEndpointBuilder
            extends
                EndpointProducerBuilder {
        default AdvancedLangChainEmbeddingsEndpointBuilder advanced() {
            return (AdvancedLangChainEmbeddingsEndpointBuilder) this;
        }
        /**
         * The EmbeddingModel engine to use.
         * 
         * The option is a:
         * &lt;code&gt;dev.langchain4j.model.embedding.EmbeddingModel&lt;/code&gt; type.
         * 
         * Required: true
         * Group: producer
         * 
         * @param embeddingModel the value to set
         * @return the dsl builder
         */
        default LangChainEmbeddingsEndpointBuilder embeddingModel(
                dev.langchain4j.model.embedding.EmbeddingModel embeddingModel) {
            doSetProperty("embeddingModel", embeddingModel);
            return this;
        }
        /**
         * The EmbeddingModel engine to use.
         * 
         * The option will be converted to a
         * &lt;code&gt;dev.langchain4j.model.embedding.EmbeddingModel&lt;/code&gt; type.
         * 
         * Required: true
         * Group: producer
         * 
         * @param embeddingModel the value to set
         * @return the dsl builder
         */
        default LangChainEmbeddingsEndpointBuilder embeddingModel(
                String embeddingModel) {
            doSetProperty("embeddingModel", embeddingModel);
            return this;
        }
    }

    /**
     * Advanced builder for endpoint for the LangChain4j Embeddings component.
     */
    public interface AdvancedLangChainEmbeddingsEndpointBuilder
            extends
                EndpointProducerBuilder {
        default LangChainEmbeddingsEndpointBuilder basic() {
            return (LangChainEmbeddingsEndpointBuilder) this;
        }
        /**
         * Whether the producer should be started lazy (on the first message).
         * By starting lazy you can use this to allow CamelContext and routes to
         * startup in situations where a producer may otherwise fail during
         * starting and cause the route to fail being started. By deferring this
         * startup to be lazy then the startup failure can be handled during
         * routing messages via Camel's routing error handlers. Beware that when
         * the first message is processed then creating and starting the
         * producer may take a little time and prolong the total processing time
         * of the processing.
         * 
         * The option is a: &lt;code&gt;boolean&lt;/code&gt; type.
         * 
         * Default: false
         * Group: producer (advanced)
         * 
         * @param lazyStartProducer the value to set
         * @return the dsl builder
         */
        default AdvancedLangChainEmbeddingsEndpointBuilder lazyStartProducer(
                boolean lazyStartProducer) {
            doSetProperty("lazyStartProducer", lazyStartProducer);
            return this;
        }
        /**
         * Whether the producer should be started lazy (on the first message).
         * By starting lazy you can use this to allow CamelContext and routes to
         * startup in situations where a producer may otherwise fail during
         * starting and cause the route to fail being started. By deferring this
         * startup to be lazy then the startup failure can be handled during
         * routing messages via Camel's routing error handlers. Beware that when
         * the first message is processed then creating and starting the
         * producer may take a little time and prolong the total processing time
         * of the processing.
         * 
         * The option will be converted to a &lt;code&gt;boolean&lt;/code&gt;
         * type.
         * 
         * Default: false
         * Group: producer (advanced)
         * 
         * @param lazyStartProducer the value to set
         * @return the dsl builder
         */
        default AdvancedLangChainEmbeddingsEndpointBuilder lazyStartProducer(
                String lazyStartProducer) {
            doSetProperty("lazyStartProducer", lazyStartProducer);
            return this;
        }
    }

    public interface LangChainEmbeddingsBuilders {
        /**
         * LangChain4j Embeddings (camel-langchain-embeddings)
         * LangChain4j Embeddings
         * 
         * Category: ai
         * Since: 4.5
         * Maven coordinates: org.apache.camel:camel-langchain-embeddings
         * 
         * @return the dsl builder for the headers' name.
         */
        default LangChainEmbeddingsHeaderNameBuilder langchainEmbeddings() {
            return LangChainEmbeddingsHeaderNameBuilder.INSTANCE;
        }
        /**
         * LangChain4j Embeddings (camel-langchain-embeddings)
         * LangChain4j Embeddings
         * 
         * Category: ai
         * Since: 4.5
         * Maven coordinates: org.apache.camel:camel-langchain-embeddings
         * 
         * Syntax: <code>langchain-embeddings:embeddingId</code>
         * 
         * Path parameter: embeddingId (required)
         * The id
         * 
         * @param path embeddingId
         * @return the dsl builder
         */
        default LangChainEmbeddingsEndpointBuilder langchainEmbeddings(
                String path) {
            return LangChainEmbeddingsEndpointBuilderFactory.endpointBuilder("langchain-embeddings", path);
        }
        /**
         * LangChain4j Embeddings (camel-langchain-embeddings)
         * LangChain4j Embeddings
         * 
         * Category: ai
         * Since: 4.5
         * Maven coordinates: org.apache.camel:camel-langchain-embeddings
         * 
         * Syntax: <code>langchain-embeddings:embeddingId</code>
         * 
         * Path parameter: embeddingId (required)
         * The id
         * 
         * @param componentName to use a custom component name for the endpoint
         * instead of the default name
         * @param path embeddingId
         * @return the dsl builder
         */
        default LangChainEmbeddingsEndpointBuilder langchainEmbeddings(
                String componentName,
                String path) {
            return LangChainEmbeddingsEndpointBuilderFactory.endpointBuilder(componentName, path);
        }
    }

    /**
     * The builder of headers' name for the LangChain4j Embeddings component.
     */
    public static class LangChainEmbeddingsHeaderNameBuilder {
        /**
         * The internal instance of the builder used to access to all the
         * methods representing the name of headers.
         */
        private static final LangChainEmbeddingsHeaderNameBuilder INSTANCE = new LangChainEmbeddingsHeaderNameBuilder();

        /**
         * The Finish Reason.
         * 
         * The option is a: {@code dev.langchain4j.model.output.FinishReason}
         * type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code
         * LangChainEmbeddingsFinishReason}.
         */
        public String langChainEmbeddingsFinishReason() {
            return "CamelLangChainEmbeddingsFinishReason";
        }

        /**
         * The Input Token Count.
         * 
         * The option is a: {@code int} type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code
         * LangChainEmbeddingsInputTokenCount}.
         */
        public String langChainEmbeddingsInputTokenCount() {
            return "CamelLangChainEmbeddingsInputTokenCount";
        }

        /**
         * The Output Token Count.
         * 
         * The option is a: {@code int} type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code
         * LangChainEmbeddingsOutputTokenCount}.
         */
        public String langChainEmbeddingsOutputTokenCount() {
            return "CamelLangChainEmbeddingsOutputTokenCount";
        }

        /**
         * The Total Token Count.
         * 
         * The option is a: {@code int} type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code
         * LangChainEmbeddingsTotalTokenCount}.
         */
        public String langChainEmbeddingsTotalTokenCount() {
            return "CamelLangChainEmbeddingsTotalTokenCount";
        }

        /**
         * A dense vector embedding of a text.
         * 
         * The option is a: {@code float[]} type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code LangChainEmbeddingsVector}.
         */
        public String langChainEmbeddingsVector() {
            return "CamelLangChainEmbeddingsVector";
        }
    }
    static LangChainEmbeddingsEndpointBuilder endpointBuilder(
            String componentName,
            String path) {
        class LangChainEmbeddingsEndpointBuilderImpl extends AbstractEndpointBuilder implements LangChainEmbeddingsEndpointBuilder, AdvancedLangChainEmbeddingsEndpointBuilder {
            public LangChainEmbeddingsEndpointBuilderImpl(String path) {
                super(componentName, path);
            }
        }
        return new LangChainEmbeddingsEndpointBuilderImpl(path);
    }
}