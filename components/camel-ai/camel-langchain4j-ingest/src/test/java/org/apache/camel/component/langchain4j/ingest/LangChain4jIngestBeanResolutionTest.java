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
package org.apache.camel.component.langchain4j.ingest;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The bean-resolution UX is deliberate: picking one of several beans silently would bind a pipeline to whichever bean
 * happened to be found first, so zero and several candidates each fail with the fix in the message.
 */
class LangChain4jIngestBeanResolutionTest {

    @Test
    void missingStoreFailsWithTheFixInTheMessage() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("model", new DeterministicEmbeddingModel(8));
            context.addRoutes(route("langchain4j-ingest:pipe"));

            assertThatThrownBy(context::start)
                    .hasStackTraceContaining("needs an embedding store")
                    .hasStackTraceContaining("embeddingStore endpoint option");
        }
    }

    @Test
    void missingModelFailsWithTheFixInTheMessage() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("store", new InMemoryEmbeddingStore<TextSegment>());
            context.addRoutes(route("langchain4j-ingest:pipe"));

            assertThatThrownBy(context::start)
                    .hasStackTraceContaining("needs an embedding model")
                    .hasStackTraceContaining("embeddingModel endpoint option");
        }
    }

    @Test
    void ambiguousStoresFailNamingTheOption() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("store1", new InMemoryEmbeddingStore<TextSegment>());
            context.getRegistry().bind("store2", new InMemoryEmbeddingStore<TextSegment>());
            context.getRegistry().bind("model", new DeterministicEmbeddingModel(8));
            context.addRoutes(route("langchain4j-ingest:pipe"));

            assertThatThrownBy(context::start)
                    .hasStackTraceContaining("2 embedding store beans")
                    .hasStackTraceContaining("embeddingStore=#bean:name");
        }
    }

    @Test
    void namedBeanDisambiguates() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("store1", new InMemoryEmbeddingStore<TextSegment>());
            context.getRegistry().bind("store2", new InMemoryEmbeddingStore<TextSegment>());
            context.getRegistry().bind("model", new DeterministicEmbeddingModel(8));
            context.addRoutes(route("langchain4j-ingest:pipe?embeddingStore=#bean:store2"));

            assertThatCode(context::start).doesNotThrowAnyException();
        }
    }

    private static RouteBuilder route(String uri) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in").to(uri);
            }
        };
    }
}
