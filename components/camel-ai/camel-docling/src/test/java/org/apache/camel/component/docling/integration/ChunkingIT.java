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
package org.apache.camel.component.docling.integration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

import ai.docling.serve.api.chunk.response.Chunk;
import ai.docling.serve.api.chunk.response.ChunkDocumentResponse;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.docling.DoclingHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Too much resources on GitHub Actions")
class ChunkingIT extends DoclingITestSupport {

    @Test
    void chunkWithHybridChunker() throws Exception {
        Path testFile = createTestFile();

        Exchange result = template.request("direct:chunk-hybrid",
                e -> e.getIn().setHeader(DoclingHeaders.INPUT_FILE_PATH, testFile.toString()));

        @SuppressWarnings("unchecked")
        List<Chunk> chunks = result.getIn().getBody(List.class);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getText()).isNotBlank();
            assertThat(chunk.getChunkIndex()).isGreaterThanOrEqualTo(0);
        });

        LOG.info("HybridChunker produced {} chunks from markdown", chunks.size());
    }

    @Test
    void chunkWithHierarchicalChunker() throws Exception {
        Path testFile = createTestFile();

        Exchange result = template.request("direct:chunk-hierarchical",
                e -> e.getIn().setHeader(DoclingHeaders.INPUT_FILE_PATH, testFile.toString()));

        @SuppressWarnings("unchecked")
        List<Chunk> chunks = result.getIn().getBody(List.class);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getText()).isNotBlank();
        });

        LOG.info("HierarchicalChunker produced {} chunks from markdown", chunks.size());
    }

    @Test
    void chunkHybridReturnsFullResponse() throws Exception {
        Path testFile = createTestFile();

        Exchange result = template.request("direct:chunk-hybrid-full-response",
                e -> e.getIn().setHeader(DoclingHeaders.INPUT_FILE_PATH, testFile.toString()));

        ChunkDocumentResponse response = result.getIn().getBody(ChunkDocumentResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.getChunks()).isNotEmpty();
    }

    @Test
    void chunkHybridWithHeaderOverrides() throws Exception {
        Path testFile = createTestFile();

        Exchange result = template.request("direct:chunk-hybrid", e -> {
            e.getIn().setHeader(DoclingHeaders.INPUT_FILE_PATH, testFile.toString());
            e.getIn().setHeader(DoclingHeaders.CHUNKING_TOKENIZER, "sentence-transformers/all-MiniLM-L6-v2");
            e.getIn().setHeader(DoclingHeaders.CHUNKING_MAX_TOKENS, 64);
            e.getIn().setHeader(DoclingHeaders.CHUNKING_MERGE_PEERS, true);
        });

        @SuppressWarnings("unchecked")
        List<Chunk> chunks = result.getIn().getBody(List.class);

        assertThat(chunks).isNotEmpty();

        LOG.info("HybridChunker with header overrides produced {} chunks", chunks.size());
    }

    @Test
    void chunkHybridFromPdf() throws Exception {
        Path testFile = createMultiChapterPdf();

        Exchange result = template.request("direct:chunk-hybrid",
                e -> e.getIn().setHeader(DoclingHeaders.INPUT_FILE_PATH, testFile.toString()));

        @SuppressWarnings("unchecked")
        List<Chunk> chunks = result.getIn().getBody(List.class);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getText()).isNotBlank();
            assertThat(chunk.getFilename()).isNotBlank();
        });

        LOG.info("HybridChunker produced {} chunks from multi-chapter PDF", chunks.size());
    }

    @Test
    void chunkHybridWithOperationHeader() throws Exception {
        Path testFile = createTestFile();

        Map<String, Object> headers = Map.of(
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString(),
                DoclingHeaders.OPERATION, "CHUNK_HYBRID");

        Exchange result = template.request("direct:chunk-via-header",
                e -> e.getIn().setHeaders(headers));

        @SuppressWarnings("unchecked")
        List<Chunk> chunks = result.getIn().getBody(List.class);

        assertThat(chunks).isNotEmpty();
    }

    private Path createTestFile() throws Exception {
        Path tempFile = Files.createTempFile("docling-chunk-test-", ".md");
        String content = """
                # Apache Camel Overview

                Apache Camel is an open-source integration framework based on known
                Enterprise Integration Patterns. It provides a routing and mediation
                engine that allows developers to define routing rules.

                ## Components

                Camel provides over 300 components for connecting to external systems
                including databases, message brokers, cloud services, and APIs.

                ### Kafka Component

                The Kafka component enables integration with Apache Kafka for
                high-throughput messaging and event streaming.

                ### HTTP Component

                The HTTP component allows sending and receiving HTTP requests,
                supporting both synchronous and asynchronous communication.

                ## Architecture

                Camel uses a pipeline architecture where messages flow through a
                series of processors. Each processor can transform, filter, or
                route messages based on content or headers.
                """;
        Files.write(tempFile, content.getBytes());
        return tempFile;
    }

    private Path createMultiChapterPdf() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("multi_chapter_lorem.pdf")) {
            Path tempFile = Files.createTempFile("docling-chunk-test-multichapter", ".pdf");
            Files.copy(is, tempFile.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
            return tempFile;
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:chunk-hybrid")
                        .to("docling:convert?operation=CHUNK_HYBRID&contentInBody=true");

                from("direct:chunk-hierarchical")
                        .to("docling:convert?operation=CHUNK_HIERARCHICAL&contentInBody=true");

                from("direct:chunk-hybrid-full-response")
                        .to("docling:convert?operation=CHUNK_HYBRID&contentInBody=false");

                from("direct:chunk-via-header")
                        .to("docling:convert?contentInBody=true");
            }
        };
    }
}
