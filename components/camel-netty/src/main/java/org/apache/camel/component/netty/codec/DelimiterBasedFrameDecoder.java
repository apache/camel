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
package org.apache.camel.component.netty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

public class DelimiterBasedFrameDecoder extends io.netty.handler.codec.DelimiterBasedFrameDecoder {

    private final LineBasedFrameDecoder lineBasedFrameDecoder;

    public DelimiterBasedFrameDecoder(int maxFrameLength, boolean stripDelimiter, ByteBuf[] delimiters) {
        super(maxFrameLength, stripDelimiter, delimiters);
        if (isLineBased(delimiters)) {
            this.lineBasedFrameDecoder = new LineBasedFrameDecoder(maxFrameLength, stripDelimiter, true);
        } else {
            this.lineBasedFrameDecoder = null;
        }
    }

    @Override
    public Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        if (lineBasedFrameDecoder != null) {
            return lineBasedFrameDecoder.decode(ctx, in);
        }
        return super.decode(ctx, in);
    }

    /** Returns true if the delimiters are "\n" and "\r\n".
     * Copied from io.netty.handler.codec.DelimiterBasedFrameDecoder */
    private static boolean isLineBased(final ByteBuf[] delimiters) {
        if (delimiters.length != 2) {
            return false;
        }
        ByteBuf a = delimiters[0];
        ByteBuf b = delimiters[1];
        if (a.capacity() < b.capacity()) {
            a = delimiters[1];
            b = delimiters[0];
        }
        return a.capacity() == 2 && b.capacity() == 1
                && a.getByte(0) == '\r' && a.getByte(1) == '\n'
                && b.getByte(0) == '\n';
    }
}
