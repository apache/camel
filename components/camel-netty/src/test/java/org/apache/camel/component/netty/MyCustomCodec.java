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

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;

public final class MyCustomCodec {

    private static ByteBuf nullDelimiter = Unpooled.wrappedBuffer(new byte[]{0});

    private MyCustomCodec() {
        // Helper class
    }

    public static ChannelHandlerFactory createMyCustomDecoder() {
        ByteBuf[] delimiters = new ByteBuf[]{nullDelimiter, nullDelimiter};
        return ChannelHandlerFactories.newDelimiterBasedFrameDecoder(4096, delimiters, "tcp");
    }

    public static ChannelHandler createMyCustomDecoder2() {
        return new BytesDecoder();
    }

    public static ChannelHandler createMyCustomEncoder() {
        return new BytesEncoder();
    }

    @ChannelHandler.Sharable
    public static class BytesDecoder extends MessageToMessageDecoder<ByteBuf> {

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
            // it may be empty, then return null
            if (msg.isReadable()) {
                // ByteBuf may not expose array method for accessing the under layer bytes
                byte[] bytes = new byte[msg.readableBytes()];
                int readerIndex = msg.readerIndex();
                msg.getBytes(readerIndex, bytes);
                out.add(bytes);
            }
        }

    }

    @ChannelHandler.Sharable
    public static class BytesEncoder extends MessageToMessageEncoder<byte[]> {

        @Override
        protected void encode(ChannelHandlerContext ctx, byte[] msg, List<Object> out) throws Exception {
            byte[] bytes = msg;
            ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(bytes.length);
            buf.writeBytes(bytes);
            out.add(buf);
        }
    }
}
