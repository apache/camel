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
package org.apache.camel.component.lumberjack;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(value = { OS.LINUX },
              architectures = { "s390x" },
              disabledReason = "This test does not run reliably on multiple platforms (see CAMEL-21438)")
@Isolated
class LumberjackOutOfOrderAckTest extends CamelTestSupport {

    private static final String ROUTE_ID = "lumberjack-" + LumberjackOutOfOrderAckTest.class.getSimpleName();
    private static final String MOCK_OUTPUT = "mock:output-" + LumberjackOutOfOrderAckTest.class.getSimpleName();

    private final CountDownLatch firstMessageProcessing = new CountDownLatch(1);
    private final CountDownLatch holdFirstMessage = new CountDownLatch(1);
    private final AtomicBoolean first = new AtomicBoolean(true);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("lumberjack:0.0.0.0:0").routeId(ROUTE_ID)
                        .threads(5)
                        .process(exchange -> {
                            if (first.compareAndSet(true, false)) {
                                firstMessageProcessing.countDown();
                                if (!holdFirstMessage.await(5, TimeUnit.SECONDS)) {
                                    throw new AssertionError("Timed out waiting for holdFirstMessage to be released");
                                }
                            }
                        })
                        .to(MOCK_OUTPUT);
            }
        };
    }

    private int getActualPort() {
        return ((LumberjackConsumer) context.getRoute(ROUTE_ID).getConsumer()).getLocalPort();
    }

    @Test
    void shouldNotSendAckUntilAllMessagesComplete() throws Exception {
        MockEndpoint mock = getMockEndpoint(MOCK_OUTPUT);
        mock.expectedMessageCount(14);

        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
        try {
            List<Integer> responses = new CopyOnWriteArrayList<>();
            ChannelInitializer<Channel> initializer = new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                            assertThat(msg.readUnsignedByte()).isEqualTo((short) '2');
                            assertThat(msg.readUnsignedByte()).isEqualTo((short) 'A');
                            responses.add(msg.readInt());
                        }
                    });
                }
            };

            Channel channel = new Bootstrap()
                    .group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .handler(initializer)
                    .connect("127.0.0.1", getActualPort()).sync().channel();

            try {
                // Send window of 15 messages
                channel.writeAndFlush(LumberjackUtil.readSample("io/window15.bin"));

                // Wait until the first message has arrived and is blocked
                boolean firstStarted = firstMessageProcessing.await(5, TimeUnit.SECONDS);
                assertThat(firstStarted).as("First message should start processing").isTrue();

                // Wait until the other 14 messages (including the 15th with sequenceNumber == windowSize) complete
                mock.assertIsSatisfied(5, TimeUnit.SECONDS);

                // Give the Netty pipeline a moment to deliver any in-flight response
                Thread.sleep(200);

                // Verify that no ACK has been sent yet because the first message is still in flight
                assertThat(responses).as("No ACK should be sent before all messages in the window complete").isEmpty();

                // Prepare mock for the remaining message
                mock.reset();
                mock.expectedMessageCount(1);

                // Unblock the first message
                holdFirstMessage.countDown();

                // Wait for the last message to reach mock
                mock.assertIsSatisfied(5, TimeUnit.SECONDS);

                // Assert that the ACK is now received
                Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                    assertThat(responses).containsExactly(15);
                });
            } finally {
                channel.close().sync();
            }
        } finally {
            eventLoopGroup.shutdownGracefully().syncUninterruptibly();
        }
    }
}
