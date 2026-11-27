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
package org.apache.camel.component.undertow.ws;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.component.undertow.UndertowConstants;
import org.apache.camel.test.infra.common.http.WebsocketTestClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code Reader} or {@code InputStream} body sent to several peers is read once, so that every peer receives the
 * whole message, whether the route caches streams or not.
 */
public class UndertowWsProducerSendToAllStreamTest extends BaseUndertowTest {

    private static final String MESSAGE = "Beer on stock at Apache Mall";

    static Stream<Arguments> bodies() {
        return Stream.of("direct:cached", "direct:uncached").flatMap(route -> Stream.of(
                Arguments.of(route, "Reader"),
                Arguments.of(route, "InputStream")));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("bodies")
    public void everyPeerReceivesTheWholeMessage(String route, String type) throws Exception {
        WebsocketTestClient first = new WebsocketTestClient("ws://localhost:" + getPort() + "/shop");
        WebsocketTestClient second = new WebsocketTestClient("ws://localhost:" + getPort() + "/shop");
        first.connect();
        second.connect();
        try {
            // Undertow registers a peer after the handshake, so send probes until both peers receive them
            await().atMost(20, TimeUnit.SECONDS).until(() -> {
                template.sendBodyAndHeader("direct:uncached", "probe", UndertowConstants.SEND_TO_ALL, true);
                return !first.getReceived().isEmpty() && !second.getReceived().isEmpty();
            });

            Object body = "Reader".equals(type)
                    ? new StringReader(MESSAGE)
                    : new ByteArrayInputStream(MESSAGE.getBytes(StandardCharsets.UTF_8));
            template.sendBodyAndHeader(route, body, UndertowConstants.SEND_TO_ALL, true);

            await().atMost(20, TimeUnit.SECONDS)
                    .until(() -> received(first).contains(MESSAGE) && received(second).contains(MESSAGE));
            assertTrue(received(first).stream().noneMatch(String::isEmpty), "first peer: " + received(first));
            assertTrue(received(second).stream().noneMatch(String::isEmpty), "second peer: " + received(second));
        } finally {
            first.close();
            second.close();
        }
    }

    private static List<String> received(WebsocketTestClient client) {
        return client.getReceived().stream()
                .map(o -> o instanceof byte[] ? new String((byte[]) o, StandardCharsets.UTF_8) : String.valueOf(o))
                .toList();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:cached").streamCache("true")
                        .to("undertow:ws://localhost:" + getPort() + "/shop");
                from("direct:uncached").streamCache("false")
                        .to("undertow:ws://localhost:" + getPort() + "/shop");
            }
        };
    }
}
