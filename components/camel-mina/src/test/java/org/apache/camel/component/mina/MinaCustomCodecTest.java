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
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.junit.Test;

/**
 * Unit test with custom codec.
 */
public class MinaCustomCodecTest extends BaseMinaTest {

    @BindToRegistry("myCodec")
    private MyCodec codec1 = new MyCodec();

    @BindToRegistry("failingCodec")
    private MyCodec codec2 = new MyCodec(true);

    @Test
    public void testMyCodec() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Bye World");

        Object out = template.requestBody(String.format("mina:tcp://localhost:%1$s?sync=true&codec=#myCodec", getPort()), "Hello World");
        assertEquals("Bye World", out);

        mock.assertIsSatisfied();
    }

    @Test
    public void testProducerFailInDecodingResponse() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        try {
            template.requestBody(String.format("mina:tcp://localhost:%1$s?sync=true&codec=#failingCodec", getPort()), "Hello World");
            fail("Expecting that decode of result fails");
        } catch (Exception e) {
            assertTrue(e instanceof CamelExecutionException);
            assertNotNull(e.getCause());
            Throwable rootCause = e;
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }
            assertTrue(rootCause instanceof IllegalArgumentException);
            assertTrue(rootCause.getMessage().contains("Something went wrong in decode"));
        }

    }

    @Test
    public void testTCPEncodeUTF8InputIsString() throws Exception {
        final String myUri = String.format("mina:tcp://localhost:%1$s?encoding=UTF-8&sync=false", getNextPort());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(myUri).to("mock:result");
            }
        });

        MockEndpoint endpoint = getMockEndpoint("mock:result");

        // include a UTF-8 char in the text \u0E08 is a Thai elephant
        String body = "Hello Thai Elephant \u0E08";

        endpoint.expectedMessageCount(1);
        endpoint.expectedBodiesReceived(body);

        template.sendBody(myUri, body);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testBadConfiguration() throws Exception {
        try {
            template.sendBody(String.format("mina:tcp://localhost:%1$s?sync=true&codec=#XXX", getPort()), "Hello World");
            fail("Should have thrown a ResolveEndpointFailedException");
        } catch (ResolveEndpointFailedException e) {
            // ok
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from(String.format("mina:tcp://localhost:%1$s?sync=true&codec=#myCodec", getPort())).transform(constant("Bye World")).to("mock:result");
            }
        };
    }

    private static class MyCodec implements ProtocolCodecFactory {

        private final boolean failing;

        MyCodec(boolean failing) {
            this.failing = failing;
        }

        MyCodec() {
            this.failing = false;
        }

        @Override
        public ProtocolEncoder getEncoder(IoSession session) throws Exception {
            return new ProtocolEncoder() {

                @Override
                public void encode(IoSession ioSession, Object message, ProtocolEncoderOutput out) throws Exception {
                    IoBuffer bb = IoBuffer.allocate(32).setAutoExpand(true);
                    String s = (String)message;
                    bb.put(s.getBytes("US-ASCII"));
                    bb.flip();
                    out.write(bb);
                }

                @Override
                public void dispose(IoSession ioSession) throws Exception {
                    // do nothing
                }
            };

        }

        @Override
        public ProtocolDecoder getDecoder(IoSession session) throws Exception {
            return new CumulativeProtocolDecoder() {

                @Override
                protected boolean doDecode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
                    if (failing) {
                        throw new IllegalArgumentException("Something went wrong in decode");
                    }

                    if (in.remaining() > 0) {
                        byte[] buf = new byte[in.remaining()];
                        in.get(buf);
                        out.write(new String(buf, "US-ASCII"));
                        return true;
                    } else {
                        return false;
                    }
                }
            };
        }
    }
}
