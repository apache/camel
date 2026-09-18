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
package org.apache.camel.component.mina;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The consumer used to write {@code exchange.getException()} straight back over the socket, so a route failure handed
 * the remote peer the exception - its class and message over a textline codec, and its serialised form, cause chain
 * included, over the object codec.
 * <p>
 * {@code muteException} defaults to true, matching the http consumers aligned by CAMEL-23651.
 */
class MinaMuteExceptionTest extends BaseMinaTest {

    private static final String DETAIL = "the-internal-detail-a-peer-must-not-see";

    /** getNextPort() allocates a fresh port on every call, so the second consumer's port is resolved once. */
    private Integer unmutedPort;

    @Test
    void aFailedExchangeDoesNotWriteTheExceptionByDefault() {
        String reply = template.requestBody(mutedUri(), "hello", String.class);

        assertThat(reply)
                .as("the reply must carry neither the class nor the message of the route's exception")
                .doesNotContain(DETAIL)
                .doesNotContain("IllegalStateException");
        // still a reply rather than a dropped connection, so a synchronous peer is not left to time out
        assertThat(reply).isNotEmpty();
    }

    @Test
    void muteExceptionFalseWritesTheExceptionAsBefore() {
        String reply = template.requestBody(unmutedUri(), "hello", String.class);

        assertThat(reply).contains(DETAIL).contains("IllegalStateException");
    }

    private String mutedUri() {
        return uri(getPort(), "");
    }

    private String unmutedUri() {
        if (unmutedPort == null) {
            unmutedPort = getNextPort();
        }
        return uri(unmutedPort, "&muteException=false");
    }

    private static String uri(int port, String extra) {
        return String.format("mina:tcp://localhost:%1$s?sync=true&textline=true%2$s", port, extra);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(mutedUri())
                        .process(e -> {
                            throw new IllegalStateException(DETAIL);
                        });
                from(unmutedUri())
                        .process(e -> {
                            throw new IllegalStateException(DETAIL);
                        });
            }
        };
    }
}
