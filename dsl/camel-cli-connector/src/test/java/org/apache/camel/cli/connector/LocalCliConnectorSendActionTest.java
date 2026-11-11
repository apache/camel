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
package org.apache.camel.cli.connector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The send action with a repeat or a folder of messages (camel cmd send --repeat --interval, CAMEL-26767): it runs on a
 * thread of its own and answers with a summary.
 */
class LocalCliConnectorSendActionTest {

    @TempDir
    Path dir;

    private final List<String> received = new CopyOnWriteArrayList<>();
    private CamelContext context;
    private LocalCliConnector connector;

    @BeforeEach
    void start() throws Exception {
        context = new DefaultCamelContext();
        // the action is dispatched here: a connector of the context would poll the {pid} files of other tests
        var disabled = new DefaultCliConnectorFactory();
        disabled.setEnabled(false);
        context.getCamelContextExtension().addContextPlugin(CliConnectorFactory.class, disabled);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:refunds").routeId("refunds")
                        .process(e -> received.add(e.getMessage().getBody(String.class) + " "
                                                   + e.getMessage().getHeader("country", "-", String.class)))
                        .filter(body().contains("bad")).throwException(new IllegalStateException("bad refund"));
            }
        });
        context.start();
        connector = new LocalCliConnector(new DefaultCliConnectorFactory()) {
            @Override
            protected CliConnectorTransport createTransport(String name) {
                // the action is dispatched directly: the file transport would poll the {pid} files and take the
                // action files of other tests running at the same time
                return new CliConnectorTransport() {
                    @Override
                    public void configure(
                            CamelContext camelContext, CliActionDispatcher dispatcher,
                            CliSnapshotProducer snapshots, Runnable shutdown) {
                    }

                    @Override
                    public void start() {
                    }

                    @Override
                    public void stop() {
                    }
                };
            }
        };
        connector.setCamelContext(context);
        connector.start();
    }

    @AfterEach
    void stop() {
        connector.stop();
        context.stop();
    }

    private JsonObject send(JsonObject request) throws Exception {
        AtomicReference<JsonObject> answer = new AtomicReference<>();
        assertThat(connector.dispatchAsync(request, answer::set).get(20, TimeUnit.SECONDS)).isTrue();
        return answer.get();
    }

    private static JsonObject request(String body) {
        JsonObject request = new JsonObject();
        request.put("action", "send");
        request.put("endpoint", "direct:refunds");
        request.put("exchangePattern", "InOnly");
        request.put("body", body);
        return request;
    }

    @Test
    void theSameMessageAgainWithAPause() throws Exception {
        JsonObject request = request("R-1");
        request.put("repeat", 5);
        request.put("interval", 20);

        JsonObject answer = send(request);

        assertThat(answer.getString("status")).as(answer.toJson()).isEqualTo("success");
        assertThat(answer.getLong("messages")).isEqualTo(5);
        assertThat(answer.getLong("sent")).isEqualTo(5);
        assertThat(answer.getLong("failed")).isZero();
        assertThat(answer.getString("endpoint")).isEqualTo("direct://refunds");
        // four pauses of 20 millis
        assertThat(answer.getLong("elapsed")).isGreaterThanOrEqualTo(80);
        assertThat(received).hasSize(5).containsOnly("R-1 -");
    }

    @Test
    void aPauseDrawnFromARange() throws Exception {
        JsonObject request = request("R-1");
        request.put("repeat", 5);
        request.put("interval", 20);
        request.put("intervalMax", 60);

        JsonObject answer = send(request);

        assertThat(answer.getLong("sent")).isEqualTo(5);
        // four pauses of 20 to 60 millis
        assertThat(answer.getLong("elapsed")).isGreaterThanOrEqualTo(80);
    }

    @Test
    void aRepeatedFileNameIsNumbered() throws Exception {
        // a file endpoint writes one file per message (CAMEL-26795)
        Path inbox = dir.resolve("inbox");
        JsonObject request = request("{}");
        request.put("endpoint", "file:" + inbox);
        request.put("repeat", 3);
        JsonObject header = new JsonObject();
        header.put("key", "CamelFileName");
        header.put("value", "order.json");
        request.put("headers", new JsonArray(List.of(header)));

        JsonObject answer = send(request);

        assertThat(answer.getLong("sent")).as(answer.toJson()).isEqualTo(3);
        try (var files = Files.list(inbox)) {
            assertThat(files.map(f -> f.getFileName().toString()).sorted())
                    .containsExactly("order-1.json", "order-2.json", "order-3.json");
        }
        assertThat(RepeatedSend.numbered("order", 2)).isEqualTo("order-2");
        assertThat(RepeatedSend.numbered("in.box/order", 2)).isEqualTo("in.box/order-2");
        assertThat(RepeatedSend.numbered(".hidden", 2)).isEqualTo(".hidden-2");
    }

    @Test
    void anIntervalIsATimeOrARange() {
        assertThat(RepeatedSend.interval("200")).containsExactly(200, 200);
        assertThat(RepeatedSend.interval("200ms")).containsExactly(200, 200);
        assertThat(RepeatedSend.interval("100..500")).containsExactly(100, 500);
        assertThat(RepeatedSend.interval("100ms..2s")).containsExactly(100, 2000);
        assertThat(RepeatedSend.interval(null)).containsExactly(0, 0);
        assertThatThrownBy(() -> RepeatedSend.interval("500..100")).hasMessageContaining("from the shortest to the longest");
        assertThatThrownBy(() -> RepeatedSend.interval("1s..2m")).hasMessageContaining("at most 60s");
        assertThatThrownBy(() -> RepeatedSend.interval("soon")).hasMessageContaining("not a time or a range");
        assertThatThrownBy(() -> RepeatedSend.interval("1..2..3")).hasMessageContaining("not a time or a range");
    }

    @Test
    void theFilesOfAFolderInNameOrderWithTheirHeaders() throws Exception {
        Path feed = Files.createDirectories(dir.resolve("refunds-fails"));
        Files.writeString(feed.resolve("002.json"), "R-2");
        Files.writeString(feed.resolve("001.json"), "R-1");
        Files.writeString(feed.resolve("001.properties"), "country=ES\n");
        Files.writeString(feed.resolve(".hidden"), "not a message");

        JsonObject answer = send(request("file:" + feed));

        assertThat(answer.getLong("sent")).as(answer.toJson()).isEqualTo(2);
        assertThat(received).containsExactly("R-1 ES", "R-2 -");

        // a repeat goes round the files again, and a header of the action goes with every message
        received.clear();
        JsonObject request = request("file:" + feed);
        request.put("repeat", 5);
        JsonObject header = new JsonObject();
        header.put("key", "country");
        header.put("value", "DK");
        request.put("headers", new JsonArray(List.of(header)));
        answer = send(request);
        assertThat(answer.getLong("sent")).isEqualTo(5);
        assertThat(received).containsExactly("R-1 ES", "R-2 DK", "R-1 ES", "R-2 DK", "R-1 ES");
    }

    @Test
    void theFailuresAreCountedWithTheirErrors() throws Exception {
        Path feed = Files.createDirectories(dir.resolve("mixed"));
        Files.writeString(feed.resolve("1.txt"), "good");
        Files.writeString(feed.resolve("2.txt"), "bad");
        JsonObject request = request("file:" + feed);
        request.put("repeat", 4);

        JsonObject answer = send(request);

        assertThat(answer.getString("status")).isEqualTo("failed");
        assertThat(answer.getLong("sent")).isEqualTo(4);
        assertThat(answer.getLong("failed")).isEqualTo(2);
        JsonObject error = (JsonObject) answer.getCollection("errors").iterator().next();
        assertThat(error.getString("message")).isEqualTo("IllegalStateException: bad refund");
        assertThat(error.getLong("count")).isEqualTo(2);
    }

    @Test
    void anEndpointThatIsNotThereStopsAtTheFirstMessage() throws Exception {
        JsonObject request = request("R-1");
        request.put("endpoint", "nosuchroute");
        request.put("repeat", 10);

        JsonObject answer = send(request);

        assertThat(answer.getLong("sent")).isEqualTo(1);
        assertThat(answer.getLong("failed")).isEqualTo(1);
    }

    @Test
    void whatCannotBeSent() throws Exception {
        JsonObject poll = request("R-1");
        poll.put("repeat", 3);
        poll.put("poll", true);
        assertThatThrownBy(() -> send(poll)).isInstanceOf(ExecutionException.class)
                .hasMessageContaining("A poll does not repeat");

        JsonObject many = request("R-1");
        many.put("repeat", RepeatedSend.MAX_MESSAGES + 1);
        assertThatThrownBy(() -> send(many)).hasMessageContaining("At most 10000 messages");

        Files.createDirectories(dir.resolve("empty"));
        assertThatThrownBy(() -> send(request("file:" + dir.resolve("empty")))).hasMessageContaining("No messages");
        assertThat(received).isEmpty();
    }

    @Test
    void oneMessageIsAPlainSend() throws Exception {
        JsonObject request = request("R-1");
        request.put("repeat", 1);
        assertThat(RepeatedSend.isRepeated(request)).isFalse();

        JsonObject answer = send(request);

        // the answer of a single send, with no summary
        assertThat(answer.getString("status")).isEqualTo("success");
        assertThat(answer.containsKey("sent")).isFalse();
        assertThat(received).containsExactly("R-1 -");
    }
}
