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
package org.apache.camel.example.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Netty encoder that writes the Camel message to bytes with start and end byte markers.
 */
public class MyCodecEncoder extends MessageToByteEncoder<Object> {

    private static char startByte = 0x0b; // 11 decimal
    private static char endByte1 = 0x1c; // 28 decimal
    private static char endByte2 = 0x0d; // 13 decimal

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Object message, ByteBuf byteBuf) throws Exception {

        byte[] body;
        if (message instanceof String) {
            body = ((String) message).getBytes();
        } else if (message instanceof byte[]) {
            body = (byte[]) message;
        } else {
            throw new IllegalArgumentException("The message to encode is not a supported type: "
                + message.getClass().getCanonicalName());
        }

        byteBuf.writeByte(startByte);
        byteBuf.writeBytes(body);
        byteBuf.writeByte(endByte1);
        byteBuf.writeByte(endByte2);
    }
}
