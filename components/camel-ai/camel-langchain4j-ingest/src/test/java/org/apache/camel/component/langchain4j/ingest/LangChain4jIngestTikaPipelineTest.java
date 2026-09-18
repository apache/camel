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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The endpoint behind a hand-rolled parse route, against real Tika output — the route shape the component docs
 * describe: capture the id before the parse, {@code tika:parse} to plain text at a pinned encoding,
 * {@link TikaTextDecode}, then the {@code langchain4j-ingest} endpoint.
 */
class LangChain4jIngestTikaPipelineTest {

    @TempDir
    Path directory;

    private RouteBuilder parseRoute(String pipeline) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // no charset on the file endpoint: the format is the parser's business, and a
                // charset conversion would corrupt a binary document
                from("file:" + directory + "?noop=true&readLock=changed")
                        .setProperty(LangChain4jIngest.DOCUMENT_ID_PROPERTY, header(Exchange.FILE_NAME))
                        .to("tika:parse?tikaParseOutputFormat=text&tikaParseOutputEncoding=UTF-8")
                        .process(new TikaTextDecode())
                        .to("langchain4j-ingest:" + pipeline);
            }
        };
    }

    @Test
    void tikaParsesARealDocumentIntoTheEndpoint() throws Exception {
        Files.writeString(directory.resolve("notice.html"),
                "<html><head><title>Secret Title</title></head><body>"
                                                            + "<p>First paragraph about camels.</p>"
                                                            + "<p>Second paragraph about routing.</p>"
                                                            + "</body></html>");

        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        DeterministicEmbeddingModel model = new DeterministicEmbeddingModel(16);
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("store", store);
            context.getRegistry().bind("model", model);
            context.addRoutes(parseRoute("html-docs"));
            NotifyBuilder ingested = new NotifyBuilder(context).whenDone(1).create();

            context.start();

            assertThat(ingested.matches(15, TimeUnit.SECONDS)).as("document ingested").isTrue();

            String storedText = store.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(model.embed("First paragraph about camels.").content())
                    .maxResults(100)
                    .build()).matches().stream()
                    .map(match -> match.embedded().text())
                    .collect(Collectors.joining("\n"));

            assertThat(storedText)
                    .contains("First paragraph about camels.")
                    .contains("Second paragraph about routing.")
                    // Tika's text output keeps the body subtree only - the <title> lives in <head>
                    .doesNotContain("Secret Title")
                    // no markup survives the parse
                    .doesNotContain("<p>").doesNotContain("</")
                    // block boundaries hold on real Tika output, not just on hand-made XHTML
                    .doesNotContain("camels.Second");

            EmbeddingMatch<TextSegment> top = store.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(model.embed("First paragraph about camels.").content())
                    .maxResults(1)
                    .build()).matches().get(0);
            assertThat(top.embedded().metadata().getString(LangChain4jIngest.METADATA_DOCUMENT_ID))
                    .isEqualTo("notice.html");
            assertThat(top.embedded().metadata().getString(LangChain4jIngest.METADATA_PIPELINE))
                    .isEqualTo("html-docs");
        }
    }

    /**
     * A binary format: a PDF dropped into the watched directory is parsed to text in-process and ingested like any text
     * file, the file name as the document id. The fixture is a checked-in one-page PDF carrying the asserted token; it
     * needs {@code tika-parser-pdf-module} on the classpath — the per-format module story the docs describe.
     */
    @Test
    void pdfIsParsedAndIngested() throws Exception {
        try (var pdf = getClass().getResourceAsStream("/pump.pdf")) {
            Files.copy(pdf, directory.resolve("pump.pdf"));
        }

        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        DeterministicEmbeddingModel model = new DeterministicEmbeddingModel(16);
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("store", store);
            context.getRegistry().bind("model", model);
            context.addRoutes(parseRoute("pdf-docs"));
            NotifyBuilder ingested = new NotifyBuilder(context).whenDone(1).create();

            context.start();

            // generous on purpose: the first PDF parse on a fresh machine builds PDFBox's
            // on-disk font cache, which alone can exceed the usual timeout
            assertThat(ingested.matches(120, TimeUnit.SECONDS)).as("PDF ingested").isTrue();

            EmbeddingMatch<TextSegment> top = store.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(model.embed("What does the pump tolerate?").content())
                    .maxResults(1)
                    .build()).matches().get(0);
            assertThat(top.embedded().text()).contains("DELTA-5");
            assertThat(top.embedded().metadata().getString(LangChain4jIngest.METADATA_DOCUMENT_ID))
                    .isEqualTo("pump.pdf");
        }
    }
}
