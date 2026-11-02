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

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.serialization.ClassResolvers;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.netty.codec.ObjectDecoder;
import org.apache.camel.component.netty.codec.ObjectEncoder;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * With transferExchange=true, allowSerializedHeaders applies to the reply of the consumer as it does to the request of
 * the producer.
 */
class NettyTransferExchangeAllowSerializedHeadersTest extends BaseNettyTest {

    private static final List<String> CHEESES = List.of("feta", "cheddar");

    @RegisterExtension
    AvailablePortFinder.Port notAllowedPort = AvailablePortFinder.find();

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
    void serializedHeaderOfTheReplyIsTransferredWhenAllowed() {
        Exchange out = request(port.getPort(), true);
        assertNull(out.getException());
        if (out.getException() != null) {
            out.getException().printStackTrace(System.out);
        }
        assertEquals("Bye World", out.getMessage().getBody());
        assertEquals("gouda", out.getMessage().getHeader("cheese"));
        assertEquals(CHEESES, out.getMessage().getHeader("cheeses"));
    }

    @Test
    void serializedHeaderOfTheReplyIsDroppedWhenNotAllowed() {
        Exchange out = request(notAllowedPort.getPort(), false);
        assertNull(out.getException());
        assertEquals("Bye World", out.getMessage().getBody());
        assertEquals("gouda", out.getMessage().getHeader("cheese"));
        assertNull(out.getMessage().getHeader("cheeses"));
    }

    private Exchange request(int port, boolean allowSerializedHeaders) {
        return template.request("netty:tcp://localhost:" + port
                                + "?transferExchange=true&allowSerializedHeaders=" + allowSerializedHeaders
                                + "&encoders=#encoder&decoders=#decoder",
                exchange -> exchange.getIn().setBody("Hello World"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty:tcp://localhost:{{port}}?transferExchange=true&allowSerializedHeaders=true"
                     + "&encoders=#encoder&decoders=#decoder")
                        .process(NettyTransferExchangeAllowSerializedHeadersTest::reply);

                from("netty:tcp://localhost:" + notAllowedPort.getPort() + "?transferExchange=true"
                     + "&encoders=#encoder&decoders=#decoder")
                        .process(NettyTransferExchangeAllowSerializedHeadersTest::reply);
            }
        };
    }

    @SuppressWarnings("deprecation")
    private static void reply(Exchange exchange) {
        // the in headers are transferred as well: remove the address headers of the consumer (InetSocketAddress),
        // as the default deserialization filter of the ObjectDecoder of the client rejects java.net classes
        exchange.getIn().removeHeaders("CamelNetty*");
        // the reply set as the out message is the one marshalled by NettyPayloadHelper.getOut
        Message out = exchange.getOut();
        out.setBody("Bye World");
        out.setHeader("cheese", "gouda");
        // a serializable header value that is not a String, number, date or boolean
        out.setHeader("cheeses", new ArrayList<>(CHEESES));
    }
}
