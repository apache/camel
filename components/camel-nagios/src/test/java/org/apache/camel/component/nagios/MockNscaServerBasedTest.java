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
package org.apache.camel.component.nagios;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.netty.NettyConsumer;
import org.apache.camel.component.netty.ServerInitializerFactory;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.codec.digest.DigestUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * In the context of this test, the NSCA nagios server is mocked with a camel-netty route. Incoming message digests are
 * computed and later on verified against expectations. The protocol is simulated in a way that ensure predictable
 * digest outputs given incoming messages are always produced in the same order.
 */
public class MockNscaServerBasedTest extends CamelTestSupport {

    @BindToRegistry("mockNscaServer")
    protected static MockNscaServerInitializerFactory mockNscaServer = new MockNscaServerInitializerFactory();

    private static final String EXPECTED_NSCA_FRAME_DIGEST = "315d4b1aed2bb2db79d516f7c651b0d1";
    private static final int INIT_VECTOR_PLUS_TIMESTAMP_SIZE_IN_BYTES = 128 + Integer.BYTES;

    private int nscaPort;

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();
        nscaPort = AvailablePortFinder.getNextAvailable();
    }

    @Test
    public void sendFixedNscaFrameShouldReturnExpectedDigest() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(NagiosConstants.LEVEL, "CRITICAL");
        headers.put(NagiosConstants.HOST_NAME, "myHost");
        headers.put(NagiosConstants.SERVICE_NAME, "myService");
        template.sendBodyAndHeaders("direct:start", "Hello Nagios", headers);
        mockNscaServer.verifyFrameReceived(EXPECTED_NSCA_FRAME_DIGEST);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").toF("nagios:localhost:%s?password=secret", nscaPort);
                fromF("netty:tcp://0.0.0.0:%s?serverInitializerFactory=#mockNscaServer", nscaPort).log("NSCA frame received");
            }
        };
    }

    public static class MockNscaServerInitializerFactory extends ServerInitializerFactory {

        private volatile String actualFrameDigest;

        protected void initChannel(Channel ch) {
            ch.pipeline().addFirst("mock-nsca-handler", new ByteToMessageDecoder() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) {
                    final ByteBuf initVectorAndTimeStamp = ctx.alloc().buffer(INIT_VECTOR_PLUS_TIMESTAMP_SIZE_IN_BYTES);
                    initVectorAndTimeStamp.writeBytes(new byte[INIT_VECTOR_PLUS_TIMESTAMP_SIZE_IN_BYTES]);
                    ctx.writeAndFlush(initVectorAndTimeStamp);
                }

                @Override
                protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
                    byte[] bytes = new byte[in.readableBytes()];
                    in.readBytes(bytes);
                    actualFrameDigest = DigestUtils.md5Hex(bytes);
                }
            });
        }

        void verifyFrameReceived(String expectedFrameDigest) {
            if (expectedFrameDigest == null) {
                throw new IllegalArgumentException("argument expectedFrameDigest can't be null");
            }
            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> expectedFrameDigest.equals(actualFrameDigest));
        }

        @Override
        public ServerInitializerFactory createPipelineFactory(NettyConsumer consumer) {
            return this;
        }
    }

}
