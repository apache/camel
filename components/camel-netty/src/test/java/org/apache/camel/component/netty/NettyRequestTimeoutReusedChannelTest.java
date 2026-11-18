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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.handler.timeout.ReadTimeoutException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The requestTimeout applies to every request on a channel that is used again without a borrow from the producer pool:
 * with producerPoolEnabled=false, and with reuseChannel=true for the second call in the same exchange.
 */
class NettyRequestTimeoutReusedChannelTest extends BaseNettyTest {

    // the server answers a slow request when the test ends, or after 5 seconds
    private final CountDownLatch slowReply = new CountDownLatch(1);

    @AfterEach
    void releaseServer() {
        slowReply.countDown();
    }

    @Test
    void testRequestTimeoutWithoutProducerPool() {
        Endpoint endpoint = resolveMandatoryEndpoint(
                "netty:tcp://localhost:{{port}}?textline=true&sync=true&requestTimeout=200&producerPoolEnabled=false");

        assertEquals("Bye World", template.requestBody(endpoint, "Hello", String.class));
        // the same channel is used again: the request must time out
        assertTimeout(() -> template.requestBody(endpoint, "Hello Camel", String.class));
    }

    @Test
    void testRequestTimeoutWithReuseChannel() {
        assertTimeout(() -> template.requestBody("direct:reuse", "Hello", String.class));
    }

    private static void assertTimeout(Runnable request) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class, request::run);
        ReadTimeoutException cause = assertIsInstanceOf(ReadTimeoutException.class, e.getCause());
        assertNotNull(cause);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty:tcp://localhost:{{port}}?textline=true&sync=true")
                        .process(exchange -> {
                            if (exchange.getIn().getBody(String.class).contains("Camel")) {
                                slowReply.await(5, TimeUnit.SECONDS);
                            }
                        })
                        .transform().constant("Bye World");

                // two calls in the same exchange on the same channel
                from("direct:reuse")
                        .to("netty:tcp://localhost:{{port}}?textline=true&sync=true&requestTimeout=200&reuseChannel=true")
                        .setBody(constant("Hello Camel"))
                        .to("netty:tcp://localhost:{{port}}?textline=true&sync=true&requestTimeout=200&reuseChannel=true");
            }
        };
    }
}
