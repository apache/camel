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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class NettyCustomCorrelationManagerTest extends BaseNettyTest {

    @BindToRegistry("myManager")
    private final MyCorrelationManager myManager = new MyCorrelationManager();

    @Test
    public void testCustomCorrelationManager() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // the messages can be processed in any order
        mock.expectedBodiesReceivedInAnyOrder("Bye A", "Bye B", "Bye C");
        // the custom manager should be used
        mock.allMessages().header("manager").isEqualTo(myManager);
        // check that the request and reply are correlated correctly
        mock.allMessages().predicate(exchange -> {
            String request = exchange.getMessage().getHeader("request", String.class);
            String reply = exchange.getMessage().getBody(String.class);
            return reply.endsWith(request);
        });

        template.sendBodyAndHeader("seda:start", "A", "request", "A");
        template.sendBodyAndHeader("seda:start", "B", "request", "B");
        template.sendBodyAndHeader("seda:start", "C", "request", "C");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start").log("before ${body}").to("netty:tcp://localhost:{{port}}?textline=true&sync=true&producerPoolEnabled=false&correlationManager=#myManager")
                    .log("after ${body}").to("mock:result");

                from("netty:tcp://localhost:{{port}}?textline=true&sync=true").transform(body().prepend("Bye "));
            }
        };
    }

    private static final class MyCorrelationManager implements NettyCamelStateCorrelationManager {

        private volatile NettyCamelState stateA;
        private volatile NettyCamelState stateB;
        private volatile NettyCamelState stateC;
        private volatile Channel channel;

        @Override
        public void putState(Channel channel, NettyCamelState state) {
            if (this.channel != null && this.channel != channel) {
                throw new IllegalStateException("Should use same channel as producer pool is disabled");
            }
            this.channel = channel;

            String body = state.getExchange().getMessage().getBody(String.class);
            if ("A".equals(body)) {
                stateA = state;
            } else if ("B".equals(body)) {
                stateB = state;
            } else if ("C".equals(body)) {
                stateC = state;
            }
        }

        @Override
        public void removeState(ChannelHandlerContext ctx, Channel channel) {
            // noop
        }

        @Override
        public NettyCamelState getState(ChannelHandlerContext ctx, Channel channel, Object msg) {
            String body = msg.toString();
            if (body.endsWith("A")) {
                stateA.getExchange().getMessage().setHeader("manager", this);
                return stateA;
            } else if (body.endsWith("B")) {
                stateB.getExchange().getMessage().setHeader("manager", this);
                return stateB;
            } else if (body.endsWith("C")) {
                stateC.getExchange().getMessage().setHeader("manager", this);
                return stateC;
            }
            return null;
        }

        @Override
        public NettyCamelState getState(ChannelHandlerContext ctx, Channel channel, Throwable cause) {
            // noop
            return null;
        }
    }
}
