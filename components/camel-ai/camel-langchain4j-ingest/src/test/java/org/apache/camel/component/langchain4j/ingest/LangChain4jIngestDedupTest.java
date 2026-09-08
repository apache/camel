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
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LangChain4jIngestDedupTest extends CamelTestSupport {

    @BindToRegistry("store")
    private final InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

    @BindToRegistry("model")
    private final DeterministicEmbeddingModel model = new DeterministicEmbeddingModel(16);

    @BindToRegistry("register")
    private final MemoryIdempotentRepository register = new MemoryIdempotentRepository();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in").to("langchain4j-ingest:dedup?idempotentRepository=#bean:register");
            }
        };
    }

    @Test
    void duplicateIdIsSkippedFirstWriteWins() {
        IngestResult first = ingest("doc-1", "the original content");
        IngestResult second = ingest("doc-1", "an updated content that must not be written");

        assertThat(first.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
        assertThat(second.outcome()).isEqualTo(IngestResult.Outcome.SKIPPED);
        assertThat(second.segmentsWritten()).isZero();
    }

    @Test
    void emptyDeliveryReleasesItsClaim() {
        IngestResult blank = ingest("doc-2", "   ");
        IngestResult populated = ingest("doc-2", "arrived later with content");

        assertThat(blank.outcome()).isEqualTo(IngestResult.Outcome.EMPTY);
        assertThat(populated.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
    }

    @Test
    void failedWriteReleasesItsClaim() {
        assertThatThrownBy(() -> ingest("doc-3", "boom " + DeterministicEmbeddingModel.POISON))
                .isInstanceOf(CamelExecutionException.class);
        assertThat(register.contains("doc-3")).isFalse();

        IngestResult retried = ingest("doc-3", "recovered content");
        assertThat(retried.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
    }

    private IngestResult ingest(String documentId, String body) {
        return template.requestBodyAndHeader("direct:in", body,
                LangChain4jIngestHeaders.DOCUMENT_ID, documentId, IngestResult.class);
    }
}
