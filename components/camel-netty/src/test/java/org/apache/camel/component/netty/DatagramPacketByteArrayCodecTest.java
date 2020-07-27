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

import java.net.InetSocketAddress;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DatagramPacketByteArrayCodecTest {

    private static final String VALUE = "~!Camel rocks@%";

    @Test
    public void testDecoder() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes(VALUE.getBytes());
        ByteBuf input = buf.duplicate();
        AddressedEnvelope<Object, InetSocketAddress> addressedEnvelop
                = new DefaultAddressedEnvelope<>(input, new InetSocketAddress(8888));
        EmbeddedChannel channel = new EmbeddedChannel(ChannelHandlerFactories.newByteArrayDecoder("udp").newChannelHandler());
        assertTrue(channel.writeInbound(addressedEnvelop));
        assertTrue(channel.finish());
        AddressedEnvelope<Object, InetSocketAddress> result = (AddressedEnvelope) channel.readInbound();
        assertEquals(result.recipient().getPort(), addressedEnvelop.recipient().getPort());
        assertTrue(result.content() instanceof byte[]);
        assertEquals(VALUE, new String((byte[]) result.content()));
        assertNull(channel.readInbound());
    }

    @Test
    public void testEncoder() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes(VALUE.getBytes());
        AddressedEnvelope<Object, InetSocketAddress> addressedEnvelop
                = new DefaultAddressedEnvelope<>(VALUE.getBytes(), new InetSocketAddress(8888));
        EmbeddedChannel channel = new EmbeddedChannel(ChannelHandlerFactories.newByteArrayEncoder("udp").newChannelHandler());
        assertTrue(channel.writeOutbound(addressedEnvelop));
        assertTrue(channel.finish());
        AddressedEnvelope output = (AddressedEnvelope) channel.readOutbound();
        assertTrue(output.content() instanceof ByteBuf);
        ByteBuf resultContent = (ByteBuf) output.content();
        assertEquals(VALUE, new String(resultContent.array()));
        assertNull(channel.readOutbound());
    }
}
