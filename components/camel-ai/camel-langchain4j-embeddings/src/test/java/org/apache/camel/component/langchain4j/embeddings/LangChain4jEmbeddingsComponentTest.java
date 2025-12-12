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

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.RelevanceScore;
import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LangChain4jEmbeddingsComponentTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        LangChain4jEmbeddingsComponent component
                = context.getComponent(LangChain4jEmbeddings.SCHEME, LangChain4jEmbeddingsComponent.class);

        component.getConfiguration().setEmbeddingModel(new AllMiniLmL6V2EmbeddingModel());

        return context;
    }

    @Test
    public void testSimpleEmbedding() {

        Message first = fluentTemplate.to("langchain4j-embeddings:first")
                .withBody("hi")
                .request(Message.class);

        Embedding firstEmbedding = first.getHeader(LangChain4jEmbeddingsHeaders.VECTOR, Embedding.class);
        assertThat(firstEmbedding.vector()).hasSize(384);

        TextSegment firstTextSegment = first.getHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT, TextSegment.class);
        assertThat(firstTextSegment).isNotNull();
        assertThat(firstTextSegment.text()).isEqualTo("hi");

        Message second = fluentTemplate.to("langchain4j-embeddings:second")
                .withBody("hello")
                .request(Message.class);

        Embedding secondEmbedding = second.getHeader(LangChain4jEmbeddingsHeaders.VECTOR, Embedding.class);
        assertThat(secondEmbedding.vector()).hasSize(384);

        double cosineSimilarity = CosineSimilarity.between(firstEmbedding, secondEmbedding);
        assertThat(RelevanceScore.fromCosineSimilarity(cosineSimilarity)).isGreaterThan(0.9);

        TextSegment secondTextSegment = second.getHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT, TextSegment.class);
        assertThat(secondTextSegment).isNotNull();
        assertThat(secondTextSegment.text()).isEqualTo("hello");
    }
}
