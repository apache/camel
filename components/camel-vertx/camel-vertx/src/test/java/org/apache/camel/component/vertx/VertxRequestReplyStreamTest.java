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
package org.apache.camel.component.vertx;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import io.vertx.core.buffer.Buffer;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A stream has no codec on the event bus, so the consumer replies with its content as a Buffer, and the producer sends
 * it as a Buffer.
 */
public class VertxRequestReplyStreamTest extends VertxBaseTestSupport {

    @Test
    public void testEchoBuffer() throws Exception {
        // with stream caching enabled, the received Buffer is a stream cache that the route does not change
        MockEndpoint mock = getMockEndpoint("mock:echo");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(StreamCache.class);

        // send from the template, as a route would turn the Buffer into a stream cache before the producer
        Exchange out = template.request("vertx:foo.echo", e -> e.getIn().setBody(Buffer.buffer("Camel")));

        MockEndpoint.assertIsSatisfied(context);
        assertNull(out.getException());
        Buffer reply = assertInstanceOf(Buffer.class, out.getMessage().getBody());
        assertEquals("Camel", reply.toString());
    }

    @Test
    public void testReplyInputStream() {
        Exchange out = template.request("direct:stream", e -> e.getIn().setBody("Camel"));

        assertNull(out.getException());
        Buffer reply = assertInstanceOf(Buffer.class, out.getMessage().getBody());
        assertEquals("Hello Camel", reply.toString());
    }

    @Test
    public void testForwardBuffer() {
        // the route receives the Buffer as a stream cache and sends it on to another vertx endpoint
        Exchange out = template.request("vertx:foo.forward", e -> e.getIn().setBody(Buffer.buffer("Camel")));

        assertNull(out.getException());
        assertEquals("Bye Camel", out.getMessage().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("vertx:foo.echo").to("mock:echo");

                from("vertx:foo.forward").to("vertx:foo.bye");
                from("vertx:foo.bye")
                        .transform(simple("Bye ${bodyAs(String)}"));

                from("direct:stream").to("vertx:foo.stream");
                from("vertx:foo.stream")
                        .setBody(e -> new ByteArrayInputStream(
                                ("Hello " + e.getMessage().getBody(String.class)).getBytes(StandardCharsets.UTF_8)));
            }
        };
    }
}
