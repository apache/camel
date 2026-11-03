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

import org.apache.camel.CamelExchangeException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With useByteBuf=true a body that cannot be converted to a ByteBuf fails the exchange instead of sending nothing.
 */
class NettyUseByteBufTest extends BaseNettyTest {

    @RegisterExtension
    AvailablePortFinder.Port replyPort = AvailablePortFinder.find();

    @Test
    void aStringBodyIsSent() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("netty:tcp://localhost:{{port}}?sync=false&useByteBuf=true", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void aBodyThatCannotBeConvertedFailsTheExchange() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("netty:tcp://localhost:{{port}}?sync=false&useByteBuf=true", new Order()));
        assertInstanceOf(InvalidPayloadException.class, e.getCause());
    }

    @Test
    void aReplyThatCannotBeConvertedClosesTheChannel() {
        // the client is not left waiting until its request timeout: the consumer closes the channel
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("netty:tcp://localhost:" + replyPort.getPort() + "?sync=true&requestTimeout=5000",
                        "Hello World"));
        CamelExchangeException cause = assertInstanceOf(CamelExchangeException.class, e.getCause());
        assertTrue(cause.getMessage().startsWith("No response received from remote server"), cause.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty:tcp://localhost:{{port}}?sync=false")
                        .to("mock:result");

                fromF("netty:tcp://localhost:%s?sync=true&useByteBuf=true&disconnectOnNoReply=false", replyPort.getPort())
                        .process(exchange -> exchange.getMessage().setBody(new Order()));
            }
        };
    }

    private static final class Order {
        @Override
        public String toString() {
            return "Order";
        }
    }
}
