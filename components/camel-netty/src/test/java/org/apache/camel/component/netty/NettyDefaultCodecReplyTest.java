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
package org.apache.camel.component.netty;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The default codec (no textline, no custom encoders) only writes a String: a reply of another type, including the
 * exception of a failed exchange, is converted to a String instead of failing the write and leaving the client waiting.
 */
class NettyDefaultCodecReplyTest extends BaseNettyTest {

    private static final String DETAIL = "the-internal-detail-a-peer-must-not-see";

    @RegisterExtension
    AvailablePortFinder.Port unmutedPort = AvailablePortFinder.find();

    @RegisterExtension
    AvailablePortFinder.Port mapPort = AvailablePortFinder.find();

    @RegisterExtension
    AvailablePortFinder.Port bytesPort = AvailablePortFinder.find();

    @Test
    void aFailedExchangeRepliesTheMutedException() {
        assertEquals("java.lang.Exception: Exchange processing failed", request(port.getPort()));
    }

    @Test
    void aFailedExchangeRepliesTheExceptionWhenNotMuted() {
        assertEquals("java.lang.IllegalStateException: " + DETAIL, request(unmutedPort.getPort()));
    }

    @Test
    void aMapReplyIsConvertedToString() {
        assertEquals("{greeting=Bye World}", request(mapPort.getPort()));
    }

    @Test
    void aByteArrayReplyIsConvertedToString() {
        assertEquals("Bye World", request(bytesPort.getPort()));
    }

    private String request(int port) {
        // without the conversion the client gets no reply and fails with a ReadTimeoutException
        return template.requestBody("netty:tcp://localhost:" + port + "?sync=true&requestTimeout=5000", "Hello World",
                String.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty:tcp://localhost:{{port}}?sync=true")
                        .throwException(new IllegalStateException(DETAIL));

                fromF("netty:tcp://localhost:%s?sync=true&muteException=false", unmutedPort.getPort())
                        .throwException(new IllegalStateException(DETAIL));

                fromF("netty:tcp://localhost:%s?sync=true", mapPort.getPort())
                        .process(exchange -> exchange.getMessage().setBody(Map.of("greeting", "Bye World")));

                fromF("netty:tcp://localhost:%s?sync=true", bytesPort.getPort())
                        .process(exchange -> exchange.getMessage().setBody("Bye World".getBytes(StandardCharsets.UTF_8)));
            }
        };
    }
}
