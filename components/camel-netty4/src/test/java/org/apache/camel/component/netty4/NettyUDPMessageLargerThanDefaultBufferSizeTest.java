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
package org.apache.camel.component.netty4;

import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

public class NettyUDPMessageLargerThanDefaultBufferSizeTest extends BaseNettyTest {

    private byte[] getMessageBytes(int messageSize) {
        byte[] msgBytes = new byte[messageSize];
        for (int i = 0; i < messageSize; i++) {
            msgBytes[i] = 'A';
        }
        return msgBytes;
    }

    private void sendMessage(int messageSize) throws Exception {
        byte[] msgBytes = getMessageBytes(messageSize);

        assertEquals(msgBytes.length, messageSize);
        String message = new String(msgBytes);

        getMockEndpoint("mock:result").expectedBodiesReceived(message);
        template.sendBody("netty4:udp://localhost:{{port}}?sync=false", message);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSend2048Message() throws Exception {
        //Will fail unless the buffer was increased correctly
        sendMessage(2048);
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        FixedRecvByteBufAllocator fixedRecvByteBufAllocator = new FixedRecvByteBufAllocator(4096);
        jndi.bind(ChannelOption.RCVBUF_ALLOCATOR.name(), fixedRecvByteBufAllocator);
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty4:udp://localhost:{{port}}?option." + ChannelOption.RCVBUF_ALLOCATOR.name() + "=#" + ChannelOption.RCVBUF_ALLOCATOR.name())
                    .to("mock:result");
            }
        };
    }
}
