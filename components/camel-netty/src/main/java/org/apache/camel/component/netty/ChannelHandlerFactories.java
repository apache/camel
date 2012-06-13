/**
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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.serialization.ClassResolvers;
import org.jboss.netty.handler.codec.serialization.ObjectDecoder;
import org.jboss.netty.handler.codec.serialization.ObjectEncoder;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;

/**
 * Helper to create commonly used {@link ChannelHandlerFactory} instances.
 */
public final class ChannelHandlerFactories {

    private ChannelHandlerFactories() {
    }

    public static ChannelHandlerFactory newStringEncoder(Charset charset) {
        return new ShareableChannelHandlerFactory(new StringEncoder(charset));
    }

    public static ChannelHandlerFactory newStringDecoder(Charset charset) {
        return new ShareableChannelHandlerFactory(new StringDecoder(charset));
    }

    public static ChannelHandlerFactory newObjectDecoder() {
        return new ChannelHandlerFactory() {
            @Override
            public ChannelHandler newChannelHandler() {
                return new ObjectDecoder(ClassResolvers.weakCachingResolver(null));
            }
        };
    }

    public static ChannelHandlerFactory newObjectEncoder() {
        return new ShareableChannelHandlerFactory(new ObjectEncoder());
    }

    public static ChannelHandlerFactory newDelimiterBasedFrameDecoder(final int maxFrameLength, final ChannelBuffer[] delimiters) {
        return new ChannelHandlerFactory() {
            @Override
            public ChannelHandler newChannelHandler() {
                return new DelimiterBasedFrameDecoder(maxFrameLength, true, delimiters);
            }
        };
    }

    public static ChannelHandlerFactory newLengthFieldBasedFrameDecoder(final int maxFrameLength, final int lengthFieldOffset,
                                                                        final int lengthFieldLength, final int lengthAdjustment,
                                                                        final int initialBytesToStrip) {
        return new ChannelHandlerFactory() {
            @Override
            public ChannelHandler newChannelHandler() {
                return new LengthFieldBasedFrameDecoder(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
            }
        };
    }

}
