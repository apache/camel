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
import org.apache.camel.CamelExchangeException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.netty.codec.ObjectDecoder;
import org.apache.camel.component.netty.codec.ObjectEncoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When writing the reply fails, the consumer closes the channel instead of leaving the client waiting for a reply that
 * never comes.
 */
class NettyReplyWriteFailureTest extends BaseNettyTest {

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
    void aReplyThatCannotBeWrittenClosesTheChannel() {
        // the request timeout is what the client got before: no reply until it timed out
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody(
                        "netty:tcp://localhost:{{port}}?encoders=#encoder&decoders=#decoder&requestTimeout=10000",
                        "Hello World", String.class));
        CamelExchangeException cause = assertInstanceOf(CamelExchangeException.class, e.getCause());
        assertTrue(cause.getMessage().startsWith("No response received from remote server"), cause.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the object encoder cannot write a body that is not serializable
                from("netty:tcp://localhost:{{port}}?encoders=#encoder&decoders=#decoder&sync=true")
                        .process(exchange -> exchange.getMessage().setBody(new NotSerializableReply()));
            }
        };
    }

    private static class NotSerializableReply {
    }
}
