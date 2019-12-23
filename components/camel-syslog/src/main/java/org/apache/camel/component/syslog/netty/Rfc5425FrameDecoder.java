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
package org.apache.camel.component.syslog.netty;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.apache.camel.component.netty.ChannelHandlerFactory;

public class Rfc5425FrameDecoder extends ByteToMessageDecoder implements ChannelHandlerFactory {

    private Integer currentFramelength;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (currentFramelength == null) {
            // find index of the first space, it should be after the length field
            int index = indexOf(in, Unpooled.wrappedBuffer(new byte[]{' '}));

            // Read part until the first space, if we have found one
            StringBuffer lengthbuffer = new StringBuffer();
            if (index > -1) {
                ByteBuf byteBuf = in.readBytes(index);
                byte[] dest = new byte[byteBuf.readableBytes()];
                byteBuf.readBytes(dest);
                lengthbuffer.append(new String(dest));
            }

            int length;
            try {
                // add one because we have to take in account the space after
                // the length field
                length = Integer.parseInt(lengthbuffer.toString()) + 1;
            } catch (NumberFormatException e) {
                length = -1;
            }

            // We have not found the length field, reset the buffer so we can
            // retry next time
            if (length < 0) {
                in.resetReaderIndex();
                return;
            }
            currentFramelength = length;
        }

        // Buffer does not contain enough data yet, wait until it does
        if (in.readableBytes() < currentFramelength) {
            return;
        }

        // read the message
        int lengthToRead = currentFramelength;
        currentFramelength = null;
        out.add(in.readBytes(lengthToRead));
    }

    /**
     * Borrowed from the DelimiterBasedFrameDecoder Returns the number of bytes
     * between the readerIndex of the haystack and the first needle found in the
     * haystack. -1 is returned if no needle is found in the haystack.
     */
    private static int indexOf(ByteBuf haystack, ByteBuf needle) {
        for (int i = haystack.readerIndex(); i < haystack.writerIndex(); i++) {
            int haystackIndex = i;
            int needleIndex;
            for (needleIndex = 0; needleIndex < needle.capacity(); needleIndex++) {
                if (haystack.getByte(haystackIndex) != needle.getByte(needleIndex)) {
                    break;
                } else {
                    haystackIndex++;
                    if (haystackIndex == haystack.writerIndex() && needleIndex != needle.capacity() - 1) {
                        return -1;
                    }
                }
            }

            if (needleIndex == needle.capacity()) {
                // Found the needle from the haystack!
                return i - haystack.readerIndex();
            }
        }
        return -1;
    }

    @Override
    public ChannelHandler newChannelHandler() {
        return new Rfc5425FrameDecoder();
    }
}
