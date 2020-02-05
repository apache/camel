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

import java.nio.charset.Charset;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.serialization.ClassResolvers;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.netty.codec.ObjectDecoder;
import org.apache.camel.component.netty.codec.ObjectEncoder;
import org.junit.Test;

public class NettyTransferExchangeOptionTest extends BaseNettyTest {

    @Test
    public void testNettyTransferExchangeOptionWithoutException() throws Exception {
        Exchange exchange = sendExchange(false);
        assertExchange(exchange, false);
    }

    @Test
    public void testNettyTransferExchangeOptionWithException() throws Exception {
        Exchange exchange = sendExchange(true);
        assertExchange(exchange, true);
    }

    @BindToRegistry("encoder")
    public ChannelHandler getEncoder() throws Exception {
        return new ShareableChannelHandlerFactory(new ObjectEncoder());
    }

    @BindToRegistry("decoder")
    public ChannelHandler getDecoder() throws Exception {
        return new DefaultChannelHandlerFactory() {
            @Override
            public ChannelHandler newChannelHandler() {
                return new ObjectDecoder(ClassResolvers.weakCachingResolver(null));
            }
        };
    }

    private Exchange sendExchange(boolean setException) throws Exception {
        Endpoint endpoint = context.getEndpoint("netty:tcp://localhost:{{port}}?transferExchange=true&encoders=#encoder&decoders=#decoder");
        Exchange exchange = endpoint.createExchange();

        Message message = exchange.getIn();
        message.setBody("Hello!");
        message.setHeader("cheese", "feta");
        exchange.setProperty("ham", "old");
        exchange.setProperty("setException", setException);

        Producer producer = endpoint.createProducer();
        producer.start();

        // ensure to stop producer after usage
        try {
            producer.process(exchange);
        } finally {
            producer.stop();
        }

        return exchange;
    }

    private void assertExchange(Exchange exchange, boolean hasException) {
        if (!hasException) {
            Message out = exchange.getOut();
            assertNotNull(out);
            assertEquals("Goodbye!", out.getBody());
            assertEquals("cheddar", out.getHeader("cheese"));
        } else {
            Message fault = exchange.getOut();
            assertNotNull(fault);
            assertNotNull(fault.getBody());
            assertTrue("Should get the InterruptedException exception", fault.getBody() instanceof InterruptedException);
            assertEquals("nihao", fault.getHeader("hello"));
        }


        // in should stay the same
        Message in = exchange.getIn();
        assertNotNull(in);
        assertEquals("Hello!", in.getBody());
        assertEquals("feta", in.getHeader("cheese"));
        // however the shared properties have changed
        assertEquals("fresh", exchange.getProperty("salami"));
        assertNull(exchange.getProperty("Charset"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("netty:tcp://localhost:{{port}}?transferExchange=true&encoders=#encoder&decoders=#decoder").process(new Processor() {
                    public void process(Exchange e) throws InterruptedException {
                        assertNotNull(e.getIn().getBody());
                        assertNotNull(e.getIn().getHeaders());
                        assertNotNull(e.getProperties());
                        assertEquals("Hello!", e.getIn().getBody());
                        assertEquals("feta", e.getIn().getHeader("cheese"));
                        assertEquals("old", e.getProperty("ham"));
                        assertEquals(ExchangePattern.InOut, e.getPattern());
                        Boolean setException = (Boolean) e.getProperty("setException");

                        if (setException) {
                            e.getOut().setBody(new InterruptedException());
                            e.getOut().setHeader("hello", "nihao");
                        } else {
                            e.getOut().setBody("Goodbye!");
                            e.getOut().setHeader("cheese", "cheddar");
                        }
                        e.setProperty("salami", "fresh");
                        e.setProperty("Charset", Charset.defaultCharset());
                    }
                });
            }
        };
    }
}


