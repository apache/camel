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
package org.apache.camel.component.lumberjack.io;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.lumberjack.io.LumberjackConstants.FRAME_ACKNOWLEDGE_LENGTH;
import static org.apache.camel.component.lumberjack.io.LumberjackConstants.TYPE_ACKNOWLEDGE;
import static org.apache.camel.component.lumberjack.io.LumberjackConstants.VERSION_V1;
import static org.apache.camel.component.lumberjack.io.LumberjackConstants.VERSION_V2;

/**
 * Handles lumberjack window and send acknowledge when required.
 */
final class LumberjackSessionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(LumberjackSessionHandler.class);
    private static final int ACK_UNSET = -1;

    private volatile int version = -1;
    private volatile int windowSize = 1;
    private volatile int nextAck = ACK_UNSET;

    void versionRead(int version) {
        if (this.version == -1) {
            if (version != VERSION_V1 && version != VERSION_V2) {
                throw new RuntimeException("Unsupported frame version=" + version);
            }
            LOG.debug("Lumberjack protocol version is {}", (char) version);
            this.version = version;
        } else if (this.version != version) {
            throw new IllegalStateException("Protocol version changed during session from " + this.version + " to " + version);
        }
    }

    void windowSizeRead(int windowSize) {
        LOG.debug("Lumberjack window size is {}", windowSize);
        this.windowSize = windowSize;
        nextAck = ACK_UNSET;
    }

    void notifyMessageProcessed(ChannelHandlerContext ctx, int sequenceNumber) {
        if (nextAck == ACK_UNSET) {
            nextAck = sequenceNumber + windowSize - 1;
        }

        if (sequenceNumber == nextAck) {
            LOG.debug("Sequence number is {}. Sending ACK", sequenceNumber);
            ByteBuf response = ctx.alloc().heapBuffer(FRAME_ACKNOWLEDGE_LENGTH, FRAME_ACKNOWLEDGE_LENGTH);
            response.writeByte(version);
            response.writeByte(TYPE_ACKNOWLEDGE);
            response.writeInt(sequenceNumber);
            ctx.writeAndFlush(response);
        }
    }
}
