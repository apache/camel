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
import org.junit.Test;

public class NettyReuseChannelTest extends BaseNettyTest {

    private final List<Channel> channels = new ArrayList<>();

    @Test
    public void testReuse() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello Hello World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Hello Hello World");

        template.sendBody("direct:start", "World\n");

        assertMockEndpointsSatisfied();

        assertTrue(notify.matchesWaitTime());

        assertEquals(2, channels.size());
        assertSame("Should reuse channel", channels.get(0), channels.get(1));
        assertFalse("And closed when routing done", channels.get(0).isOpen());
        assertFalse("And closed when routing done", channels.get(1).isOpen());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("netty:tcp://localhost:{{port}}?textline=true&sync=true&reuseChannel=true&disconnect=true")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            Channel channel = exchange.getProperty(NettyConstants.NETTY_CHANNEL, Channel.class);
                            channels.add(channel);
                            assertTrue("Should be active", channel.isActive());
                        }
                    })
                    .to("mock:a")
                    .to("netty:tcp://localhost:{{port}}?textline=true&sync=true&reuseChannel=true&disconnect=true")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            Channel channel = exchange.getProperty(NettyConstants.NETTY_CHANNEL, Channel.class);
                            channels.add(channel);
                            assertTrue("Should be active", channel.isActive());
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
