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
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.weaviate.WeaviateEmbeddingStore;
import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.component.langchain4j.embeddingstore.LangChain4jEmbeddingStore;
import org.apache.camel.component.langchain4j.embeddingstore.LangChain4jEmbeddingStoreComponent;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class LangChain4jEmbeddingStoreComponentTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        LangChain4jEmbeddingStoreComponent component
                = context.getComponent(LangChain4jEmbeddingStore.SCHEME, LangChain4jEmbeddingStoreComponent.class);

        EmbeddingStore<TextSegment> embeddingStore = WeaviateEmbeddingStore.builder()
                .scheme("http")
                .host("localhost")
                .objectClass("Test")
                .avoidDups(true)
                .consistencyLevel("ALL")
                .build();
        component.getConfiguration().setEmbeddingStore(embeddingStore);

        return context;
    }

    @Test
    public void testSimpleEmbedding() {

        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        TextSegment segment1 = TextSegment.from("I like football.");
        Embedding testEmbedding = embeddingModel.embed(segment1).content();

        Message first = fluentTemplate.to("langchain4j-embeddingstore:first")
                .withBody(testEmbedding)
                .request(Message.class);

    }
}
