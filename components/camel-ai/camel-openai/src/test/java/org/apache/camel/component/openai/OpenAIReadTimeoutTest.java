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
package org.apache.camel.component.openai;

import java.net.SocketTimeoutException;
import java.time.Duration;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that readTimeout reaches the socket rather than only the configuration object: the mock accepts the
 * connection and then stalls, so only a read-phase timeout can end the call.
 */
public class OpenAIReadTimeoutTest extends CamelTestSupport {

    private static final long READ_TIMEOUT_MILLIS = 500;
    private static final long SERVER_STALL_MILLIS = 20_000;

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("stall")
            .thenRespondWith((exchange, input) -> {
                try {
                    Thread.sleep(SERVER_STALL_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "{}";
            })
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:stall")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&maxRetries=0"
                            + "&readTimeout=" + READ_TIMEOUT_MILLIS
                            + "&baseUrl=" + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void readTimeoutEndsTheCallWhileTheServerIsStillStalling() {
        long startedAt = System.nanoTime();
        Exchange result = template.request("direct:stall", e -> e.getIn().setBody("stall"));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        // OkHttp reports the timeout somewhere in the cause chain, not always as the root, so search the whole chain
        assertThat(result.getException(SocketTimeoutException.class)).isNotNull();
        assertThat(elapsed).isLessThan(Duration.ofMillis(SERVER_STALL_MILLIS / 2));
    }
}
