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
package org.apache.camel.component.weaviate.transform;

import java.util.HashMap;
import java.util.Map;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.apache.camel.Message;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.component.weaviate.WeaviateVectorDbAction;
import org.apache.camel.component.weaviate.WeaviateVectorDbHeaders;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeaviateEmbeddingsDataTypeTransformerTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> transformProperties(WeaviateVectorDbAction action) throws Exception {
        Embedding embedding = new Embedding(new float[] { 0.1f, 0.2f, 0.3f });
        TextSegment segment = TextSegment.from("the source passage");

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            Message in = new DefaultExchange(context).getMessage();
            in.setHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR, embedding);
            in.setHeader(WeaviateVectorDbHeaders.ACTION, action);
            in.setHeader(WeaviateVectorDbHeaders.KEY_NAME, "id");
            in.setHeader(WeaviateVectorDbHeaders.KEY_VALUE, "doc-1");
            in.setBody(segment);

            new WeaviateEmbeddingsDataTypeTransformer().transform(in, DataType.ANY, DataType.ANY);

            return in.getHeader(WeaviateVectorDbHeaders.PROPERTIES, Map.class);
        }
    }

    @Test
    void createStoresTheDocumentTextInProperties() throws Exception {
        Map<String, Object> props = transformProperties(WeaviateVectorDbAction.CREATE);
        assertThat(props)
                .isNotNull()
                .containsEntry("text", "the source passage")
                .containsEntry("id", "doc-1");
    }

    @Test
    void updateStoresTheDocumentTextInProperties() throws Exception {
        Map<String, Object> props = transformProperties(WeaviateVectorDbAction.UPDATE_BY_ID);
        assertThat(props)
                .isNotNull()
                .containsEntry("text", "the source passage")
                .containsEntry("id", "doc-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createMergesTextIntoAnExistingPropertiesHeader() throws Exception {
        Embedding embedding = new Embedding(new float[] { 0.1f, 0.2f, 0.3f });
        TextSegment segment = TextSegment.from("the source passage");

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            Message in = new DefaultExchange(context).getMessage();
            in.setHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR, embedding);
            in.setHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.CREATE);
            // Properties the caller populated before the transformer runs must survive.
            Map<String, Object> callerProperties = new HashMap<>();
            callerProperties.put("sky", "blue");
            callerProperties.put("age", "34");
            in.setHeader(WeaviateVectorDbHeaders.PROPERTIES, callerProperties);
            in.setBody(segment);

            new WeaviateEmbeddingsDataTypeTransformer().transform(in, DataType.ANY, DataType.ANY);

            Map<String, Object> props = in.getHeader(WeaviateVectorDbHeaders.PROPERTIES, Map.class);
            assertThat(props)
                    .containsEntry("sky", "blue")
                    .containsEntry("age", "34")
                    .containsEntry("text", "the source passage");
        }
    }
}
