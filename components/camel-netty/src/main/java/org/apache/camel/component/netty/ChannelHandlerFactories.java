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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.apache.camel.component.netty.codec.DatagramPacketByteArrayDecoder;
import org.apache.camel.component.netty.codec.DatagramPacketByteArrayEncoder;
import org.apache.camel.component.netty.codec.DatagramPacketDecoder;
import org.apache.camel.component.netty.codec.DatagramPacketDelimiterDecoder;
import org.apache.camel.component.netty.codec.DatagramPacketEncoder;
import org.apache.camel.component.netty.codec.DatagramPacketStringDecoder;
import org.apache.camel.component.netty.codec.DatagramPacketStringEncoder;
import org.apache.camel.component.netty.codec.DelimiterBasedFrameDecoder;

/**
 * Helper to create commonly used {@link ChannelHandlerFactory} instances.
 */
public final class ChannelHandlerFactories {

    private ChannelHandlerFactories() {
    }

    public static ChannelHandlerFactory newStringEncoder(Charset charset, String protocol) {
        if ("udp".equalsIgnoreCase(protocol)) {
            return new ShareableChannelHandlerFactory(new DatagramPacketStringEncoder(charset));
        } else {
            return new ShareableChannelHandlerFactory(new StringEncoder(charset));
        }
    }

    public static ChannelHandlerFactory newStringDecoder(Charset charset, String protocol) {
        if ("udp".equalsIgnoreCase(protocol)) {
            return new ShareableChannelHandlerFactory(new DatagramPacketStringDecoder(charset));
        } else {
            return new ShareableChannelHandlerFactory(new StringDecoder(charset));
        }
    }

    public static ChannelHandlerFactory newDelimiterBasedFrameDecoder(
            final int maxFrameLength, final ByteBuf[] delimiters, String protocol) {
        return newDelimiterBasedFrameDecoder(maxFrameLength, delimiters, true, protocol);
    }

    public static ChannelHandlerFactory newDelimiterBasedFrameDecoder(
            final int maxFrameLength, final ByteBuf[] delimiters, final boolean stripDelimiter, String protocol) {
        if ("udp".equals(protocol)) {
            return new DefaultChannelHandlerFactory() {
                @Override
                public ChannelHandler newChannelHandler() {
                    return new DatagramPacketDelimiterDecoder(maxFrameLength, stripDelimiter, delimiters);
                }
            };
        } else {
            return new DefaultChannelHandlerFactory() {
                @Override
                public ChannelHandler newChannelHandler() {
                    return new DelimiterBasedFrameDecoder(maxFrameLength, stripDelimiter, delimiters);
                }
            };
        }
    }

    public static ChannelHandlerFactory newDatagramPacketDecoder() {
        return new ShareableChannelHandlerFactory(new DatagramPacketDecoder());
    }

    public static ChannelHandlerFactory newDatagramPacketEncoder() {
        return new ShareableChannelHandlerFactory(new DatagramPacketEncoder());
    }

    public static ChannelHandlerFactory newLengthFieldBasedFrameDecoder(
            final int maxFrameLength, final int lengthFieldOffset,
            final int lengthFieldLength, final int lengthAdjustment,
            final int initialBytesToStrip) {
        return new DefaultChannelHandlerFactory() {
            @Override
            public ChannelHandler newChannelHandler() {
                return new LengthFieldBasedFrameDecoder(
                        maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
            }
        };
    }

    public static ChannelHandlerFactory newByteArrayDecoder(String protocol) {
        if ("udp".equals(protocol)) {
            return new ShareableChannelHandlerFactory(new DatagramPacketByteArrayDecoder());
        } else {
            return new DefaultChannelHandlerFactory() {
                @Override
                public ChannelHandler newChannelHandler() {
                    return new ByteArrayDecoder();
                }
            };
        }
    }

    public static ChannelHandlerFactory newByteArrayEncoder(String protocol) {
        if ("udp".equals(protocol)) {
            return new ShareableChannelHandlerFactory(new DatagramPacketByteArrayEncoder());
        } else {
            return new ShareableChannelHandlerFactory(new ByteArrayEncoder());
        }
    }

}
