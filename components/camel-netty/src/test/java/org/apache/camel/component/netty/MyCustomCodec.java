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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

import static org.jboss.netty.buffer.ChannelBuffers.copiedBuffer;

public final class MyCustomCodec {
    
    private static ChannelBuffer nullDelimiter = ChannelBuffers.wrappedBuffer(new byte[]{0});
    
    private MyCustomCodec() {
        // Helper class
    }

    public static ChannelHandlerFactory createMyCustomDecoder() {
        ChannelBuffer[] delimiters = new ChannelBuffer[]{nullDelimiter, nullDelimiter};
        return ChannelHandlerFactories.newDelimiterBasedFrameDecoder(4096, delimiters);
    }

    public static ChannelHandler createMyCustomDecoder2() {
        return new BytesDecoder();
    }

    public static ChannelHandler createMyCustomEncoder() {
        return new BytesEncoder();
    }

    @ChannelHandler.Sharable
    public static class BytesDecoder extends OneToOneDecoder {

        @Override
        protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
            if (!(msg instanceof ChannelBuffer)) {
                return msg;
            } else {
                // it may be empty, then return null
                ChannelBuffer cb = (ChannelBuffer) msg;
                if (cb.hasArray() && cb.readable()) {
                    return cb.array();
                } else {
                    return null;
                }
            }
        }

    }

    @ChannelHandler.Sharable
    public static class BytesEncoder extends OneToOneEncoder {

        @Override
        protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
            if (msg instanceof byte[]) {
                return copiedBuffer((byte[]) msg);
            }
            return msg;
        }
    }
}
