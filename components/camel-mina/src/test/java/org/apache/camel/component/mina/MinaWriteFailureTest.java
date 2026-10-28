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
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that a write failure (encoder throws, write completes but isWritten() == false) is correctly reported on the
 * exchange when sync=false (CAMEL-25621).
 */
class MinaWriteFailureTest extends BaseMinaTest {

    @BindToRegistry("failingEncoderCodec")
    private final FailingEncoderCodec codec = new FailingEncoderCodec();

    @Test
    void writeFailureDueToEncoderErrorIsReportedOnExchange() {
        String uri = String.format("mina:tcp://localhost:%1$s?sync=false&codec=#failingEncoderCodec", getPort());

        CamelExecutionException ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBody(uri, "Hello World"),
                "Expected a CamelExecutionException when the encoder fails");

        Throwable cause = ex.getCause();
        assertNotNull(cause, "CamelExecutionException must have a cause");
        assertInstanceOf(org.apache.camel.CamelExchangeException.class, cause,
                "Cause must be a CamelExchangeException, was: " + cause.getClass().getName());

        // The root cause must carry the encoder's IllegalStateException
        Throwable root = cause;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertInstanceOf(IllegalStateException.class, root,
                "Root cause must be the encoder's IllegalStateException, was: " + root.getClass().getName());
        assertTrue(root.getMessage().contains("Simulated encoder failure"),
                "Root cause message must mention the simulated failure, was: " + root.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Consumer listens on the same port with the failing codec; the producer (template.sendBody)
                // connects to it and attempts to encode the message, which triggers the encoder failure.
                fromF("mina:tcp://localhost:%1$s?sync=false&codec=#failingEncoderCodec", getPort())
                        .to("mock:result");
            }
        };
    }

    /**
     * A codec whose encoder always throws, causing the WriteFuture to complete but with isWritten() == false.
     */
    private static class FailingEncoderCodec implements ProtocolCodecFactory {

        @Override
        public ProtocolEncoder getEncoder(IoSession session) {
            return new ProtocolEncoder() {
                @Override
                public void encode(IoSession ioSession, Object message, ProtocolEncoderOutput out) throws Exception {
                    throw new IllegalStateException("Simulated encoder failure");
                }

                @Override
                public void dispose(IoSession ioSession) {
                    // nothing to dispose
                }
            };
        }

        @Override
        public ProtocolDecoder getDecoder(IoSession session) {
            return new CumulativeProtocolDecoder() {
                @Override
                protected boolean doDecode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
                    if (in.remaining() > 0) {
                        byte[] buf = new byte[in.remaining()];
                        in.get(buf);
                        out.write(new String(buf));
                        return true;
                    }
                    return false;
                }
            };
        }
    }
}
