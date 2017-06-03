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
package org.apache.camel.component.hl7;

import ca.uhn.hl7v2.model.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * HL7 MLLP Encoder for Netty4
 */
class HL7MLLPNettyEncoder extends MessageToByteEncoder<Object> {

    private final HL7MLLPConfig config;

    HL7MLLPNettyEncoder() {
        this(new HL7MLLPConfig());
    }

    HL7MLLPNettyEncoder(HL7MLLPConfig config) {
        this.config = config;
    }

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Object message, ByteBuf byteBuf) throws Exception {
        if (message == null) {
            throw new IllegalArgumentException("Message to be encoded is null");
        } else if (message instanceof Exception) {
            // we cannot handle exceptions
            throw (Exception) message;
        }

        byte[] body;
        if (message instanceof Message) {
            body = ((Message) message).encode().getBytes(config.getCharset());
        } else if (message instanceof String) {
            body = ((String) message).getBytes(config.getCharset());
        } else if (message instanceof byte[]) {
            body = (byte[]) message;
        } else {
            throw new IllegalArgumentException("The message to encode is not a supported type: "
                    + message.getClass().getCanonicalName());
        }
        byteBuf.writeByte(config.getStartByte());
        byteBuf.writeBytes(body);
        byteBuf.writeByte(config.getEndByte1());
        byteBuf.writeByte(config.getEndByte2());
    }
}
