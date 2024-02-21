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
package org.apache.camel.component.mina;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test to test what happens if remote server closes session but doesn't reply
 */
public class MinaNoResponseFromServerTest extends BaseMinaTest {

    @BindToRegistry("myCodec")
    private final MyCodec codec1 = new MyCodec();

    @Test
    public void testNoResponse() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        final String format = String.format("mina:tcp://localhost:%1$s?sync=true&codec=#myCodec", getPort());
        RuntimeCamelException e = assertThrows(RuntimeCamelException.class,
                () -> template.requestBody(format, "Hello World"),
                "Should throw a CamelExchangeException");

        assertIsInstanceOf(CamelExchangeException.class, e.getCause());
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                fromF("mina:tcp://localhost:%1$s?sync=true&codec=#myCodec", getPort())
                        .transform(constant("Bye World")).to("mock:result");
            }
        };
    }

    private static class MyCodec implements ProtocolCodecFactory {

        @Override
        public ProtocolEncoder getEncoder(IoSession session) {
            return new ProtocolEncoder() {

                public void encode(IoSession ioSession, Object message, ProtocolEncoderOutput out) {
                    // close session instead of returning a reply
                    ioSession.closeNow();
                }

                public void dispose(IoSession ioSession) {
                    // do nothing
                }
            };

        }

        @Override
        public ProtocolDecoder getDecoder(IoSession session) {
            return new ProtocolDecoder() {

                public void decode(IoSession ioSession, IoBuffer in, ProtocolDecoderOutput out) {
                    // close session instead of returning a reply
                    ioSession.closeNow();
                }

                public void finishDecode(IoSession ioSession, ProtocolDecoderOutput protocolDecoderOutput) {
                    // do nothing
                }

                public void dispose(IoSession ioSession) {
                    // do nothing
                }
            };
        }
    }
}
