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
            int spaceIndex = in.indexOf(in.readerIndex(), in.writerIndex(), (byte) ' ');
            if (spaceIndex < 0) {
                // The length delimiter has not arrived yet, wait for more data without modifying reader index
                return;
            }

            int lengthDigits = spaceIndex - in.readerIndex();
            if (lengthDigits == 0) {
                throw new IllegalArgumentException("Invalid RFC 5425 frame: empty length field");
            }

            int length = 0;
            for (int i = 0; i < lengthDigits; i++) {
                byte b = in.getByte(in.readerIndex() + i);
                if (b < '0' || b > '9') {
                    throw new IllegalArgumentException("Invalid RFC 5425 frame: non-digit character in length field");
                }
                length = length * 10 + (b - '0');
            }

            // add one because we have to take in account the space after the length field
            currentFramelength = length + 1;
            in.skipBytes(lengthDigits);
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

    @Override
    public ChannelHandler newChannelHandler() {
        return new Rfc5425FrameDecoder();
    }
}
