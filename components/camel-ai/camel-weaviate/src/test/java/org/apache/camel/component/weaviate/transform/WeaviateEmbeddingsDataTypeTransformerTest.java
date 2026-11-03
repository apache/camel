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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.apache.camel.Message;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.component.weaviate.WeaviateVectorDbAction;
import org.apache.camel.component.weaviate.WeaviateVectorDbHeaders;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.SimpleTypeConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @SuppressWarnings("unchecked")
    void theBodyTypesSayTheBody() throws Exception {
        // CAMEL-25928: any body, as transformerIn says, of which a TextSegment or a String is stored as the text, with the
        // embedding and the action in headers, and the List of Float transformerOut says
        BodyTypes bodyTypes = WeaviateEmbeddingsDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        assertThat(bodyTypes.transformerIn().value()).containsExactly(Carrier.ANY);
        assertThat(bodyTypes.transformerOut().value()).containsExactly(Carrier.LIST);
        assertThat(bodyTypes.transformerOut().javaType()).isEqualTo("java.util.List<java.lang.Float>");

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            // the converter camel-langchain4j-embeddings brings, which is not on the classpath of this module
            context.getTypeConverterRegistry().addTypeConverter(TextSegment.class, String.class,
                    new SimpleTypeConverter(false, (type, exchange, value) -> TextSegment.from((String) value)));
            context.start();

            for (WeaviateVectorDbAction action : List.of(WeaviateVectorDbAction.CREATE, WeaviateVectorDbAction.UPDATE_BY_ID,
                    WeaviateVectorDbAction.QUERY)) {
                for (Object body : Arrays.asList(TextSegment.from("hello"), "hello", 5, null)) {
                    Message in = new DefaultExchange(context).getMessage();
                    in.setHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR,
                            new Embedding(new float[] { 0.1f, 0.2f, 0.3f }));
                    in.setHeader(WeaviateVectorDbHeaders.ACTION, action);
                    in.setBody(body);
                    new WeaviateEmbeddingsDataTypeTransformer().transform(in, DataType.ANY, DataType.ANY);

                    assertThat(in.getBody()).as(action + " " + body).isInstanceOf(List.class);
                    assertThat(in.getBody(List.class)).containsExactly(0.1f, 0.2f, 0.3f);
                    Map<String, Object> props = in.getHeader(WeaviateVectorDbHeaders.PROPERTIES, Map.class);
                    if (action != WeaviateVectorDbAction.QUERY && (body instanceof TextSegment || body instanceof String)) {
                        assertThat(props).containsEntry("text", "hello");
                    } else {
                        // another body, or a QUERY, stores no text
                        assertThat(props).isNull();
                    }
                }
            }

            // a message without the action, or without the embedding, fails
            Message noAction = new DefaultExchange(context).getMessage();
            noAction.setHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR,
                    new Embedding(new float[] { 0.1f, 0.2f, 0.3f }));
            noAction.setBody(TextSegment.from("hello"));
            assertThatThrownBy(() -> new WeaviateEmbeddingsDataTypeTransformer().transform(noAction, DataType.ANY,
                    DataType.ANY)).isInstanceOf(IllegalStateException.class);
            Message noEmbedding = new DefaultExchange(context).getMessage();
            noEmbedding.setHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.CREATE);
            noEmbedding.setBody(TextSegment.from("hello"));
            assertThatThrownBy(() -> new WeaviateEmbeddingsDataTypeTransformer().transform(noEmbedding, DataType.ANY,
                    DataType.ANY)).isInstanceOf(NullPointerException.class);
        }
    }
}
