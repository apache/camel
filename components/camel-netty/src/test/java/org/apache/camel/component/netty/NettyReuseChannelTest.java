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

import java.util.ArrayList;
import java.util.List;

import io.netty.channel.Channel;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NettyReuseChannelTest extends BaseNettyTest {

    private final List<Channel> channels = new ArrayList<>();

    @Test
    public void testReuse() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello Hello World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Hello Hello World");

        template.sendBody("direct:start", "World\n");

        MockEndpoint.assertIsSatisfied(context);

        assertTrue(notify.matchesWaitTime());

        assertEquals(2, channels.size());
        assertSame(channels.get(0), channels.get(1), "Should reuse channel");
        assertFalse(channels.get(0).isOpen(), "And closed when routing done");
        assertFalse(channels.get(1).isOpen(), "And closed when routing done");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("netty:tcp://localhost:{{port}}?textline=true&sync=true&reuseChannel=true&disconnect=true")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                                Channel channel = exchange.getProperty(NettyConstants.NETTY_CHANNEL, Channel.class);
                                channels.add(channel);
                                assertTrue(channel.isActive(), "Should be active");
                            }
                        })
                        .to("mock:a")
                        .to("netty:tcp://localhost:{{port}}?textline=true&sync=true&reuseChannel=true&disconnect=true")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                                Channel channel = exchange.getProperty(NettyConstants.NETTY_CHANNEL, Channel.class);
                                channels.add(channel);
                                assertTrue(channel.isActive(), "Should be active");
                            }
                        })
                        .to("mock:b");

                from("netty:tcp://localhost:{{port}}?textline=true&sync=true")
                        .transform(body().prepend("Hello "))
                        .to("mock:result");
            }
        };
    }
}
