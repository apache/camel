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

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.docling.DoclingComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The endpoint behind a hand-rolled docling route, against a stubbed Docling Serve instance (the real container image
 * is a multi-gigabyte download): the id is captured before the parse, the body pinned to bytes and the
 * {@code CamelDocling*} control headers swept — none of them may be decided by a consumer-delivered payload — then the
 * serve API answers markdown and the endpoint ingests it. The stub answers every conversion with one sentinel sentence,
 * so finding that sentence in the store proves the whole chain.
 */
class LangChain4jIngestDoclingPipelineTest {

    private static final String CONVERTED_MARKDOWN = "The EPSILON-2 valve seals at 80 bar.";

    @TempDir
    Path directory;

    @Test
    void doclingConvertsThroughTheServeApiIntoTheEndpoint() throws Exception {
        AtomicInteger conversions = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        // a catch-all handler rather than the exact convert path, so the stub survives client
        // path changes; the response mirrors a real docling-serve convert answer
        server.createContext("/", httpExchange -> {
            conversions.incrementAndGet();
            byte[] response = ("{\"document\":{\"filename\":\"scan.pdf\",\"md_content\":\""
                               + CONVERTED_MARKDOWN + "\",\"json_content\":null,\"html_content\":null,"
                               + "\"text_content\":null,\"doctags_content\":null},\"status\":\"success\","
                               + "\"errors\":[],\"processing_time\":0.01,\"timings\":{}}")
                    .getBytes(StandardCharsets.UTF_8);
            httpExchange.getResponseHeaders().add("Content-Type", "application/json");
            httpExchange.sendResponseHeaders(200, response.length);
            try (OutputStream body = httpExchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();

        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        DeterministicEmbeddingModel model = new DeterministicEmbeddingModel(16);
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("store", store);
            context.getRegistry().bind("model", model);
            DoclingComponent docling = context.getComponent("docling", DoclingComponent.class);
            // the component's default mode executes a local docling CLI; this route uses the
            // serve API
            docling.getConfiguration().setUseDoclingServe(true);
            docling.getConfiguration().setDoclingServeUrl("http://localhost:" + server.getAddress().getPort());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("file:" + directory + "?noop=true&readLock=changed")
                            .setProperty(LangChain4jIngest.DOCUMENT_ID_PROPERTY, header(Exchange.FILE_NAME))
                            .convertBodyTo(byte[].class)
                            .removeHeaders("CamelDocling*")
                            .to("docling:convert?operation=CONVERT_TO_MARKDOWN&contentInBody=true")
                            .to("langchain4j-ingest:scans");
                }
            });
            Files.writeString(directory.resolve("scan.pdf"), "opaque binary payload, the stub answers regardless");
            NotifyBuilder ingested = new NotifyBuilder(context).whenDone(1).create();

            context.start();

            assertThat(ingested.matches(30, TimeUnit.SECONDS)).as("document ingested").isTrue();
            assertThat(conversions.get()).as("one conversion request reached the serve stub").isEqualTo(1);

            EmbeddingMatch<TextSegment> top = store.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(model.embed(CONVERTED_MARKDOWN).content())
                    .maxResults(1)
                    .build()).matches().get(0);
            assertThat(top.embedded().text()).contains("EPSILON-2");
            assertThat(top.embedded().metadata().getString(LangChain4jIngest.METADATA_DOCUMENT_ID))
                    .isEqualTo("scan.pdf");
            assertThat(top.embedded().metadata().getString(LangChain4jIngest.METADATA_PIPELINE))
                    .isEqualTo("scans");
        } finally {
            server.stop(0);
        }
    }
}
