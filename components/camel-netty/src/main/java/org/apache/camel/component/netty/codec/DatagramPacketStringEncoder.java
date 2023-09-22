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

import java.net.InetSocketAddress;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.List;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.handler.codec.MessageToMessageEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Sharable
public class DatagramPacketStringEncoder
        extends
        MessageToMessageEncoder<AddressedEnvelope<Object, InetSocketAddress>> {

    private static final Logger LOG = LoggerFactory.getLogger(DatagramPacketStringEncoder.class);

    private final Charset charset;

    /**
     * Creates a new instance with the current system character set.
     */
    public DatagramPacketStringEncoder() {
        this(Charset.defaultCharset());
    }

    /**
     * Creates a new instance with the specified character set.
     */
    public DatagramPacketStringEncoder(Charset charset) {
        if (charset == null) {
            throw new NullPointerException("charset");
        }
        this.charset = charset;
    }

    @Override
    protected void encode(
            ChannelHandlerContext ctx, AddressedEnvelope<Object, InetSocketAddress> msg,
            List<Object> out)
            throws Exception {
        if (msg.content() instanceof CharSequence) {
            CharSequence payload = (CharSequence) msg.content();
            if (payload.isEmpty()) {
                return;
            }
            AddressedEnvelope<Object, InetSocketAddress> addressedEnvelop = new DefaultAddressedEnvelope<>(
                    ByteBufUtil.encodeString(ctx.alloc(), CharBuffer.wrap(payload), charset), msg.recipient(), msg.sender());
            out.add(addressedEnvelop);
        } else {
            LOG.debug("Ignoring message content as it is not a java.lang.CharSequence instance.");
        }
    }

}
