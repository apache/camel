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

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.serialization.ClassResolvers;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.netty.codec.ObjectDecoder;
import org.apache.camel.component.netty.codec.ObjectEncoder;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NettyTransferExchangeDisabledTest extends BaseNettyTest {

    @BindToRegistry("encoder")
    public ChannelHandler getEncoder() {
        return new ShareableChannelHandlerFactory(new ObjectEncoder());
    }

    @BindToRegistry("decoder")
    public ChannelHandler getDecoder() {
        return new DefaultChannelHandlerFactory() {
            @Override
            public ChannelHandler newChannelHandler() {
                return new ObjectDecoder(ClassResolvers.weakCachingResolver(null));
            }
        };
    }

    @Test
    public void testTransferExchangeDisabledTreatsHolderAsBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        Exchange sourceExchange = new DefaultExchange(context);
        sourceExchange.getIn().setBody("Inner Payload");
        sourceExchange.getIn().setHeader("CustomHeader", "HeaderValue");
        sourceExchange.setProperty("CustomProperty", "PropertyValue");

        DefaultExchangeHolder holder = DefaultExchangeHolder.marshal(sourceExchange, true, true);

        Endpoint endpoint
                = context.getEndpoint("netty:tcp://localhost:{{port}}?sync=false&encoders=#encoder&decoders=#decoder");
        Producer producer = endpoint.createProducer();
        producer.start();
        try {
            Exchange sendExchange = endpoint.createExchange();
            sendExchange.getIn().setBody(holder);
            producer.process(sendExchange);
        } finally {
            producer.stop();
        }

        mock.assertIsSatisfied();

        Exchange received = mock.getReceivedExchanges().get(0);
        assertNotNull(received);

        // Holder must remain the body and NOT be unmarshalled into the exchange
        assertTrue(received.getIn().getBody() instanceof DefaultExchangeHolder,
                "Body should remain a DefaultExchangeHolder when transferExchange=false");
        assertNull(received.getIn().getHeader("CustomHeader"),
                "Headers from DefaultExchangeHolder must not be unmarshalled when transferExchange=false");
        assertNull(received.getProperty("CustomProperty"),
                "Properties from DefaultExchangeHolder must not be unmarshalled when transferExchange=false");
        assertNotNull(received.getIn().getHeader(NettyConstants.NETTY_REMOTE_ADDRESS),
                "Connection headers must be preserved");
    }

    @Test
    public void testHelperGating() {
        NettyEndpoint transferOn = context.getEndpoint("netty:tcp://localhost:1111?transferExchange=true", NettyEndpoint.class);
        NettyEndpoint transferOff
                = context.getEndpoint("netty:tcp://localhost:2222?transferExchange=false", NettyEndpoint.class);

        Exchange source = new DefaultExchange(context);
        source.getIn().setBody("Inner Content");
        source.getIn().setHeader("Key", "Value");
        source.setProperty("Prop", "Val");
        DefaultExchangeHolder holder = DefaultExchangeHolder.marshal(source, true, true);

        // 1. With transferExchange = true
        Exchange targetOn = new DefaultExchange(context);
        NettyPayloadHelper.setIn(transferOn, targetOn, holder);
        assertEquals("Inner Content", targetOn.getIn().getBody());
        assertEquals("Value", targetOn.getIn().getHeader("Key"));
        assertEquals("Val", targetOn.getProperty("Prop"));

        // 2. With transferExchange = false
        Exchange targetOff = new DefaultExchange(context);
        NettyPayloadHelper.setIn(transferOff, targetOff, holder);
        assertSame(holder, targetOff.getIn().getBody());
        assertNull(targetOff.getIn().getHeader("Key"));
        assertNull(targetOff.getProperty("Prop"));

        // 3. With null endpoint (deprecated overload)
        Exchange targetNull = new DefaultExchange(context);
        NettyPayloadHelper.setIn(targetNull, holder);
        assertSame(holder, targetNull.getIn().getBody());
        assertNull(targetNull.getIn().getHeader("Key"));
        assertNull(targetNull.getProperty("Prop"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty:tcp://localhost:{{port}}?sync=false&encoders=#encoder&decoders=#decoder")
                        .to("mock:result");
            }
        };
    }
}
