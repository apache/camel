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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Unit testing using different encodings with the TCP protocol.
 */
public class MinaEncodingTest extends BaseMinaTest {

    @Test
    public void testTCPEncodeUTF8InputIsBytes() throws Exception {
        final String uri = String.format("mina:tcp://localhost:%1$s?encoding=UTF-8&sync=false", getPort());
        context.addRoutes(new RouteBuilder() {

            public void configure() {
                from(uri).to("mock:result");
            }
        });

        MockEndpoint endpoint = getMockEndpoint("mock:result");

        // include a UTF-8 char in the text \u0E08 is a Thai elephant
        byte[] body = "Hello Thai Elephant \u0E08".getBytes("UTF-8");

        endpoint.expectedMessageCount(1);
        endpoint.expectedBodiesReceived(body);

        template.sendBody(uri, body);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTCPEncodeUTF8InputIsString() throws Exception {
        final String uri = String.format("mina:tcp://localhost:%1$s?encoding=UTF-8&sync=false", getPort());
        context.addRoutes(new RouteBuilder() {

            public void configure() {
                from(uri).to("mock:result");
            }
        });

        MockEndpoint endpoint = getMockEndpoint("mock:result");

        // include a UTF-8 char in the text \u0E08 is a Thai elephant
        String body = "Hello Thai Elephant \u0E08";

        endpoint.expectedMessageCount(1);
        endpoint.expectedBodiesReceived(body);

        template.sendBody(uri, body);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTCPEncodeUTF8TextLineInputIsString() throws Exception {
        final String uri = String.format("mina:tcp://localhost:%1$s?textline=true&encoding=UTF-8&sync=false", getPort());
        context.addRoutes(new RouteBuilder() {

            public void configure() {
                from(uri).to("mock:result");
            }
        });

        MockEndpoint endpoint = getMockEndpoint("mock:result");

        // include a UTF-8 char in the text \u0E08 is a Thai elephant
        String body = "Hello Thai Elephant \u0E08";

        endpoint.expectedMessageCount(1);
        endpoint.expectedBodiesReceived(body);

        template.sendBody(uri, body);
        assertMockEndpointsSatisfied();
    }

    // Note: MINA does not support sending bytes with the textline codec
    // See TextLineEncoder#encode where the message is converted to String using .toString()
    @Test
    public void testUDPEncodeUTF8InputIsBytes() throws Exception {
        final String uri = String.format("mina:udp://localhost:%1$s?encoding=UTF-8&sync=false", getPort());
        context.addRoutes(new RouteBuilder() {

            public void configure() {
                from(uri).to("mock:result");
            }
        });

        MockEndpoint endpoint = getMockEndpoint("mock:result");

        // include a UTF-8 char in the text \u0E08 is a Thai elephant
        byte[] body = "Hello Thai Elephant \u0E08".getBytes();

        endpoint.expectedMessageCount(1);
        endpoint.expectedBodiesReceived(body);

        template.sendBody(uri, body);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUDPEncodeUTF8InputIsString() throws Exception {
        final String uri = String.format("mina:udp://localhost:%1$s?encoding=UTF-8&sync=false", getPort());
        context.addRoutes(new RouteBuilder() {

            public void configure() {
                from(uri).to("mock:result");
            }
        });

        MockEndpoint endpoint = getMockEndpoint("mock:result");

        // include a UTF-8 char in the text \u0E08 is a Thai elephant
        String body = "Hello Thai Elephant \u0E08";
        //String body = "Hello Thai Elephant Yay";

        endpoint.expectedMessageCount(1);
        endpoint.expectedBodiesReceived(body);

        template.sendBody(uri, body);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUDPEncodeUTF8InputIsStringNoMock() throws Exception {
        // this unit test covers for testUDPEncodeUTF8InputIsString until the encoding is fixed

        // include a UTF-8 char in the text \u0E08 is a Thai elephant
        final String hello = "Hello Thai Elephant \u0E08";
        final String bye = "Hello Thai Elephant \u0E08";

        final String uri = String.format("mina:udp://localhost:%1$s?sync=true&encoding=UTF-8", getPort());
        context.addRoutes(new RouteBuilder() {

            public void configure() {
                from(uri).process(exchange -> {
                    String s = exchange.getIn().getBody(String.class);
                    assertEquals(hello, s);
                    exchange.getMessage().setBody(bye);
                });
            }
        });

        Endpoint endpoint = context.getEndpoint(uri);
        Producer producer = endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(hello);

        producer.start();
        producer.process(exchange);

        String s = exchange.getMessage().getBody(String.class);
        assertEquals(bye, s);

        producer.stop();
    }

    @Test
    public void testInvalidEncoding() throws Exception {
        final String uri = String.format("mina:tcp://localhost:%1$s?textline=true&encoding=XXX&sync=false", getPort());

        try {
            context.addRoutes(new RouteBuilder() {

                public void configure() {
                    from(uri).to("mock:result");
                }
            });
            fail("Should have thrown a ResolveEndpointFailedException due invalid encoding parameter");
        } catch (Exception e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("The encoding: XXX is not supported", iae.getMessage());
        }
    }
}
