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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.deleteDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LangChain4jEmbeddingsBodyTest extends CamelTestSupport {

    private static final String FILE_DIR = "target/data/langchain4j-embeddings-body";

    @Override
    protected void doPreSetup() throws Exception {
        deleteDirectory(FILE_DIR);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        LangChain4jEmbeddingsComponent component
                = context.getComponent(LangChain4jEmbeddings.SCHEME, LangChain4jEmbeddingsComponent.class);
        component.getConfiguration().setEmbeddingModel(new AllMiniLmL6V2EmbeddingModel());

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("file:" + FILE_DIR + "?initialDelay=0&delete=true")
                        .to("langchain4j-embeddings:file")
                        .to("mock:file");

                from("direct:batchThenSingle")
                        .to("langchain4j-embeddings:batch")
                        .setBody(constant("hello"))
                        .to("langchain4j-embeddings:single");

                from("direct:singleThenBatch")
                        .to("langchain4j-embeddings:single")
                        .setBody(constant(List.of("hello", "world")))
                        .to("langchain4j-embeddings:batch");
            }
        };
    }

    @Test
    void bytesBodyIsEmbeddedAsText() {
        Message result = embed("hello world".getBytes(StandardCharsets.UTF_8));

        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT, TextSegment.class).text())
                .isEqualTo("hello world");
        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.VECTOR, float[].class))
                .isEqualTo(embed("hello world").getHeader(LangChain4jEmbeddingsHeaders.VECTOR, float[].class));
    }

    @Test
    void streamBodyIsEmbeddedAsText() {
        Message result = embed(new ByteArrayInputStream("hello stream".getBytes(StandardCharsets.UTF_8)));

        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT, TextSegment.class).text())
                .isEqualTo("hello stream");
        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, Embedding.class)).isNotNull();
    }

    @Test
    void textSegmentBodyIsKept() {
        TextSegment segment = TextSegment.from("hello segment");

        Message result = embed(segment);

        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT)).isSameAs(segment);
    }

    @Test
    void fileConsumerBodyIsEmbeddedAsText() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:file");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("file:" + FILE_DIR, "hello file", Exchange.FILE_NAME, "hello.txt");

        mock.assertIsSatisfied();
        Message result = mock.getReceivedExchanges().get(0).getMessage();
        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT, TextSegment.class).text())
                .isEqualTo("hello file");
        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, Embedding.class)).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void batchOfBytesAndStreamsIsEmbeddedAsText() {
        InputStream stream = new ByteArrayInputStream("from a stream".getBytes(StandardCharsets.UTF_8));

        Message result = embed(List.of("from a string", "from bytes".getBytes(StandardCharsets.UTF_8), stream));

        List<TextSegment> segments = result.getHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENTS, List.class);
        assertThat(segments).extracting(TextSegment::text)
                .containsExactly("from a string", "from bytes", "from a stream");
        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDINGS, List.class)).hasSize(3);
    }

    @Test
    void noBodyFails() {
        assertThatThrownBy(() -> template.requestBody("langchain4j-embeddings:test", (Object) null))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(InvalidPayloadException.class);
    }

    @Test
    void mapBodyFails() {
        Exchange exchange = send(Map.of("text", "hello map"));

        assertThat(exchange.getException()).isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("convert the body to text");
        assertThat(exchange.getMessage().getHeader(LangChain4jEmbeddingsHeaders.EMBEDDING)).isNull();
        assertThat(exchange.getMessage().getHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT)).isNull();
    }

    @Test
    void pojoBodyFails() {
        Exchange exchange = send(new Note("hello pojo"));

        assertThat(exchange.getException()).isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("convert the body to text");
        assertThat(exchange.getMessage().getHeader(LangChain4jEmbeddingsHeaders.EMBEDDING)).isNull();
        assertThat(exchange.getMessage().getHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT)).isNull();
    }

    @Test
    void batchWithAMapElementFails() {
        Exchange exchange = send(List.of("hello", Map.of("text", "hello map")));

        assertThat(exchange.getException()).isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("the element at index 1 of the List body is java.util")
                .hasMessageContaining("convert the body to text");
        assertThat(exchange.getMessage().getHeader(LangChain4jEmbeddingsHeaders.EMBEDDINGS)).isNull();
        assertThat(exchange.getMessage().getHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENTS)).isNull();
    }

    @Test
    void singleAfterBatchRemovesTheBatchHeaders() {
        Message result = fluentTemplate.to("direct:batchThenSingle")
                .withBody(List.of("one", "two"))
                .request(Message.class);

        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDINGS)).isNull();
        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENTS)).isNull();
        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, Embedding.class)).isNotNull();
        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT, TextSegment.class).text())
                .isEqualTo("hello");
    }

    @Test
    void batchAfterSingleRemovesTheSingleHeaders() {
        Message result = fluentTemplate.to("direct:singleThenBatch")
                .withBody("one")
                .request(Message.class);

        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDING)).isNull();
        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.VECTOR)).isNull();
        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT)).isNull();
        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDINGS, List.class)).hasSize(2);
        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENTS, List.class)).hasSize(2);
    }

    @Test
    void headerJavaTypesHaveNoLeadingSpace() throws Exception {
        String json;
        try (InputStream is = getClass().getResourceAsStream(
                "/META-INF/org/apache/camel/component/langchain4j/embeddings/langchain4j-embeddings.json")) {
            json = IOHelper.loadText(is);
        }

        assertThat(json).contains("\"javaType\": \"dev.langchain4j.data.segment.TextSegment\"")
                .doesNotContain("\"javaType\": \" ");
    }

    private Exchange send(Object body) {
        return template.send("langchain4j-embeddings:test", e -> e.getMessage().setBody(body));
    }

    private record Note(String text) {
    }

    private Message embed(Object body) {
        return fluentTemplate.to("langchain4j-embeddings:test")
                .withBody(body)
                .request(Message.class);
    }
}
