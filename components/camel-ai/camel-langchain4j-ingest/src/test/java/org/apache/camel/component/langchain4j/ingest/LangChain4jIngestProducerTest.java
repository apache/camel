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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LangChain4jIngestProducerTest extends CamelTestSupport {

    @BindToRegistry("store")
    private final InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

    @BindToRegistry("model")
    private final DeterministicEmbeddingModel model = new DeterministicEmbeddingModel(16);

    /** Splits on '|' and deliberately propagates NO metadata, so the re-stamping must kick in. */
    @BindToRegistry("pipeSplitter")
    private final DocumentSplitter pipeSplitter = document -> {
        List<TextSegment> segments = new ArrayList<>();
        for (String part : document.text().split("\\|")) {
            segments.add(TextSegment.from(part));
        }
        return segments;
    };

    /** Returns no segments for any text — the zero-segment ingestion case. */
    @BindToRegistry("emptySplitter")
    private final DocumentSplitter emptySplitter = document -> List.of();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:ingest").to("langchain4j-ingest:products");
                from("direct:small")
                        .to("langchain4j-ingest:small?documentIdHeader=MyDocId&maxSegmentSize=30&maxOverlapSize=0");
                from("direct:batch8")
                        .to("langchain4j-ingest:batch8?documentIdHeader=MyDocId"
                            + "&maxSegmentSize=30&maxOverlapSize=0&embeddingBatchSize=8");
                from("direct:custom-splitter")
                        .to("langchain4j-ingest:custom-splitter?documentIdHeader=MyDocId"
                            + "&documentSplitter=#bean:pipeSplitter");
                from("direct:capped")
                        .to("langchain4j-ingest:capped?documentIdHeader=MyDocId&maxDocumentSize=20");
                // without stream caching, which would read the whole stream before the endpoint does
                from("direct:capped-stream").noStreamCaching()
                        .to("langchain4j-ingest:capped-stream?documentIdHeader=MyDocId&maxDocumentSize=20");
                from("direct:empty-splitter")
                        .to("langchain4j-ingest:empty-splitter?documentIdHeader=MyDocId"
                            + "&documentSplitter=#bean:emptySplitter");
                // the splitter sizes are documented as ignored with a custom splitter, so
                // even nonsensical explicit values must not fail the start
                from("direct:splitter-ignores-sizes")
                        .to("langchain4j-ingest:splitter-ignores-sizes?documentIdHeader=MyDocId"
                            + "&documentSplitter=#bean:pipeSplitter&maxSegmentSize=0&maxOverlapSize=-1");
            }
        };
    }

    @Test
    void ingestsAndStampsMetadata() {
        IngestResult result = template.requestBodyAndHeader("direct:ingest",
                "Camel rides through the desert. It carries integration patterns.",
                LangChain4jIngestHeaders.DOCUMENT_ID, "doc-1", IngestResult.class);

        assertThat(result.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
        assertThat(result.pipeline()).isEqualTo("products");
        assertThat(result.documentId()).isEqualTo("doc-1");
        assertThat(result.segmentsWritten()).isGreaterThanOrEqualTo(1);

        var matches = search("Camel rides through the desert.");
        assertThat(matches).isNotEmpty();
        for (EmbeddingMatch<TextSegment> match : matches) {
            assertThat(match.embedded().metadata().getString(LangChain4jIngest.METADATA_PIPELINE))
                    .isEqualTo("products");
            assertThat(match.embedded().metadata().getString(LangChain4jIngest.METADATA_DOCUMENT_ID))
                    .isEqualTo("doc-1");
        }
    }

    @Test
    void blankBodyAnswersEmpty() {
        IngestResult result = template.requestBodyAndHeader("direct:ingest", "   ",
                LangChain4jIngestHeaders.DOCUMENT_ID, "doc-blank", IngestResult.class);

        assertThat(result.outcome()).isEqualTo(IngestResult.Outcome.EMPTY);
        assertThat(result.segmentsWritten()).isZero();
    }

    @Test
    void missingIdFailsActionably() {
        assertThatThrownBy(() -> template.requestBody("direct:ingest", "some text"))
                .isInstanceOf(CamelExecutionException.class)
                .hasStackTraceContaining("no document id")
                .hasStackTraceContaining("documentIdHeader");
    }

    @Test
    void idPropertyWinsOverHeader() {
        IngestResult result = template.request("direct:ingest", exchange -> {
            exchange.setProperty(LangChain4jIngest.DOCUMENT_ID_PROPERTY, "prop-id");
            exchange.getIn().setHeader(LangChain4jIngestHeaders.DOCUMENT_ID, "header-id");
            exchange.getIn().setBody("body text");
        }).getMessage().getBody(IngestResult.class);

        assertThat(result.documentId()).isEqualTo("prop-id");
    }

    @Test
    void customDocumentIdHeaderOption() {
        IngestResult result = template.requestBodyAndHeader("direct:small", "short text",
                "MyDocId", "doc-custom", IngestResult.class);

        assertThat(result.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
        assertThat(result.documentId()).isEqualTo("doc-custom");
    }

    @Test
    void customSplitterIsUsedAndItsSegmentsAreReStamped() {
        IngestResult result = template.requestBodyAndHeader("direct:custom-splitter", "alpha|beta|gamma",
                "MyDocId", "doc-split", IngestResult.class);

        assertThat(result.segmentsWritten()).isEqualTo(3);
        var matches = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(model.embed("alpha").content())
                .maxResults(100)
                .build()).matches().stream()
                .filter(m -> "doc-split".equals(
                        m.embedded().metadata().getString(LangChain4jIngest.METADATA_DOCUMENT_ID)))
                .toList();
        assertThat(matches).hasSize(3);
        assertThat(matches).extracting(m -> m.embedded().text())
                .containsExactlyInAnyOrder("alpha", "beta", "gamma");
        // the splitter dropped all metadata; the engine re-stamped the identity anyway
        assertThat(matches).allSatisfy(m -> assertThat(
                m.embedded().metadata().getString(LangChain4jIngest.METADATA_PIPELINE))
                .isEqualTo("custom-splitter"));
    }

    @Test
    void customSplitterIgnoresExplicitSizeBounds() {
        // the endpoint started despite maxSegmentSize=0/maxOverlapSize=-1: with a custom
        // documentSplitter the sizes are documented as ignored, so they are not validated
        IngestResult result = template.requestBodyAndHeader("direct:splitter-ignores-sizes", "alpha|beta",
                "MyDocId", "doc-sizes-ignored", IngestResult.class);

        assertThat(result.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
        assertThat(result.segmentsWritten()).isEqualTo(2);
    }

    @Test
    void zeroSegmentIngestionAnswersEmptyNotIngested() {
        // a splitter producing no segments for non-blank text wrote nothing: the outcome is
        // EMPTY, so a dedup claim would be released - not INGESTED with segmentsWritten=0
        IngestResult result = template.requestBodyAndHeader("direct:empty-splitter", "non-blank text",
                "MyDocId", "doc-zero-segments", IngestResult.class);

        assertThat(result.outcome()).isEqualTo(IngestResult.Outcome.EMPTY);
        assertThat(result.segmentsWritten()).isZero();
    }

    @Test
    void oversizedDocumentFailsAndAnExactlyCappedOneIngests() {
        assertThatThrownBy(() -> template.requestBodyAndHeader("direct:capped",
                "exactly thirty characters long", "MyDocId", "doc-oversized"))
                .isInstanceOf(CamelExecutionException.class)
                .hasStackTraceContaining("exceeds maxDocumentSize")
                .hasStackTraceContaining("characters");
        // the cap is checked before the blank check: an oversized blank document fails, not EMPTY
        assertThatThrownBy(() -> template.requestBodyAndHeader("direct:capped",
                " ".repeat(30), "MyDocId", "doc-oversized-blank"))
                .isInstanceOf(CamelExecutionException.class)
                .hasStackTraceContaining("exceeds maxDocumentSize (30 > 20 characters)");

        IngestResult result = template.requestBodyAndHeader("direct:capped",
                "twenty characters ok", "MyDocId", "doc-at-cap", IngestResult.class);
        assertThat(result.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
    }

    @Test
    void oversizedStreamIsReadOnlyPastTheCap() {
        // 10 MB of text is refused after one character past the cap; a blank start does not make
        // it EMPTY, the rest being unread
        for (char content : new char[] { 'a', ' ' }) {
            AtomicLong read = new AtomicLong();
            assertThatThrownBy(() -> template.requestBodyAndHeader("direct:capped-stream",
                    stream(10_000_000, content, read), "MyDocId", "doc-stream"))
                    .isInstanceOf(CamelExecutionException.class)
                    .hasStackTraceContaining("exceeds maxDocumentSize (21 > 20 characters)");
            // the reader buffers ahead, but nowhere near the whole stream
            assertThat(read.get()).isLessThan(100_000);
        }

        IngestResult result = template.requestBodyAndHeader("direct:capped-stream",
                new ByteArrayInputStream("twenty characters ok".getBytes(StandardCharsets.UTF_8)), "MyDocId",
                "doc-stream-at-cap", IngestResult.class);
        assertThat(result.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
    }

    /** A stream of the given number of bytes of one character, counting how many are read. */
    private static InputStream stream(long size, char content, AtomicLong read) {
        return new InputStream() {
            @Override
            public int read() {
                if (read.get() >= size) {
                    return -1;
                }
                read.incrementAndGet();
                return content;
            }
        };
    }

    @Test
    void oversizedFileIsReadOnlyPastTheCap(@TempDir Path directory) throws Exception {
        // a File or a Path, as the file-watch consumer delivers, is read one character past the
        // cap as well: the reported size is what was read, not the whole file
        Path big = Files.writeString(directory.resolve("big.txt"), "a".repeat(10_000));
        for (Object body : new Object[] { big.toFile(), big }) {
            assertThatThrownBy(() -> template.requestBodyAndHeader("direct:capped", body, "MyDocId", "doc-file"))
                    .isInstanceOf(CamelExecutionException.class)
                    .hasStackTraceContaining("exceeds maxDocumentSize (21 > 20 characters)");
        }

        Path atCap = Files.writeString(directory.resolve("ok.txt"), "twenty characters ok");
        IngestResult result = template.requestBodyAndHeader("direct:capped", atCap.toFile(), "MyDocId",
                "doc-file-at-cap", IngestResult.class);
        assertThat(result.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
    }

    @Test
    void customBatchSizeControlsTheEmbeddingRequests() {
        // 10 paragraphs of ~19 characters against maxSegmentSize=30: one segment each, and a
        // batch size of 8 must split them into exactly two embedding requests
        String text = IntStream.range(0, 10)
                .mapToObj(i -> "Paragraph number " + i)
                .collect(Collectors.joining("\n\n"));
        int callsBefore = model.embedAllCalls();

        IngestResult result = template.requestBodyAndHeader("direct:batch8", text,
                "MyDocId", "doc-batch8", IngestResult.class);

        assertThat(result.segmentsWritten()).isEqualTo(10);
        assertThat(model.embedAllCalls() - callsBefore).isEqualTo(2);
    }

    @Test
    void largeDocumentEmbedsInBatches() {
        // 40 paragraphs of ~19 characters against maxSegmentSize=30: each paragraph becomes its
        // own segment, crossing the internal batch size of 32
        String text = IntStream.range(0, 40)
                .mapToObj(i -> "Paragraph number " + i)
                .collect(Collectors.joining("\n\n"));
        int callsBefore = model.embedAllCalls();

        IngestResult result = template.requestBodyAndHeader("direct:small", text,
                "MyDocId", "doc-large", IngestResult.class);

        assertThat(result.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
        assertThat(result.segmentsWritten()).isGreaterThan(32);
        assertThat(model.embedAllCalls() - callsBefore).isGreaterThanOrEqualTo(2);
    }

    private List<EmbeddingMatch<TextSegment>> search(String text) {
        return store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(model.embed(text).content())
                .maxResults(100)
                .build()).matches();
    }
}
