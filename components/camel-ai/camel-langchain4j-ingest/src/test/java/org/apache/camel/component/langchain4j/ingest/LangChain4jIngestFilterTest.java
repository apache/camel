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
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The filter options: id patterns act before the dedup claim, content filters release theirs, and a duplicate is
 * answered before any filter runs.
 */
class LangChain4jIngestFilterTest extends CamelTestSupport {

    @BindToRegistry("store")
    private final InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

    @BindToRegistry("model")
    private final DeterministicEmbeddingModel model = new DeterministicEmbeddingModel(16);

    @BindToRegistry("register")
    private final MemoryIdempotentRepository register = new MemoryIdempotentRepository();

    @BindToRegistry("confidential")
    private final Predicate confidential
            = exchange -> !exchange.getMessage().getBody(String.class).contains("CONFIDENTIAL");

    @BindToRegistry("explosive")
    private final Predicate explosive = exchange -> {
        if (exchange.getMessage().getBody(String.class).contains("BOOM")) {
            throw new IllegalStateException("filter blew up");
        }
        return true;
    };

    @BindToRegistry("requireContent")
    private final Predicate requireContent = exchange -> {
        String body = exchange.getMessage().getBody(String.class);
        return body != null && !body.isBlank();
    };

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:globs").to("langchain4j-ingest:globs?includeId=docs/**,*.md&excludeId=**/draft-*"
                                        + "&idempotentRepository=#bean:register");
                from("direct:min").to("langchain4j-ingest:min?minDocumentSize=25"
                                      + "&idempotentRepository=#bean:register");
                from("direct:predicate").to("langchain4j-ingest:predicate?documentFilter=#bean:confidential"
                                            + "&idempotentRepository=#bean:register");
                from("direct:norepo").to("langchain4j-ingest:norepo?documentFilter=#bean:confidential"
                                         + "&minDocumentSize=25");
                from("direct:throwing").to("langchain4j-ingest:throwing?documentFilter=#bean:explosive"
                                           + "&idempotentRepository=#bean:register");
                from("direct:requireContent").to("langchain4j-ingest:require-content"
                                                 + "?documentFilter=#bean:requireContent"
                                                 + "&idempotentRepository=#bean:register");
            }
        };
    }

    @Test
    void idPatternsGovernIngestionWithoutClaiming() {
        IngestResult matching = ingest("direct:globs", "docs/guide.md", "a matching document");
        IngestResult wrongExtension = ingest("direct:globs", "notes.txt", "a non-matching document");
        IngestResult excluded = ingest("direct:globs", "docs/draft-plan.md", "included but excluded");

        assertThat(matching.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
        assertThat(wrongExtension.outcome()).isEqualTo(IngestResult.Outcome.FILTERED);
        assertThat(wrongExtension.segmentsWritten()).isZero();
        // exclusion wins over a matching include
        assertThat(excluded.outcome()).isEqualTo(IngestResult.Outcome.FILTERED);

        // a filtered id never touched the register
        assertThat(register.contains("docs/guide.md")).isTrue();
        assertThat(register.contains("notes.txt")).isFalse();
        assertThat(register.contains("docs/draft-plan.md")).isFalse();
    }

    @Test
    void shortDocumentIsFilteredAndReleasesItsClaim() {
        IngestResult tooShort = ingest("direct:min", "doc-min", "too short");
        assertThat(tooShort.outcome()).isEqualTo(IngestResult.Outcome.FILTERED);
        assertThat(register.contains("doc-min")).isFalse();

        IngestResult populated = ingest("direct:min", "doc-min", "long enough to carry retrievable content");
        assertThat(populated.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
    }

    @Test
    void rejectedByThePredicateReleasesItsClaim() {
        IngestResult rejected = ingest("direct:predicate", "doc-p1", "the CONFIDENTIAL relay spec");
        assertThat(rejected.outcome()).isEqualTo(IngestResult.Outcome.FILTERED);
        assertThat(register.contains("doc-p1")).isFalse();

        IngestResult accepted = ingest("direct:predicate", "doc-p1", "the public relay spec");
        assertThat(accepted.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
    }

    @Test
    void duplicateIsAnsweredBeforeTheFilterRuns() {
        IngestResult first = ingest("direct:predicate", "doc-p2", "the public pump spec");
        // the claim is held, so the duplicate is SKIPPED - the filter never sees it
        IngestResult duplicate = ingest("direct:predicate", "doc-p2", "the CONFIDENTIAL pump spec");

        assertThat(first.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
        assertThat(duplicate.outcome()).isEqualTo(IngestResult.Outcome.SKIPPED);
    }

    @Test
    void filtersApplyWithoutARepository() {
        IngestResult rejected = ingest("direct:norepo", "doc-n1", "the CONFIDENTIAL relay spec");
        IngestResult tooShort = ingest("direct:norepo", "doc-n2", "too short");
        IngestResult accepted = ingest("direct:norepo", "doc-n3", "long enough to carry retrievable content");

        assertThat(rejected.outcome()).isEqualTo(IngestResult.Outcome.FILTERED);
        assertThat(tooShort.outcome()).isEqualTo(IngestResult.Outcome.FILTERED);
        assertThat(accepted.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
    }

    @Test
    void blankBodyAnswersEmptyNotFiltered() {
        // the blank check outranks minDocumentSize on purpose: an empty delivery is EMPTY
        IngestResult blank = ingest("direct:min", "doc-blank", "   ");
        assertThat(blank.outcome()).isEqualTo(IngestResult.Outcome.EMPTY);
    }

    @Test
    void throwingFilterReleasesItsClaim() {
        assertThatThrownBy(() -> ingest("direct:throwing", "doc-t1", "BOOM"))
                .isInstanceOf(CamelExecutionException.class);
        assertThat(register.contains("doc-t1")).isFalse();

        IngestResult retried = ingest("direct:throwing", "doc-t1", "recovered content");
        assertThat(retried.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
    }

    /**
     * The predicate runs before the blank-document check and must tolerate an absent body: a predicate rejecting a
     * blank delivery answers FILTERED, one accepting it leaves the blank check to answer EMPTY. Both release the claim.
     */
    @Test
    void blankBodyMeetsThePredicateFirst() {
        IngestResult rejected = ingest("direct:requireContent", "doc-b1", "   ");
        assertThat(rejected.outcome()).isEqualTo(IngestResult.Outcome.FILTERED);
        assertThat(register.contains("doc-b1")).isFalse();

        IngestResult absent = ingest("direct:requireContent", "doc-b2", null);
        assertThat(absent.outcome()).isEqualTo(IngestResult.Outcome.FILTERED);
        assertThat(register.contains("doc-b2")).isFalse();

        // the confidential predicate tolerates and accepts a blank body, so EMPTY wins
        IngestResult accepted = ingest("direct:predicate", "doc-b3", "   ");
        assertThat(accepted.outcome()).isEqualTo(IngestResult.Outcome.EMPTY);
    }

    private IngestResult ingest(String uri, String documentId, String body) {
        return template.requestBodyAndHeader(uri, body,
                LangChain4jIngestHeaders.DOCUMENT_ID, documentId, IngestResult.class);
    }
}
